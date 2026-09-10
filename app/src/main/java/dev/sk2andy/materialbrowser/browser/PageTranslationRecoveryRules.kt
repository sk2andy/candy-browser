package dev.sk2andy.materialbrowser.browser

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal enum class PageTranslationRecoveryAction {
    TryYandex,
    OpenOriginal,
}

internal enum class PageTranslationContentOutcome {
    Visible,
    Empty,
    ProviderError,
    Unknown,
}

internal enum class PageTranslationContentAction {
    Complete,
    Retry,
    ReportFailure,
}

internal data class PageTranslationAttempt(
    val tabId: String,
    val sourceUrl: String,
    val provider: PageTranslationProvider,
)

internal data class PageTranslationRecoveryOffer(
    val token: Long,
    val tabId: String,
    val sourceUrl: String,
    val failedUrl: String,
    val provider: PageTranslationProvider,
    val navigationGeneration: Int,
) {
    val action: PageTranslationRecoveryAction
        get() = PageTranslationRecoveryRules.actionFor(provider)
}

internal object PageTranslationRecoveryRules {
    fun actionFor(provider: PageTranslationProvider): PageTranslationRecoveryAction =
        if (provider == PageTranslationProvider.Yandex) {
            PageTranslationRecoveryAction.OpenOriginal
        } else {
            PageTranslationRecoveryAction.TryYandex
        }

    fun isProviderFailure(
        provider: PageTranslationProvider,
        url: String?,
        navigationFailed: Boolean,
        httpStatusCode: Int?,
    ): Boolean = PageTranslationRules.isProviderPage(provider, url) &&
        (navigationFailed || httpStatusCode?.let { statusCode -> statusCode in 400..599 } == true)

    fun isRejectedEntryPage(
        provider: PageTranslationProvider,
        url: String?,
        navigationCommitted: Boolean,
    ): Boolean = navigationCommitted &&
        PageTranslationRules.isProviderPage(provider, url) &&
        !PageTranslationRules.isProviderResultPage(provider, url)

    fun shouldCheckResultContent(
        provider: PageTranslationProvider,
        url: String?,
    ): Boolean = PageTranslationRules.isProviderResultPage(provider, url)

    fun contentOutcome(rawResult: String?): PageTranslationContentOutcome {
        val value = rawResult
            ?.takeIf { candidate -> candidate != "null" && candidate.length <= MAX_RESULT_CHARS }
            ?.trim()
            ?: return PageTranslationContentOutcome.Unknown
        val jsonText = if (value.startsWith('{')) {
            value
        } else {
            runCatching { JSONArray("[$value]").getString(0) }.getOrNull()
                ?: return PageTranslationContentOutcome.Unknown
        }
        return runCatching {
            val root = JSONObject(jsonText)
            val hasVisibleContent = root.opt("hasVisibleContent")
            if (hasVisibleContent !is Boolean) {
                return@runCatching PageTranslationContentOutcome.Unknown
            }
            if (!hasVisibleContent) {
                return@runCatching PageTranslationContentOutcome.Empty
            }
            if (isProviderError(root)) {
                PageTranslationContentOutcome.ProviderError
            } else {
                PageTranslationContentOutcome.Visible
            }
        }.getOrDefault(PageTranslationContentOutcome.Unknown)
    }

    fun contentAction(
        outcome: PageTranslationContentOutcome,
        emptyChecksRemaining: Int,
    ): PageTranslationContentAction = when (outcome) {
        PageTranslationContentOutcome.Empty -> if (emptyChecksRemaining > 1) {
            PageTranslationContentAction.Retry
        } else {
            PageTranslationContentAction.ReportFailure
        }
        PageTranslationContentOutcome.ProviderError ->
            PageTranslationContentAction.ReportFailure
        PageTranslationContentOutcome.Visible,
        PageTranslationContentOutcome.Unknown,
        -> PageTranslationContentAction.Complete
    }

    private fun isProviderError(root: JSONObject): Boolean {
        val text = root.optString("visibleText").trim()
        if (text.isEmpty() || text.length > MAX_PROVIDER_ERROR_TEXT_CHARS) return false
        val normalizedText = text.lowercase(Locale.ROOT)
        return providerErrorPhrases.any(normalizedText::contains)
    }

    private const val MAX_RESULT_CHARS = 2_000_000
    private const val MAX_PROVIDER_ERROR_TEXT_CHARS = 1_000
    private val providerErrorPhrases = listOf(
        "couldn't translate",
        "could not translate",
        "can't translate",
        "unable to translate",
        "translation failed",
        "konnte diese seite nicht übersetzen",
        "seite konnte nicht übersetzt",
        "übersetzung fehlgeschlagen",
        "no se pudo traducir",
        "no ha podido traducir",
        "traducción fallida",
        "impossible de traduire",
        "n’a pas pu traduire",
        "n'a pas pu traduire",
        "échec de la traduction",
        "não foi possível traduzir",
        "não conseguiu traduzir",
        "falha na tradução",
    )
}
