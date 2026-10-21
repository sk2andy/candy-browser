package dev.sk2andy.materialbrowser.browser.integration

import java.net.URI

internal object ExternalAppWebUrlRules {
    /** Android app filters can require a root slash even when the web URL omits its path. */
    fun forAndroidApp(url: String?): String? {
        val safeUrl = BrowserUriPolicy.normalizeHttpUrl(url) ?: return null
        if (!URI(safeUrl).rawPath.isNullOrEmpty()) return safeUrl
        val suffixIndex = safeUrl.indexOfAny(charArrayOf('?', '#'))
            .takeIf { it >= 0 }
            ?: safeUrl.length
        return safeUrl.substring(0, suffixIndex) + "/" + safeUrl.substring(suffixIndex)
    }
}
