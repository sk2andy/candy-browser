package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchEngineTest {
    @Test
    fun `stable ids are unique and round trip`() {
        assertEquals(SearchEngine.entries.size, SearchEngine.entries.map(SearchEngine::stableId).toSet().size)
        SearchEngine.entries.forEach { engine ->
            assertEquals(engine, SearchEngine.fromStableId(engine.stableId))
        }
    }

    @Test
    fun `unknown and absent stable ids use google`() {
        assertEquals(SearchEngine.Google, SearchEngine.fromStableId(null))
        assertEquals(SearchEngine.Google, SearchEngine.fromStableId("unknown"))
    }

    @Test
    fun `all engines build expected secure encoded search urls`() {
        val expected = mapOf(
            SearchEngine.Google to "https://www.google.com/search?q=candy%20%26%20browser",
            SearchEngine.DuckDuckGo to "https://duckduckgo.com/?q=candy%20%26%20browser",
            SearchEngine.Bing to "https://www.bing.com/search?q=candy%20%26%20browser",
            SearchEngine.Brave to "https://search.brave.com/search?q=candy%20%26%20browser",
            SearchEngine.Ecosia to "https://www.ecosia.org/search?q=candy%20%26%20browser",
            SearchEngine.Startpage to "https://www.startpage.com/sp/search?query=candy%20%26%20browser",
            SearchEngine.Qwant to "https://www.qwant.com/?q=candy%20%26%20browser",
            SearchEngine.Kagi to "https://kagi.com/search?q=candy%20%26%20browser",
            SearchEngine.Perplexity to "https://www.perplexity.ai/search?q=candy%20%26%20browser",
            SearchEngine.ChatGPT to "https://chatgpt.com/?q=candy%20%26%20browser",
        )

        assertEquals(SearchEngine.entries.toSet() - SearchEngine.SearXNG, expected.keys)
        expected.forEach { (engine, url) ->
            assertEquals(url, engine.buildSearchUrl("candy & browser"))
        }
    }

    @Test
    fun `searxng builds search url from configured instance`() {
        assertEquals(
            "https://search.example/searxng/search?q=candy%20%26%20browser",
            SearchEngine.SearXNG.buildSearchUrl(
                query = "candy & browser",
                searxngInstanceUrl = "https://search.example/searxng/",
            ),
        )
    }

    @Test
    fun `searxng with invalid instance does not leak query to another provider`() {
        assertEquals(
            BLANK_URL,
            SearchEngine.SearXNG.buildSearchUrl("candy browser"),
        )
    }

    @Test
    fun `google builds dedicated ai mode url`() {
        assertEquals(
            "https://www.google.com/ai?q=candy%20%26%20browser",
            SearchEngine.Google.buildSearchUrl("candy & browser", SearchMode.Ai),
        )
    }

    @Test
    fun `engines without ai route safely use web search`() {
        assertEquals(
            "https://duckduckgo.com/?q=candy%20browser",
            SearchEngine.DuckDuckGo.buildSearchUrl("candy browser", SearchMode.Ai),
        )
    }
}
