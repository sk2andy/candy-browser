package dev.sk2andy.materialbrowser.feeds

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.reader.ReaderBlock
import dev.sk2andy.materialbrowser.reader.ReaderBlockKind
import dev.sk2andy.materialbrowser.reader.ReaderDocument
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val profileId = "feeds-store-${UUID.randomUUID()}"
    private val store = FeedStore(context, profileId)
    private val file = File(context.filesDir, "feeds/${FeedRules.identity(profileId)}.json")

    @After fun tearDown() = store.delete()

    @Test
    fun failedEncodingKeepsPreviousFileAndInterruptedWriteRecoversBackup() {
        val url = "https://example.org/article"
        val article = FeedArticle(FeedRules.identity(url), emptySet(), url, "Article")
        val state = FeedState(articles = listOf(article), loaded = true)
        store.write(state)
        assertTrue(
            runCatching {
                    store.write(
                        state.copy(articles = listOf(article.copy(readingProgress = Float.NaN)))
                    )
                }
                .isFailure
        )
        assertEquals(state, store.read())

        val backup = File("${file.path}.bak")
        assertTrue(file.renameTo(backup))
        assertFalse(file.exists())
        assertEquals(state, FeedStore(context, profileId).read())
        assertTrue(file.exists())
        assertFalse(backup.exists())
    }

    @Test
    fun interruptedDownloadBecomesWaitingWithoutInventingOfflineContent() {
        val url = "https://example.org/article"
        val article =
            FeedArticle(
                FeedRules.identity(url),
                emptySet(),
                url,
                "Article",
                offlineStatus = FeedOfflineStatus.Downloading,
            )
        store.write(FeedState(articles = listOf(article)))
        val recovered = store.read().articles.single()
        assertEquals(FeedOfflineStatus.Waiting, recovered.offlineStatus)
        assertEquals(null, recovered.offlineDocument)
    }

    @Test
    fun malformedStoreIsReportedInsteadOfBeingSilentlyOverwritten() {
        file.parentFile?.mkdirs()
        file.writeText("{broken")
        assertTrue(runCatching { store.read() }.isFailure)
        assertEquals("{broken", file.readText())
    }

    @Test
    fun offlineDocumentsAreLazyAndReplacementCommitsBeforeOldGenerationIsRemoved() {
        val article = offlineArticle("one", "Original offline text")
        val initial = store.write(FeedState(articles = listOf(article), loaded = true))
        val stored = initial.articles.single()
        assertNull(stored.offlineDocument)
        assertNotNull(stored.offlineDocumentId)
        assertEquals(FeedOfflineStatus.Ready, stored.offlineStatus)
        assertEquals(article.offlineDocument, store.loadDocument(stored))
        assertNull(store.read().articles.single().offlineDocument)

        val replacementDocument = document(article.url, "Replacement offline text")
        val replaced =
            store.write(
                initial.copy(articles = listOf(stored.copy(offlineDocument = replacementDocument))),
                initial,
            )
        val replacement = replaced.articles.single()
        assertNotEquals(stored.offlineDocumentId, replacement.offlineDocumentId)
        assertEquals(replacementDocument, store.loadDocument(replacement))
        assertEquals(1, documentFiles().size)
        assertFalse(documentFiles().any { it.name.startsWith(stored.offlineDocumentId!!) })

        val removed =
            store.write(
                replaced.copy(
                    articles =
                        listOf(
                            replacement.copy(
                                offlineDocumentId = null,
                                offlineStatus = FeedOfflineStatus.None,
                            )
                        )
                ),
                replaced,
            )
        assertNull(store.loadDocument(removed.articles.single()))
        assertTrue(documentFiles().isEmpty())
    }

    @Test
    fun failedMetadataCommitRemovesNewGenerationAndKeepsPreviousOfflineDocument() {
        val article = offlineArticle("one", "Original offline text")
        val initial = store.write(FeedState(articles = listOf(article), loaded = true))
        val stored = initial.articles.single()
        val invalid =
            stored.copy(
                readingProgress = Float.NaN,
                offlineDocument = document(article.url, "Uncommitted replacement"),
            )
        assertTrue(
            runCatching { store.write(initial.copy(articles = listOf(invalid)), initial) }.isFailure
        )
        val after = store.read()
        assertEquals(stored.offlineDocumentId, after.articles.single().offlineDocumentId)
        assertEquals(article.offlineDocument, store.loadDocument(after.articles.single()))
        assertEquals(1, documentFiles().size)
    }

    @Test
    fun legacyInlineDocumentsMigrateWithoutRemainingInMetadataOrMemory() {
        val article = offlineArticle("legacy", "Only the offline file contains this sentence")
        file.parentFile?.mkdirs()
        file.writeText(FeedCodec.encode(FeedState(articles = listOf(article))))
        val restored = store.read().articles.single()
        assertNull(restored.offlineDocument)
        assertNotNull(restored.offlineDocumentId)
        assertFalse(file.readText().contains("Only the offline file"))
        assertEquals(article.offlineDocument, store.loadDocument(restored))
        assertEquals(restored, store.read().articles.single())
    }

    @Test
    fun budgetCountsFilesAndStillAllowsCleanupAfterBudgetReduction() {
        val body = "x".repeat(400_000)
        val articles = (1..28).map { offlineArticle("large-$it", body) }
        val initial = store.write(FeedState(articles = articles, loaded = true))
        assertTrue(file.length() < 100_000L)
        assertTrue(
            initial.articles.all { it.offlineDocument == null && it.offlineDocumentId != null }
        )
        val reduced =
            store.write(
                initial.copy(settings = initial.settings.copy(offlineBudgetMb = 10)),
                initial,
            )
        val extra = offlineArticle("extra", body)
        assertTrue(
            runCatching { store.write(reduced.copy(articles = reduced.articles + extra), reduced) }
                .isFailure
        )
        assertEquals(28, documentFiles().size)
        val first = reduced.articles.first()
        val cleaned =
            store.write(
                reduced.copy(
                    articles =
                        reduced.articles.map {
                            if (it.id == first.id)
                                it.copy(
                                    offlineDocumentId = null,
                                    offlineStatus = FeedOfflineStatus.None,
                                )
                            else it
                        }
                ),
                reduced,
            )
        assertEquals(27, documentFiles().size)
        assertEquals(27, cleaned.articles.count { it.offlineDocumentId != null })
    }

    @Test
    fun missingGenerationIsFailedAndProfileDeletionRemovesAllOfflineFiles() {
        val initial =
            store.write(FeedState(articles = listOf(offlineArticle("one", "Offline body"))))
        assertEquals(1, documentFiles().size)
        documentFiles().single().delete()
        val restored = store.read().articles.single()
        assertEquals(FeedOfflineStatus.Failed, restored.offlineStatus)
        assertNull(restored.offlineDocumentId)
        store.write(initial.copy(articles = listOf(offlineArticle("two", "Another body"))))
        store.delete()
        assertFalse(file.exists())
        assertTrue(documentFiles().isEmpty())
    }

    private fun offlineArticle(suffix: String, text: String): FeedArticle {
        val url = "https://example.org/$suffix"
        return FeedArticle(
            id = FeedRules.identity(url),
            sourceIds = emptySet(),
            url = url,
            title = "Article",
            offlineStatus = FeedOfflineStatus.Ready,
            offlineDocument = document(url, text),
        )
    }

    private fun document(url: String, text: String) =
        ReaderDocument(
            title = "Article",
            sourceUrl = url,
            siteName = "example.org",
            blocks = text.chunked(12_000).map { ReaderBlock(ReaderBlockKind.Paragraph, it) },
        )

    private fun documentFiles(): List<File> =
        File(context.filesDir, "feeds/documents/${FeedRules.identity(profileId)}")
            .listFiles()
            ?.toList()
            .orEmpty()
}
