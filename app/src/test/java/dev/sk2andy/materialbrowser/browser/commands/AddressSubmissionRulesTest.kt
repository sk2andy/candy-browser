package dev.sk2andy.materialbrowser.browser.commands

import dev.sk2andy.materialbrowser.data.AddressSuggestion
import dev.sk2andy.materialbrowser.recall.RecallMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AddressSubmissionRulesTest {
    private val command = AddressSuggestionItem.Command(
        CommandSuggestion(
            BrowserCommand("reload", BrowserCommandKind.Reload),
            "Reload",
            "Reloads this tab",
        ),
    )
    private val recall = AddressSuggestionItem.Recall(
        RecallMatch("personal", "https://example.com", "Example", "match", 1L, 1.0),
    )

    @Test
    fun `normal hardware submit navigates when no suggestion is highlighted`() {
        assertEquals(
            AddressSubmission.Navigate("example.com"),
            AddressSubmissionRules.resolve("example.com", listOf(command), -1),
        )
    }

    @Test
    fun `explicit command submit selects first command and never navigates`() {
        assertEquals(
            AddressSubmission.Select(command),
            AddressSubmissionRules.resolve("> reload", listOf(command), -1),
        )
        assertSame(
            AddressSubmission.None,
            AddressSubmissionRules.resolve("> no-match", emptyList(), -1),
        )
    }

    @Test
    fun `highlighted navigation or command is selected exactly`() {
        val navigation = AddressSuggestionItem.Navigation(
            AddressSuggestion("https://example.com", "Example"),
        )
        val suggestions = listOf(navigation, command)

        assertEquals(
            AddressSubmission.Select(command),
            AddressSubmissionRules.resolve("reload", suggestions, 1),
        )
        assertEquals(
            AddressSubmission.Select(navigation),
            AddressSubmissionRules.resolve("example", suggestions, 0),
        )
    }

    @Test
    fun `explicit recall submit selects local result and never navigates`() {
        assertEquals(
            AddressSubmission.Select(recall),
            AddressSubmissionRules.resolve(">recall candy", listOf(recall), -1),
        )
        assertSame(
            AddressSubmission.None,
            AddressSubmissionRules.resolve(">recall missing", emptyList(), -1),
        )
    }
}
