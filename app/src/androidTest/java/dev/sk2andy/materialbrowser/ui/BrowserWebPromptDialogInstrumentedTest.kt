package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.BrowserWebPrompt
import dev.sk2andy.materialbrowser.browser.BrowserWebPromptKind
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserWebPromptDialogInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun textPromptReturnsBoundedEnteredValue() {
        val confirmed = AtomicReference<String?>()
        composeRule.setContent {
            MaterialBrowserTheme {
                BrowserWebPromptDialog(
                    prompt = BrowserWebPrompt(
                        id = 1,
                        tabId = "tab-a",
                        kind = BrowserWebPromptKind.Text,
                        title = "Question",
                        message = "Answer",
                        defaultValue = "initial",
                    ),
                    onConfirm = confirmed::set,
                    onCancel = {},
                )
            }
        }

        composeRule.onNodeWithTag(BrowserWebPromptTestTags.Input).performTextInput(" candy")
        composeRule.onNodeWithTag(BrowserWebPromptTestTags.Confirm).performClick()

        assertEquals("initial candy", confirmed.get())
    }
}
