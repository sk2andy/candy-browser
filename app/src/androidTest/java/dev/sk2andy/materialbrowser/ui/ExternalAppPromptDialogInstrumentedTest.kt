package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.ExternalAppPrompt
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalAppPromptDialogInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun promptNamesDestinationAndRequiresExplicitConfirmation() {
        val confirmed = AtomicBoolean()
        composeRule.setContent {
            MaterialBrowserTheme {
                ExternalAppPromptDialog(
                    prompt = ExternalAppPrompt(id = 1L, destination = "chatgpt.com"),
                    onConfirm = { confirmed.set(true) },
                    onCancel = {},
                )
            }
        }

        composeRule.onNodeWithTag(ExternalAppPromptTestTags.Dialog).assertExists()
        composeRule.onNodeWithText("chatgpt.com", substring = true).assertExists()
        composeRule.onNodeWithTag(ExternalAppPromptTestTags.Confirm).performClick()

        assertTrue(confirmed.get())
    }

    @Test
    fun cancelRejectsTheHandoff() {
        val cancelled = AtomicBoolean()
        composeRule.setContent {
            MaterialBrowserTheme {
                ExternalAppPromptDialog(
                    prompt = ExternalAppPrompt(id = 1L, destination = "chatgpt.com"),
                    onConfirm = {},
                    onCancel = { cancelled.set(true) },
                )
            }
        }

        composeRule.onNodeWithTag(ExternalAppPromptTestTags.Cancel).performClick()

        assertTrue(cancelled.get())
    }
}
