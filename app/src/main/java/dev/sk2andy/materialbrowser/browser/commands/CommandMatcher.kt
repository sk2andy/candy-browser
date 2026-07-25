package dev.sk2andy.materialbrowser.browser.commands

import java.text.Normalizer
import java.util.Locale

data class CommandSuggestion(
    val command: BrowserCommand,
    val name: String,
    val effect: String,
)

object CommandMatcher {
    fun isExplicitCommandQuery(query: String): Boolean = query.trimStart().startsWith('>')

    fun match(
        query: String,
        commands: List<CommandSuggestion>,
        limit: Int,
    ): List<CommandSuggestion> {
        val explicit = isExplicitCommandQuery(query)
        val term = normalized(
            if (explicit) query.trimStart().removePrefix(">").trim() else query.trim(),
        )
        if (!explicit && !isSafeImplicitTerm(term)) return emptyList()
        if (explicit && term.isEmpty()) return commands.take(limit.coerceAtLeast(0))

        val matches = commands.asSequence()
            .mapNotNull { suggestion ->
                val searchableValues = buildList {
                    add(normalized(suggestion.name) to 0)
                    suggestion.command.targetProfileLabel?.let { add(normalized(it) to 40) }
                    if (explicit) add(normalized(suggestion.effect) to 80)
                }
                searchableValues.mapNotNull { (value, penalty) ->
                    score(value, term, explicit)?.minus(penalty)
                }.maxOrNull()?.let { score -> ScoredCommand(suggestion, score) }
            }
            .sortedWith(
                compareByDescending<ScoredCommand> { it.score }
                    .thenBy { commands.indexOf(it.suggestion) },
            )
            .toList()
        if (!explicit && matches.size != 1) return emptyList()
        return matches.asSequence()
            .map(ScoredCommand::suggestion)
            .take(if (explicit) limit.coerceAtLeast(0) else minOf(1, limit.coerceAtLeast(0)))
            .toList()
    }

    private fun isSafeImplicitTerm(term: String): Boolean =
        term.length >= MIN_IMPLICIT_LENGTH &&
            term.none { it == '.' || it == '/' || it == ':' || it == '@' }

    private fun score(name: String, term: String, explicit: Boolean): Int? {
        if (name == term) return 1_000
        if (name.startsWith(term)) return 900
        if (name.split(' ').any { it.startsWith(term) }) return if (explicit) 800 else null
        if (!explicit) return null
        if (name.contains(term)) return 700
        val gapPenalty = subsequenceGapPenalty(name, term) ?: return null
        return (600 - gapPenalty).coerceAtLeast(1)
    }

    private fun subsequenceGapPenalty(name: String, term: String): Int? {
        var nameIndex = 0
        var firstMatch = -1
        var lastMatch = -1
        term.forEach { character ->
            val matchIndex = name.indexOf(character, startIndex = nameIndex)
            if (matchIndex < 0) return null
            if (firstMatch < 0) firstMatch = matchIndex
            lastMatch = matchIndex
            nameIndex = matchIndex + 1
        }
        return (lastMatch - firstMatch + 1 - term.length) * 8 + firstMatch
    }

    private fun normalized(value: String): String = Normalizer.normalize(
        value.lowercase(Locale.ROOT),
        Normalizer.Form.NFD,
    ).replace(COMBINING_MARKS, "")

    private data class ScoredCommand(
        val suggestion: CommandSuggestion,
        val score: Int,
    )

    private val COMBINING_MARKS = "\\p{M}+".toRegex()
    private const val MIN_IMPLICIT_LENGTH = 4
}
