package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class SiteConnectionRulesTest {
    @Test
    fun `https and http addresses have distinct connection indicators`() {
        assertEquals(
            SiteConnectionKind.Https,
            SiteConnectionRules.kind("https://www.example.com/path"),
        )
        assertEquals("www.example.com", SiteConnectionRules.host("https://www.example.com/path"))
        assertEquals(SiteConnectionKind.Http, SiteConnectionRules.kind("http://example.com"))
    }

    @Test
    fun `blank and malformed addresses do not show a secure indicator`() {
        assertEquals(SiteConnectionKind.Other, SiteConnectionRules.kind(BLANK_URL))
        assertEquals(SiteConnectionKind.Other, SiteConnectionRules.kind("https://"))
        assertEquals(SiteConnectionKind.Other, SiteConnectionRules.kind("https://user@example.com"))
        assertEquals("", SiteConnectionRules.host("https://"))
    }

    @Test
    fun `loading and failed navigation never show a closed lock`() {
        assertEquals(
            SiteConnectionKind.Unavailable,
            SiteConnectionRules.kind("https://example.com", isLoading = true),
        )
        assertEquals(
            SiteConnectionKind.Unavailable,
            SiteConnectionRules.kind("https://example.com", hasError = true),
        )
    }
}
