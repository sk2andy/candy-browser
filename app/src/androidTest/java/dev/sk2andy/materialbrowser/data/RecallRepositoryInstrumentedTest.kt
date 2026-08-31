package dev.sk2andy.materialbrowser.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.recall.RecallDocument
import dev.sk2andy.materialbrowser.recall.RecallMatch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class RecallRepositoryInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository by lazy { RecallRepository.get(context) }

    @Before
    fun setUp() {
        assertTrue(repository.clear())
    }

    @After
    fun tearDown() {
        repository.clear()
    }

    @Test
    fun ranksTitleHitsBeforeBodyHitsAndIsolatesProfiles() {
        repository.index(document("personal", "https://title.example", "Candy browser guide", "other words", 1L))
        repository.index(document("personal", "https://body.example", "Other", "candy browser guide", 2L))
        repository.index(document("work", "https://work.example", "Candy browser work", "candy browser", 3L))
        assertTrue(repository.awaitIdleForTesting())

        val matches = search(setOf("personal"), "candy browser", 10)

        assertEquals(listOf("https://title.example/", "https://body.example/"), matches.map(RecallMatch::url))
        assertTrue(matches.all { it.profileId == "personal" })
        assertTrue(matches.first().score > matches.last().score)
    }

    @Test
    fun ranksAllBoundedCandidatesBeforeApplyingResultLimit() {
        repeat(120) { index ->
            repository.index(
                document(
                    profileId = "personal",
                    url = "https://candidate.example/$index",
                    title = if (index == 119) "Wide candidate" else "Page $index",
                    text = "wide candidate reference",
                    visitedAt = index.toLong(),
                ),
            )
        }
        assertTrue(repository.awaitIdleForTesting())

        assertEquals(
            "https://candidate.example/119",
            search(setOf("personal"), "wide candidate", 1).single().url,
        )
    }

    @Test
    fun upsertBoundsTextAndKeepsLatestDocumentPerProfileUrl() {
        repository.index(document("personal", "https://example.com", "Old", "obsolete phrase", 1L))
        repository.index(document("personal", "https://example.com#new", "New", "current phrase", 2L))
        assertTrue(repository.awaitIdleForTesting())

        assertTrue(search(setOf("personal"), "obsolete phrase", 10).isEmpty())
        assertEquals("New", search(setOf("personal"), "current phrase", 10).single().title)
    }

    @Test
    fun entryRangeProfileAndFullCleanupRemoveMatchingContent() {
        val personal = document("personal", "https://personal.example", "Personal", "cleanup phrase", 10L)
        val work = document("work", "https://work.example", "Work", "cleanup phrase", 20L)
        repository.index(personal)
        repository.index(work)
        assertTrue(repository.awaitIdleForTesting())

        assertTrue(
            repository.deleteEntries(
                listOf(HistoryEntry(personal.url, personal.title, personal.visitedAt, personal.profileId)),
            ),
        )
        assertTrue(search(setOf("personal"), "cleanup phrase", 10).isEmpty())
        assertEquals(1, search(setOf("work"), "cleanup phrase", 10).size)

        repository.index(personal)
        assertTrue(repository.awaitIdleForTesting())
        assertTrue(
            repository.deleteRange(
                HistoryClearRequest(setOf("personal"), 5L, 15L),
            ),
        )
        assertTrue(search(setOf("personal"), "cleanup phrase", 10).isEmpty())

        assertTrue(repository.deleteProfiles(setOf("work")))
        assertTrue(search(setOf("work"), "cleanup phrase", 10).isEmpty())
        assertTrue(repository.clear())
    }

    @Test
    fun prunesOldestDocumentAtGlobalEntryBound() {
        repeat(501) { index ->
            repository.index(
                document(
                    profileId = "personal",
                    url = "https://example.com/$index",
                    title = "Page $index",
                    text = "common marker$index",
                    visitedAt = index.toLong(),
                ),
            )
        }
        assertTrue(repository.awaitIdleForTesting())

        assertTrue(search(setOf("personal"), "marker0", 10).isEmpty())
        assertEquals("https://example.com/500", search(setOf("personal"), "marker500", 10).single().url)
    }

    @Test
    fun corruptDatabaseFailsClosedAndCleanupRecoversStorage() {
        repository.index(document("personal", "https://example.com", "Page", "private phrase", 1L))
        assertTrue(repository.awaitIdleForTesting())
        assertTrue(repository.corruptStorageForTesting())

        assertTrue(search(setOf("personal"), "private phrase", 10).isEmpty())
        assertTrue(repository.clear())
        repository.index(document("personal", "https://recovered.example", "Recovered", "new phrase", 2L))
        assertTrue(repository.awaitIdleForTesting())
        assertEquals(1, search(setOf("personal"), "new phrase", 10).size)
    }

    @Test
    fun cleanupEpochRejectsExtractionCapturedBeforeDeletion() {
        val staleEpoch = repository.captureCleanupEpoch()
        assertTrue(repository.clear())
        val payload = JSONObject()
            .put("sourceUrl", "https://stale.example")
            .put("title", "Stale")
            .put("text", "stale private phrase")
            .toString()

        repository.indexExtracted(
            webViewResult = JSONObject.quote(payload),
            profileId = "personal",
            expectedUrl = "https://stale.example",
            expectedCleanupEpoch = staleEpoch,
            visitedAt = 3L,
        )
        assertTrue(repository.awaitIdleForTesting())

        assertTrue(search(setOf("personal"), "stale private", 10).isEmpty())
    }

    @Test
    fun fullClearDeletesDatabaseAndSidecars() {
        repository.index(document("personal", "https://example.com", "Page", "secret phrase", 1L))
        assertTrue(repository.awaitIdleForTesting())
        assertTrue(repository.storageExistsForTesting())

        assertTrue(repository.clear())

        assertFalse(repository.storageExistsForTesting())
    }

    private fun search(profileIds: Set<String>, query: String, limit: Int): List<RecallMatch> {
        val latch = CountDownLatch(1)
        var result = emptyList<RecallMatch>()
        repository.search(profileIds, query, limit) { matches ->
            result = matches
            latch.countDown()
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        return result
    }

    private fun document(
        profileId: String,
        url: String,
        title: String,
        text: String,
        visitedAt: Long,
    ) = RecallDocument(profileId, url, title, text, visitedAt)
}
