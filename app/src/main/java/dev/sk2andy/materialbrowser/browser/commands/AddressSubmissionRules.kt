package dev.sk2andy.materialbrowser.browser.commands

import dev.sk2andy.materialbrowser.recall.RecallRules

sealed interface AddressSubmission {
    data class Select(val suggestion: AddressSuggestionItem) : AddressSubmission
    data class Navigate(val input: String) : AddressSubmission
    data object None : AddressSubmission
}

object AddressSubmissionRules {
    fun resolve(
        input: String,
        suggestions: List<AddressSuggestionItem>,
        highlightedIndex: Int,
    ): AddressSubmission {
        val highlighted = suggestions.getOrNull(highlightedIndex)
        if (highlighted != null) return AddressSubmission.Select(highlighted)
        if (RecallRules.isExplicitCommand(input)) {
            val recall = suggestions.firstOrNull { it is AddressSuggestionItem.Recall }
            return recall?.let(AddressSubmission::Select) ?: AddressSubmission.None
        }
        if (CommandMatcher.isExplicitCommandQuery(input)) {
            val command = suggestions.firstOrNull { it is AddressSuggestionItem.Command }
            return command?.let(AddressSubmission::Select) ?: AddressSubmission.None
        }
        return AddressSubmission.Navigate(input)
    }
}
