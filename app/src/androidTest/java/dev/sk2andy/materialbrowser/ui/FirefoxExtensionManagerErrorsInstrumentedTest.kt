package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionManagerMessage
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionManagerState
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirefoxExtensionManagerErrorsInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun failedRefreshDoesNotClaimPreviouslyEmptyInventoryIsStillEmpty() {
        render(GeckoExtensionManagerMessage.InventoryFailed, inventoryLoaded = true)
        composeRule.onNodeWithText(context.getString(R.string.gecko_extensions_empty))
            .assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.gecko_extensions_retry_inventory))
            .assertIsDisplayed()
    }

    @Test
    fun successfulEmptyInventoryShowsEmptyState() {
        render(inventoryLoaded = true)
        composeRule.onNodeWithText(context.getString(R.string.gecko_extensions_empty))
            .assertIsDisplayed()
    }

    @Test
    fun timedOutInventoryExplainsRetryWithoutDataDeletion() {
        render(GeckoExtensionManagerMessage.InventoryTimedOut)
        composeRule.onNodeWithText(context.getString(R.string.gecko_extension_inventory_timed_out))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.gecko_extensions_empty))
            .assertDoesNotExist()
    }

    @Test
    fun failedInventoryShowsRetryNotFalseEmptyState() {
        var retries = 0
        render(GeckoExtensionManagerMessage.InventoryFailed, onRefresh = { retries++ })
        composeRule.onNodeWithText(context.getString(R.string.gecko_extension_inventory_failed))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.gecko_extensions_empty))
            .assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.gecko_extensions_retry_inventory))
            .performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun incompatibleExtensionExplainsAndroidFirefoxVersionBoundary() {
        render(GeckoExtensionManagerMessage.InstallIncompatible)

        composeRule.onNodeWithText(
            context.getString(R.string.gecko_extension_install_incompatible),
        ).assertIsDisplayed()
    }

    @Test
    fun invalidInstallDomainExplainsPublisherSourceBoundary() {
        render(GeckoExtensionManagerMessage.InstallInvalidDomain)

        composeRule.onNodeWithText(
            context.getString(R.string.gecko_extension_install_invalid_domain),
        ).assertIsDisplayed()
    }

    @Test
    fun materialSearchFieldIsVisibleInExtensionManager() {
        render()

        composeRule.onNodeWithTag(FirefoxExtensionManagerTestTags.Search).assertIsDisplayed()
    }

    private fun render(
        message: GeckoExtensionManagerMessage? = null,
        onRefresh: () -> Unit = {},
        inventoryLoaded: Boolean = false,
    ) {
        composeRule.setContent {
            MaterialBrowserTheme {
                FirefoxExtensionManagerOverlay(
                    state = GeckoExtensionManagerState(message = message, inventoryLoaded = inventoryLoaded),
                    onRefresh = onRefresh,
                    onInstall = {},
                    onSetEnabled = { _, _ -> },
                    onSetPrivate = { _, _ -> },
                    onUpdate = {},
                    onUninstall = {},
                    onOpenOptionsPage = {},
                    onPermissionDecision = { _, _ -> },
                    onDismiss = {},
                )
            }
        }
    }
}
