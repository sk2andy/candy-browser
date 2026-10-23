package dev.sk2andy.materialbrowser.ui

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.filters.SdkSuppress
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.ReleaseNotesMarkdownRules
import dev.sk2andy.materialbrowser.data.FavoriteFolder
import dev.sk2andy.materialbrowser.data.FavoriteLibrary
import dev.sk2andy.materialbrowser.data.HistoryEntry
import dev.sk2andy.materialbrowser.reader.rss.RssFeed
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@SdkSuppress(minSdkVersion = 34)
class CollectionBackNavigationInstrumentedTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun historyRootDelegatesToAndroidButSelectionConsumesBack() {
        var dismissals = 0
        val entry =
            HistoryEntry("https://example.com", "Article", System.currentTimeMillis(), "profile")
        composeRule.setContent {
            MaterialBrowserTheme {
                HistoryScreen(
                    profiles = listOf(BrowserProfile(id = "profile", emoji = "🏠")),
                    activeProfileId = "profile",
                    history = listOf(entry),
                    onDeleteEntries = {},
                    onClearHistory = {},
                    onOpenEntry = {},
                    onBack = { dismissals++ },
                )
            }
        }
        composeRule.runOnIdle { assertFalse(dispatcher.hasEnabledCallbacks()) }
        composeRule.onNodeWithTag(HistoryScreenTestTags.select(entry)).performClick()
        composeRule.runOnIdle { assertTrue(dispatcher.hasEnabledCallbacks()) }
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(HistoryScreenTestTags.DeleteSelected).assertDoesNotExist()
        composeRule.runOnIdle {
            assertFalse(dispatcher.hasEnabledCallbacks())
            assertEquals(0, dismissals)
        }
    }

    @Test
    fun favoriteFolderBackCanCancelThenCommitWithoutFinishingActivity() {
        var dismissals = 0
        val library = FavoriteLibrary(listOf(FavoriteFolder("folder", "Folder")))
        composeRule.setContent {
            MaterialBrowserTheme {
                FavoritesScreen(
                    favorites = emptyList(),
                    library = library,
                    onDeleteFavorite = { _, complete -> complete(null) },
                    onUndoDelete = {},
                    onOpenFavorite = {},
                    onBack = { dismissals++ },
                )
            }
        }
        composeRule.runOnIdle { assertFalse(dispatcher.hasEnabledCallbacks()) }
        composeRule.onNodeWithTag("favorites_folder:folder").performClick()
        gesture()
        composeRule.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("favorites_parent").assertIsDisplayed()
        gesture()
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("favorites_folder:folder").assertIsDisplayed()
        composeRule.runOnIdle {
            assertFalse(dispatcher.hasEnabledCallbacks())
            assertEquals(0, dismissals)
        }
    }

    @Test
    fun releaseNotesFollowPredictiveGestureAndOnlyCommitDismisses() {
        var dismissals = 0
        val document =
            requireNotNull(ReleaseNotesMarkdownRules.parse("# Release\n\nNew features", "1.0"))
        composeRule.setContent {
            MaterialBrowserTheme { ReleaseNotesScreen("1.0", document, { dismissals++ }, {}) }
        }
        val width =
            composeRule
                .onNodeWithTag(ReleaseNotesTestTags.Content)
                .fetchSemanticsNode()
                .boundsInRoot
                .width
        gesture()
        assertTrue(
            composeRule
                .onNodeWithTag(ReleaseNotesTestTags.Content)
                .fetchSemanticsNode()
                .boundsInRoot
                .width < width
        )
        composeRule.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(0, dismissals) }
        gesture()
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test
    fun legacyRssRootDelegatesToAndroidAndDetailReturnsToRetainedSourceList() {
        var dismissals = 0
        val feed =
            RssFeed("source", "Example feed", "https://example.com/feed", "https://example.com")
        composeRule.setContent {
            MaterialBrowserTheme {
                RssFeedsScreen(
                    pageUrl = "",
                    candidates = emptyList(),
                    feeds = listOf(feed),
                    isWorking = false,
                    errorMessage = "",
                    statusMessage = "",
                    onBack = { dismissals++ },
                    onDiscover = {},
                    onSubscribe = {},
                    onRemove = {},
                    onNotificationsChanged = { _, _ -> },
                    onOpenArticle = {},
                )
            }
        }
        composeRule.runOnIdle { assertFalse(dispatcher.hasEnabledCallbacks()) }
        composeRule.onNodeWithText("Example feed").performClick()
        gesture()
        composeRule.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("rss_source_detail").assertIsDisplayed()
        gesture()
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("rss_source_detail").assertDoesNotExist()
        composeRule.onNodeWithText("Example feed").assertIsDisplayed()
        composeRule.runOnIdle {
            assertFalse(dispatcher.hasEnabledCallbacks())
            assertEquals(0, dismissals)
        }
    }

    private val dispatcher
        get() = composeRule.activity.onBackPressedDispatcher

    private fun gesture() {
        composeRule.runOnIdle {
            dispatcher.dispatchOnBackStarted(
                BackEventCompat(0f, 500f, 0f, BackEventCompat.EDGE_LEFT)
            )
        }
        composeRule.runOnIdle {
            dispatcher.dispatchOnBackProgressed(
                BackEventCompat(100f, 500f, 0.65f, BackEventCompat.EDGE_LEFT)
            )
        }
        composeRule.waitForIdle()
    }
}
