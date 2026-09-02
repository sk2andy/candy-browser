package dev.sk2andy.materialbrowser.sync

import java.net.URI

object SyncEndpointRules {
    private val loopbackHosts = setOf("localhost", "127.0.0.1", "[::1]", "::1")

    fun normalize(value: String?, allowRemoteHttp: Boolean = false): String? {
        val candidate = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "https" && !(scheme == "http" && (allowRemoteHttp || uri.host?.lowercase() in loopbackHosts))) {
            return null
        }
        if (!uri.isAbsolute || uri.host.isNullOrBlank()) return null
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        if (uri.rawPath != null && uri.rawPath != "" && uri.rawPath != "/") return null
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        val host = if (uri.host.contains(':') && !uri.host.startsWith('[')) "[${uri.host}]" else uri.host
        return "$scheme://$host$port/"
    }

    fun requiresRemoteHttpApproval(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme.equals("http", ignoreCase = true) && uri.host?.lowercase() !in loopbackHosts
    }
}
