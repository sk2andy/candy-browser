package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.sk2andy.materialbrowser.browser.SearchShortcut
import dev.sk2andy.materialbrowser.browser.SearchShortcutRules

internal object AddressSearchShortcutRules {
    fun edit(
        previous: TextFieldValue,
        next: TextFieldValue,
        shortcuts: List<SearchShortcut>,
    ): TextFieldValue {
        val previousMatch = SearchShortcutRules.match(previous.text, shortcuts)
        // IMEs delete from the real buffer even when the visible query is empty.
        if (previousMatch?.query?.isEmpty() == true && next.text.length < previous.text.length) {
            return TextFieldValue()
        }
        if (previousMatch != null && previousMatch.query.isNotEmpty()) {
            val prefixLength = previousMatch.prefix.length
            if (
                previous.selection == TextRange(prefixLength) &&
                next.text == previous.text.removeRange(prefixLength - 1, prefixLength)
            ) return previous
        }
        val match = SearchShortcutRules.match(next.text, shortcuts) ?: return next
        val prefixLength = match.prefix.length
        return next.copy(
            selection = TextRange(
                next.selection.start.coerceAtLeast(prefixLength),
                next.selection.end.coerceAtLeast(prefixLength),
            ),
            composition = next.composition?.takeIf { it.min >= prefixLength },
        )
    }

    fun fill(input: String, query: String, shortcuts: List<SearchShortcut>): String =
        SearchShortcutRules.match(input, shortcuts)?.let { it.prefix + query } ?: query
}
