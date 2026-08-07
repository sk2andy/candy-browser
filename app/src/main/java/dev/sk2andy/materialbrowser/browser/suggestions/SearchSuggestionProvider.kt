package dev.sk2andy.materialbrowser.browser.suggestions

import dev.sk2andy.materialbrowser.browser.AddressResolver
import dev.sk2andy.materialbrowser.browser.SearchEngine
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.json.JSONArray

enum class SearchSuggestionProvider(
    val stableId: String,
    private val suggestionUrl: String?,
) {
    None("none", null),
    DuckDuckGo("duckduckgo", "https://duckduckgo.com/ac/?q=%s&type=list"),
    Brave("brave", "https://search.brave.com/api/suggest?q=%s"),
    Ecosia("ecosia", "https://ac.ecosia.org/autocomplete?q=%s&type=list"),
    Qwant("qwant", "https://api.qwant.com/v3/suggest/?q=%s&client=opensearch"),
    Startpage("startpage", "https://www.startpage.com/osuggestions?q=%s"),
    ;

    fun buildSuggestionUrl(query: String): String? = suggestionUrl?.format(query.urlEncoded())

    companion object {
        fun fromStableId(stableId: String?): SearchSuggestionProvider =
            entries.firstOrNull { it.stableId == stableId } ?: DuckDuckGo
    }
}

object SearchSuggestionRules {
    const val MIN_QUERY_LENGTH = 3
    const val MAX_QUERY_LENGTH = 256
    const val MAX_REMOTE_SUGGESTIONS = 4
    const val DEBOUNCE_MILLIS = 250L

    fun shouldRequest(
        query: String,
        provider: SearchSuggestionProvider,
        isIncognito: Boolean,
    ): Boolean {
        if (provider == SearchSuggestionProvider.None || isIncognito) return false
        val value = query.trim()
        if (value.length !in MIN_QUERY_LENGTH..MAX_QUERY_LENGTH || value.startsWith('>')) {
            return false
        }
        return AddressResolver.resolve(value, SearchEngine.DuckDuckGo) ==
            SearchEngine.DuckDuckGo.buildSearchUrl(value)
    }

    fun sanitize(
        query: String,
        suggestions: List<String>,
        limit: Int = MAX_REMOTE_SUGGESTIONS,
    ): List<String> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        return suggestions.asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && it.length <= MAX_SUGGESTION_LENGTH }
            .filterNot { it.lowercase(Locale.ROOT) == normalizedQuery }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(limit.coerceAtLeast(0))
            .toList()
    }

    private const val MAX_SUGGESTION_LENGTH = 200
}

class SearchSuggestionClient {
    private val cache = object : LinkedHashMap<String, List<String>>(
        CACHE_SIZE,
        CACHE_LOAD_FACTOR,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<String>>,
        ): Boolean = size > CACHE_SIZE
    }

    suspend fun suggestions(
        provider: SearchSuggestionProvider,
        query: String,
    ): List<String> {
        val cacheKey = "${provider.stableId}:${query.trim().lowercase(Locale.ROOT)}"
        synchronized(cache) { cache[cacheKey] }?.let { return it }
        val url = provider.buildSuggestionUrl(query) ?: return emptyList()
        val result = runInterruptible(Dispatchers.IO) { fetch(url, query) }
        if (result.isNotEmpty()) synchronized(cache) { cache[cacheKey] = result }
        return result
    }

    private fun fetch(url: String, query: String): List<String> {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (connection.responseCode !in 200..299) return emptyList()
            val body = connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use {
                it.readAtMost(MAX_RESPONSE_CHARS)
            } ?: return emptyList()
            val root = JSONArray(body)
            val rawSuggestions = root.optJSONArray(1) ?: return emptyList()
            SearchSuggestionRules.sanitize(
                query = query,
                suggestions = buildList {
                    for (index in 0 until rawSuggestions.length()) {
                        rawSuggestions.optString(index).takeIf(String::isNotBlank)?.let(::add)
                    }
                },
            )
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 2_000
        const val READ_TIMEOUT_MILLIS = 2_500
        const val CACHE_SIZE = 64
        const val CACHE_LOAD_FACTOR = 0.75f
        const val MAX_RESPONSE_CHARS = 65_536
        const val USER_AGENT = "Candy Browser Search Suggestions"
    }
}

private fun String.urlEncoded(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.toString()).replace("+", "%20")

internal fun Reader.readAtMost(maxChars: Int): String? {
    val result = StringBuilder(minOf(maxChars, 4_096))
    val buffer = CharArray(2_048)
    while (true) {
        val remaining = maxChars + 1 - result.length
        if (remaining <= 0) return null
        val read = read(buffer, 0, minOf(buffer.size, remaining))
        if (read < 0) return result.toString()
        result.append(buffer, 0, read)
        if (result.length > maxChars) return null
    }
}
