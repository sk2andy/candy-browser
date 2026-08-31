package dev.sk2andy.materialbrowser.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.recall.RecallDocument
import dev.sk2andy.materialbrowser.recall.RecallMatch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowsingHistoryRepositoryInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences by lazy {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    private val repository by lazy { BrowsingHistoryRepository.get(context) }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        RecallRepository.get(context).clearForTesting()
    }

    @After
    fun tearDown() {
        RecallRepository.get(context).clearForTesting()
        preferences.edit().clear().commit()
    }

    @Test
    fun disabledModeRejectsNewHistoryWithoutDeletingExistingEntries() {
        val existing = HistoryEntry("https://existing.example/", "Existing", 1L, "personal")
        BrowserSessionStore(context).saveHistory(listOf(existing))
        repository.setRecordingMode(HistoryRecordingMode.Disabled)

        val result = repository.record(
            HistoryEntry("https://new.example/", "New", 2L, "personal"),
        )

        assertEquals(listOf(existing), result)
        assertEquals(listOf(existing), repository.snapshot())
    }

    @Test
    fun mutationsReadLatestStoreSnapshotBeforeWriting() {
        val personal = HistoryEntry("https://personal.example/", "Personal", 1L, "personal")
        val work = HistoryEntry("https://work.example/", "Work", 2L, "work")
        BrowserSessionStore(context).saveHistory(listOf(personal))
        repository.setRecordingMode(HistoryRecordingMode.Enabled)
        repository.record(work)

        val retained = repository.remove(listOf(personal))

        assertEquals(listOf(work), retained)
    }

    @Test
    fun rangeClearUsesLatestPersistedSnapshot() {
        val older = HistoryEntry("https://older.example/", "Older", 10L, "personal")
        val latest = HistoryEntry("https://latest.example/", "Latest", 20L, "personal")
        BrowserSessionStore(context).saveHistory(listOf(older))
        BrowserSessionStore(context).saveHistory(listOf(older, latest))

        val retained = repository.clearRange(
            HistoryClearRequest(
                profileIds = setOf("personal"),
                sinceInclusiveMillis = 15L,
                untilExclusiveMillis = 25L,
            ),
        )

        assertTrue(retained.committed)
        assertEquals(listOf(older), retained.history)
        assertEquals(listOf(older), repository.snapshot())
    }

    @Test
    fun clearOnExitOnlyClearsConfiguredHistory() {
        val entry = HistoryEntry("https://example.com/", "Example", 1L)
        BrowserSessionStore(context).saveHistory(listOf(entry))

        repository.setRecordingMode(HistoryRecordingMode.Enabled)
        assertTrue(!repository.clearOnExit())
        assertEquals(listOf(entry), repository.snapshot())

        repository.setRecordingMode(HistoryRecordingMode.ClearOnExit)
        assertTrue(repository.clearOnExit())
        assertTrue(repository.snapshot().isEmpty())
    }

    @Test
    fun clearOnExitRetainsNewSessionHistoryWhenForegroundChangesMidCleanup() {
        val store = BrowserSessionStore(context)
        val previousSession = HistoryEntry("https://old.example/", "Old", 10L)
        val newSession = HistoryEntry("https://new.example/", "New", 20L)
        val tabId = UUID.randomUUID().toString()
        store.saveTabs(
            tabs = listOf(BrowserTab(id = tabId, lastAccessedAt = 1L)),
            selectedTabId = tabId,
        )
        store.saveHistory(listOf(previousSession))
        repository.setRecordingMode(HistoryRecordingMode.ClearOnExit)
        var sessionChecks = 0

        assertTrue(
            repository.clearOnExit(
                isSessionCurrent = {
                    if (sessionChecks++ == 0) {
                        true
                    } else {
                        store.saveHistory(listOf(previousSession, newSession))
                        false
                    }
                },
                untilExclusiveMillis = 15L,
            ),
        )

        assertEquals(listOf(newSession), repository.snapshot())
        assertTrue(store.loadHistorySessionActive())
        assertEquals(
            15L,
            store.loadPendingCandyTrailRedactions().single().untilExclusiveMillis,
        )
    }

    @Test
    fun clearOnExitCommitsTrailRedactionForPersistedTabs() {
        val tabId = UUID.randomUUID().toString()
        val store = BrowserSessionStore(context)
        store.saveTabs(
            tabs = listOf(BrowserTab(id = tabId, lastAccessedAt = 1L)),
            selectedTabId = tabId,
        )
        store.saveHistory(listOf(HistoryEntry("https://example.com/", "Example", 1L)))
        repository.setRecordingMode(HistoryRecordingMode.ClearOnExit)

        assertTrue(repository.clearOnExit())

        assertEquals(
            setOf(tabId),
            store.loadPendingCandyTrailRedactions().single().tabIds,
        )
    }

    @Test
    fun unfinishedClearOnExitSessionIsClearedOnNextStart() {
        val entry = HistoryEntry("https://crash.example/", "Crash", 1L)
        BrowserSessionStore(context).saveHistory(listOf(entry))
        repository.setRecordingMode(HistoryRecordingMode.ClearOnExit)

        repository.beginSession()

        assertTrue(repository.snapshot().isEmpty())
    }

    @Test
    fun resumedForegroundRearmsInterruptedSessionRecovery() {
        repository.setRecordingMode(HistoryRecordingMode.ClearOnExit)
        repository.clearOnExit()
        repository.markForegroundSessionActive()
        repository.record(HistoryEntry("https://resumed.example/", "Resumed", 2L))

        repository.beginSession()

        assertTrue(repository.snapshot().isEmpty())
    }

    @Test
    fun individualHistoryDeletionAlsoDeletesRecallContent() {
        val entry = HistoryEntry("https://example.com/", "Example", 1L, "personal")
        BrowserSessionStore(context).saveHistory(listOf(entry))
        RecallRepository.get(context).index(
            RecallDocument("personal", entry.url, entry.title, "sensitive page phrase", 1L),
        )
        assertTrue(RecallRepository.get(context).awaitIdleForTesting())

        repository.remove(listOf(entry))

        assertTrue(searchRecall("personal", "sensitive page").isEmpty())
    }

    private fun searchRecall(profileId: String, query: String): List<RecallMatch> {
        val latch = CountDownLatch(1)
        var matches = emptyList<RecallMatch>()
        RecallRepository.get(context).search(setOf(profileId), query, 10) { result ->
            matches = result
            latch.countDown()
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        return matches
    }
}
