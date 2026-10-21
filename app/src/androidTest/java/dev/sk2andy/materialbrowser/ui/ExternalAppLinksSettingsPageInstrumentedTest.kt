package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.ExternalAppLinkHandling
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalAppLinksSettingsPageInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun appLinkChoiceUpdatesWithoutChangingSavedSites() {
        var handling by mutableStateOf(ExternalAppLinkHandling.Automatic)
        val sites = listOf("github.com")
        composeRule.setContent {
            MaterialBrowserTheme {
                ExternalAppLinksSettingsPage(
                    externalAppLinkHandling = handling,
                    siteExceptions = sites,
                    onExternalAppLinkHandlingChanged = { handling = it },
                    onResetSiteException = { error("Unexpected site reset") },
                    onResetAllSiteExceptions = { error("Unexpected reset all") },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("settings_header_artwork_externalapps").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_external_app_links_ask_every_time))
            .performScrollTo().performClick().assertIsSelected()
        composeRule.runOnIdle { assertEquals(ExternalAppLinkHandling.AskEveryTime, handling) }
        composeRule.onNodeWithText("github.com").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun resetOneSiteKeepsOtherPreferences() {
        var sites by mutableStateOf(listOf("github.com", "reddit.com"))
        composeRule.setContent {
            MaterialBrowserTheme {
                ExternalAppLinksSettingsPage(
                    externalAppLinkHandling = ExternalAppLinkHandling.Automatic,
                    siteExceptions = sites,
                    onExternalAppLinkHandlingChanged = {},
                    onResetSiteException = { domain -> sites = sites - domain },
                    onResetAllSiteExceptions = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(ExternalAppLinksSettingsTestTags.reset("github.com"))
            .performScrollTo().performClick()

        composeRule.runOnIdle { assertEquals(listOf("reddit.com"), sites) }
        composeRule.onNodeWithText("github.com").assertDoesNotExist()
        composeRule.onNodeWithText("reddit.com").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun resetAllRequiresConfirmationAndShowsEmptyState() {
        var sites by mutableStateOf(listOf("github.com", "reddit.com"))
        var resets = 0
        composeRule.setContent {
            MaterialBrowserTheme {
                ExternalAppLinksSettingsPage(
                    externalAppLinkHandling = ExternalAppLinkHandling.Automatic,
                    siteExceptions = sites,
                    onExternalAppLinkHandlingChanged = {},
                    onResetSiteException = {},
                    onResetAllSiteExceptions = { sites = emptyList(); resets++ },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(ExternalAppLinksSettingsTestTags.ResetAll).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(0, resets) }
        composeRule.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
        composeRule.runOnIdle { assertEquals(listOf("github.com", "reddit.com"), sites) }
        composeRule.onNodeWithTag(ExternalAppLinksSettingsTestTags.ResetAll).performScrollTo().performClick()
        composeRule.onNodeWithTag(ExternalAppLinksSettingsTestTags.ConfirmResetAll).performClick()

        composeRule.runOnIdle { assertEquals(1, resets) }
        composeRule.onNodeWithTag(ExternalAppLinksSettingsTestTags.Empty).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ExternalAppLinksSettingsTestTags.ResetAll).assertDoesNotExist()
        composeRule.onNodeWithTag(ExternalAppLinksSettingsTestTags.ConfirmResetAll).assertDoesNotExist()
    }
}
