package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressResolverTest {
    @Test
    fun `keeps complete web urls`() {
        assertEquals("https://example.com/path?q=1", AddressResolver.resolve("https://example.com/path?q=1"))
        assertEquals("http://localhost:8080", AddressResolver.resolve("http://localhost:8080"))
    }

    @Test
    fun `adds https to host-like inputs`() {
        assertEquals("https://example.com/news", AddressResolver.resolve("example.com/news"))
        assertEquals("https://example.com?q=1", AddressResolver.resolve("example.com?q=1"))
        assertEquals("https://example.com#top", AddressResolver.resolve("example.com#top"))
        assertEquals("https://localhost:8080", AddressResolver.resolve("localhost:8080"))
        assertEquals("https://127.0.0.1:3000/path", AddressResolver.resolve("127.0.0.1:3000/path"))
    }

    @Test
    fun `normalizes international host names`() {
        assertEquals("https://xn--mnchen-3ya.de", AddressResolver.resolve("münchen.de"))
    }

    @Test
    fun `uses google for plain text and forbidden schemes`() {
        assertEquals(
            "https://www.google.com/search?q=material%20browser",
            AddressResolver.resolve("material browser"),
        )
        assertTrue(AddressResolver.resolve("javascript://alert(1)").startsWith("https://www.google.com/search?q="))
        assertTrue(AddressResolver.resolve("file:///data/local/tmp/x").startsWith("https://www.google.com/search?q="))
    }

    @Test
    fun `uses selected search engine for plain text and forbidden schemes`() {
        assertEquals(
            "https://duckduckgo.com/?q=privacy%20browser",
            AddressResolver.resolve("privacy browser", SearchEngine.DuckDuckGo),
        )
        assertTrue(
            AddressResolver.resolve("javascript://alert(1)", SearchEngine.Brave)
                .startsWith("https://search.brave.com/search?q="),
        )
    }

    @Test
    fun `selected search engine does not change direct address handling`() {
        assertEquals(
            "https://example.com/news",
            AddressResolver.resolve("example.com/news", SearchEngine.Perplexity),
        )
    }

    @Test
    fun `empty input resolves to blank tab`() {
        assertEquals(BLANK_URL, AddressResolver.resolve("  "))
    }
}
