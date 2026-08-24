package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import java.net.IDN
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class SearxngSettings(
    val instanceUrl: String = "",
    val suggestionFallback: SearchSuggestionProvider = SearchSuggestionProvider.None,
)

object SearxngRules {
    const val MAX_INSTANCE_URL_LENGTH = 2_048

    fun sanitize(settings: SearxngSettings): SearxngSettings = settings.copy(
        instanceUrl = normalizedInstanceUrl(settings.instanceUrl).orEmpty(),
        suggestionFallback = settings.suggestionFallback
            .takeUnless { it == SearchSuggestionProvider.SearXNG }
            ?: SearchSuggestionProvider.None,
    )

    fun normalizedInstanceUrl(value: String): String? {
        val candidate = value.trim()
        if (candidate.isEmpty() || candidate.length > MAX_INSTANCE_URL_LENGTH) return null
        val safeUrl = BrowserUriPolicy.normalizeHttpUrl(candidate) ?: return null
        val uri = runCatching { URI(safeUrl).normalize() }.getOrNull() ?: return null
        if (uri.rawQuery != null || uri.rawFragment != null) return null
        if (uri.port != -1 && uri.port !in 1..65_535) return null
        val host = runCatching { uri.toURL().host }.getOrNull() ?: return null
        val asciiHost = if (host.startsWith('[') && host.endsWith(']')) {
            host
        } else {
            runCatching { IDN.toASCII(host) }.getOrNull() ?: return null
        }
        val authority = buildString {
            append(asciiHost)
            if (uri.port >= 0) append(':').append(uri.port)
        }
        return "${uri.scheme.lowercase()}://$authority${uri.rawPath.orEmpty()}".trimEnd('/')
    }

    fun buildSearchUrl(
        instanceUrl: String,
        query: String,
    ): String? = buildEndpointUrl(instanceUrl, "search", query)

    fun buildSuggestionUrl(
        instanceUrl: String,
        query: String,
    ): String? = buildEndpointUrl(instanceUrl, "autocompleter", query)

    private fun buildEndpointUrl(
        instanceUrl: String,
        endpoint: String,
        query: String,
    ): String? = normalizedInstanceUrl(instanceUrl)?.let { baseUrl ->
        "$baseUrl/$endpoint?q=${query.urlEncoded()}"
    }
}

internal fun String.urlEncoded(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.toString()).replace("+", "%20")
