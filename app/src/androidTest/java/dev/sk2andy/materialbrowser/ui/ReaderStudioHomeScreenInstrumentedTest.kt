package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.reader.ReaderDocument
import dev.sk2andy.materialbrowser.reader.ReaderLibraryState
import dev.sk2andy.materialbrowser.reader.ReaderSnapshot
import dev.sk2andy.materialbrowser.reader.rss.RssArticle
import dev.sk2andy.materialbrowser.reader.rss.RssFeed
import dev.sk2andy.materialbrowser.reader.rss.RssInboxRules
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class ReaderStudioHomeScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun sourcePickerFiltersFeedAndHeaderStaysVisibleAfterListScroll() {
        val firstFeed = feed("first", (1..24).map { article("first-$it", "First article $it", it.toLong()) })
        val secondArticle = article("second-one", "Second feed article", 100L)
        val secondFeed = feed("second", listOf(secondArticle))

        composeRule.setContent {
            MaterialBrowserTheme {
                ReaderStudioHomeScreen(
                    library = ReaderLibraryState(),
                    feeds = listOf(firstFeed, secondFeed),
                    readArticleKeys = emptySet(),
                    isLoading = false,
                    onDismiss = {},
                    onOpenSnapshot = {},
                    onDeleteSnapshot = {},
                    onOpenArticle = { _, _ -> },
                    onManageFeeds = {},
                    initialTab = ReaderStudioHomeTab.Feeds,
                )
            }
        }

        composeRule.onNodeWithTag("reader-home-feed-list")
            .performScrollToNode(hasTestTag("reader-home-article-first-1"))
        composeRule.onNodeWithTag("reader-home-article-first-1").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-home-source-button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("reader-home-source-sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-home-source-row-second").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag("reader-home-article-second-one").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-home-article-first-1").assertDoesNotExist()
        composeRule.onNodeWithTag("reader-home-source-button").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.reader_home_newest_first)).assertIsDisplayed()
        composeRule.onRoot().captureToImage().asAndroidBitmap().useBitmap { bitmap ->
            FileOutputStream(context.cacheDir.resolve("reader-home-preview.png")).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }
    }

    @Test
    fun unreadBatchKeepsOpenedArticleUntilFilterIsAppliedAgain() {
        val feed = feed(
            "news",
            listOf(
                article("new", "Newest story", 2L),
                article("older", "Older story", 1L),
            ),
        )
        val readKeys = mutableStateOf(emptySet<String>())

        composeRule.setContent {
            MaterialBrowserTheme {
                ReaderStudioHomeScreen(
                    library = ReaderLibraryState(),
                    feeds = listOf(feed),
                    readArticleKeys = readKeys.value,
                    isLoading = false,
                    onDismiss = {},
                    onOpenSnapshot = {},
                    onDeleteSnapshot = {},
                    onOpenArticle = { selectedFeed, selectedArticle ->
                        readKeys.value = readKeys.value + RssInboxRules.articleKey(
                            selectedFeed.id,
                            selectedArticle.id,
                        )
                    },
                    onManageFeeds = {},
                    initialTab = ReaderStudioHomeTab.Feeds,
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.reader_home_unread_count, 2))
            .performClick()
        composeRule.onNodeWithTag("reader-home-article-new").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reader-home-article-new").assertIsDisplayed()
        assertEquals(1, readKeys.value.size)

        composeRule.onNodeWithText(context.getString(R.string.reader_home_all_posts)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.reader_home_unread_count, 1))
            .performClick()
        composeRule.onNodeWithTag("reader-home-article-new").assertDoesNotExist()
        composeRule.onNodeWithTag("reader-home-article-older").assertIsDisplayed()
    }

    @Test
    fun asyncReadStateIsAppliedWhenInitialLoadFinishes() {
        val article = article("already-read", "Previously read story", 1L)
        val feed = feed("saved-read", listOf(article))
        val loading = mutableStateOf(true)
        val readKeys = mutableStateOf(emptySet<String>())

        composeRule.setContent {
            MaterialBrowserTheme {
                ReaderStudioHomeScreen(
                    library = ReaderLibraryState(),
                    feeds = listOf(feed),
                    readArticleKeys = readKeys.value,
                    isLoading = loading.value,
                    onDismiss = {},
                    onOpenSnapshot = {},
                    onDeleteSnapshot = {},
                    onOpenArticle = { _, _ -> },
                    onManageFeeds = {},
                    initialTab = ReaderStudioHomeTab.Feeds,
                )
            }
        }

        composeRule.runOnIdle {
            readKeys.value = setOf(RssInboxRules.articleKey(feed.id, article.id))
            loading.value = false
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.reader_home_unread_count, 0))
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.reader_home_all_read_title)).assertIsDisplayed()
        composeRule.onNodeWithTag("reader-home-article-already-read").assertDoesNotExist()
    }

    @Test
    fun selectedFeedSurvivesStateRestoreWhileFeedsAreReloading() {
        val firstFeed = feed("restore-first", listOf(article("first-story", "First story", 2L)))
        val secondFeed = feed("restore-second", listOf(article("second-story", "Second story", 1L)))
        val feeds = mutableStateOf(listOf(firstFeed, secondFeed))
        val loading = mutableStateOf(false)
        val restorationTester = StateRestorationTester(composeRule)

        restorationTester.setContent {
            MaterialBrowserTheme {
                ReaderStudioHomeScreen(
                    library = ReaderLibraryState(),
                    feeds = feeds.value,
                    readArticleKeys = emptySet(),
                    isLoading = loading.value,
                    onDismiss = {},
                    onOpenSnapshot = {},
                    onDeleteSnapshot = {},
                    onOpenArticle = { _, _ -> },
                    onManageFeeds = {},
                    initialTab = ReaderStudioHomeTab.Feeds,
                )
            }
        }

        composeRule.onNodeWithTag("reader-home-source-button").performClick()
        composeRule.onNodeWithTag("reader-home-source-row-restore-second").performClick()
        composeRule.onNodeWithTag("reader-home-article-second-story").assertIsDisplayed()

        composeRule.runOnIdle {
            feeds.value = emptyList()
            loading.value = true
        }
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle {
            feeds.value = listOf(firstFeed, secondFeed)
            loading.value = false
        }

        composeRule.onNodeWithTag("reader-home-article-second-story").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-home-article-first-story").assertDoesNotExist()
    }

    @Test
    fun libraryAndFeedsKeepIndependentVisibleItems() {
        val saved = ReaderSnapshot(
            id = "saved-offline",
            document = ReaderDocument(
                title = "Offline article",
                sourceUrl = "https://example.com/offline",
                siteName = "Example",
                blocks = emptyList(),
            ),
            progress = 0.35f,
            savedAtMillis = 1L,
        )
        val feed = feed("live", listOf(article("latest", "Fresh feed article", 2L)))

        composeRule.setContent {
            MaterialBrowserTheme {
                ReaderStudioHomeScreen(
                    library = ReaderLibraryState(snapshots = listOf(saved)),
                    feeds = listOf(feed),
                    readArticleKeys = emptySet(),
                    isLoading = false,
                    onDismiss = {},
                    onOpenSnapshot = {},
                    onDeleteSnapshot = {},
                    onOpenArticle = { _, _ -> },
                    onManageFeeds = {},
                )
            }
        }

        composeRule.onNodeWithTag("reader-home-snapshot-saved-offline").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-home-feeds-tab").performClick()
        composeRule.onNodeWithTag("reader-home-article-latest").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-home-library-tab").performClick()
        composeRule.onNodeWithTag("reader-home-snapshot-saved-offline").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-home-article-latest").assertDoesNotExist()
        composeRule.onNodeWithTag("reader-home-library-tab").assertIsSelected()
    }

    private fun feed(id: String, articles: List<RssArticle>) = RssFeed(
        id = id,
        title = "Feed $id",
        feedUrl = "https://$id.example/rss",
        siteUrl = "https://$id.example",
        articles = articles,
    )

    private fun article(id: String, title: String, publishedAt: Long) = RssArticle(
        id = id,
        title = title,
        url = "https://example.com/$id",
        summary = "Summary for $title",
        publishedAt = publishedAt,
    )

    private inline fun <T> Bitmap.useBitmap(block: (Bitmap) -> T): T = try {
        block(this)
    } finally {
        recycle()
    }
}
