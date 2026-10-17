package dev.sk2andy.materialbrowser.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrowserWebPromptRulesTest {
    @Test
    fun rejectsSpoofedDisplayFieldsWithoutRejectingValidPrompt() {
        val prompt = BrowserWebPromptRules.sanitized(
            id = 1,
            tabId = "tab",
            kind = BrowserWebPromptKind.Text,
            title = "example.org\u202E",
            message = "  Continue?  ",
            defaultValue = "secret\u0000",
        )

        assertNull(prompt?.title)
        assertEquals("Continue?", prompt?.message)
        assertNull(prompt?.defaultValue)
    }

    @Test
    fun boundsInputAndPreservesMultilingualText() {
        val prompt = BrowserWebPromptRules.sanitized(
            id = 2,
            tabId = "tab",
            kind = BrowserWebPromptKind.Text,
            title = "example.org",
            message = "日本語 🍬",
            defaultValue = "x".repeat(4_097),
        )

        assertEquals("日本語 🍬", prompt?.message)
        assertNull(prompt?.defaultValue)
    }
}
