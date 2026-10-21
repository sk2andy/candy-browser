package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.shared.browser.BrowserUrlRules

data class SearchShortcut(
    val keyword: String,
    val template: String,
)

object SearchShortcutRules {
    const val MAX_SHORTCUTS = 30
    const val MAX_KEYWORD_LENGTH = 32
    const val MAX_TEMPLATE_LENGTH = 2_048
    private const val MAX_INPUT_LENGTH = 16_384

    private val keywordPattern = Regex("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,31}$")
    private val reservedKeywords = SearchEngine.entries.map { it.stableId }.toSet()

    fun sanitize(shortcuts: List<SearchShortcut>): List<SearchShortcut> {
        val accepted = mutableListOf<SearchShortcut>()
        shortcuts.take(MAX_SHORTCUTS).forEach { shortcut ->
            val normalized = normalize(shortcut) ?: return@forEach
            if (accepted.none { it.keyword.equals(normalized.keyword, ignoreCase = true) }) {
                accepted += normalized
            }
        }
        return accepted
    }

    fun normalize(shortcut: SearchShortcut): SearchShortcut? {
        val keyword = shortcut.keyword.trim().lowercase()
        val template = shortcut.template.trim()
        if (
            !keywordPattern.matches(keyword) || keyword in reservedKeywords ||
            template.length > MAX_TEMPLATE_LENGTH || template.countSubstring("%s") != 1
        ) return null
        val sampleUrl = template.replace("%s", "search-term")
        if (BrowserUrlRules.normalizeHttpUrl(sampleUrl) == null) return null
        return SearchShortcut(keyword, template)
    }

    fun add(shortcuts: List<SearchShortcut>, keyword: String, template: String): List<SearchShortcut>? {
        val normalized = normalize(SearchShortcut(keyword, template)) ?: return null
        if (shortcuts.size >= MAX_SHORTCUTS || shortcuts.any {
                it.keyword.equals(normalized.keyword, ignoreCase = true)
            }
        ) return null
        return sanitize(shortcuts + normalized)
    }

    fun resolve(input: String, shortcuts: List<SearchShortcut>): String? {
        if (
            input.length > MAX_INPUT_LENGTH ||
            input.any { it.code in 0..31 || it.code == 127 }
        ) return null
        val trimmed = input.trim()
        val separator = trimmed.indexOfFirst(Char::isWhitespace)
        if (separator <= 0) return null
        val keyword = trimmed.substring(0, separator)
        val query = trimmed.substring(separator).trim()
        if (query.isEmpty()) return null
        val shortcut = shortcuts.firstOrNull { it.keyword.equals(keyword, ignoreCase = true) }
            ?: return null
        return shortcut.template.replace("%s", BrowserUrlRules.encodeSearchQuery(query))
    }

    private fun String.countSubstring(substring: String): Int =
        windowed(substring.length).count { it == substring }
}
