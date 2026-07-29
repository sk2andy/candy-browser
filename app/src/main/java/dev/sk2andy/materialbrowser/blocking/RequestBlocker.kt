package dev.sk2andy.materialbrowser.blocking

import java.net.URI

class RequestBlocker(
    hostRules: Sequence<String>,
    blockedHostPairs: Sequence<String> = emptySequence(),
    allowedHostPairs: Sequence<String> = emptySequence(),
) {
    private val blockedHosts = hostRules
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith('#') }
        .map { it.lowercase().removePrefix("||").removeSuffix("^").trim('.') }
        .filter { rule -> rule.all { it.isLetterOrDigit() || it == '.' || it == '-' } }
        .distinct()
        .sorted()
        .toList()
    private val blockedPageHostsByRequestHost = parseHostPairs(blockedHostPairs)
    private val allowedPageHostsByRequestHost = parseHostPairs(allowedHostPairs)

    private fun parseHostPairs(lines: Sequence<String>): Map<String, List<String>> = lines
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith('#') }
        .mapNotNull { line ->
            val fields = line.lowercase().split('\t', limit = 2)
            if (fields.size != 2) return@mapNotNull null
            val requestHost = fields[0].trim('.')
            val pageHost = fields[1].trim('.')
            if (!requestHost.isHostRule() || (pageHost != "*" && !pageHost.isHostRule())) {
                return@mapNotNull null
            }
            requestHost to pageHost
        }
        .groupBy({ it.first }, { it.second })

    fun shouldBlock(requestUrl: String, pageUrl: String?): Boolean {
        val request = runCatching { URI(requestUrl) }.getOrNull() ?: return false
        if (request.scheme?.lowercase() !in WEB_SCHEMES) return false
        val requestHost = request.host?.lowercase()?.trim('.') ?: return false
        val pageHost = pageUrl?.let { url ->
            runCatching { URI(url).host?.lowercase()?.trim('.') }.getOrNull()
        }

        // Keep the current site functional when a list contains its own host. This mirrors the
        // first-party escape used by DuckDuckGo's Android tracker detector:
        // https://github.com/duckduckgo/Android/blob/4472de82e610b12689dcd2fc1b8421439020af62/app/src/main/java/com/duckduckgo/app/trackerdetection/TrackerDetectorImpl.kt
        if (pageHost != null && isSameHostOrSubdomain(requestHost, pageHost)) return false
        if (isAllowedByFilterException(requestHost, pageHost)) return false
        if (isBlockedByFilterPair(requestHost, pageHost)) return true

        var candidate = requestHost
        while (true) {
            if (blockedHosts.binarySearch(candidate) >= 0) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }

    private fun isSameHostOrSubdomain(first: String, second: String): Boolean =
        first == second || first.endsWith(".$second") || second.endsWith(".$first")

    private fun isAllowedByFilterException(requestHost: String, pageHost: String?): Boolean {
        var candidate = requestHost
        while (true) {
            val allowedPageHosts = allowedPageHostsByRequestHost[candidate]
            if (allowedPageHosts != null && allowedPageHosts.any { allowedPageHost ->
                    allowedPageHost == "*" ||
                        (pageHost != null && pageHost.matchesHostOrSubdomain(allowedPageHost))
                }
            ) return true

            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }

    private fun isBlockedByFilterPair(requestHost: String, pageHost: String?): Boolean {
        if (pageHost == null) return false
        var candidate = requestHost
        while (true) {
            val blockedPageHosts = blockedPageHostsByRequestHost[candidate]
            if (blockedPageHosts != null && blockedPageHosts.any { blockedPageHost ->
                    pageHost.matchesHostOrSubdomain(blockedPageHost)
                }
            ) {
                return true
            }

            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }

    private fun String.matchesHostOrSubdomain(ruleHost: String): Boolean =
        this == ruleHost || endsWith(".$ruleHost")

    private fun String.isHostRule(): Boolean =
        isNotEmpty() && all { it.isLetterOrDigit() || it == '.' || it == '-' }

    private companion object {
        val WEB_SCHEMES = setOf("http", "https")
    }
}
