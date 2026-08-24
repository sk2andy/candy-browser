package dev.sk2andy.materialbrowser.browser.suggestions

import dev.sk2andy.materialbrowser.browser.AddressResolver
import dev.sk2andy.materialbrowser.browser.SearchEngine
import dev.sk2andy.materialbrowser.browser.SearxngRules
import dev.sk2andy.materialbrowser.browser.SearxngSettings
import dev.sk2andy.materialbrowser.browser.urlEncoded
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    Kagi("kagi", "https://kagisuggest.com/api/autosuggest?q=%s"),
    SearXNG("searxng", null),
    ;

    fun buildSuggestionUrl(
        query: String,
        searxngInstanceUrl: String = "",
    ): String? = if (this == SearXNG) {
        SearxngRules.buildSuggestionUrl(searxngInstanceUrl, query)
    } else {
        suggestionUrl?.format(query.urlEncoded())
    }

    companion object {
        fun fromStableId(
            stableId: String?,
            fallback: SearchSuggestionProvider = DuckDuckGo,
        ): SearchSuggestionProvider = entries.firstOrNull { it.stableId == stableId } ?: fallback
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
        searxngInstanceUrl: String = "",
    ): Boolean {
        if (provider == SearchSuggestionProvider.None || isIncognito) return false
        val value = query.trim()
        val minimumLength = if (provider == SearchSuggestionProvider.SearXNG) {
            SEARXNG_MIN_QUERY_LENGTH
        } else {
            MIN_QUERY_LENGTH
        }
        if (value.length !in minimumLength..MAX_QUERY_LENGTH || value.startsWith('>')) {
            return false
        }
        if (
            provider == SearchSuggestionProvider.SearXNG &&
            SearxngRules.normalizedInstanceUrl(searxngInstanceUrl) == null
        ) return false
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
    private const val SEARXNG_MIN_QUERY_LENGTH = 4
}

internal fun interface SearchSuggestionTransport {
    fun responseBody(url: String): String?
}

internal class SearchSuggestionClient(
    private val transport: SearchSuggestionTransport = HttpSearchSuggestionTransport,
) {
    private val cache = object : LinkedHashMap<SuggestionCacheKey, List<String>>(
        CACHE_SIZE,
        CACHE_LOAD_FACTOR,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<SuggestionCacheKey, List<String>>,
        ): Boolean = size > CACHE_SIZE
    }

    suspend fun suggestions(
        provider: SearchSuggestionProvider,
        query: String,
        searxngSettings: SearxngSettings = SearxngSettings(),
    ): List<String> {
        val settings = SearxngRules.sanitize(searxngSettings)
        val cacheKey = SuggestionCacheKey(
            provider = provider,
            searxngInstanceUrl = SearxngRules.normalizedInstanceUrl(settings.instanceUrl).orEmpty(),
            fallback = settings.suggestionFallback,
            query = query.trim().lowercase(Locale.ROOT),
        )
        synchronized(cache) { cache[cacheKey] }?.let { return it }
        val primaryUrl = provider.buildSuggestionUrl(
            query = query,
            searxngInstanceUrl = settings.instanceUrl,
        ) ?: return emptyList()
        val primary = runInterruptible(Dispatchers.IO) { fetch(primaryUrl, query) }
        val result = when (primary) {
            is SuggestionFetchResult.Success -> primary.suggestions
            SuggestionFetchResult.Failure -> {
                currentCoroutineContext().ensureActive()
                if (provider != SearchSuggestionProvider.SearXNG) return emptyList()
                val fallbackUrl = settings.suggestionFallback.buildSuggestionUrl(query)
                    ?: return emptyList()
                when (val fallback = runInterruptible(Dispatchers.IO) { fetch(fallbackUrl, query) }) {
                    is SuggestionFetchResult.Success -> fallback.suggestions
                    SuggestionFetchResult.Failure -> emptyList()
                }
            }
        }
        if (result.isNotEmpty()) synchronized(cache) { cache[cacheKey] = result }
        return result
    }

    private fun fetch(url: String, query: String): SuggestionFetchResult {
        return try {
            val body = transport.responseBody(url) ?: return SuggestionFetchResult.Failure
            SuggestionFetchResult.Success(parseSearchSuggestions(body, query))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            SuggestionFetchResult.Failure
        }
    }

    private sealed interface SuggestionFetchResult {
        data class Success(val suggestions: List<String>) : SuggestionFetchResult
        data object Failure : SuggestionFetchResult
    }

    private data class SuggestionCacheKey(
        val provider: SearchSuggestionProvider,
        val searxngInstanceUrl: String,
        val fallback: SearchSuggestionProvider,
        val query: String,
    )

    private companion object {
        const val CACHE_SIZE = 64
        const val CACHE_LOAD_FACTOR = 0.75f
    }
}

private object HttpSearchSuggestionTransport : SearchSuggestionTransport {
    override fun responseBody(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val uri = URI(url)
            connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                if (uri.host.equals(KAGI_SUGGESTION_HOST, ignoreCase = true)) {
                    setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag())
                }
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use {
                it.readAtMost(MAX_RESPONSE_CHARS)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            if (Thread.currentThread().isInterrupted) throw CancellationException()
            null
        } finally {
            connection?.disconnect()
        }
    }

    private const val CONNECT_TIMEOUT_MILLIS = 2_000
    private const val READ_TIMEOUT_MILLIS = 2_500
    private const val MAX_RESPONSE_CHARS = 65_536
    private const val KAGI_SUGGESTION_HOST = "kagisuggest.com"
    private const val USER_AGENT = "Candy Browser Search Suggestions"
}

internal fun parseSearchSuggestions(body: String, query: String): List<String> {
    val root = JSONArray(body)
    val rawSuggestions = root.optJSONArray(1) ?: root
    return SearchSuggestionRules.sanitize(
        query = query,
        suggestions = buildList {
            for (index in 0 until rawSuggestions.length()) {
                rawSuggestions.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        },
    )
}

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
