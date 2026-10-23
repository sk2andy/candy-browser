package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebAnnotationMarkdownInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun noteRendersFormattingAndOpensValidatedLink() {
        val opened = AtomicReference<String?>(null)
        composeRule.setContent {
            MaterialBrowserTheme {
                WebAnnotationMarkdown(
                    note = "# Research\n\n**Key** point\n\n- Read again\n\n[Source](https://example.com/source)",
                    onOpenLink = opened::set,
                )
            }
        }

        composeRule.onNodeWithText("Research").assertIsDisplayed()
        composeRule.onNodeWithText("Key point").assertIsDisplayed()
        composeRule.onNodeWithText("Read again").assertIsDisplayed()
        composeRule.onNodeWithText("Source").performTouchInput { click() }
        assertEquals("https://example.com/source", opened.get())
    }
}
