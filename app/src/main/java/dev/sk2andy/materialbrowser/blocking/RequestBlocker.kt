package dev.sk2andy.materialbrowser.blocking

import java.net.URI

class RequestBlocker(hostRules: Sequence<String>) {
    private val blockedHosts = hostRules
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith('#') }
        .map { it.lowercase().removePrefix("||").removeSuffix("^").trim('.') }
        .toSet()

    fun shouldBlock(requestUrl: String, pageUrl: String?): Boolean {
        val request = runCatching { URI(requestUrl) }.getOrNull() ?: return false
        if (request.scheme != "http" && request.scheme != "https") return false
        val requestHost = request.host?.lowercase()?.trim('.') ?: return false
        val pageHost = pageUrl?.let { url ->
            runCatching { URI(url).host?.lowercase()?.trim('.') }.getOrNull()
        }

        // Never block top-level/same-site resources. Host rules target third-party ads and trackers.
        if (pageHost != null && isSameSite(requestHost, pageHost)) return false
        return hostSuffixes(requestHost).any(blockedHosts::contains)
    }

    private fun isSameSite(first: String, second: String): Boolean =
        first == second || first.endsWith(".$second") || second.endsWith(".$first")

    private fun hostSuffixes(host: String): Sequence<String> = sequence {
        var current = host
        while (true) {
            yield(current)
            val dot = current.indexOf('.')
            if (dot < 0) break
            current = current.substring(dot + 1)
        }
    }
}
