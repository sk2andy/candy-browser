package dev.sk2andy.materialbrowser.ui.feeds

import android.content.Context
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.feeds.FeedRepository
import dev.sk2andy.materialbrowser.reader.ReaderLibraryState
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedCatalogNavigationInstrumentedTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun sourcesOfferCatalogAndDirectManualEntryWithoutLosingReturnDestination() {
        showFeeds()
        composeRule.onNodeWithTag("feeds_destination_sources").performClick()
        composeRule.onNodeWithTag("feeds_sources_manual").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("feeds_subscribe_url").assertIsDisplayed()
        composeRule
            .onNodeWithTag("feeds_subscribe_handle", useUnmergedTree = true)
            .assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithTag("feeds_destination_sources").assertIsSelected()
        composeRule.onNodeWithTag("feeds_sources_add").performClick()
        composeRule.onNodeWithTag("feed_catalog_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("feed_catalog_manual").performClick()
        composeRule.onNodeWithTag("feeds_subscribe_url").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithTag("feed_catalog_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("feed_catalog_back").performClick()
        composeRule.onNodeWithTag("feeds_destination_sources").assertIsSelected()
    }

    @Test
    fun toolbarCatalogPreservesManualAddressAndInvalidAddressDoesNotDismiss() {
        showFeeds()
        composeRule.onNodeWithTag("feeds_add_source").performClick()
        composeRule.onNodeWithTag("feed_catalog_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("feed_catalog_manual").performClick()
        composeRule.onNodeWithTag("feeds_subscribe_url").performTextInput("not a feed address")
        composeRule.onNodeWithTag("feeds_subscribe_confirm").performClick()
        composeRule.onNodeWithTag("feeds_subscribe_error").assertIsDisplayed()
        composeRule.onNodeWithTag("feeds_subscribe_url").assertIsDisplayed()
    }

    @Test
    fun largeFontKeepsSourceActionsReachableAndCatalogCanReturn() {
        var dismissed = false
        showFeeds(fontScale = 2f, onDismiss = { dismissed = true })
        composeRule.onNodeWithTag("feeds_destination_sources").performClick()
        composeRule.onNodeWithTag("feeds_sources_add").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("feed_catalog_search").assertIsDisplayed()
        composeRule.onNodeWithTag("feed_catalog_back").performClick()
        composeRule.onNodeWithTag("feeds_destination_sources").assertIsSelected()
        composeRule.onNodeWithTag("feeds_sources_manual").assertIsDisplayed()
        composeRule.runOnIdle { assertFalse(dismissed) }
    }

    @Test
    fun catalogPredictiveBackRevealsCollectionAndCancellationRestoresCatalog() {
        var dismissals = 0
        showFeeds(onDismiss = { dismissals++ })
        composeRule.onNodeWithTag("feeds_destination_sources").performClick()
        composeRule.onNodeWithTag("feeds_sources_add").performClick()
        val originalWidth =
            composeRule.onNodeWithTag("feed_catalog_search").fetchSemanticsNode().boundsInRoot.width

        startGesture(0.7f)
        val gestureWidth =
            composeRule.onNodeWithTag("feed_catalog_search").fetchSemanticsNode().boundsInRoot.width
        assertTrue("Catalog must follow gesture progress", gestureWidth < originalWidth)
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.dispatchOnBackCancelled()
        }
        composeRule.waitForIdle()
        assertEquals(
            originalWidth,
            composeRule
                .onNodeWithTag("feed_catalog_search")
                .fetchSemanticsNode()
                .boundsInRoot
                .width,
            1f,
        )
        composeRule.runOnIdle { assertEquals(0, dismissals) }

        startGesture(0.6f)
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("feed_catalog_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("feeds_destination_sources").assertIsSelected()
        composeRule.runOnIdle { assertEquals(0, dismissals) }

        startGesture(0.6f)
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test
    fun addSourceEntersWithMotionAndThenSettlesAtFullWidth() {
        showFeeds()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("feeds_add_source").performClick()
        composeRule.mainClock.advanceTimeBy(32)
        val entering =
            composeRule.onNodeWithTag("feed_catalog_screen").fetchSemanticsNode().boundsInRoot.left
        composeRule.mainClock.advanceTimeBy(2000)
        val settled =
            composeRule.onNodeWithTag("feed_catalog_screen").fetchSemanticsNode().boundsInRoot.left
        assertTrue("Catalog entry must move rather than jump-cut", entering > settled)
        composeRule.mainClock.autoAdvance = true
        composeRule.onNodeWithTag("feed_catalog_screen").assertIsDisplayed()
    }

    private fun startGesture(progress: Float) {
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.dispatchOnBackStarted(
                BackEventCompat(0f, 500f, 0f, BackEventCompat.EDGE_LEFT)
            )
        }
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.dispatchOnBackProgressed(
                BackEventCompat(100f, 500f, progress, BackEventCompat.EDGE_LEFT)
            )
        }
        composeRule.waitForIdle()
    }

    private fun showFeeds(fontScale: Float = 1f, onDismiss: () -> Unit = {}) {
        val repository = FeedRepository.get(context, "catalog-navigation-${UUID.randomUUID()}")
        composeRule.setContent {
            MaterialBrowserTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale)
                ) {
                    FeedsScreen(
                        repository = repository,
                        library = ReaderLibraryState(),
                        onOpenArticle = {},
                        onOpenSnapshot = {},
                        onOpenOriginal = {},
                        onDownloadArticle = {},
                        onRemoveSnapshot = {},
                        onPlayQueue = {},
                        onImportOpml = {},
                        onExportOpml = {},
                        onDismiss = onDismiss,
                    )
                }
            }
        }
    }
}
