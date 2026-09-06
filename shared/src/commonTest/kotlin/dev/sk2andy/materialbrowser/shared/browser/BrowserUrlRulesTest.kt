package dev.sk2andy.materialbrowser.shared.browser

import dev.sk2andy.materialbrowser.browser.SearchEngine
import dev.sk2andy.materialbrowser.browser.SearchSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrowserUrlRulesTest {
    @Test
    fun explicitWebUrlIsNormalized() {
        val result = BrowserUrlRules.resolve("HTTPS://Example.COM/path?q=1")

        assertEquals(AddressResolutionKind.WebUrl, result.kind)
        assertEquals("https://example.com/path?q=1", result.url?.value)
    }

    @Test
    fun hostInputGetsHttps() {
        val result = BrowserUrlRules.resolve("example.com/docs")

        assertEquals(AddressResolutionKind.WebUrl, result.kind)
        assertEquals("https://example.com/docs", result.url?.value)
    }

    @Test
    fun wordsBecomeEncodedSearch() {
        val result = BrowserUrlRules.resolve("Candy Browser")

        assertEquals(AddressResolutionKind.Search, result.kind)
        assertEquals("https://www.google.com/search?q=Candy%20Browser", result.url?.value)
    }

    @Test
    fun singleSearchTermDoesNotBecomeHttpsHost() {
        val result = BrowserUrlRules.resolve("candy")

        assertEquals(AddressResolutionKind.Search, result.kind)
        assertEquals("https://www.google.com/search?q=candy", result.url?.value)
    }

    @Test
    fun selectedProviderOwnsPlainTextSearchUrl() {
        val result = BrowserUrlRules.resolve(
            input = "private search",
            searchSettings = SearchSettings(searchEngine = SearchEngine.Brave),
        )

        assertEquals(AddressResolutionKind.Search, result.kind)
        assertEquals(
            "https://search.brave.com/search?q=private%20search",
            result.url?.value,
        )
    }

    @Test
    fun searxngRequiresValidSharedInstance() {
        val missingInstance = BrowserUrlRules.resolve(
            input = "private search",
            searchSettings = SearchSettings(searchEngine = SearchEngine.SearXNG),
        )
        val configuredInstance = BrowserUrlRules.resolve(
            input = "private search",
            searchSettings = SearchSettings(
                searchEngine = SearchEngine.SearXNG,
                searxngInstanceUrl = "https://search.example/candy",
            ),
        )

        assertEquals(AddressResolutionKind.Rejected, missingInstance.kind)
        assertNull(missingInstance.url)
        assertEquals(AddressResolutionKind.Search, configuredInstance.kind)
        assertEquals(
            "https://search.example/candy/search?q=private%20search",
            configuredInstance.url?.value,
        )
    }

    @Test
    fun nonWebSchemeIsRejected() {
        val result = BrowserUrlRules.resolve("javascript:alert(1)")

        assertEquals(AddressResolutionKind.Rejected, result.kind)
        assertNull(result.url)
    }
}
