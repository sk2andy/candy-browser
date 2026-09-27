package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import java.net.IDN
import java.net.URI

internal enum class SiteConnectionKind {
    Https,
    Http,
    Unavailable,
    Other,
}

internal object SiteConnectionRules {
    fun kind(
        pageUrl: String,
        isLoading: Boolean = false,
        hasError: Boolean = false,
    ): SiteConnectionKind {
        val safeUrl = BrowserUriPolicy.normalizeHttpUrl(pageUrl) ?: return SiteConnectionKind.Other
        if (isLoading || hasError) return SiteConnectionKind.Unavailable
        return if (URI(safeUrl).scheme.equals("https", ignoreCase = true)) {
            SiteConnectionKind.Https
        } else {
            SiteConnectionKind.Http
        }
    }

    fun host(pageUrl: String): String {
        val safeUrl = BrowserUriPolicy.normalizeHttpUrl(pageUrl) ?: return ""
        val host = runCatching { URI(safeUrl).toURL().host }.getOrNull().orEmpty()
        return runCatching { IDN.toUnicode(host) }.getOrDefault(host)
    }
}
