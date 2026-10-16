package dev.sk2andy.materialbrowser.constellations

/** Cased BERT basic tokenization and greedy WordPiece for the pinned DistilUSE vocabulary. */
internal class ConstellationWordPieceTokenizer(vocabulary: List<String>) {
    private val vocabularyIds = vocabulary.withIndex().associate { it.value to it.index.toLong() }
    private val unknownId = requireNotNull(vocabularyIds["[UNK]"])
    private val startId = requireNotNull(vocabularyIds["[CLS]"])
    private val endId = requireNotNull(vocabularyIds["[SEP]"])
    private val specialTokens = listOf("[PAD]", "[UNK]", "[CLS]", "[SEP]", "[MASK]")
        .filter(vocabularyIds::containsKey)

    fun encode(text: String): LongArray {
        return frame(tokenIds(text).take(MAX_TOKENS - 2))
    }

    fun encodeWindows(title: String, text: String): List<LongArray> {
        val titleIds = tokenIds(title.take(MAX_TITLE_CHARACTERS)).take(MAX_TITLE_TOKENS)
        val bodyIds = tokenIds(text)
        val bodyCapacity = MAX_TOKENS - 2 - titleIds.size
        val lastStart = (bodyIds.size - bodyCapacity).coerceAtLeast(0)
        return listOf(0, lastStart / 2, lastStart).distinct().map { start ->
            frame(titleIds + bodyIds.subList(start, (start + bodyCapacity).coerceAtMost(bodyIds.size)))
        }
    }

    private fun tokenIds(text: String): List<Long> =
        basicTokens(text.take(MAX_INPUT_CHARACTERS)).flatMap(::wordPieces)

    private fun frame(ids: List<Long>): LongArray = (listOf(startId) + ids + endId).toLongArray()

    private fun basicTokens(text: String): List<String> {
        val tokens = ArrayList<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) {
                tokens += current.toString()
                current.setLength(0)
            }
        }
        var offset = 0
        while (offset < text.length) {
            val special = if (text[offset] == '[') {
                specialTokens.firstOrNull { text.startsWith(it, offset) }
            } else {
                null
            }
            if (special != null) {
                flush()
                tokens += special
                offset += special.length
                continue
            }
            val codePoint = text.codePointAt(offset)
            offset += Character.charCount(codePoint)
            when {
                codePoint == 0 || codePoint == 0xfffd -> Unit
                isWhitespace(codePoint) -> flush()
                isControl(codePoint) -> Unit
                isPunctuation(codePoint) || isChinese(codePoint) -> {
                    flush()
                    tokens += String(Character.toChars(codePoint))
                }
                else -> current.appendCodePoint(codePoint)
            }
        }
        flush()
        return tokens
    }

    private fun wordPieces(token: String): List<Long> {
        vocabularyIds[token]?.let { return listOf(it) }
        val codePoints = token.codePoints().toArray()
        if (codePoints.size > MAX_WORD_CHARACTERS) return listOf(unknownId)
        val result = ArrayList<Long>()
        var start = 0
        while (start < codePoints.size) {
            var end = codePoints.size
            var matched: Long? = null
            while (end > start) {
                val piece = (if (start == 0) "" else "##") +
                    String(codePoints, start, end - start)
                matched = vocabularyIds[piece]
                if (matched != null) break
                end--
            }
            if (matched == null) return listOf(unknownId)
            result += matched
            start = end
        }
        return result
    }

    private fun isWhitespace(codePoint: Int): Boolean =
        codePoint == 0x20 || codePoint == 0x09 || codePoint == 0x0a || codePoint == 0x0d ||
            Character.getType(codePoint) == Character.SPACE_SEPARATOR.toInt()

    private fun isControl(codePoint: Int): Boolean = when (Character.getType(codePoint)) {
        Character.CONTROL.toInt(), Character.FORMAT.toInt() -> true
        else -> false
    }

    private fun isPunctuation(codePoint: Int): Boolean =
        codePoint in 33..47 || codePoint in 58..64 || codePoint in 91..96 || codePoint in 123..126 ||
            when (Character.getType(codePoint)) {
                Character.CONNECTOR_PUNCTUATION.toInt(),
                Character.DASH_PUNCTUATION.toInt(),
                Character.START_PUNCTUATION.toInt(),
                Character.END_PUNCTUATION.toInt(),
                Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
                Character.FINAL_QUOTE_PUNCTUATION.toInt(),
                Character.OTHER_PUNCTUATION.toInt() -> true
                else -> false
            }

    private fun isChinese(codePoint: Int): Boolean =
        codePoint in 0x4e00..0x9fff || codePoint in 0x3400..0x4dbf ||
            codePoint in 0x20000..0x2a6df || codePoint in 0x2a700..0x2b73f ||
            codePoint in 0x2b740..0x2b81f || codePoint in 0x2b820..0x2ceaf ||
            codePoint in 0xf900..0xfaff || codePoint in 0x2f800..0x2fa1f

    companion object {
        const val MAX_TOKENS = 128
        private const val MAX_INPUT_CHARACTERS = 16_384
        private const val MAX_TITLE_CHARACTERS = 256
        private const val MAX_TITLE_TOKENS = 24
        private const val MAX_WORD_CHARACTERS = 100
    }
}
