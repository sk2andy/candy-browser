package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearxngSettingsTest {
    @Test
    fun `normalizes root and nested instance urls`() {
        assertEquals(
            "https://search.example",
            SearxngRules.normalizedInstanceUrl(" https://search.example/ "),
        )
        assertEquals(
            "http://localhost:8080/searxng",
            SearxngRules.normalizedInstanceUrl("http://localhost:8080/searxng/"),
        )
        assertEquals(
            "https://xn--bcher-kva.example/searxng",
            SearxngRules.normalizedInstanceUrl("https://bücher.example/searxng"),
        )
    }

    @Test
    fun `rejects unsafe or ambiguous instance urls`() {
        listOf(
            "",
            "search.example",
            "ftp://search.example",
            "https://user:secret@search.example",
            "https://search.example?q=secret",
            "https://search.example#fragment",
            "https://search.example:0",
            "https://search.example:99999",
            "https://",
            "https://search.example\n.example.org",
            "https://search.example/${"x".repeat(SearxngRules.MAX_INSTANCE_URL_LENGTH)}",
        ).forEach { value ->
            assertNull(value, SearxngRules.normalizedInstanceUrl(value))
        }
    }

    @Test
    fun `builds encoded endpoints while preserving deployment path`() {
        assertEquals(
            "https://search.example/searxng/search?q=candy%20%26%20browser",
            SearxngRules.buildSearchUrl("https://search.example/searxng/", "candy & browser"),
        )
        assertEquals(
            "https://search.example/searxng/autocompleter?q=candy%20%26%20browser",
            SearxngRules.buildSuggestionUrl(
                "https://search.example/searxng/",
                "candy & browser",
            ),
        )
    }

    @Test
    fun `sanitize bounds input and blocks recursive suggestion fallback`() {
        val settings = SearxngRules.sanitize(
            SearxngSettings(
                instanceUrl = " x${"y".repeat(SearxngRules.MAX_INSTANCE_URL_LENGTH)} ",
                suggestionFallback = SearchSuggestionProvider.SearXNG,
            ),
        )

        assertEquals("", settings.instanceUrl)
        assertEquals(SearchSuggestionProvider.None, settings.suggestionFallback)
    }
}
