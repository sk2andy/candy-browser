package dev.sk2andy.materialbrowser.ui

import java.text.Normalizer
import java.util.Locale

internal data class SettingsSearchEntry(
    val id: String,
    val title: String,
    val description: String,
    val context: String,
    val destination: SettingsDestination?,
)

internal object SettingsSearchRules {
    const val MAX_QUERY_LENGTH = 200

    fun results(query: String, entries: List<SettingsSearchEntry>): List<SettingsSearchEntry> {
        val terms = normalize(query.take(MAX_QUERY_LENGTH)).split(Regex("\\s+"))
            .filter(String::isNotBlank)
        if (terms.isEmpty()) return emptyList()
        return entries.mapNotNull { entry ->
            val title = normalize(entry.title)
            val text = normalize("${entry.title} ${entry.description} ${entry.context}")
            if (!terms.all(text::contains)) return@mapNotNull null
            val rank = when {
                title == terms.joinToString(" ") -> 0
                terms.all(title::contains) -> 1
                else -> 2
            }
            entry to rank
        }.sortedBy { (_, rank) -> rank }.map { (entry, _) -> entry }
    }

    private fun normalize(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
}
