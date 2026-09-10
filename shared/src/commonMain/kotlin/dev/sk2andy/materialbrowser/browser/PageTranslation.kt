package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.shared.browser.BrowserUrlRules

enum class PageTranslationProvider(
    val stableId: String,
    val displayName: String,
) {
    Google(
        stableId = "google",
        displayName = "Google Translate",
    ),
    Yandex(
        stableId = "yandex",
        displayName = "Yandex Translate",
    ),
    Kagi(
        stableId = "kagi",
        displayName = "Kagi Translate",
    ),
    ;

    companion object {
        fun fromStableId(stableId: String?): PageTranslationProvider =
            entries.firstOrNull { it.stableId == stableId } ?: Yandex
    }
}

object PageTranslationRules {
    private val targetLanguagePattern = Regex("^[a-z]{2,3}$")
    private val translationHosts = setOf(
        "translate.google.com",
        "translate.yandex.com",
        "translate.kagi.com",
        "translated.turbopages.org",
    )
    private val kagiProviderParameters = setOf("to", "kt_quality", "kt_view")

    fun canTranslate(
        provider: PageTranslationProvider,
        sourceUrl: String?,
    ): Boolean {
        val safeUrl = normalizedSourceUrl(sourceUrl) ?: return false
        val host = httpHost(safeUrl)?.lowercase()?.trimEnd('.') ?: return false
        if (host in translationHosts || host.endsWith(".translate.goog")) return false
        return provider != PageTranslationProvider.Kagi ||
            !hasKagiProviderParameter(rawQuery(safeUrl))
    }

    fun isProviderPage(
        provider: PageTranslationProvider,
        url: String?,
    ): Boolean {
        val safeUrl = normalizedSourceUrl(url) ?: return false
        val host = httpHost(safeUrl)?.lowercase()?.trimEnd('.') ?: return false
        return when (provider) {
            PageTranslationProvider.Google ->
                host == "translate.google.com" || host.endsWith(".translate.goog")
            PageTranslationProvider.Yandex ->
                host == "translate.yandex.com" || host == "translated.turbopages.org"
            PageTranslationProvider.Kagi -> host == "translate.kagi.com"
        }
    }

    fun isProviderResultPage(
        provider: PageTranslationProvider,
        url: String?,
    ): Boolean {
        val safeUrl = normalizedSourceUrl(url) ?: return false
        val host = httpHost(safeUrl)?.lowercase()?.trimEnd('.') ?: return false
        return when (provider) {
            PageTranslationProvider.Google -> host.endsWith(".translate.goog")
            PageTranslationProvider.Yandex -> host == "translated.turbopages.org"
            PageTranslationProvider.Kagi -> host == "translate.kagi.com"
        }
    }

    fun buildTranslationUrl(
        provider: PageTranslationProvider,
        sourceUrl: String?,
        targetLanguage: String,
    ): String? {
        val safeUrl = normalizedSourceUrl(sourceUrl)
            ?.takeIf { canTranslate(provider, it) }
            ?: return null
        val safeLanguage = targetLanguage(targetLanguage)
        return when (provider) {
            PageTranslationProvider.Google ->
                "https://translate.google.com/translate?sl=auto&tl=$safeLanguage&u=${safeUrl.urlEncoded()}"
            PageTranslationProvider.Yandex ->
                "https://translate.yandex.com/translate?url=${safeUrl.urlEncoded()}&lang=$safeLanguage"
            PageTranslationProvider.Kagi -> buildKagiTranslationUrl(
                sourceUrl = safeUrl,
                targetLanguage = safeLanguage,
            )
        }
    }

    fun targetLanguage(language: String): String = language
        .trim()
        .lowercase()
        .takeIf(targetLanguagePattern::matches)
        ?: DEFAULT_TARGET_LANGUAGE

    private fun normalizedSourceUrl(sourceUrl: String?): String? = sourceUrl
        ?.let(BrowserUrlRules::normalizeHttpUrl)
        ?.value
        ?.takeIf { it.length <= MAX_SOURCE_URL_LENGTH }

