package dev.sk2andy.materialbrowser.data

import android.content.Context
import dev.sk2andy.materialbrowser.browser.BrowserTab
import java.util.UUID

internal enum class HistoryRecordingMode(val storedId: String) {
    Enabled("enabled"),
    ClearOnExit("clear_on_exit"),
    Disabled("disabled"),
    ;

    internal companion object {
        fun fromStoredValue(value: String?): HistoryRecordingMode = when (value) {
            null, Enabled.storedId, Enabled.name -> Enabled
            ClearOnExit.storedId, ClearOnExit.name -> ClearOnExit
            Disabled.storedId, Disabled.name -> Disabled
            else -> Disabled
        }
    }
}

internal data class HistoryMutationResult(
    val history: List<HistoryEntry>,
    val committed: Boolean,
)

internal class BrowsingHistoryRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val store = BrowserSessionStore(appContext)
    private val recallRepository = RecallRepository.get(appContext)

    @Synchronized
    fun snapshot(): List<HistoryEntry> = store.loadHistory()

    @Synchronized
    fun record(entry: HistoryEntry): List<HistoryEntry> {
        val current = store.loadHistory()
        if (recordingMode() == HistoryRecordingMode.Disabled) return current
        val updated = BrowsingLibraryRules.addHistory(current, entry)
        if (updated != current) store.saveHistory(updated)
        return updated
    }

    @Synchronized
    fun remove(entries: Collection<HistoryEntry>): List<HistoryEntry> {
        val current = store.loadHistory()
        if (!recallRepository.deleteEntries(entries)) return current
        return mutate(current) { history -> BrowsingHistoryRules.removeEntries(history, entries) }
    }

    @Synchronized
    fun clearProfiles(
        profileIds: Set<String>,
        trailTabIds: Set<String> = emptySet(),
        recallAlreadyDeleted: Boolean = false,
    ): HistoryMutationResult {
        if (!recallAlreadyDeleted && !recallRepository.deleteProfiles(profileIds)) {
            return HistoryMutationResult(history = store.loadHistory(), committed = false)
        }
        return mutateWithTrailRedaction(trailTabIds) { current ->
            BrowsingHistoryRules.removeProfiles(current, profileIds)
        }
    }

    @Synchronized
    fun clearRange(
        request: HistoryClearRequest,
        trailTabIds: Set<String> = emptySet(),
    ): HistoryMutationResult {
        if (!recallRepository.deleteRange(request)) {
            return HistoryMutationResult(history = store.loadHistory(), committed = false)
        }
        return mutateWithTrailRedaction(
            trailTabIds = trailTabIds,
            sinceInclusiveMillis = request.sinceInclusiveMillis,
            untilExclusiveMillis = request.untilExclusiveMillis,
        ) { current ->
            BrowsingHistoryRules.removeRange(current, request)
        }
    }

    @Synchronized
    fun clear(): List<HistoryEntry> {
        val current = store.loadHistory()
        if (!recallRepository.clear()) return current
        return mutate(current) { emptyList() }
    }

    @Synchronized
    fun recordingMode(): HistoryRecordingMode = store.loadHistoryRecordingMode()

    @Synchronized
    fun setRecordingMode(mode: HistoryRecordingMode): Boolean =
        store.saveHistoryRecordingMode(
            mode = mode,
            sessionActive = mode == HistoryRecordingMode.ClearOnExit,
        )

    @Synchronized
    fun beginSession() {
        val clearsOnExit = recordingMode() == HistoryRecordingMode.ClearOnExit
        if (clearsOnExit && store.loadHistorySessionActive()) {
            recallRepository.clearAsync()
            saveClearedHistoryWithTrailRedaction(
                sessionActive = true,
                clearRecall = false,
            )
        } else {
            store.saveHistorySessionActive(clearsOnExit)
        }
    }

    @Synchronized
    fun markForegroundSessionActive() {
        store.saveHistorySessionActive(recordingMode() == HistoryRecordingMode.ClearOnExit)
    }

    fun clearOnExit(): Boolean = clearOnExit(
        isSessionCurrent = { true },
        untilExclusiveMillis = Long.MAX_VALUE,
    )

    fun clearOnExit(
        isSessionCurrent: () -> Boolean,
        untilExclusiveMillis: Long,
    ): Boolean {
        val shouldClear = synchronized(this) {
            recordingMode() == HistoryRecordingMode.ClearOnExit && isSessionCurrent()
        }
        if (!shouldClear || !recallRepository.clear()) return false
        return synchronized(this) {
            val sessionStillCurrent = isSessionCurrent()
            val retainedHistory = if (sessionStillCurrent) {
                emptyList()
            } else {
                store.loadHistory().filter { entry ->
                    entry.lastVisitedAt >= untilExclusiveMillis
                }
            }
            saveClearedHistoryWithTrailRedaction(
                sessionActive = !sessionStillCurrent &&
                    recordingMode() == HistoryRecordingMode.ClearOnExit,
                clearRecall = false,
                retainedHistory = retainedHistory,
                untilExclusiveMillis = untilExclusiveMillis,
            )
        }
    }

    private fun mutate(
        current: List<HistoryEntry> = store.loadHistory(),
        transform: (List<HistoryEntry>) -> List<HistoryEntry>,
    ): List<HistoryEntry> {
        val updated = transform(current)
        return if (updated == current || store.commitHistory(updated)) updated else current
    }

    private fun mutateWithTrailRedaction(
        trailTabIds: Set<String>,
        sinceInclusiveMillis: Long = Long.MIN_VALUE,
        untilExclusiveMillis: Long = Long.MAX_VALUE,
        transform: (List<HistoryEntry>) -> List<HistoryEntry>,
    ): HistoryMutationResult {
        val current = store.loadHistory()
        val updated = transform(current)
        val redaction = trailRedaction(
            tabIds = trailTabIds,
            sinceInclusiveMillis = sinceInclusiveMillis,
            untilExclusiveMillis = untilExclusiveMillis,
        )
        if (updated == current && redaction == null) {
            return HistoryMutationResult(history = updated, committed = true)
        }
        val saved = if (redaction == null) {
            store.commitHistory(updated)
        } else {
            store.saveHistoryAndTrailRedaction(updated, redaction)
        }
        return HistoryMutationResult(
            history = if (saved) updated else current,
            committed = saved,
        )
    }

    private fun saveClearedHistoryWithTrailRedaction(
        sessionActive: Boolean,
        clearRecall: Boolean = true,
        retainedHistory: List<HistoryEntry> = emptyList(),
        untilExclusiveMillis: Long = Long.MAX_VALUE,
    ): Boolean {
        if (clearRecall && !recallRepository.clear()) return false
        val redaction = trailRedaction(
            tabIds = persistentRegularTabIds(),
            sinceInclusiveMillis = Long.MIN_VALUE,
            untilExclusiveMillis = untilExclusiveMillis,
        )
        return if (redaction == null) {
            store.saveHistoryAndSessionState(retainedHistory, sessionActive)
        } else {
            store.saveHistoryAndTrailRedaction(
                history = retainedHistory,
                redaction = redaction,
                sessionActive = sessionActive,
            )
        }
    }

    private fun persistentRegularTabIds(): Set<String> = buildSet {
        store.loadTabs().first.asSequence()
            .filterNot { tab -> tab.isIncognito }
            .mapTo(this, BrowserTab::id)
        SnoozedTabStore(appContext).load().asSequence()
            .map { snoozed -> snoozed.tab.id }
            .forEach(::add)
    }

    private fun trailRedaction(
        tabIds: Set<String>,
        sinceInclusiveMillis: Long,
        untilExclusiveMillis: Long,
    ): PendingCandyTrailRedaction? {
        if (tabIds.isEmpty() || sinceInclusiveMillis >= untilExclusiveMillis) return null
        return PendingCandyTrailRedaction(
            id = UUID.randomUUID().toString(),
            tabIds = tabIds,
            sinceInclusiveMillis = sinceInclusiveMillis,
            untilExclusiveMillis = untilExclusiveMillis,
        )
    }

    companion object {
        @Volatile
        private var instance: BrowsingHistoryRepository? = null

        fun get(context: Context): BrowsingHistoryRepository = instance ?: synchronized(this) {
            instance ?: BrowsingHistoryRepository(context.applicationContext).also { instance = it }
        }
    }
}
