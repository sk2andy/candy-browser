package dev.sk2andy.materialbrowser.browser

import java.net.URI

internal object FaviconRules {
    fun changedSite(previousUrl: String, newUrl: String): Boolean {
        val previousOrigin = origin(previousUrl) ?: return false
        return previousOrigin != origin(newUrl)
    }

    fun belongsToDocument(tabUrl: String, reportedUrl: String?): Boolean {
        val reported = reportedUrl ?: return false
        val tab = runCatching { URI(tabUrl) }.getOrNull() ?: return false
        val iconPage = runCatching { URI(reported) }.getOrNull() ?: return false
        val tabOrigin = origin(tab) ?: return false
        return tabOrigin == origin(iconPage) &&
            tab.rawPath.orEmpty().ifBlank { "/" } ==
                iconPage.rawPath.orEmpty().ifBlank { "/" } &&
            tab.rawQuery == iconPage.rawQuery
    }

    private fun origin(url: String): Origin? = runCatching { URI(url) }
        .getOrNull()
        ?.let(::origin)

    private fun origin(uri: URI): Origin? {
        val scheme = uri.scheme?.lowercase()?.takeIf { it == "http" || it == "https" }
            ?: return null
        val host = uri.host?.lowercase()?.takeIf(String::isNotBlank) ?: return null
        if (uri.rawUserInfo != null) return null
        val port = uri.port.takeIf { it >= 0 } ?: if (scheme == "https") 443 else 80
        return Origin(scheme, host, port)
    }

    private data class Origin(val scheme: String, val host: String, val port: Int)
}
