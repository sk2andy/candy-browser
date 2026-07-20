package dev.sk2andy.materialbrowser.browser

import java.net.URI

internal object FaviconRules {
    fun changedSite(previousUrl: String, newUrl: String): Boolean {
        val previousHost = host(previousUrl) ?: return false
        val newHost = host(newUrl)
        return newHost == null || !previousHost.equals(newHost, ignoreCase = true)
    }

    private fun host(url: String): String? = runCatching { URI(url).host }
        .getOrNull()
        ?.takeIf(String::isNotBlank)
}
