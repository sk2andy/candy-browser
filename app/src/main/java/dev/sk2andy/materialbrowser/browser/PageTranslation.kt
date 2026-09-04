package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

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
        val sourceUri = runCatching { URI(safeUrl) }.getOrNull() ?: return false
        val host = runCatching { sourceUri.toURL().host }
            .getOrNull()
            ?.lowercase(Locale.ROOT)
            ?.trimEnd('.')
            ?: return false
        if (host in translationHosts || host.endsWith(".translate.goog")) return false
        return provider != PageTranslationProvider.Kagi ||
            !hasKagiProviderParameter(sourceUri.rawQuery)
    }

    fun buildTranslationUrl(
        provider: PageTranslationProvider,
        sourceUrl: String?,
        targetLanguage: String,
    ): String? {
        val safeUrl = normalizedSourceUrl(sourceUrl)
            ?.takeIf { canTranslate(provider, it) }
            ?: return null
        val safeLanguage = normalizedTargetLanguage(targetLanguage)
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

    fun targetLanguage(locale: Locale): String = normalizedTargetLanguage(locale.language)

    private fun normalizedSourceUrl(sourceUrl: String?): String? =
        BrowserUriPolicy.normalizeHttpUrl(sourceUrl)
            ?.takeIf { it.length <= MAX_SOURCE_URL_LENGTH }

    private fun normalizedTargetLanguage(language: String): String = language
        .trim()
        .lowercase(Locale.ROOT)
        .takeIf(targetLanguagePattern::matches)
        ?: DEFAULT_TARGET_LANGUAGE

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
            "$parameterSeparator" +
            "to=$targetLanguage" +
            translatedFragment
    }

    private fun hasKagiProviderParameter(rawQuery: String?): Boolean {
        if (rawQuery == null) return false
        return rawQuery.split('&').any { parameter ->
            val rawName = parameter.substringBefore('=')
            val name = runCatching {
                URLDecoder.decode(rawName, StandardCharsets.UTF_8.toString())
            }.getOrNull() ?: return true
            name.lowercase(Locale.ROOT) in kagiProviderParameters
        }
    }

    private const val DEFAULT_TARGET_LANGUAGE = "en"
    private const val MAX_SOURCE_URL_LENGTH = 8_192
}
