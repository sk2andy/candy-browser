package dev.sk2andy.materialbrowser.feeds

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.ProfileLockTrigger
import dev.sk2andy.materialbrowser.browser.ProfileProtection
import dev.sk2andy.materialbrowser.browser.ProfileProtectionSession
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.reader.ReaderLibraryStore
import dev.sk2andy.materialbrowser.reader.ReaderSnapshot
import java.util.UUID
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedRepositoryInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val profiles = BrowserSessionStore(context)
    private val firstId = "feeds-test-${UUID.randomUUID()}"
    private val secondId = "feeds-test-${UUID.randomUUID()}"
    private lateinit var savedProfiles: Pair<List<BrowserProfile>, String>

    @Before
    fun setUp() {
        savedProfiles = profiles.loadProfiles()
        profiles.saveProfiles(
            listOf(
                BrowserProfile(
                    firstId,
                    "🍬",
                    protection = ProfileProtection(ProfileLockTrigger.AppClosed),
                ),
                BrowserProfile(secondId, "🍭"),
            ),
            firstId,
        )
        ProfileProtectionSession.unlock(firstId)
    }

    @After
    fun tearDown() = runBlocking {
        FeedRepository.deleteProfile(context, firstId).getOrThrow()
        FeedRepository.deleteProfile(context, secondId).getOrThrow()
        ProfileProtectionSession.forget(firstId)
        ProfileProtectionSession.forget(secondId)
        profiles.saveProfiles(savedProfiles.first, savedProfiles.second)
    }

    @Test
    fun browserReaderActionsKeepFullDocumentAndShareTheListeningQueue() = runBlocking {
        val repository = FeedRepository.get(context, firstId)
        val document = FeedRules.document(preview().articles.single())
        repository.saveWebsiteArticle(document, starred = true, queued = true).getOrThrow()
        val article = repository.state.value.articles.single()
        assertEquals(FeedArticleOrigin.Website, article.origin)
        assertTrue(article.isStarred)
        assertEquals(listOf(article.id), repository.state.value.queue)
        assertEquals(document, repository.loadOfflineDocument(article.id).getOrThrow())
        repository.saveWebsiteArticle(document, queued = false).getOrThrow()
        assertTrue(repository.state.value.articles.single().isStarred)
        assertTrue(repository.state.value.queue.isEmpty())
        val privateRepository = FeedRepository.get(context, firstId, isPrivate = true)
        assertTrue(privateRepository.saveWebsiteArticle(document, starred = true).isFailure)
    }

    @Test
    fun deletingWebsiteRemovesDocumentAndQueueWithoutLegacyImportResurrection() = runBlocking {
        val repository = FeedRepository.get(context, firstId)
        val document = FeedRules.document(preview().articles.single())
        val snapshot =
            ReaderSnapshot("deleted-reader-copy", document, 0f, 1L, sourceProfileId = firstId)
        repository.importWebsiteSnapshot(snapshot).getOrThrow()
        val id = repository.state.value.articles.single().id
        repository.setQueued(id, true).getOrThrow()
        repository.removeWebsiteArticle(id).getOrThrow()
        assertTrue(repository.state.value.articles.isEmpty())
        assertTrue(repository.state.value.queue.isEmpty())
        assertTrue(repository.loadOfflineDocument(id).getOrNull() == null)
        val restored = FeedStore(context, firstId).read()
        assertEquals(setOf("deleted-reader-copy"), restored.deletedWebsiteSnapshotIds)
        repository.importWebsiteSnapshot(snapshot).getOrThrow()
        assertTrue(repository.state.value.articles.isEmpty())
        repository.importWebsiteSnapshot(snapshot.copy(id = "explicit-new-save")).getOrThrow()
        assertEquals(document, repository.loadOfflineDocument(id).getOrThrow())
    }

    @Test
    fun deletingBeforeLegacyImportStillRejectsThatEarlierSnapshot() = runBlocking {
        val repository = FeedRepository.get(context, firstId)
        // Legacy Reader persistence sanitizes extracted documents and requires 80 readable chars.
        val document =
            FeedRules.document(
                preview()
                    .articles
                    .single()
                    .copy(
                        content =
                            "A complete saved article from the browser, with enough readable text to pass " +
                                "the Reader extraction contract and remain available without a network connection."
                    )
            )
        val readerStore = ReaderLibraryStore(context)
        val snapshot =
            requireNotNull(
                readerStore.saveSnapshot(
                    document,
                    0f,
                    isPrivate = false,
                    sourceProfileId = firstId,
                    allowEviction = false,
                )
            )
        try {
            repository.saveWebsiteArticle(document, starred = true).getOrThrow()
            repository.removeWebsiteArticle(FeedRules.identity(document.sourceUrl)).getOrThrow()
            repository.importWebsiteSnapshot(snapshot).getOrThrow()
            assertTrue(repository.state.value.articles.isEmpty())
            assertTrue(snapshot.id in repository.state.value.deletedWebsiteSnapshotIds)
        } finally {
            readerStore.deleteSnapshot(snapshot.id, isPrivate = false)
        }
    }

    @Test
    fun browserReaderSaveJoinsFeedIdentityAndReplacesItsOlderDocument() = runBlocking {
        val repository = FeedRepository.get(context, firstId)
        repository.subscribe(preview()).getOrThrow()
        val article = repository.state.value.articles.single()
        val excerpt = FeedRules.document(article)
        repository.updateOffline(article.id, FeedOfflineStatus.Ready, excerpt).getOrThrow()
        val full = excerpt.copy(title = "Full browser article")
        repository.saveWebsiteArticle(full, starred = true).getOrThrow()
        assertEquals(FeedArticleOrigin.Website, repository.state.value.articles.single().origin)
        assertEquals(article.sourceIds, repository.state.value.articles.single().sourceIds)
        assertEquals(full, repository.loadOfflineDocument(article.id).getOrThrow())
        repository.removeWebsiteArticle(article.id).getOrThrow()
        repository.updateOffline(article.id, FeedOfflineStatus.Ready, excerpt).getOrThrow()
        repository
            .importWebsiteSnapshot(
                ReaderSnapshot("new-browser-snapshot", full, 0f, 1L, sourceProfileId = firstId)
            )
            .getOrThrow()
        assertEquals(full, repository.loadOfflineDocument(article.id).getOrThrow())
    }

    @Test
    fun deletingWebsiteAlsoInAFeedPreservesItsInboxEntry() = runBlocking {
        val repository = FeedRepository.get(context, firstId)
        repository.subscribe(preview()).getOrThrow()
        val article = repository.state.value.articles.single()
        val snapshot =
            ReaderSnapshot(
                "mixed-copy",
                FeedRules.document(article),
                0f,
                1L,
                sourceProfileId = firstId,
            )
        repository.importWebsiteSnapshot(snapshot).getOrThrow()
        repository.setStarred(article.id, true).getOrThrow()
        repository.removeWebsiteArticle(article.id).getOrThrow()
        val remaining = repository.state.value.articles.single()
        assertEquals(FeedArticleOrigin.Feed, remaining.origin)
        assertEquals(article.sourceIds, remaining.sourceIds)
        assertFalse(remaining.isStarred)
        assertEquals(FeedOfflineStatus.None, remaining.offlineStatus)
        assertTrue(remaining.offlineDocumentId == null)
    }

    @Test
    fun subscriptionsAndReadingStatePersistOnlyInTheirOwningProfile() = runBlocking {
        val first = FeedRepository.get(context, firstId)
        val second = FeedRepository.get(context, secondId)
        first.subscribe(preview()).getOrThrow()
        val articleId = first.state.value.articles.single().id
        first.setStarred(articleId, true).getOrThrow()
        first.updateProgress(articleId, 0.42f).getOrThrow()
        first.setRead(setOf(articleId), true).getOrThrow()
        second.load().getOrThrow()

        assertTrue(second.state.value.articles.isEmpty())
        val persisted = FeedStore(context, firstId).read().articles.single()
        assertTrue(persisted.isStarred)
        assertTrue(persisted.isRead)
        assertEquals(0.42f, persisted.readingProgress)
    }

    @Test
    fun privateSessionCannotReadOrChangeSavedSubscriptions() = runBlocking {
        val normal = FeedRepository.get(context, firstId)
        normal.subscribe(preview()).getOrThrow()
        val before = FeedStore(context, firstId).read()
        val privateRepository = FeedRepository.get(context, firstId, isPrivate = true)
        privateRepository.load().getOrThrow()

        assertTrue(privateRepository.state.value.articles.isEmpty())
        assertTrue(privateRepository.subscribe(preview()).isFailure)
        assertTrue(privateRepository.setRead(setOf(before.articles.single().id), true).isFailure)
        assertEquals(before, FeedStore(context, firstId).read())
    }

    @Test
    fun lockedProfileClearsObservableDataAndRejectsLateMutation() = runBlocking {
        val repository = FeedRepository.get(context, firstId)
        repository.subscribe(preview()).getOrThrow()
        val articleId = repository.state.value.articles.single().id
        ProfileProtectionSession.lock(setOf(firstId))
        FeedRepository.invalidate(setOf(firstId))

        assertTrue(repository.state.value.articles.isEmpty())
        assertTrue(repository.setStarred(articleId, true).isFailure)
        assertTrue(repository.load().isFailure)
        assertTrue(repository.state.value.articles.isEmpty())
        assertFalse(FeedStore(context, firstId).read().articles.single().isStarred)

        ProfileProtectionSession.unlock(firstId)
        repository.load().getOrThrow()
        assertEquals(1, repository.state.value.articles.size)
    }

    @Test
    fun websiteImportRequiresProvenanceAndDoesNotResetLaterEdits() = runBlocking {
        val repository = FeedRepository.get(context, firstId)
        val article = preview().articles.single()
        val legacy = ReaderSnapshot("snapshot", FeedRules.document(article), 0.4f, 1L)
        assertTrue(repository.importWebsiteSnapshot(legacy).isFailure)
        assertTrue(
            repository.importWebsiteSnapshot(legacy.copy(sourceProfileId = secondId)).isFailure
        )
        repository.importWebsiteSnapshot(legacy.copy(sourceProfileId = firstId)).getOrThrow()
        val imported = repository.state.value.articles.single()
        assertEquals(FeedArticleOrigin.Website, imported.origin)
        assertEquals(0, repository.state.value.unreadCount)
        repository.setTags(imported.id, setOf("Research")).getOrThrow()
        repository.updateProgress(imported.id, 0.7f).getOrThrow()
        repository.updateOffline(imported.id, FeedOfflineStatus.None).getOrThrow()
        repository.importWebsiteSnapshot(legacy.copy(sourceProfileId = firstId)).getOrThrow()

        val after = repository.state.value.articles.single()
        assertEquals(setOf("Research"), after.tags)
        assertEquals(0.7f, after.readingProgress)
        assertEquals(FeedOfflineStatus.None, after.offlineStatus)
    }

    @Test
    fun explicitWebsiteResaveReplacesRemovedDownloadButRepeatedImportDoesNot() = runBlocking {
        val repository = FeedRepository.get(context, firstId)
        repository.subscribe(preview()).getOrThrow()
        val article = repository.state.value.articles.single()
        val original =
            ReaderSnapshot(
                "original-snapshot",
                FeedRules.document(article),
                0.2f,
                1L,
                sourceProfileId = firstId,
            )
        repository.importWebsiteSnapshot(original).getOrThrow()
        repository.setRead(setOf(article.id), true).getOrThrow()
        repository.setStarred(article.id, true).getOrThrow()
        repository.setTags(article.id, setOf("Research")).getOrThrow()
        repository.updateOffline(article.id, FeedOfflineStatus.None).getOrThrow()
        repository.importWebsiteSnapshot(original).getOrThrow()
        assertEquals(FeedOfflineStatus.None, repository.state.value.articles.single().offlineStatus)
        assertEquals(null, repository.loadOfflineDocument(article.id).getOrThrow())

        val revisedDocument =
            original.document.copy(
                title = "Revised article",
                blocks = original.document.blocks.map { it.copy(text = "New saved text") },
            )
        val revised =
            original.copy(
                id = "revised-snapshot",
                document = revisedDocument,
                progress = 0.8f,
                savedAtMillis = 2L,
            )
        repository.importWebsiteSnapshot(revised).getOrThrow()
        val saved = FeedStore(context, firstId).read().articles.single()
        assertEquals(revised.id, saved.websiteSnapshotId)
        assertEquals(revisedDocument.title, saved.title)
        assertEquals("New saved text", saved.content)
        assertEquals(article.content, saved.previousContent)
        assertEquals(0.8f, saved.readingProgress)
        assertEquals(article.sourceIds, saved.sourceIds)
        assertTrue(saved.isRead)
        assertTrue(saved.isStarred)
        assertEquals(setOf("Research"), saved.tags)
        assertEquals(revisedDocument, repository.loadOfflineDocument(article.id).getOrThrow())

        repository.updateProgress(article.id, 0.9f).getOrThrow()
        repository.importWebsiteSnapshot(revised).getOrThrow()
        assertEquals(0.9f, repository.state.value.articles.single().readingProgress)
        repository.subscribe(preview()).getOrThrow()
        assertEquals(revised.id, repository.state.value.articles.single().websiteSnapshotId)
    }

    @Test
    fun legacyWebsiteProvenanceAdoptionDoesNotRestoreRemovedDownload() = runBlocking {
        val article = preview().articles.single().copy(origin = FeedArticleOrigin.Website)
        FeedStore(context, firstId).write(FeedState(articles = listOf(article)))
        val repository = FeedRepository.get(context, firstId)
        val snapshot =
            ReaderSnapshot(
                "adopted-snapshot",
                FeedRules.document(article),
                0.2f,
                1L,
                sourceProfileId = firstId,
            )
        repository.importWebsiteSnapshot(snapshot).getOrThrow()
        val adopted = FeedStore(context, firstId).read().articles.single()
        assertEquals(snapshot.id, adopted.websiteSnapshotId)
        assertEquals(FeedOfflineStatus.None, adopted.offlineStatus)
        assertEquals(null, repository.loadOfflineDocument(article.id).getOrThrow())
    }

    @Test
    fun websiteSnapshotJoinsExistingFeedWithoutLosingMetadataOrOfflineAccess() = runBlocking {
        val repository = FeedRepository.get(context, firstId)
        repository.subscribe(preview()).getOrThrow()
        val article = repository.state.value.articles.single()
        repository.setRead(setOf(article.id), true).getOrThrow()
        repository.setStarred(article.id, true).getOrThrow()
        repository.setTags(article.id, setOf("Research")).getOrThrow()
        repository.updateProgress(article.id, 0.6f).getOrThrow()
        val snapshot =
            ReaderSnapshot(
                "snapshot",
                FeedRules.document(article),
                0.2f,
                1L,
                sourceProfileId = firstId,
            )
        repository.importWebsiteSnapshot(snapshot).getOrThrow()
        val joined = repository.state.value.articles.single()
        assertEquals(FeedArticleOrigin.Website, joined.origin)
        assertEquals(article.sourceIds, joined.sourceIds)
        assertTrue(joined.isRead)
        assertTrue(joined.isStarred)
        assertEquals(setOf("Research"), joined.tags)
        assertEquals(0.6f, joined.readingProgress)
        assertEquals(null, joined.offlineDocument)
        assertTrue(joined.offlineDocumentId != null)
        assertEquals(snapshot.document, repository.loadOfflineDocument(article.id).getOrThrow())
        ProfileProtectionSession.lock(setOf(firstId))
        assertTrue(repository.loadOfflineDocument(article.id).isFailure)
    }

    @Test
    fun queuedArticlesAreAddedAtomicallyWithoutChangingMarkersOrDownloads() = runBlocking {
        val article = preview().articles.single()
        FeedStore(context, firstId)
            .write(
                FeedState(
                    articles =
                        listOf(
                            article,
                            article.copy(
                                id = FeedRules.identity("https://example.org/second"),
                                url = "https://example.org/second",
                            ),
                        )
                )
            )
        val repository = FeedRepository.get(context, firstId)
        repository.load().getOrThrow()
        coroutineScope {
            launch { repository.setQueued(article.id, true).getOrThrow() }
            launch {
                repository
                    .setQueued(FeedRules.identity("https://example.org/second"), true)
                    .getOrThrow()
            }
        }
        assertEquals(
            setOf(article.id, FeedRules.identity("https://example.org/second")),
            repository.state.value.queue.toSet(),
        )
        repository.setQueued(article.id, true).getOrThrow()
        assertEquals(2, repository.state.value.queue.size)
        repository.setQueued(article.id, false).getOrThrow()
        assertEquals(
            listOf(FeedRules.identity("https://example.org/second")),
            FeedStore(context, firstId).read().queue,
        )
        assertFalse(repository.state.value.articles.any { it.isStarred })
        assertTrue(
            repository.state.value.articles.all { it.offlineStatus == FeedOfflineStatus.None }
        )
        assertTrue(
            FeedRepository.get(context, firstId, isPrivate = true)
                .setQueued(FeedRules.identity("https://example.org/second"), true)
                .isFailure
        )
    }

    @Test
    fun speechCompletionCommitsReadProgressAndQueueTogetherForCurrentArticleOnly() = runBlocking {
        val repository = FeedRepository.get(context, firstId)
        repository.subscribe(preview()).getOrThrow()
        val article = repository.state.value.articles.single()
        repository.setQueued(article.id, true).getOrThrow()
        repository.updateProgress(article.id, 0.42f).getOrThrow()
        repository.completeQueuedArticle("stale-article").getOrThrow()
        assertEquals(listOf(article.id), repository.state.value.queue)
        assertFalse(repository.state.value.articles.single().isRead)
        repository.completeQueuedArticle(article.id).getOrThrow()
        val persisted = FeedStore(context, firstId).read()
        assertEquals(emptyList<String>(), persisted.queue)
        assertTrue(persisted.articles.single().isRead)
        assertEquals(1f, persisted.articles.single().readingProgress)
    }

    @Test
    fun fullQueueRejectsNewMembershipWithoutDiscardingExistingItems() = runBlocking {
        val article = preview().articles.single()
        val articles =
            (0..500).map {
                article.copy(
                    id = FeedRules.identity("https://example.org/$it"),
                    url = "https://example.org/$it",
                )
            }
        val queue = articles.take(500).map { it.id }
        FeedStore(context, firstId).write(FeedState(articles = articles, queue = queue))
        val repository = FeedRepository.get(context, firstId)
        repository.load().getOrThrow()
        assertTrue(
            repository.setQueued(FeedRules.identity("https://example.org/500"), true).isFailure
        )
        assertEquals(queue, repository.state.value.queue)
        repository.setQueued(FeedRules.identity("https://example.org/0"), true).getOrThrow()
        assertEquals(queue, repository.state.value.queue)
    }

    @Test
    fun xmlParserRunsOnAndroidAndRejectsEntityExpansion() {
        assertEquals("News", preview().title)
        val result = runCatching {
            FeedParser.parse(
                "<!DOCTYPE rss [<!ENTITY x 'expanded'>]><rss><channel><title>&x;</title></channel></rss>",
                "https://example.org/feed",
            )
        }
        assertTrue(result.isFailure)
    }

    private fun preview(): FeedPreview =
        FeedParser.parse(
            """<rss><channel><title>News</title><link>https://example.org</link><item><title>Article</title><link>https://example.org/article</link><description>A saved article that remains available without a connection.</description></item></channel></rss>""",
            "https://example.org/feed",
        )
}
