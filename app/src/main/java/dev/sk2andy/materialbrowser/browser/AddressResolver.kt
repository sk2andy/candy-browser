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

    fun resolve(
        input: String,
        searchEngine: SearchEngine,
        searchMode: SearchMode = SearchMode.Web,
    ): String = when (val target = classify(input)) {
        AddressTarget.Blank -> BLANK_URL
        is AddressTarget.Url -> target.value
        is AddressTarget.Search -> searchEngine.buildSearchUrl(target.query, searchMode)
    }

    fun isSearchQuery(input: String): Boolean = classify(input) is AddressTarget.Search

    private fun classify(input: String): AddressTarget {
        val value = input.trim()
        if (value.isEmpty()) return AddressTarget.Blank

        if (schemePattern.containsMatchIn(value)) {
            val scheme = runCatching { URI(value).scheme }.getOrNull()
            return if (scheme.equals("http", true) || scheme.equals("https", true)) {
                AddressTarget.Url(value)
            } else {
                AddressTarget.Search(value)
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
            AddressTarget.Url("https://$asciiCandidate")
        } else {
            AddressTarget.Search(value)
        }
    }

    fun displayText(url: String): String = when (url) {
        BLANK_URL -> ""
        else -> displayHost(url)?.removePrefix("www.") ?: url
    }

    private fun displayHost(url: String): String? {
        runCatching { URI(url).host }.getOrNull()?.let { return it }

        val authorityStart = url.indexOf("://").takeIf { it > 0 }?.plus(3) ?: return null
        val authorityEnd = url.indexOfAny(charArrayOf('/', '?', '#'), authorityStart)
            .takeIf { it >= 0 }
            ?: url.length
        return runCatching { URI(url.substring(0, authorityEnd)).host }.getOrNull()
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

    private sealed interface AddressTarget {
        data object Blank : AddressTarget
        data class Url(val value: String) : AddressTarget
        data class Search(val query: String) : AddressTarget
    }
}
