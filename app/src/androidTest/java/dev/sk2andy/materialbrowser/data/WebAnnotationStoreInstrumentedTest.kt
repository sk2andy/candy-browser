package dev.sk2andy.materialbrowser.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.annotation.WebAnnotation
import dev.sk2andy.materialbrowser.recall.RecallMatch
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebAnnotationStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = WebAnnotationStore(context)
    private val annotation = WebAnnotation(
        id = "test-quote", profileId = "work", url = "https://example.org/article", title = "Article",
        quote = "Original quotation survives a changed article", note = "Check return policy", createdAt = 100L,
    )

    @Before
    fun setUp() { assertTrue(store.clear()) }

    @After
    fun tearDown() { store.clear() }

    @Test
    fun explicitQuotesSurviveReopenAndRemainProfileScoped() {
        assertTrue(store.save(annotation, isPrivate = false))
        assertTrue(store.save(annotation.copy(id = "personal-quote", profileId = "personal"), isPrivate = false))
        assertEquals(2, WebAnnotationStore(context).all().size)
        assertTrue(store.deleteProfile("work"))
        assertEquals(listOf("personal"), store.all().map { it.profileId })
        assertEquals(annotation.quote, store.all().single().quote)
        assertFalse(AppDataArchiveRules.shouldExportRelativePath("no_backup/web_annotations.json"))
        assertFalse(AppDataArchiveRules.isAllowedArchiveRelativePath("no_backup/web_annotations.json.bak"))
    }

    @Test
    fun privateInvalidAndCorruptDataCannotEnterSavedQuotes() {
        assertFalse(store.save(annotation, isPrivate = true))
        assertTrue(store.all().isEmpty())
        assertFalse(store.save(annotation.copy(quote = "x".repeat(8_001)), isPrivate = false))
        assertTrue(store.save(annotation, isPrivate = false))
        File(context.noBackupFilesDir, "web_annotations.json").writeText("corrupt")
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun importCleanupRejectsPreviouslyQueuedSaveWithoutBlockingFreshExplicitSave() {
        val queuedEpoch = WebAnnotationStore.epoch
        assertTrue(store.save(annotation, isPrivate = false))
        assertTrue(WebAnnotationStore(context).clear())

        assertFalse(store.save(annotation, isPrivate = false, expectedEpoch = queuedEpoch))
        assertTrue(store.all().isEmpty())
        assertTrue(store.save(annotation, isPrivate = false))
        assertEquals(listOf(annotation), store.all())
    }

    @Test
    fun recallFindsNotesWithoutOverwritingOrDependingOnIndexedPageText() {
        assertTrue(store.save(annotation, isPrivate = false))
        val repository = RecallRepository.get(context)
        assertTrue(repository.clear())
        fun search(profiles: Set<String>): List<RecallMatch> {
            val finished = CountDownLatch(1)
            val result = AtomicReference<List<RecallMatch>>()
            repository.search(profiles, "return policy", 10) {
                result.set(it)
                finished.countDown()
            }
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            return result.get()
        }
        assertEquals(annotation.url, search(setOf("work")).single().url)
        assertTrue(search(setOf("personal")).isEmpty())
        assertEquals(annotation, store.all().single())
        assertTrue(store.delete(annotation.id, annotation.profileId))
        assertTrue(search(setOf("work")).isEmpty())
    }
}
