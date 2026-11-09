package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomePageRulesTest {
    @Test
    fun `empty defaults stay internal and private tabs never load configured URL`() {
        assertEquals(BLANK_URL, HomePageRules.newTabUrl(HomePageSettings(), false))
        val settings = HomePageSettings("https://home.example/", "https://new.example/?q=1#top")
        assertEquals("https://new.example/?q=1#top", HomePageRules.newTabUrl(settings, false))
        assertEquals(BLANK_URL, HomePageRules.newTabUrl(settings, true))
    }

    @Test
    fun `website URLs are trimmed without dropping paths queries or fragments`() {
        assertEquals("https://example.com/a?q=1#top", HomePageRules.normalizeUrl(" https://example.com/a?q=1#top "))
        assertEquals("http://localhost:8080/", HomePageRules.normalizeUrl("http://localhost:8080/"))
        assertEquals("", HomePageRules.normalizeUrl("  "))
    }

    @Test
    fun `unsafe malformed and excessive inputs are rejected`() {
        listOf("javascript:alert(1)", "file:///tmp/a", "data:text/html,test", "about:blank",
            "https://user:secret@example.com/", "https://example.com:99999/", "https://example.com/a b",
            "example.com", "https://", "https://example.com/" + "a".repeat(4096),
        ).forEach { assertNull(it, HomePageRules.normalizeUrl(it)) }
    }

    @Test
    fun `corrupt persisted settings fall back independently`() {
        assertEquals(HomePageSettings("", "https://new.example/"),
            HomePageRules.sanitize(HomePageSettings("javascript:alert(1)", "https://new.example/")))
    }
}
