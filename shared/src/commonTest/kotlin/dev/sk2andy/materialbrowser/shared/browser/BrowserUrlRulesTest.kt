package dev.sk2andy.materialbrowser.shared.browser

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
        assertEquals("https://duckduckgo.com/?q=Candy%20Browser", result.url?.value)
    }

    @Test
    fun nonWebSchemeIsRejected() {
        val result = BrowserUrlRules.resolve("javascript:alert(1)")

        assertEquals(AddressResolutionKind.Rejected, result.kind)
        assertNull(result.url)
    }
}
