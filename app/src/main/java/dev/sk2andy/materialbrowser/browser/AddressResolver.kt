package dev.sk2andy.materialbrowser.browser

import java.net.IDN
import java.net.URI

object AddressResolver {
    private val schemePattern = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
    private val ipPattern = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d+)?(?:/.*)?$")
    private val hostPattern = Regex(
        "^(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}(?::\\d+)?(?:[/?#].*)?$",
    )

    fun resolve(input: String): String = resolve(input, SearchEngine.Google)

    fun resolve(input: String, searchEngine: SearchEngine): String {
        val value = input.trim()
        if (value.isEmpty()) return BLANK_URL

        if (schemePattern.containsMatchIn(value)) {
            val scheme = runCatching { URI(value).scheme }.getOrNull()
            return if (scheme.equals("http", true) || scheme.equals("https", true)) {
                value
            } else {
                searchEngine.buildSearchUrl(value)
            }
        }

        val asciiCandidate = value.toAsciiHostCandidate()
        val isLocalhost = asciiCandidate.startsWith("localhost:") ||
            asciiCandidate == "localhost" ||
            asciiCandidate.startsWith("localhost/")
        return if (
            !value.contains(' ') &&
            (hostPattern.matches(asciiCandidate) || ipPattern.matches(asciiCandidate) || isLocalhost)
        ) {
            "https://$asciiCandidate"
        } else {
            searchEngine.buildSearchUrl(value)
        }
    }

    fun displayText(url: String): String = when (url) {
        BLANK_URL -> ""
        else -> runCatching { URI(url).host }.getOrNull()?.removePrefix("www.") ?: url
    }

    private fun String.toAsciiHostCandidate(): String {
        val suffixIndex = listOf(indexOf('/'), indexOf('?'), indexOf('#'))
            .filter { it >= 0 }
            .minOrNull()
            ?: length
        val hostPort = substring(0, suffixIndex)
        val path = substring(suffixIndex)
        val colonIndex = hostPort.lastIndexOf(':')
        val hasPort = colonIndex > 0 && hostPort.substring(colonIndex + 1).all(Char::isDigit)
        val host = if (hasPort) hostPort.substring(0, colonIndex) else hostPort
        val port = if (hasPort) hostPort.substring(colonIndex) else ""
        return runCatching { IDN.toASCII(host) }.getOrDefault(host) + port + path
    }
}