    private fun buildKagiTranslationUrl(
        sourceUrl: String,
        targetLanguage: String,
    ): String {
        val sourceWithoutScheme = sourceUrl.substringAfter("://")
        val fragment = sourceWithoutScheme.substringAfter('#', missingDelimiterValue = "")
        val sourceWithoutFragment = sourceWithoutScheme.substringBefore('#')
        val parameterSeparator = if ('?' in sourceWithoutFragment) '&' else '?'
        val translatedFragment = fragment.takeIf(String::isNotEmpty)?.let { "#$it" }.orEmpty()
        return "https://translate.kagi.com/$sourceWithoutFragment" +
            parameterSeparator +
            "to=$targetLanguage" +
            translatedFragment
    }

    private fun hasKagiProviderParameter(rawQuery: String?): Boolean {
        if (rawQuery == null) return false
        return rawQuery.split('&').any { parameter ->
            val rawName = parameter.substringBefore('=')
            val name = rawName.formUrlDecoded() ?: return true
            name.lowercase() in kagiProviderParameters
        }
    }

    private fun httpHost(url: String): String? {
        val authority = url.substringAfter("://", missingDelimiterValue = "")
            .substringBeforeAny('/', '?', '#')
        if (authority.isEmpty()) return null
        return if (authority.startsWith('[')) {
            authority.substringBefore(']', missingDelimiterValue = "")
                .takeIf(String::isNotEmpty)
                ?.plus(']')
        } else {
            authority.substringBefore(':').takeIf(String::isNotEmpty)
        }
    }

    private fun rawQuery(url: String): String? {
        val withoutFragment = url.substringBefore('#')
        return withoutFragment.substringAfter('?', missingDelimiterValue = "")
            .takeIf { '?' in withoutFragment }
    }

    private fun String.substringBeforeAny(vararg delimiters: Char): String {
        val index = indexOfAny(delimiters)
        return if (index < 0) this else substring(0, index)
    }

    private fun String.urlEncoded(): String = buildString {
        encodeToByteArray().forEach { byte ->
            val value = byte.toInt() and 0xff
            if (value.isUnreservedUrlByte()) {
                append(value.toChar())
            } else {
                append('%')
                append(HEX[value ushr 4])
                append(HEX[value and 0x0f])
            }
        }
    }

    private fun String.formUrlDecoded(): String? {
        val bytes = mutableListOf<Byte>()
        var index = 0
        while (index < length) {
            when (val character = this[index]) {
                '+' -> {
                    bytes += ' '.code.toByte()
                    index += 1
                }
                '%' -> {
                    if (index + 2 >= length) return null
                    val high = this[index + 1].hexValue() ?: return null
                    val low = this[index + 2].hexValue() ?: return null
                    bytes += ((high shl 4) or low).toByte()
                    index += 3
                }
                else -> {
                    bytes += character.toString().encodeToByteArray().toList()
                    index += 1
                }
            }
        }
        return runCatching {
            bytes.toByteArray().decodeToString(throwOnInvalidSequence = true)
        }.getOrNull()
    }

    private fun Int.isUnreservedUrlByte(): Boolean =
        this in 'a'.code..'z'.code ||
            this in 'A'.code..'Z'.code ||
            this in '0'.code..'9'.code ||
            this == '-'.code ||
            this == '.'.code ||
            this == '_'.code ||
            this == '~'.code

    private fun Char.hexValue(): Int? = when (this) {
        in '0'..'9' -> code - '0'.code
        in 'a'..'f' -> code - 'a'.code + 10
        in 'A'..'F' -> code - 'A'.code + 10
        else -> null
    }

    private const val DEFAULT_TARGET_LANGUAGE = "en"
    private const val MAX_SOURCE_URL_LENGTH = 8_192
    private const val HEX = "0123456789ABCDEF"
}
