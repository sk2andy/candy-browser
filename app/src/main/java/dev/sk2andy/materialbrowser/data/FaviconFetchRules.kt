package dev.sk2andy.materialbrowser.data

import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.UUID

internal object FaviconFetchRules {
    fun originIconUrl(pageUrl: String): String? {
        val page = runCatching { URI(pageUrl.trim()) }.getOrNull() ?: return null
        val scheme = page.scheme?.lowercase()?.takeIf { it == "http" || it == "https" }
            ?: return null
        val host = page.host?.takeIf(String::isNotBlank) ?: return null
        if (page.rawUserInfo != null) return null
        return runCatching {
            URI(scheme, null, host, page.port, "/favicon.ico", null, null).toASCIIString()
        }.getOrNull()
    }

    fun allowedRedirect(iconUrl: String, location: String): String? {
        val current = runCatching { URI(iconUrl) }.getOrNull() ?: return null
        val redirected = runCatching { current.resolve(location) }.getOrNull() ?: return null
        if (origin(current) != origin(redirected) || redirected.rawUserInfo != null) return null
        return redirected.toASCIIString()
    }

    fun cacheId(pageUrl: String): String? = originIconUrl(pageUrl)?.let { iconUrl ->
        UUID.nameUUIDFromBytes(iconUrl.toByteArray(StandardCharsets.UTF_8)).toString()
    }

    private fun origin(uri: URI): Origin? {
        val scheme = uri.scheme?.lowercase()?.takeIf { it == "http" || it == "https" }
            ?: return null
        val host = uri.host?.lowercase()?.takeIf(String::isNotBlank) ?: return null
        val port = when {
            uri.port >= 0 -> uri.port
            scheme == "https" -> 443
            else -> 80
        }
        return Origin(scheme = scheme, host = host, port = port)
    }

    private data class Origin(
        val scheme: String,
        val host: String,
        val port: Int,
    )
}
