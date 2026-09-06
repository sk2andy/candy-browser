package dev.sk2andy.materialbrowser.capsule

import java.util.Locale

data class CapsuleIconPack(
    val packageName: String,
    val label: String,
)

data class CapsuleIconPackEntry(
    val packageName: String,
    val drawableName: String,
    val label: String,
    val searchText: String,
)

object CapsuleIconPackRules {
    const val MAX_VISIBLE_ENTRIES = 500

    fun visibleEntries(
        entries: List<CapsuleIconPackEntry>,
        query: String,
    ): List<CapsuleIconPackEntry> {
        val tokens = normalize(query)
            .split(' ')
            .filter(String::isNotBlank)
        return entries.asSequence()
            .filter { entry ->
                tokens.isEmpty() || tokens.all(entry.searchText::contains)
            }
            .take(MAX_VISIBLE_ENTRIES)
            .toList()
    }

    internal fun mergeEntries(
        entries: Collection<CapsuleIconPackEntry>,
    ): List<CapsuleIconPackEntry> = entries
        .groupBy { entry -> entry.packageName to entry.drawableName }
        .map { (_, matches) ->
            val first = matches.first()
            first.copy(
                searchText = matches.asSequence()
                    .map(CapsuleIconPackEntry::searchText)
                    .distinct()
                    .joinToString(" ")
                    .take(MAX_SEARCH_TEXT_LENGTH),
            )
        }
        .sortedWith(
            compareBy<CapsuleIconPackEntry, String>(
                String.CASE_INSENSITIVE_ORDER,
            ) { it.label }.thenBy { it.drawableName },
        )

    internal fun entry(
        packageName: String,
        drawableName: String,
        components: Collection<String>,
    ): CapsuleIconPackEntry {
        val label = drawableName
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .split(WHITESPACE)
            .filter(String::isNotBlank)
            .joinToString(" ") { word ->
                word.replaceFirstChar { character ->
                    if (character.isLowerCase()) {
                        character.titlecase(Locale.ROOT)
                    } else {
                        character.toString()
                    }
                }
            }
            .ifBlank { drawableName }
        val searchText = buildString {
            append(normalize(drawableName))
            append(' ')
            append(normalize(label))
            components.forEach { component ->
                append(' ')
                append(normalize(component))
            }
        }.take(MAX_SEARCH_TEXT_LENGTH)
        return CapsuleIconPackEntry(
            packageName = packageName,
            drawableName = drawableName,
            label = label.take(MAX_LABEL_LENGTH),
            searchText = searchText,
        )
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(NON_SEARCH_CHARACTER, " ")
        .trim()
        .replace(WHITESPACE, " ")

    private const val MAX_LABEL_LENGTH = 80
    private const val MAX_SEARCH_TEXT_LENGTH = 1_024
    private val NON_SEARCH_CHARACTER = Regex("[^\\p{L}\\p{N}]+")
    private val WHITESPACE = Regex("\\s+")
}
