package dev.sk2andy.materialbrowser.browser.commands

import dev.sk2andy.materialbrowser.data.AddressSuggestion

sealed interface AddressSuggestionItem {
    val stableId: String

    data class Navigation(val suggestion: AddressSuggestion) : AddressSuggestionItem {
        override val stableId: String = "navigation:${suggestion.openTabId ?: suggestion.url}"
    }

    data class Command(val suggestion: CommandSuggestion) : AddressSuggestionItem {
        override val stableId: String = "command:${suggestion.command.executionId}"
    }
}

object AddressSuggestionComposer {
    fun compose(
        query: String,
        navigation: List<AddressSuggestion>,
        commands: List<CommandSuggestion>,
        limit: Int,
    ): List<AddressSuggestionItem> {
        val safeLimit = limit.coerceAtLeast(0)
        if (CommandMatcher.isExplicitCommandQuery(query)) {
            return commands.take(safeLimit).map(AddressSuggestionItem::Command)
        }
        val command = commands.firstOrNull()
        val navigationLimit = if (command == null || (safeLimit == 1 && navigation.isNotEmpty())) {
            safeLimit
        } else {
            (safeLimit - 1).coerceAtLeast(0)
        }
        return buildList {
            navigation.take(navigationLimit).forEach { add(AddressSuggestionItem.Navigation(it)) }
            command?.takeIf { size < safeLimit }?.let { add(AddressSuggestionItem.Command(it)) }
        }
    }
}
