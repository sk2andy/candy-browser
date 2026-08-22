package dev.sk2andy.materialbrowser.blocking

import java.net.URI

internal class RequestBlocker(
    hostRules: Sequence<String>,
    private val indexedHostRules: SortedHostIndex = SortedHostIndex.Empty,
    private val additionalIndexedHostRules: List<SortedHostIndex> = emptyList(),
    blockedHostPairs: Sequence<String> = emptySequence(),
    allowedHostPairs: Sequence<String> = emptySequence(),
    allowedFirstPartyFamilyPairs: Sequence<String> = emptySequence(),
) {
    private val blockedHosts = hostRules
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith('#') }
        .map { it.lowercase().removePrefix("||").removeSuffix("^").trim('.') }
        .filter { rule -> rule.all { it.isLetterOrDigit() || it == '.' || it == '-' } }
        .distinct()
        .toHashSet()
    private val blockedPageHostsByRequestHost = parseHostPairs(blockedHostPairs)
    private val allowedPageHostsByRequestHost = parseHostPairs(allowedHostPairs)
    private val allowedPageHostPatternsByRequestHost =
        parseHostPatternPairs(allowedFirstPartyFamilyPairs)

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

    private fun parseHostPatternPairs(lines: Sequence<String>): Map<String, List<String>> = lines
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith('#') }
        .mapNotNull { line ->
            val fields = line.lowercase().split('\t', limit = 2)
            if (fields.size != 2) return@mapNotNull null
            val requestHost = fields[0].trim('.')
            val pageHostPattern = CosmeticHostPattern.canonicalize(fields[1])
                ?: return@mapNotNull null
            if (!requestHost.isHostRule()) return@mapNotNull null
            requestHost to pageHostPattern
        }
        .groupBy({ it.first }, { it.second })

    fun shouldBlock(requestUrl: String, pageUrl: String?): Boolean {
        val request = runCatching { URI(requestUrl) }.getOrNull() ?: return false
        if (request.scheme?.lowercase() !in WEB_SCHEMES) return false
        val requestHost = request.host ?: return false
        val pageHost = pageUrl?.let { url ->
            runCatching { URI(url).host?.lowercase()?.trim('.') }.getOrNull()
        }
        return shouldBlockHosts(requestHost, pageHost)
    }

    fun shouldBlockHosts(requestHost: String?, pageHost: String?): Boolean {
        val normalizedRequestHost = requestHost?.lowercase()?.trim('.')
            ?.takeIf { it.isHostRule() } ?: return false
        val normalizedPageHost = pageHost?.lowercase()?.trim('.')?.takeIf { it.isHostRule() }

        // Keep the current site functional when a list contains its own host. This mirrors the
        // first-party escape used by DuckDuckGo's Android tracker detector:
        // https://github.com/duckduckgo/Android/blob/4472de82e610b12689dcd2fc1b8421439020af62/app/src/main/java/com/duckduckgo/app/trackerdetection/TrackerDetectorImpl.kt
        if (normalizedPageHost != null &&
            isSameHostOrSubdomain(normalizedRequestHost, normalizedPageHost)
        ) return false
        if (isAllowedByFilterException(normalizedRequestHost, normalizedPageHost)) return false
        if (isAllowedByFirstPartyFamily(normalizedRequestHost, normalizedPageHost)) return false
        if (isBlockedByFilterPair(normalizedRequestHost, normalizedPageHost)) return true

        var candidate = normalizedRequestHost
        while (true) {
            if (
                candidate in blockedHosts ||
                candidate in indexedHostRules ||
                additionalIndexedHostRules.any { index -> candidate in index }
            ) return true
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

    private fun isAllowedByFirstPartyFamily(
        requestHost: String,
        pageHost: String?,
    ): Boolean {
        if (pageHost == null) return false
        var candidate = requestHost
        while (true) {
            val allowedPatterns = allowedPageHostPatternsByRequestHost[candidate]
            if (allowedPatterns != null && allowedPatterns.any { pattern ->
                    CosmeticHostPattern.matches(pageHost, pattern)
                }
            ) return true

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

internal class SortedHostIndex private constructor(
    private val bytes: ByteArray,
    private val starts: IntArray,
    private val ends: IntArray,
) {
    val size: Int
        get() = starts.size

    operator fun contains(host: String): Boolean {
        var low = 0
        var high = starts.lastIndex
        while (low <= high) {
            val middle = (low + high).ushr(1)
            val comparison = compare(host, middle)
            when {
                comparison < 0 -> high = middle - 1
                comparison > 0 -> low = middle + 1
                else -> return true
            }
        }
        return false
    }

    private fun compare(host: String, index: Int): Int {
        val start = starts[index]
        val length = ends[index] - start
        val commonLength = minOf(host.length, length)
        for (offset in 0 until commonLength) {
            val difference = host[offset].code - bytes[start + offset].toUByte().toInt()
            if (difference != 0) return difference
        }
        return host.length - length
    }

    companion object {
        val Empty = SortedHostIndex(ByteArray(0), IntArray(0), IntArray(0))

        fun from(bytes: ByteArray): SortedHostIndex {
            var count = 0
            forEachRuleLine(bytes) { _, _ -> count++ }
            if (count == 0) return Empty
            val starts = IntArray(count)
            val ends = IntArray(count)
            var index = 0
            forEachRuleLine(bytes) { start, end ->
                require(
                    index == 0 || compareLines(
                        bytes,
                        starts[index - 1],
                        ends[index - 1],
                        start,
                        end,
                    ) < 0,
                ) { "Host index must be strictly sorted" }
                starts[index] = start
                ends[index] = end
                index++
            }
            return SortedHostIndex(bytes, starts, ends)
        }

        private fun compareLines(
            bytes: ByteArray,
            firstStart: Int,
            firstEnd: Int,
            secondStart: Int,
            secondEnd: Int,
        ): Int {
            val firstLength = firstEnd - firstStart
            val secondLength = secondEnd - secondStart
            val commonLength = minOf(firstLength, secondLength)
            for (offset in 0 until commonLength) {
                val difference = bytes[firstStart + offset].toUByte().toInt() -
                    bytes[secondStart + offset].toUByte().toInt()
                if (difference != 0) return difference
            }
            return firstLength - secondLength
        }

        private inline fun forEachRuleLine(
            bytes: ByteArray,
            block: (start: Int, end: Int) -> Unit,
        ) {
            var start = 0
            while (start < bytes.size) {
                var end = start
                while (end < bytes.size && bytes[end] != '\n'.code.toByte()) end++
                val contentEnd = if (end > start && bytes[end - 1] == '\r'.code.toByte()) {
                    end - 1
                } else {
                    end
                }
                if (contentEnd > start && bytes[start] != '#'.code.toByte()) {
                    block(start, contentEnd)
                }
                start = end + 1
            }
        }
    }
}
