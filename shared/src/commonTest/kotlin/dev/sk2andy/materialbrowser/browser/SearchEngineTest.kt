package dev.sk2andy.materialbrowser.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchEngineTest {
    @Test
    fun providerStableIdsRoundTripFromSharedCatalog() {
        assertEquals(
            SearchEngine.entries.size,
            SearchEngine.entries.map(SearchEngine::stableId).toSet().size,
        )
        SearchEngine.entries.forEach { engine ->
            assertEquals(engine, SearchEngine.fromStableId(engine.stableId))
        }
        assertEquals(SearchEngine.Google, SearchEngine.fromStableId("unknown"))
    }

    @Test
    fun everyBuiltInProviderBuildsItsSearchUrlInSharedCode() {
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
    fun searxngSettingsAreValidatedAndSanitizedInSharedCode() {
        assertEquals(
            "https://search.example/candy",
            SearchSettingsRules.normalizedSearxngInstanceUrl(
                " https://search.example/candy/ ",
            ),
        )
        assertNull(
            SearchSettingsRules.normalizedSearxngInstanceUrl(
                "https://search.example?q=secret",
            ),
        )
        assertEquals(
            SearchSettings(
                searchEngine = SearchEngine.SearXNG,
                searxngInstanceUrl = "https://search.example/candy",
            ),
            SearchSettingsRules.sanitize(
                SearchSettings(
                    searchEngine = SearchEngine.SearXNG,
                    searxngInstanceUrl = " https://search.example/candy/ ",
                ),
            ),
        )
    }
}
