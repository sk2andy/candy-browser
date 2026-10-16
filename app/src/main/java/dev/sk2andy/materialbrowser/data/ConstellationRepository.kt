package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.sk2andy.materialbrowser.browser.ProfileProtectionSession
import dev.sk2andy.materialbrowser.browser.guest.GuestBubbleSessionStore
import dev.sk2andy.materialbrowser.constellations.ConstellationDocument
import dev.sk2andy.materialbrowser.constellations.ConstellationEmbedder
import dev.sk2andy.materialbrowser.constellations.ConstellationSnapshot
import dev.sk2andy.materialbrowser.recall.RecallDocument
import dev.sk2andy.materialbrowser.recall.RecallExtractionParser
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Short storage operations never wait for model downloads or inference. */
internal class ConstellationRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val store = ConstellationStore(appContext)
    private val sessionStore = BrowserSessionStore(appContext)
    private val io = Executors.newSingleThreadExecutor { Thread(it, "constellation-store") }
    private val inference = Executors.newSingleThreadExecutor { Thread(it, "constellation-model") }
    private val main = Handler(Looper.getMainLooper())
    private val epoch = AtomicLong()
    private val downloading = AtomicBoolean()
    private val notificationPending = AtomicBoolean()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val pending = linkedMapOf<String, PendingDocument>()
    @Volatile private var enabledProfiles: Set<String> = emptySet()
    @Volatile private var modelReady = false
    @Volatile private var initialValidationComplete = false
    @Volatile private var indexing = false
    @Volatile private var progress: Float? = null
    @Volatile private var modelFailure = false
    @Volatile private var indexingProfileId: String? = null
    private val failedProfiles = ConcurrentHashMap.newKeySet<String>()

    init {
        io.execute { runCatching { enabledProfiles = store.enabledProfiles() }; notifyChanged() }
        inference.execute {
            try {
                modelReady = ConstellationEmbedder(appContext).use { it.isModelReady() }
            } finally {
                initialValidationComplete = true
                notifyChanged()
            }
        }
    }

    fun isEnabled(profileId: String): Boolean = profileId in enabledProfiles
    fun captureCleanupEpoch(): Long = epoch.get()
    fun addListener(listener: () -> Unit) { listeners += listener }
    fun removeListener(listener: () -> Unit) { listeners -= listener }

    fun snapshot(profileId: String, onComplete: (ConstellationSnapshot) -> Unit) {
        val expectedEpoch = epoch.get()
        io.execute {
            val documents = runCatching {
                if (isAccessible(profileId) && isEnabled(profileId)) store.documents(profileId)
                else emptyList()
            }.getOrElse { failedProfiles += profileId; emptyList() }
            main.post {
                val accessible = isAccessible(profileId)
                onComplete(ConstellationSnapshot(
                    profileId = profileId,
                    enabled = accessible && isEnabled(profileId),
                    documents = documents.takeIf {
                        accessible && isEnabled(profileId) && expectedEpoch == epoch.get()
                    }.orEmpty(),
                    modelReady = modelReady,
                    busy = isIndexingProfile(profileId) || downloading.get(),
                    downloadProgress = progress,
                    error = modelFailure || profileId in failedProfiles,
                ))
            }
        }
    }

    fun setEnabled(profileId: String, enabled: Boolean) {
        if (!ConstellationStore.validProfile(profileId) || !isAccessible(profileId)) return
        if (!enabled) {
            epoch.incrementAndGet()
            enabledProfiles = enabledProfiles - profileId
            synchronized(pending) { pending.entries.removeAll { it.value.document.profileId == profileId } }
        }
        io.execute {
            val saved = runCatching {
                store.setEnabled(profileId, enabled)
                enabledProfiles = store.enabledProfiles()
            }.isSuccess
            if (saved) {
                failedProfiles -= profileId
            } else {
                failedProfiles += profileId
                enabledProfiles = runCatching { store.enabledProfiles() }.getOrDefault(emptySet())
            }
            notifyChanged()
        }
    }

    fun downloadModel() {
        if (!downloading.compareAndSet(false, true)) return
        modelFailure = false
        progress = 0f
        notifyChanged()
        inference.execute {
            try {
                modelReady = ConstellationEmbedder(appContext).use { embedder ->
                    var previousPercent = -1
                    embedder.downloadModel { value ->
                        val bounded = value.coerceIn(0f, 1f)
                        val percent = (bounded * 100).toInt()
                        if (percent != previousPercent) {
                            previousPercent = percent
                            progress = bounded
                            notifyChanged()
                        }
                    }
                }
                modelFailure = !modelReady
            } finally {
                progress = null
                downloading.set(false)
                notifyChanged()
            }
        }
    }

    fun indexExtracted(
        rawJson: String?,
        profileId: String,
        expectedUrl: String,
        visitedAt: Long,
        expectedCleanupEpoch: Long,
        isPrivate: Boolean,
    ) {
        if (isPrivate || rawJson == null || rawJson.length > 150_000 ||
            (!modelReady && initialValidationComplete) ||
            !isEnabled(profileId) || expectedCleanupEpoch != epoch.get()) return
        io.execute {
            if (expectedCleanupEpoch != epoch.get() || !isAccessible(profileId) ||
                !isEnabled(profileId)) return@execute
            val alreadyIndexed = runCatching {
                store.hasIndexedVisit(profileId, expectedUrl, visitedAt)
            }.getOrElse { failedProfiles += profileId; true }
            if (alreadyIndexed) return@execute
            val document = RecallExtractionParser.parse(
                webViewResult = JSONObject.quote(rawJson), profileId = profileId,
                expectedUrl = expectedUrl, visitedAt = visitedAt,
            ) ?: return@execute
            synchronized(pending) {
                val key = "$profileId\u0000${document.url}"
                val existing = pending[key]
                if (existing != null && existing.document.visitedAt > document.visitedAt) return@execute
                pending.remove(key)
                pending[key] = PendingDocument(document, expectedCleanupEpoch)
                while (pending.size > MAX_PENDING_DOCUMENTS) pending.remove(pending.keys.first())
                if (!indexing) {
                    indexing = true
                    inference.execute(::drainPending)
                }
            }
            notifyChanged()
        }
    }

    private fun drainPending() {
        try {
            if (!modelReady) {
                synchronized(pending) { pending.clear() }
                return
            }
            ConstellationEmbedder(appContext).use { embedder ->
                for (batchIndex in 0 until MAX_PENDING_DOCUMENTS) {
                    val request = synchronized(pending) {
                        pending.entries.firstOrNull()?.let { entry ->
                            pending.remove(entry.key)
                            entry.value
                        }
                    } ?: break
                    val document = request.document
                    indexingProfileId = document.profileId
                    notifyChanged()
                    if (request.epoch != epoch.get() || !isEnabled(document.profileId) ||
                        !isAccessible(document.profileId)) continue
                    val embedding = embedder.embed(document.title, document.text)
                    if (embedding == null) { failedProfiles += document.profileId; continue }
                    io.execute {
                        if (request.epoch != epoch.get() || !isEnabled(document.profileId) ||
                            !isAccessible(document.profileId) || !hasHistorySource(document)) return@execute
                        val saved = runCatching {
                            store.upsert(ConstellationDocument(
                                profileId = document.profileId, url = document.url,
                                title = document.title,
                                excerpt = document.text.take(ConstellationStore.MAX_EXCERPT_CHARS),
                                visitedAt = document.visitedAt, embedding = embedding,
                            ))
                        }.isSuccess
                        if (saved) failedProfiles -= document.profileId else failedProfiles += document.profileId
                        notifyChanged()
                    }
                }
            }
        } finally {
            indexingProfileId = null
            synchronized(pending) {
                indexing = false
                if (pending.isNotEmpty()) {
                    indexing = true
                    inference.execute(::drainPending)
                }
            }
            notifyChanged()
        }
    }

    fun deleteEntries(entries: Collection<HistoryEntry>): Boolean = cleanup {
        store.deleteEntries(entries)
    }

    fun deleteProfiles(profileIds: Set<String>): Boolean = cleanup {
        store.deleteProfiles(profileIds, forgetEnabled = true)
        enabledProfiles = store.enabledProfiles()
    }

    fun deleteRange(request: HistoryClearRequest): Boolean = cleanup { store.deleteRange(request) }
    fun clear(): Boolean = cleanup { store.clearDocuments() }

    fun reset(): Boolean {
        epoch.incrementAndGet()
        enabledProfiles = emptySet()
        failedProfiles.clear()
        synchronized(pending) { pending.clear() }
        return runCatching {
            io.submit<Boolean> { store.reset() }.get()
        }.getOrDefault(false).also { notifyChanged() }
    }

    private fun cleanup(operation: () -> Unit): Boolean {
        epoch.incrementAndGet()
        synchronized(pending) { pending.clear() }
        return runCatching {
            io.submit<Boolean> { operation(); true }.get()
        }.getOrElse {
            enabledProfiles = emptySet()
            runCatching { io.submit<Boolean> { store.reset() }.get() }.getOrDefault(false)
        }.also { notifyChanged() }
    }

    private fun isIndexingProfile(profileId: String): Boolean = indexingProfileId == profileId ||
        synchronized(pending) { pending.values.any { it.document.profileId == profileId } }

    private fun isAccessible(profileId: String): Boolean {
        if (AppDataTransferLock.isActive(appContext) || GuestBubbleSessionStore(appContext).isLocked()) return false
        val profiles = sessionStore.loadProfiles().first
        val configured = if (sessionStore.loadProfilesEnabled()) profiles else profiles.take(1)
        val profile = configured.firstOrNull { it.id == profileId } ?: return false
        return profile.syncedDeviceId == null && ProfileProtectionSession.isAccessible(profile)
    }

    private fun hasHistorySource(document: RecallDocument): Boolean =
        sessionStore.loadHistory().any { entry ->
            entry.profileId == document.profileId &&
                CanonicalWebUrl.key(entry.url) == document.url &&
                entry.lastVisitedAt >= document.visitedAt
        }

    private fun notifyChanged() {
        if (notificationPending.compareAndSet(false, true)) {
            main.post {
                notificationPending.set(false)
                listeners.forEach { it() }
            }
        }
    }
    private data class PendingDocument(val document: RecallDocument, val epoch: Long)

    companion object {
        private const val MAX_PENDING_DOCUMENTS = 4
        @Volatile private var instance: ConstellationRepository? = null
        fun get(context: Context): ConstellationRepository = instance ?: synchronized(this) {
            instance ?: ConstellationRepository(context.applicationContext).also { instance = it }
        }
    }
}
