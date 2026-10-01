package dev.sk2andy.materialbrowser.browser

import java.net.URI
import java.util.Locale

/** Internal Gecko destinations entered through browser chrome, separate from external web URLs. */
internal object GeckoInternalPageRules {
    private val aboutUrlPattern = Regex("about:([a-z][a-z0-9-]*)([?#].*)?", RegexOption.IGNORE_CASE)

    fun normalizeUrl(value: String?): String? {
        val candidate = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (candidate.length > MAX_URL_LENGTH) return null
        if (candidate.any { it.code <= 0x20 || it.code == 0x7f }) return null
        val match = aboutUrlPattern.matchEntire(candidate) ?: return null
        if (runCatching { URI(candidate) }.getOrNull() == null) return null
        return "about:${match.groupValues[1].lowercase(Locale.ROOT)}${match.groupValues[2]}"
    }

    fun canNavigate(
        url: String,
        currentUrl: String?,
        isDirectNavigation: Boolean,
        isRedirect: Boolean,
        target: BrowserEngineNavigationTarget,
    ): Boolean = normalizeUrl(url) != null &&
        target == BrowserEngineNavigationTarget.Current &&
        ((isDirectNavigation && !isRedirect) || isInternalPage(currentUrl))

    private fun isInternalPage(url: String?): Boolean {
        val page = normalizeUrl(url)?.substringBefore('?')?.substringBefore('#') ?: return false
        // Blank and srcdoc documents can inherit a website's origin.
        return page != BLANK_URL && page != "about:srcdoc"
    }

    fun restoreTab(tab: BrowserTab, engineKind: AndroidBrowserEngineKind): BrowserTab =
        if (engineKind != AndroidBrowserEngineKind.GeckoView && normalizeUrl(tab.url) != null) {
            tab.copy(
                url = BLANK_URL,
                title = "",
                progress = 0,
                isLoading = false,
                canGoBack = false,
                canGoForward = false,
                blockedCount = 0,
                error = null,
                failureKind = null,
                httpStatusCode = null,
            )
        } else {
            tab
        }

    private const val MAX_URL_LENGTH = 32_768
}
