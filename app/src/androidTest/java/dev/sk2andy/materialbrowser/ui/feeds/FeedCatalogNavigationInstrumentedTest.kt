package dev.sk2andy.materialbrowser.ui.feeds

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
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
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedCatalogNavigationInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()
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
