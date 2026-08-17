package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToppingCatalogScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingStateIsVisible() {
        composeRule.setContent {
            MaterialBrowserTheme {
                ToppingCatalogScreen(
                    state = ToppingCatalogUiState.Loading,
                    onToggle = { _, _ -> },
                    onUpdate = {},
                    onRetry = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(ToppingCatalogTestTags.Loading).assertExists()
    }

    @Test
    fun errorStateIsActionable() {
        var retryRequested = false
        composeRule.setContent {
            MaterialBrowserTheme {
                ToppingCatalogScreen(
                    state = ToppingCatalogUiState.Error("Catalog offline"),
                    onToggle = { _, _ -> },
                    onUpdate = {},
                    onRetry = { retryRequested = true },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(ToppingCatalogTestTags.Error).assertExists()
        composeRule.onNodeWithText("Catalog offline").assertExists()
        composeRule.onNodeWithTag(ToppingCatalogTestTags.Retry).performClick()
        assertTrue(retryRequested)
    }

    @Test
    fun catalogCardShowsMetadataAndEmitsInstallAndUpdate() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toggles = mutableListOf<Pair<String, Boolean>>()
        val updates = mutableListOf<String>()
        composeRule.setContent {
            MaterialBrowserTheme {
                ToppingCatalogScreen(
                    state = ToppingCatalogUiState.Content(listOf(testTopping())),
                    onToggle = { id, enabled -> toggles += id to enabled },
                    onUpdate = { updates += it },
                    onRetry = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(ToppingCatalogTestTags.topping("clean-copy"))
            .assertExists()
        composeRule.onNodeWithText("Readable copy buttons").assertExists()
        composeRule.onNodeWithText(
            context.getString(R.string.topping_catalog_scope, "https://example.com/*"),
        ).assertExists()
        composeRule.onNodeWithText(
            context.getString(R.string.topping_catalog_author, "Candy Community"),
        ).assertExists()
        composeRule.onNodeWithText(
            context.getString(R.string.topping_catalog_version_license, "1.2.0", "MIT"),
        ).assertExists()
        composeRule.onNodeWithTag(ToppingCatalogTestTags.toggle("clean-copy"))
            .assertIsOff()
            .performClick()
        composeRule.onNodeWithTag(ToppingCatalogTestTags.update("clean-copy"))
            .performClick()

        assertEquals(listOf("clean-copy" to true), toggles)
        assertEquals(listOf("clean-copy"), updates)
    }

    @Test
    fun cachedCatalogIsClearlyMarked() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.setContent {
            MaterialBrowserTheme {
                ToppingCatalogScreen(
                    state = ToppingCatalogUiState.Cached(listOf(testTopping())),
                    onToggle = { _, _ -> },
                    onUpdate = {},
                    onRetry = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(ToppingCatalogTestTags.CachedNotice)
            .assertExists()
        composeRule.onNodeWithText(context.getString(R.string.topping_catalog_cached_notice))
            .assertExists()
        composeRule.onNodeWithTag(ToppingCatalogTestTags.topping("clean-copy"))
            .assertExists()
    }

    @Test
    fun busyToppingBlocksRepeatedActions() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.setContent {
            MaterialBrowserTheme {
                ToppingCatalogScreen(
                    state = ToppingCatalogUiState.Content(
                        listOf(testTopping().copy(busy = true)),
                    ),
                    onToggle = { _, _ -> },
                    onUpdate = {},
                    onRetry = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.topping_catalog_working))
            .assertExists()
        composeRule.onNodeWithTag(ToppingCatalogTestTags.toggle("clean-copy"))
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(ToppingCatalogTestTags.update("clean-copy"))
            .assertIsNotEnabled()
    }

    private fun testTopping() = ToppingCatalogItem(
        id = "clean-copy",
        name = "Clean Copy",
        description = "Readable copy buttons",
        author = "Candy Community",
        license = "MIT",
        version = "1.2.0",
        scopes = listOf("https://example.com/*"),
        installed = true,
        enabled = false,
        updateAvailable = true,
    )
}
