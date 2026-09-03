package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.FederatedLoginOffer
import dev.sk2andy.materialbrowser.browser.FederatedLoginPromptChoice
import dev.sk2andy.materialbrowser.browser.FederatedLoginProvider
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FederatedLoginPromptInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun regularPromptOffersTabProfileAndDenyChoices() {
        val choice = AtomicReference<FederatedLoginPromptChoice?>()
        composeRule.setContent {
            MaterialBrowserTheme {
                FederatedLoginPromptDialog(
                    offer = offer(isPrivate = false),
                    onChoice = choice::set,
                )
            }
        }

        composeRule.onNodeWithTag(FederatedLoginPromptTestTags.Dialog).assertExists()
        composeRule.onNodeWithTag(FederatedLoginPromptTestTags.AllowForTab).assertExists()
        composeRule.onNodeWithTag(FederatedLoginPromptTestTags.Deny).assertExists()
        composeRule.onNodeWithTag(FederatedLoginPromptTestTags.AllowForProfile)
            .assertExists()
            .performClick()

        assertEquals(FederatedLoginPromptChoice.AllowForProfile, choice.get())
    }

    @Test
    fun privatePromptOffersTabScopeOnly() {
        val choice = AtomicReference<FederatedLoginPromptChoice?>()
        composeRule.setContent {
            MaterialBrowserTheme {
                FederatedLoginPromptDialog(
                    offer = offer(isPrivate = true),
                    onChoice = choice::set,
                )
            }
        }

        composeRule.onNodeWithTag(FederatedLoginPromptTestTags.AllowForProfile)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(FederatedLoginPromptTestTags.AllowForTab)
            .assertExists()
            .performClick()

        assertEquals(FederatedLoginPromptChoice.AllowForTab, choice.get())
    }

    private fun offer(isPrivate: Boolean) = FederatedLoginOffer(
        token = 1L,
        tabId = "tab",
        profileId = "profile",
        pageHost = "login.example",
        provider = FederatedLoginProvider.Google,
        isPrivate = isPrivate,
        navigationGeneration = 1,
        showDialog = true,
    )
}
