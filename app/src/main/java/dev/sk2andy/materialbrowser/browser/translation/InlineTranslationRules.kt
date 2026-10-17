package dev.sk2andy.materialbrowser.browser.translation

internal data class InlineTranslationPair(val source: String, val target: String)

internal object InlineTranslationRules {
    const val MAX_TEXT_CHARS = 8_000
    const val MAX_RESULT_CHARS = 24_000
    const val TIMEOUT_MILLIS = 20_000L

    fun source(text: String): String? = text.takeIf {
        it.isNotBlank() && it.length <= MAX_TEXT_CHARS &&
            it.none { char -> char.isISOControl() && char != '\n' && char != '\t' && char != '\r' }
    }

    fun result(text: String?): String? = text?.takeIf {
        it.isNotBlank() && it.length <= MAX_RESULT_CHARS
    }

    fun pairs(pairs: List<InlineTranslationPair>): List<InlineTranslationPair> = pairs
        .filter { it.source.isNotBlank() && it.target.isNotBlank() && it.source != it.target }
        .distinct()
        .sortedWith(compareBy(InlineTranslationPair::source, InlineTranslationPair::target))
        .take(512)
}
