package dev.sk2andy.materialbrowser.browser.suggestions

import dev.sk2andy.materialbrowser.browser.SearxngSettings
import java.io.StringReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSuggestionProviderTest {
    @Test
    fun `duckduckgo is default for missing or unknown preference`() {
        assertEquals(SearchSuggestionProvider.DuckDuckGo, SearchSuggestionProvider.fromStableId(null))
        assertEquals(
            SearchSuggestionProvider.DuckDuckGo,
            SearchSuggestionProvider.fromStableId("unknown"),
        )
    }

    @Test
    fun `all enabled providers build encoded https URLs`() {
        SearchSuggestionProvider.entries
            .filterNot {
                it == SearchSuggestionProvider.None || it == SearchSuggestionProvider.SearXNG
            }
            .forEach { provider ->
                val url = provider.buildSuggestionUrl("candy & browser")
                assertNotNull(url)
                assertTrue(url!!.startsWith("https://"))
                assertTrue(url.contains("candy%20%26%20browser"))
            }
        assertEquals(null, SearchSuggestionProvider.None.buildSuggestionUrl("candy"))
        assertEquals(
            "https://kagisuggest.com/api/autosuggest?q=candy%20%26%20browser",
            SearchSuggestionProvider.Kagi.buildSuggestionUrl("candy & browser"),
        )
        assertEquals(
            "https://search.example/autocompleter?q=candy%20%26%20browser",
            SearchSuggestionProvider.SearXNG.buildSuggestionUrl(
                query = "candy & browser",
                searxngInstanceUrl = "https://search.example/",
            ),
        )
    }

    @Test
    fun `requests require provider three characters search input and regular tab`() {
        assertFalse(request("ca"))
        assertTrue(request("can"))
        assertTrue(request("candy browser"))
        assertFalse(request("example.com"))
        assertFalse(request("> reload"))
        assertFalse(request("candy", isIncognito = true))
        assertFalse(request("candy", provider = SearchSuggestionProvider.None))
        assertFalse(request("can", provider = SearchSuggestionProvider.SearXNG))
        assertFalse(request("candy", provider = SearchSuggestionProvider.SearXNG))
        assertTrue(
            request(
                "candy",
                provider = SearchSuggestionProvider.SearXNG,
                searxngInstanceUrl = "https://search.example",
            ),
        )
        assertTrue(request("x".repeat(SearchSuggestionRules.MAX_QUERY_LENGTH)))
        assertFalse(request("x".repeat(SearchSuggestionRules.MAX_QUERY_LENGTH + 1)))
    }

    @Test
    fun `sanitize removes exact duplicate blank and oversized suggestions`() {
        val result = SearchSuggestionRules.sanitize(
            query = "Candy",
            suggestions = listOf(
                "Candy",
                " candy browser ",
                "CANDY BROWSER",
                "",
                "Candy recipe",
                "x".repeat(201),
            ),
        )

        assertEquals(listOf("candy browser", "Candy recipe"), result)
    }

    @Test
    fun `bounded response reader rejects oversized content`() {
        assertEquals("candy", StringReader("candy").readAtMost(5))
        assertEquals(null, StringReader("candy!").readAtMost(5))
    }

    @Test
    fun `parser accepts open search and flat searxng responses`() {
        assertEquals(
            listOf("candy browser", "candy recipe"),
            parseSearchSuggestions(
                """["candy",["candy browser","candy recipe"],[],[],{}]""",
                "candy",
            ),
        )
        assertEquals(
            listOf("candy browser", "candy recipe"),
            parseSearchSuggestions("""["candy browser","candy recipe"]""", "candy"),
        )
    }

    @Test
    fun `searxng settings reject recursive fallback`() {
        val settings = dev.sk2andy.materialbrowser.browser.SearxngRules.sanitize(
            SearxngSettings(suggestionFallback = SearchSuggestionProvider.SearXNG),
        )

        assertEquals(SearchSuggestionProvider.None, settings.suggestionFallback)
    }

    @Test
    fun `failed searxng request uses configured fallback`() = runBlocking {
        val requests = mutableListOf<String>()
        val client = SearchSuggestionClient { url ->
            requests += url
            if (url.startsWith("https://search.example/")) {
                null
            } else {
                """["candy",["candy browser"],[],[]]"""
            }
        }

        val result = client.suggestions(
            provider = SearchSuggestionProvider.SearXNG,
            query = "candy",
            searxngSettings = SearxngSettings(
                instanceUrl = "https://search.example",
                suggestionFallback = SearchSuggestionProvider.Brave,
            ),
        )

        assertEquals(listOf("candy browser"), result)
        assertEquals(2, requests.size)
        assertTrue(requests[0].startsWith("https://search.example/autocompleter"))
        assertTrue(requests[1].startsWith("https://search.brave.com/api/suggest"))
    }

    @Test
    fun `valid empty searxng response does not use fallback`() = runBlocking {
        val requests = mutableListOf<String>()
        val client = SearchSuggestionClient { url ->
            requests += url
            """["candy",[],[],[]]"""
        }

        val result = client.suggestions(
            provider = SearchSuggestionProvider.SearXNG,
            query = "candy",
            searxngSettings = SearxngSettings(
                instanceUrl = "https://search.example",
                suggestionFallback = SearchSuggestionProvider.Brave,
            ),
        )

        assertEquals(emptyList<String>(), result)
        assertEquals(1, requests.size)
    }

    @Test
    fun `malformed searxng response uses configured fallback`() = runBlocking {
        val requests = mutableListOf<String>()
        val client = SearchSuggestionClient { url ->
            requests += url
            if (requests.size == 1) "not json" else """["candy",["candy fallback"],[],[]]"""
        }

        val result = client.suggestions(
            provider = SearchSuggestionProvider.SearXNG,
            query = "candy",
            searxngSettings = SearxngSettings(
                instanceUrl = "https://search.example",
                suggestionFallback = SearchSuggestionProvider.DuckDuckGo,
            ),
        )

        assertEquals(listOf("candy fallback"), result)
        assertEquals(2, requests.size)
    }

    @Test
    fun `cancellation never invokes suggestion fallback`() {
        val requests = mutableListOf<String>()
        val client = SearchSuggestionClient { url ->
            requests += url
            throw CancellationException("cancel")
        }

        val outcome = runCatching {
            runBlocking {
                client.suggestions(
                    provider = SearchSuggestionProvider.SearXNG,
                    query = "candy",
                    searxngSettings = SearxngSettings(
                        instanceUrl = "https://search.example",
                        suggestionFallback = SearchSuggestionProvider.Brave,
                    ),
                )
            }
        }

        assertTrue(outcome.exceptionOrNull() is CancellationException)
        assertEquals(1, requests.size)
    }

    @Test
    fun `cancellation after interrupted primary failure cannot start fallback`() = runBlocking {
        val primaryStarted = CountDownLatch(1)
        val primaryRelease = CountDownLatch(1)
        val requests = mutableListOf<String>()
        val client = SearchSuggestionClient { url ->
            synchronized(requests) { requests += url }
            primaryStarted.countDown()
            runCatching { primaryRelease.await() }
            null
        }
        val job = launch(Dispatchers.Default) {
            client.suggestions(
                provider = SearchSuggestionProvider.SearXNG,
                query = "candy",
                searxngSettings = SearxngSettings(
                    instanceUrl = "https://search.example",
                    suggestionFallback = SearchSuggestionProvider.Brave,
                ),
            )
        }

        assertTrue(primaryStarted.await(2, TimeUnit.SECONDS))
        job.cancelAndJoin()
        primaryRelease.countDown()

        assertEquals(1, synchronized(requests) { requests.size })
    }

    @Test
    fun `cache is isolated by searxng instance`() = runBlocking {
        val requests = mutableListOf<String>()
        val client = SearchSuggestionClient { url ->
            requests += url
            if (url.startsWith("https://one.example/")) {
                """["candy",["candy one"],[],[]]"""
            } else {
                """["candy",["candy two"],[],[]]"""
            }
        }

        val first = client.suggestions(
            provider = SearchSuggestionProvider.SearXNG,
            query = "candy",
            searxngSettings = SearxngSettings(instanceUrl = "https://one.example"),
        )
        val second = client.suggestions(
            provider = SearchSuggestionProvider.SearXNG,
            query = "candy",
            searxngSettings = SearxngSettings(instanceUrl = "https://two.example"),
        )

        assertEquals(listOf("candy one"), first)
        assertEquals(listOf("candy two"), second)
        assertEquals(2, requests.size)
    }

    private fun request(
        query: String,
        isIncognito: Boolean = false,
        provider: SearchSuggestionProvider = SearchSuggestionProvider.DuckDuckGo,
        searxngInstanceUrl: String = "",
    ): Boolean = SearchSuggestionRules.shouldRequest(
        query = query,
        provider = provider,
        isIncognito = isIncognito,
        searxngInstanceUrl = searxngInstanceUrl,
    )
}
