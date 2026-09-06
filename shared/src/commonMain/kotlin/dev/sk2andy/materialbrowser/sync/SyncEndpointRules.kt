package dev.sk2andy.materialbrowser.sync

import dev.sk2andy.materialbrowser.shared.browser.BrowserUrlRules

object SyncEndpointRules {
    private val loopbackHosts = setOf("localhost", "127.0.0.1", "[::1]", "::1")

    fun normalize(value: String?, allowRemoteHttp: Boolean = false): String? {
        val candidate = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val normalized = BrowserUrlRules.normalizeHttpUrl(candidate)?.value ?: return null
        val scheme = normalized.substringBefore("://")
        val authorityAndPath = normalized.substringAfter("://")
        val authorityEnd = authorityAndPath.indexOfAny(charArrayOf('/', '?', '#'))
        val authority = if (authorityEnd >= 0) authorityAndPath.substring(0, authorityEnd) else authorityAndPath
        val suffix = authorityAndPath.removePrefix(authority)
        if ('@' in authority || suffix !in setOf("", "/")) return null
        val host = authority.host().lowercase()
        if (host.isBlank()) return null
        if (scheme != "https" && !(scheme == "http" && (allowRemoteHttp || host in loopbackHosts))) return null
        return "$scheme://$authority/"
    }

    fun requiresRemoteHttpApproval(value: String): Boolean {
        val normalized = normalize(value, allowRemoteHttp = true) ?: return false
        return normalized.startsWith("http://") && normalized.substringAfter("://").substringBefore('/').host().lowercase() !in loopbackHosts
    }

    private fun String.host(): String = when {
        startsWith('[') -> substringBefore(']') + "]"
        else -> substringBefore(':')
    }
}
