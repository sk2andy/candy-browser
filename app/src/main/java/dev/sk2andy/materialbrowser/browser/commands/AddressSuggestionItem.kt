package dev.sk2andy.materialbrowser.browser.commands

import dev.sk2andy.materialbrowser.data.AddressSuggestion
import dev.sk2andy.materialbrowser.data.CanonicalWebUrl
import dev.sk2andy.materialbrowser.recall.RecallMatch
import dev.sk2andy.materialbrowser.recall.RecallRules

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

    data class Recall(val match: RecallMatch) : AddressSuggestionItem {
        override val stableId: String = "recall:${match.profileId}:${match.url}"
    }
}

object AddressSuggestionComposer {
    fun compose(
        query: String,
        navigation: List<AddressSuggestion>,
        commands: List<CommandSuggestion>,
        limit: Int,
        searchQueries: List<String> = emptyList(),
        recallMatches: List<RecallMatch> = emptyList(),
    ): List<AddressSuggestionItem> {
        val safeLimit = limit.coerceAtLeast(0)
        if (RecallRules.isExplicitCommand(query)) {
            return recallMatches
                .take(minOf(safeLimit, RecallRules.MAX_COMMAND_RESULTS))
                .map(AddressSuggestionItem::Recall)
        }
        if (CommandMatcher.isExplicitCommandQuery(query)) {
            return commands.take(safeLimit).map(AddressSuggestionItem::Command)
        }
        val recall = recallMatches.take(RecallRules.MAX_ADDRESS_RESULTS)
        val recallUrls = recall.mapNotNullTo(hashSetOf()) { match ->
            CanonicalWebUrl.key(match.url)
        }
        val distinctNavigation = navigation.filterNot { suggestion ->
            CanonicalWebUrl.key(suggestion.url) in recallUrls
        }
        val hasDirectSuggestion =
            distinctNavigation.isNotEmpty() || recall.isNotEmpty() || searchQueries.isNotEmpty()
        val command = commands.firstOrNull()?.takeUnless { safeLimit == 1 && hasDirectSuggestion }
        val contentLimit = (safeLimit - if (command == null) 0 else 1).coerceAtLeast(0)
        val boundedRecall = recall.take(contentLimit)
        val contentAfterRecall = (contentLimit - boundedRecall.size).coerceAtLeast(0)
        val remoteLimit = if (distinctNavigation.isEmpty()) {
            contentAfterRecall
        } else {
            (contentAfterRecall - 1).coerceAtLeast(0)
        }
        val remote = searchQueries.take(remoteLimit)
        val navigationLimit =
            (contentLimit - boundedRecall.size - remote.size).coerceAtLeast(0)
        return buildList {
            distinctNavigation.take(navigationLimit).forEach {
                add(AddressSuggestionItem.Navigation(it))
            }
            boundedRecall.forEach { add(AddressSuggestionItem.Recall(it)) }
            remote.forEach { add(AddressSuggestionItem.Search(it)) }
            command?.takeIf { size < safeLimit }?.let { add(AddressSuggestionItem.Command(it)) }
        }
    }
}
