package dev.sk2andy.materialbrowser.data

import java.net.URI
import java.util.Locale

internal object CanonicalWebUrl {
    fun key(url: String): String? = runCatching {
        val uri = URI(url.trim())
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val host = uri.host?.lowercase(Locale.ROOT)
        if ((scheme != "http" && scheme != "https") || host.isNullOrBlank()) return null
        buildString {
            append(scheme)
            append("://")
            append(host)
            val port = uri.port
            if (port >= 0 && !isDefaultPort(scheme, port)) append(":$port")
            append(uri.rawPath.orEmpty().ifEmpty { "/" })
            uri.rawQuery?.let { append("?$it") }
        }
    }.getOrNull()

    private fun isDefaultPort(scheme: String, port: Int): Boolean =
        (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
}
