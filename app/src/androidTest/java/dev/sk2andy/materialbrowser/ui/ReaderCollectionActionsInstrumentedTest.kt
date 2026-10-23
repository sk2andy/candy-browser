package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.feeds.FeedRepository
import dev.sk2andy.materialbrowser.reader.ReaderBlock
import dev.sk2andy.materialbrowser.reader.ReaderBlockKind
import dev.sk2andy.materialbrowser.reader.ReaderDocument
import dev.sk2andy.materialbrowser.reader.ReaderExtractionResult
import dev.sk2andy.materialbrowser.reader.ReaderLibraryRepository
import dev.sk2andy.materialbrowser.ui.feeds.FeedArticleActions
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderCollectionActionsInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val profiles = BrowserSessionStore(context)
    private val profileId = "reader-actions-${UUID.randomUUID()}"
    private lateinit var savedProfiles: Pair<List<BrowserProfile>, String>
    private val document =
        ReaderDocument(
            sourceUrl = "https://example.org/reader-actions",
            title = "An article opened from the browser",
            siteName = "Example",
            blocks =
                listOf(
                    ReaderBlock(
                        ReaderBlockKind.Paragraph,
                        "Full article retained for later listening.",
                    )
                ),
        )

    @Before
    fun setUp() {
        savedProfiles = profiles.loadProfiles()
        profiles.saveProfiles(
            savedProfiles.first + BrowserProfile(profileId, "🍬"),
            savedProfiles.second,
        )
    }

    @After
    fun tearDown() = runBlocking {
        FeedRepository.deleteProfile(context, profileId).getOrThrow()
        profiles.saveProfiles(savedProfiles.first, savedProfiles.second)
    }

    @Test
    fun browserReaderCanBookmarkAndQueueWithoutOpeningFeeds() {
        val feeds = FeedRepository.get(context, profileId)
        composeRule.setContent {
            MaterialBrowserTheme {
                ReaderStudioScreen(
                    result = ReaderExtractionResult.Success(document),
                    sourceUrl = document.sourceUrl,
                    isPrivate = false,
                    repository = ReaderLibraryRepository.get(context),
                    sourceProfileId = profileId,
                    onRetry = {},
                    onDismiss = {},
                    onOpenOriginal = {},
                    onOpenLink = {},
                )
            }
        }
        composeRule.onNodeWithTag(ReaderStudioTestTags.Settings).performClick()
        composeRule.onNodeWithTag("reader_collection_bookmark").assertIsOff().performClick()
        composeRule.waitUntil(5_000) {
            feeds.state.value.articles.singleOrNull()?.isStarred == true
        }
        composeRule.onNodeWithTag("reader_collection_bookmark").assertIsOn()
        composeRule.onNodeWithTag("reader_collection_queue").assertIsOff().performClick()
        composeRule.waitUntil(5_000) { feeds.state.value.queue.isNotEmpty() }
        composeRule.onNodeWithTag("reader_collection_queue").assertIsOn()
        runBlocking {
            assertEquals(
                document,
                feeds.loadOfflineDocument(feeds.state.value.queue.single()).getOrThrow(),
            )
        }
    }

    @Test
    fun websiteActionsOfferDeletionAndRemoveTheActualArticle() = runBlocking {
        val feeds = FeedRepository.get(context, profileId)
        feeds.saveWebsiteArticle(document, starred = true, queued = true).getOrThrow()
        var dismissed = false
        composeRule.setContent {
            MaterialBrowserTheme {
                val state by feeds.state.collectAsState()
                val scope = rememberCoroutineScope()
                state.articles.firstOrNull()?.let { article ->
                    FeedArticleActions(
                        article,
                        state,
                        feeds,
                        mutate = { operation -> scope.launch { operation().getOrThrow() } },
                        onOpenOriginal = {},
                        onDownload = {},
                        onDismiss = { dismissed = true },
                    )
                }
            }
        }
        composeRule.onNodeWithTag("feed_article_delete").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { feeds.state.value.articles.isEmpty() }
        composeRule.runOnIdle { assertTrue(dismissed) }
        assertTrue(feeds.state.value.queue.isEmpty())
    }
}
