package dev.sk2andy.materialbrowser.browser

import java.net.IDN
import java.net.URI
import java.util.Locale

internal object TlsErrorRules {
    fun isForMainFrame(
        errorUrl: String?,
        currentMainFrameUrls: Collection<String?>,
    ): Boolean {
        val errorTarget = normalizedTarget(errorUrl) ?: return false
        return currentMainFrameUrls.any { candidate ->
            normalizedTarget(candidate) == errorTarget
        }
    }

    private fun normalizedTarget(rawUrl: String?): Target? {
        val uri = runCatching { URI(rawUrl ?: return null) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
            ?.takeIf { it == "http" || it == "https" }
            ?: return null
        if (uri.rawUserInfo != null) return null
        val rawHost = runCatching { uri.toURL().host }.getOrNull()
            ?.takeIf(String::isNotEmpty)
            ?: return null
        val host = if (':' in rawHost) {
            rawHost.lowercase(Locale.ROOT)
        } else {
            runCatching { IDN.toASCII(rawHost, IDN.USE_STD3_ASCII_RULES) }
                .getOrNull()
                ?.lowercase(Locale.ROOT)
                ?.removeSuffix(".")
                ?.takeIf(String::isNotEmpty)
                ?: return null
        }
        val port = when {
            uri.port >= 0 -> uri.port
            scheme == "https" -> 443
            else -> 80
        }
        return Target(
            scheme = scheme,
            host = host,
            port = port,
            path = uri.rawPath?.takeIf(String::isNotEmpty) ?: "/",
            query = uri.rawQuery,
        )
    }

    private data class Target(
        val scheme: String,
        val host: String,
        val port: Int,
        val path: String,
        val query: String?,
    )
}
