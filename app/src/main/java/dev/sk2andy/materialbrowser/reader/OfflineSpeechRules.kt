package dev.sk2andy.materialbrowser.reader

internal data class OfflineSpeechChunk(val start: Int, val end: Int, val text: String)

internal object OfflineSpeechRules {
    private const val MAX_CHUNK_CHARACTERS = 240
    private val abbreviation = Regex(
        """(?iu)(?<![\p{L}\p{N}])(?:z\.\s?B\.|d\.\s?h\.|u\.\s?a\.|e\.g\.|i\.e\.|Dr\.|Prof\.|Mr\.|Mrs\.|Ms\.|bzw\.|ca\.|usw\.|etc\.|Nr\.|No\.|St\.)""",
    )

    fun nextChunk(text: String, offset: Int): OfflineSpeechChunk? {
        var start = offset.coerceIn(0, text.length)
        while (start < text.length && text[start].isWhitespace()) start++
        if (start == text.length) return null
        var end = minOf(text.length, start + MAX_CHUNK_CHARACTERS)
        val protectedAbbreviations = abbreviation.findAll(text.substring(start, minOf(text.length, end + 8)))
            .map { (it.range.first + start)..(it.range.last + start) }
            .toList()
        val punctuation = (start until end).firstOrNull { index ->
            when (text[index]) {
                '\n', '。', '！', '？' -> true
                '!', '?' -> index + 1 == text.length || text[index + 1].isWhitespace() ||
                    text[index + 1] in "\"'’”)]}"
                '.' -> isSentencePeriod(text, index, protectedAbbreviations)
                else -> false
            }
        }
        if (punctuation != null) {
            end = punctuation + 1
        } else if (end < text.length) {
            end = text.lastIndexOf(' ', end - 1).takeIf { it > start } ?: end
        }
        if (end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
        return OfflineSpeechChunk(start, end, text.substring(start, end))
    }

    private fun isSentencePeriod(text: String, index: Int, abbreviations: List<IntRange>): Boolean {
        if (abbreviations.any { index in it }) return false
        val next = text.getOrNull(index + 1)
        // Internal punctuation in numbers, domains, email addresses and initialisms is not a stop.
        if (next != null && !next.isWhitespace() && next !in "\"'’”)]}") return false
        // German ordinal dates, such as 12. Oktober, retain the date context.
        if (text.getOrNull(index - 1)?.isDigit() == true && next?.isWhitespace() == true) return false
        // Keep a person's initial with the name.
        if (index > 0 && text[index - 1].isUpperCase() &&
            (index == 1 || !text[index - 2].isLetter()) && next?.isWhitespace() == true
        ) return false
        return true
    }
}
