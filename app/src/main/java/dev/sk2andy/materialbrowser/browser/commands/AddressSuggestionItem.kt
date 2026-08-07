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

    data class Search(val query: String) : AddressSuggestionItem {
        override val stableId: String = "search:$query"
    }
}

object AddressSuggestionComposer {
    fun compose(
        query: String,
        navigation: List<AddressSuggestion>,
        commands: List<CommandSuggestion>,
        limit: Int,
        searchQueries: List<String> = emptyList(),
    ): List<AddressSuggestionItem> {
        val safeLimit = limit.coerceAtLeast(0)
        if (CommandMatcher.isExplicitCommandQuery(query)) {
            return commands.take(safeLimit).map(AddressSuggestionItem::Command)
        }
        val hasDirectSuggestion = navigation.isNotEmpty() || searchQueries.isNotEmpty()
        val command = commands.firstOrNull()?.takeUnless { safeLimit == 1 && hasDirectSuggestion }
        val contentLimit = (safeLimit - if (command == null) 0 else 1).coerceAtLeast(0)
        val remoteLimit = if (navigation.isEmpty()) {
            contentLimit
        } else {
            (contentLimit - 1).coerceAtLeast(0)
        }
        val remote = searchQueries.take(remoteLimit)
        val navigationLimit = (contentLimit - remote.size).coerceAtLeast(0)
        return buildList {
            navigation.take(navigationLimit).forEach { add(AddressSuggestionItem.Navigation(it)) }
            remote.forEach { add(AddressSuggestionItem.Search(it)) }
            command?.takeIf { size < safeLimit }?.let { add(AddressSuggestionItem.Command(it)) }
        }
    }
}
