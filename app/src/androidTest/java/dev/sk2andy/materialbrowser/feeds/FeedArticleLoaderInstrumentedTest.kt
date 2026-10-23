package dev.sk2andy.materialbrowser.feeds

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedArticleLoaderInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val profiles = BrowserSessionStore(context)
    private val profileId = "feed-full-article-${UUID.randomUUID()}"
    private lateinit var savedProfiles: Pair<List<BrowserProfile>, String>
    private lateinit var repository: FeedRepository
    private val preview = "Only a feed teaser, not the full article."
    private val body =
        "The original article contains the entire story, including the final detail that is never present in the feed teaser."

    @Before
    fun setUp() = runBlocking {
        savedProfiles = profiles.loadProfiles()
        profiles.saveProfiles(listOf(BrowserProfile(profileId, "🍬")), profileId)
        repository = FeedRepository.get(context, profileId)
        repository
            .subscribe(
                FeedParser.parse(
                    "<rss><channel><title>News</title><item><title>Story</title><link>https://example.org/story</link><description>$preview</description></item></channel></rss>",
                    "https://example.org/feed",
                )
            )
            .getOrThrow()
    }

    @After
    fun tearDown() = runBlocking {
        FeedRepository.deleteProfile(context, profileId).getOrThrow()
        profiles.saveProfiles(savedProfiles.first, savedProfiles.second)
    }

    @Test
    fun readerAndQueueLoadOriginalEvenForAnEarlierFeedTextPreference() = runBlocking {
        val source = repository.state.value.sources.single()
        repository.updateSource(source.copy(readingMode = FeedReadingMode.Feed)).getOrThrow()
        var requests = 0
        val loader =
            FeedArticleLoader(context, repository) { url ->
                assertEquals("https://example.org/story", url)
                requests++
                "<article><p>$body</p></article>"
            }
        val document = loader.read(repository.state.value.articles.single())
        assertEquals(1, requests)
        assertTrue(document.speechText.contains(body))
        assertFalse(document.speechText.contains(preview))
    }

    @Test
    fun unreachableOriginalCannotSilentlyTurnIntoPreviewPlaybackOrOfflineSuccess() = runBlocking {
        val loader = FeedArticleLoader(context, repository) { throw IOException("Offline") }
        val article = repository.state.value.articles.single()
        assertTrue(runCatching { loader.read(article) }.isFailure)
        repository
            .updateSettings(repository.state.value.settings.copy(wifiOnly = false))
            .getOrThrow()
        assertTrue(loader.download(article).isFailure)
        assertEquals(
            FeedOfflineStatus.Failed,
            repository.state.value.articles.single().offlineStatus,
        )
    }

    @Test
    fun publisherFullFeedContentRemainsAvailableWhenOriginalIsUnreachable() = runBlocking {
        val loader = FeedArticleLoader(context, repository) { throw IOException("Offline") }
        val article =
            repository.state.value.articles.single().copy(content = body, contentIsSummary = false)
        assertTrue(loader.read(article).speechText.contains(body))
    }

    @Test
    fun savedReaderDocumentLoadsWithoutAnyNetworkRequest() = runBlocking {
        val article = repository.state.value.articles.single()
        val document =
            requireNotNull(FeedArticleLoader.extract(article, "<article><p>$body</p></article>"))
        repository.updateOffline(article.id, FeedOfflineStatus.Ready, document).getOrThrow()
        val loader =
            FeedArticleLoader(context, repository) { error("No network for saved article") }
        assertEquals(document, loader.read(repository.state.value.articles.single()))
    }

    @Test
    fun downloadingAnExistingSavedDocumentDoesNotFetchItAgain() = runBlocking {
        val article = repository.state.value.articles.single()
        val document =
            requireNotNull(FeedArticleLoader.extract(article, "<article><p>$body</p></article>"))
        repository.updateOffline(article.id, FeedOfflineStatus.Ready, document).getOrThrow()
        repository
            .updateSettings(repository.state.value.settings.copy(wifiOnly = false))
            .getOrThrow()
        val loader =
            FeedArticleLoader(context, repository) { error("No network for saved article") }
        assertTrue(loader.download(repository.state.value.articles.single()).isSuccess)
        assertEquals(document, repository.loadOfflineDocument(article.id).getOrThrow())
    }

    @Test
    fun olderOfflineTeaserIsReplacedByTheFullArticleAndMarkedAsUpgraded() = runBlocking {
        val article = repository.state.value.articles.single()
        val legacy =
            article.copy(
                offlineDocument = FeedRules.document(article),
                offlineStatus = FeedOfflineStatus.Ready,
            )
        val loader = FeedArticleLoader(context, repository) { "<article><p>$body</p></article>" }
        assertTrue(loader.read(legacy).speechText.contains(body))
        val repaired = repository.state.value.articles.single()
        assertEquals(1, repaired.offlineContentVersion)
        assertTrue(
            repository.loadOfflineDocument(repaired.id).getOrThrow()!!.speechText.contains(body)
        )
        val cached =
            FeedArticleLoader(context, repository) { error("Do not redownload repaired content") }
        assertTrue(cached.read(repaired).speechText.contains(body))
    }

    @Test
    fun privateCollectionFailsBeforeLoadingOriginal() = runBlocking {
        val privateRepository = FeedRepository.get(context, profileId, isPrivate = true)
        var fetched = false
        val loader =
            FeedArticleLoader(context, privateRepository) {
                fetched = true
                ""
            }
        assertTrue(runCatching { loader.read(repository.state.value.articles.single()) }.isFailure)
        assertFalse(fetched)
    }
}
