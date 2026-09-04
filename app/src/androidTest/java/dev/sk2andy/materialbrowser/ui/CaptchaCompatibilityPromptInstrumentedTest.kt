package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.CaptchaCompatibilityOffer
import dev.sk2andy.materialbrowser.browser.CaptchaCompatibilityPromptChoice
import dev.sk2andy.materialbrowser.browser.CaptchaProvider
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptchaCompatibilityPromptInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun regularPromptExplainsScopeAndOffersAllChoices() {
        val choice = AtomicReference<CaptchaCompatibilityPromptChoice?>()
        composeRule.setContent {
            MaterialBrowserTheme {
                CaptchaCompatibilityPromptDialog(
                    offer = offer(isPrivate = false),
                    onChoice = choice::set,
                )
            }
        }

        composeRule.onNodeWithTag(CaptchaCompatibilityPromptTestTags.Dialog).assertExists()
        composeRule.onNodeWithText("Allow Cloudflare verification?").assertExists()
        composeRule.onNodeWithText("checkout.example", substring = true).assertExists()
        composeRule.onNodeWithTag(CaptchaCompatibilityPromptTestTags.AllowForTab).assertExists()
        composeRule.onNodeWithTag(CaptchaCompatibilityPromptTestTags.Deny).assertExists()
        composeRule.onNodeWithTag(CaptchaCompatibilityPromptTestTags.AllowForProfile)
            .assertExists()
            .performClick()

        assertEquals(CaptchaCompatibilityPromptChoice.AllowForProfile, choice.get())
    }

    @Test
    fun privatePromptOffersTabScopeOnly() {
        val choice = AtomicReference<CaptchaCompatibilityPromptChoice?>()
        composeRule.setContent {
            MaterialBrowserTheme {
                CaptchaCompatibilityPromptDialog(
                    offer = offer(isPrivate = true),
                    onChoice = choice::set,
                )
            }
        }

        composeRule.onNodeWithTag(CaptchaCompatibilityPromptTestTags.AllowForProfile)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(CaptchaCompatibilityPromptTestTags.AllowForTab)
            .assertExists()
            .performClick()

        assertEquals(CaptchaCompatibilityPromptChoice.AllowForTab, choice.get())
    }

    private fun offer(isPrivate: Boolean) = CaptchaCompatibilityOffer(
        token = 1L,
        tabId = "tab",
        profileId = "profile",
        pageHost = "checkout.example",
        provider = CaptchaProvider.Cloudflare,
        isPrivate = isPrivate,
        navigationGeneration = 1,
        showDialog = true,
    )
}
