package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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

    private fun render(message: GeckoExtensionManagerMessage) {
        composeRule.setContent {
            MaterialBrowserTheme {
                FirefoxExtensionManagerOverlay(
                    state = GeckoExtensionManagerState(message = message),
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
