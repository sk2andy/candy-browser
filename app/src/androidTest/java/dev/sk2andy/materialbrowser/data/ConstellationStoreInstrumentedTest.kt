package dev.sk2andy.materialbrowser.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.constellations.ConstellationDocument
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConstellationStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = ConstellationStore(context)

    @Before
    fun setUp() { assertTrue(store.reset()) }

    @After
    fun tearDown() { store.reset() }

    @Test
    fun sameUrlAndUpdatesRemainInsideOwningProfile() {
        store.setEnabled("personal", true)
        store.setEnabled("work", true)
        store.upsert(document("personal", "https://example.com", 1L).copy(title = "Personal"))
        store.upsert(document("work", "https://example.com", 2L).copy(title = "Work"))
        store.upsert(document("personal", "https://example.com#section", 3L).copy(title = "Updated"))

        assertEquals("Updated", store.documents("personal").single().title)
        assertEquals("Work", store.documents("work").single().title)
        assertTrue(store.hasIndexedVisit("personal", "https://example.com#anchor", 3L))
        assertFalse(store.hasIndexedVisit("personal", "https://example.com", 4L))
        assertFalse(store.hasIndexedVisit("work", "https://example.com", 3L))
        assertTrue(store.documents("missing").isEmpty())
        assertTrue(store.documents("personal' OR 1=1 --").isEmpty())
    }

    @Test
    fun lateOlderVisitCannotReplaceNewerContentAndEmbedding() {
        store.setEnabled("personal", true)
        store.setEnabled("work", true)
        val newest = document("personal", "https://example.com/", 30L).copy(
            title = "Newest title",
            excerpt = "Newest page content",
            embedding = List(512) { if (it == 1) 1f else 0f },
        )
        store.upsert(newest)
        store.upsert(document("personal", "https://example.com/#older-tab", 10L).copy(
            title = "Late older title",
            excerpt = "Older page content",
        ))
        store.upsert(document("work", "https://example.com/", 5L).copy(title = "Work title"))

        assertEquals(newest, store.documents("personal").single())
        assertEquals(5L, store.documents("work").single().visitedAt)

        val newer = newest.copy(visitedAt = 40L, title = "Next visit")
        store.upsert(newer)
        assertEquals(newer, store.documents("personal").single())
    }

    @Test
    fun optInAndDisableDeleteOnlyOneProfileAndPersist() {
        store.upsert(document("personal", "https://example.com/", 1L))
        assertTrue(store.documents("personal").isEmpty())
        store.setEnabled("personal", true)
        store.setEnabled("work", true)
        store.upsert(document("personal", "https://example.com/", 1L))
        store.upsert(document("work", "https://work.example/", 2L))
        store.setEnabled("personal", false)

        val reopened = ConstellationStore(context)
        assertFalse("personal" in reopened.enabledProfiles())
        assertEquals(setOf("work"), reopened.enabledProfiles())
        assertTrue(reopened.documents("personal").isEmpty())
        assertEquals("https://work.example/", reopened.documents("work").single().url)
        reopened.reset()
    }

    @Test
    fun retentionIsPerProfileAndNeverPrunesAnotherMap() {
        store.setEnabled("personal", true)
        store.setEnabled("work", true)
        store.upsert(document("work", "https://work.example/", 0L))
        repeat(181) { index -> store.upsert(document("personal", "https://example.com/$index", index.toLong())) }

        val personal = store.documents("personal")
        assertEquals(180, personal.size)
        assertFalse(personal.any { it.url == "https://example.com/0" })
        assertEquals(1, store.documents("work").size)
    }

    @Test
    fun historyEntryAndHalfOpenRangeDeletionStayProfileScoped() {
        store.setEnabled("personal", true)
        store.setEnabled("work", true)
        for (profile in listOf("personal", "work")) {
            for (timestamp in listOf(10L, 20L, 30L)) {
                store.upsert(document(profile, "https://example.com/$timestamp", timestamp))
            }
        }
        store.deleteRange(HistoryClearRequest(setOf("personal"), 10L, 30L))
        assertEquals(listOf(30L), store.documents("personal").map { it.visitedAt })
        assertEquals(3, store.documents("work").size)
        store.deleteEntries(listOf(HistoryEntry("https://example.com/30#anchor", "", 30L, "personal")))
        assertTrue(store.documents("personal").isEmpty())
        store.deleteProfiles(setOf("work"), forgetEnabled = true)
        assertTrue(store.documents("work").isEmpty())
        assertEquals(setOf("personal"), store.enabledProfiles())
    }

    @Test
    fun malformedVectorsAndUnsafeUrlsNeverEnterMap() {
        store.setEnabled("personal", true)
        val page = document("personal", "https://example.com/", 1L)
        store.upsert(page.copy(embedding = listOf(1f, 0f)))
        store.upsert(page.copy(embedding = List(512) { Float.NaN }))
        store.upsert(page.copy(embedding = List(512) { 0f }))
        store.upsert(page.copy(url = "javascript:alert(1)"))
        assertTrue(store.documents("personal").isEmpty())
        store.upsert(page)
        assertEquals(512, store.documents("personal").single().embedding.size)
    }

    @Test
    fun fullClearRemovesDocumentsAndPreservesExplicitProfileOptIn() {
        store.setEnabled("personal", true)
        store.upsert(document("personal", "https://example.com/", 1L))
        store.clearDocuments()
        assertTrue(store.documents("personal").isEmpty())
        assertEquals(setOf("personal"), store.enabledProfiles())
        assertTrue(store.reset())
        assertTrue(store.enabledProfiles().isEmpty())
    }

    private fun document(profileId: String, url: String, visitedAt: Long) = ConstellationDocument(
        profileId = profileId,
        url = url,
        title = "Page",
        excerpt = "Readable page content",
        visitedAt = visitedAt,
        embedding = List(512) { if (it == 0) 1f else 0f },
    )
}
