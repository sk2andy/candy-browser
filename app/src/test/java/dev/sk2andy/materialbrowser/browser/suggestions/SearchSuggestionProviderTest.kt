package dev.sk2andy.materialbrowser.browser.suggestions

import java.io.StringReader
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
            .filterNot { it == SearchSuggestionProvider.None }
            .forEach { provider ->
                val url = provider.buildSuggestionUrl("candy & browser")
                assertNotNull(url)
                assertTrue(url!!.startsWith("https://"))
                assertTrue(url.contains("candy%20%26%20browser"))
            }
        assertEquals(null, SearchSuggestionProvider.None.buildSuggestionUrl("candy"))
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

    private fun request(
        query: String,
        isIncognito: Boolean = false,
        provider: SearchSuggestionProvider = SearchSuggestionProvider.DuckDuckGo,
    ): Boolean = SearchSuggestionRules.shouldRequest(query, provider, isIncognito)
}
