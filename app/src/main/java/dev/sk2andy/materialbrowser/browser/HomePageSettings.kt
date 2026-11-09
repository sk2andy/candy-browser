package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import java.net.URI

data class HomePageSettings(
    val homeUrl: String = "",
    val newTabUrl: String = "",
)

object HomePageRules {
    const val MAX_URL_LENGTH = 4_096

    fun normalizeUrl(input: String): String? {
        val candidate = input.trim()
        if (candidate.isEmpty()) return ""
        if (candidate.length > MAX_URL_LENGTH) return null
        val safeUrl = BrowserUriPolicy.normalizeHttpUrl(candidate) ?: return null
        val uri = runCatching { URI(safeUrl) }.getOrNull() ?: return null
        if (uri.port != -1 && uri.port !in 1..65_535) return null
        return safeUrl
    }

    fun sanitize(settings: HomePageSettings): HomePageSettings = HomePageSettings(
        homeUrl = normalizeUrl(settings.homeUrl).orEmpty(),
        newTabUrl = normalizeUrl(settings.newTabUrl).orEmpty(),
    )

    fun newTabUrl(settings: HomePageSettings, isIncognito: Boolean): String =
        if (isIncognito) BLANK_URL else normalizeUrl(settings.newTabUrl)
            ?.takeIf(String::isNotEmpty) ?: BLANK_URL
}
