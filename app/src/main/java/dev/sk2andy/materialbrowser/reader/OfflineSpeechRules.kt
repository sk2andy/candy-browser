package dev.sk2andy.materialbrowser.reader

internal data class OfflineSpeechChunk(val start: Int, val end: Int, val text: String)

internal object OfflineSpeechRules {
    private const val MAX_CHUNK_CHARACTERS = 240

    fun nextChunk(text: String, offset: Int): OfflineSpeechChunk? {
        var start = offset.coerceIn(0, text.length)
        while (start < text.length && text[start].isWhitespace()) start++
        if (start == text.length) return null
        var end = minOf(text.length, start + MAX_CHUNK_CHARACTERS)
        val punctuation = text.indexOfAny(charArrayOf('.', '!', '?', '\n', '。', '！', '？'), start)
        if (punctuation in start until end) {
            end = punctuation + 1
        } else if (end < text.length) {
            end = text.lastIndexOf(' ', end).takeIf { it > start } ?: end
        }
        if (end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
        return OfflineSpeechChunk(start, end, text.substring(start, end))
    }
}
