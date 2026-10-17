package dev.sk2andy.materialbrowser.browser.formdraft

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.sk2andy.materialbrowser.browser.gecko.AndroidBrowserEngineSessionPort
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.FormDraftStore
import java.util.concurrent.Executors

internal data class FormDraftOffer(val token: Long)

/** Owns worker ordering and rejects stale page, session and lifecycle callbacks on main. */
internal class FormDraftCoordinator(context: Context, initiallyEnabled: Boolean) {
    private data class Binding(
        val session: AndroidBrowserEngineSessionPort,
        val profileId: String,
        val url: String,
        val generation: Int,
        val tabId: String,
        val isPrivate: Boolean,
        val isCurrent: () -> Boolean,
        val epoch: Long = FormDraftStore.epoch,
        var documentId: String? = null,
        var lookupStarted: Boolean = false,
        var edited: Boolean = false,
    )

    private val store = FormDraftStore(context)
    private val privateDrafts = PrivateFormDrafts()
    private val settings = BrowserSessionStore(context)
    private val preferences = context.applicationContext.getSharedPreferences(
        BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE,
    )
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var binding: Binding? = null
    private var offeredDraft: FormDraft? = null
    private var nextToken = 0L
    private var closed = false
    private data class Mutation(val draft: FormDraft, val delete: Boolean, val epoch: Long)
    private val pendingLock = Any()
    private val pending = linkedMapOf<Pair<String, String>, Mutation>()
    private var draining = false
    private var flushScheduled = false
    private val flush = Runnable { flushPending() }
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        val next = settings.loadFormDraftSavingEnabled()
        if (!closed && next != enabled) setEnabled(next) {}
    }
    var enabled by mutableStateOf(initiallyEnabled)
        private set
    var offer by mutableStateOf<FormDraftOffer?>(null)
        private set

    init {
        preferences.registerOnSharedPreferenceChangeListener(settingsListener)
        if (!initiallyEnabled) worker.execute { store.clear() }
    }

    fun setEnabled(next: Boolean, onCleared: (Boolean) -> Unit) {
        enabled = next
        detach()
        if (!next) privateDrafts.clear()
        if (!next) synchronized(pendingLock) { pending.clear() }
        if (!next) worker.execute {
            val cleared = store.clear()
            main.post { if (!closed) onCleared(cleared) }
        }
    }

    fun bind(
        session: AndroidBrowserEngineSessionPort,
        profileId: String,
        url: String,
        generation: Int,
        tabId: String,
        isPrivate: Boolean,
        isCurrent: () -> Boolean,
    ) {
        if (!enabled || closed || (isPrivate && tabId.isBlank()) ||
            FormDraftRules.pageUrl(url) == null || !isCurrent()) {
            detach()
            return
        }
        val old = binding
        if (old?.session === session && old.profileId == profileId && old.url == url &&
            old.generation == generation && old.tabId == tabId && old.isPrivate == isPrivate &&
            old.epoch == FormDraftStore.epoch) return
        detach()
        val next = Binding(session, profileId, url, generation, tabId, isPrivate, isCurrent)
        binding = next
        session.setFormDraftListener { raw -> accept(next, raw) }
    }

    fun detach() {
        flushPending()
        val old = binding
        binding = null
        offeredDraft = null
        offer = null
        old?.session?.setFormDraftListener(null)
    }

    fun restore(token: Long, onComplete: (Boolean) -> Unit) {
        val current = binding ?: return
        val draft = offeredDraft ?: return
        val documentId = current.documentId ?: return
        if (offer?.token != token || !isCurrentBinding(current)) return
        if (FormDraftRules.sanitize(draft, System.currentTimeMillis()) == null) {
            dismiss(token)
            onComplete(false)
            return
        }
        offer = null
        offeredDraft = null
        current.session.restoreFormDraft(FormDraftCodec.restore(draft, documentId)) { restored ->
            if (isCurrentBinding(current)) onComplete(restored)
        }
    }

    fun dismiss(token: Long) {
        if (offer?.token == token) {
            offer = null
            offeredDraft = null
        }
    }

    fun clear(onComplete: (Boolean) -> Unit = {}) {
        detach()
        privateDrafts.clear()
        synchronized(pendingLock) { pending.clear() }
        worker.execute {
            val cleared = store.clear()
            main.post { if (!closed) onComplete(cleared) }
        }
    }

    fun deleteProfile(profileId: String) {
        if (binding?.profileId == profileId) detach()
        privateDrafts.deleteProfile(profileId)
        worker.execute { store.deleteProfile(profileId, System.currentTimeMillis()) }
    }

    fun deleteTab(tabId: String) {
        if (binding?.tabId == tabId) detach()
        privateDrafts.deleteTab(tabId)
    }

    fun close() {
        closed = true
        detach()
        privateDrafts.clear()
        preferences.unregisterOnSharedPreferenceChangeListener(settingsListener)
        worker.shutdown()
    }

    private fun isCurrentBinding(expected: Binding): Boolean = !closed && enabled &&
        binding === expected && expected.epoch == FormDraftStore.epoch && expected.isCurrent()

    private fun accept(expected: Binding, raw: String) {
        if (!isCurrentBinding(expected)) return
        val snapshot = FormDraftCodec.snapshot(raw)?.takeIf { it.url == expected.url } ?: return
        if (expected.documentId != snapshot.documentId) {
            if (!snapshot.ready || expected.documentId != null) return
            expected.documentId = snapshot.documentId
            expected.lookupStarted = false
            expected.edited = false
            offer = null
            offeredDraft = null
        }
        if (snapshot.ready) {
            if (expected.lookupStarted) return
            expected.lookupStarted = true
            if (expected.isPrivate) {
                privateDrafts.load(expected.tabId, expected.profileId, expected.url, System.currentTimeMillis())
                    ?.let { showOffer(expected, snapshot, it) }
                return
            }
            worker.execute {
                val draft = store.load(expected.profileId, expected.url, System.currentTimeMillis())
                main.post {
                    if (draft != null) showOffer(expected, snapshot, draft)
                }
            }
            return
        }
        expected.edited = true
        offer = null
        offeredDraft = null
        val fields = snapshot.fields.filter { it.value.isNotBlank() }
        val draft = FormDraft(expected.profileId, expected.url, fields, System.currentTimeMillis())
        if (expected.isPrivate) {
            privateDrafts.update(expected.tabId, draft, snapshot.submitted || fields.isEmpty(), draft.updatedAt)
            return
        }
        synchronized(pendingLock) {
            pending[draft.profileId to draft.url] = Mutation(draft, snapshot.submitted || fields.isEmpty(), expected.epoch)
            while (pending.size > FormDraftRules.MAX_PAGES) pending.remove(pending.keys.first())
        }
        if (snapshot.submitted) flushPending()
        else if (!flushScheduled) {
            flushScheduled = true
            main.postDelayed(flush, 300L)
        }
    }

    private fun showOffer(expected: Binding, snapshot: FormDraftSnapshot, draft: FormDraft) {
        if (!isCurrentBinding(expected) || expected.documentId != snapshot.documentId || expected.edited) return
        offeredDraft = draft
        offer = FormDraftOffer(++nextToken)
    }

    private fun flushPending() {
        main.removeCallbacks(flush)
        flushScheduled = false
        synchronized(pendingLock) {
            if (draining || pending.isEmpty()) return
            draining = true
        }
        worker.execute {
            while (true) {
                val mutation = synchronized(pendingLock) {
                    val key = pending.keys.firstOrNull()
                    if (key == null) { draining = false; null } else pending.remove(key)
                } ?: break
                if (mutation.epoch != FormDraftStore.epoch) continue
                val draft = mutation.draft
                if (mutation.delete) store.delete(draft.profileId, draft.url, System.currentTimeMillis())
                else store.save(draft, isPrivate = false, now = System.currentTimeMillis(), expectedEpoch = mutation.epoch)
            }
        }
    }
}
