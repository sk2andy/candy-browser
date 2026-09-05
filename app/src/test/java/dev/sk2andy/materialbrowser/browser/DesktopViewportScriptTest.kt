package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopViewportScriptTest {
    @Test
    fun `empty domains produce no script or origins`() {
        assertEquals("", DesktopViewportScript.create(emptySet()))
        assertTrue(DesktopViewportScript.allowedOrigins(emptySet()).isEmpty())
    }

    @Test
    fun `origin coverage distinguishes default wildcard and explicit ports`() {
        val origins = DesktopViewportScript.allowedOrigins(setOf("example.com")) +
            "https://news.example.com:8443"

        assertTrue(DesktopViewportScript.covers("https://example.com/story", origins))
        assertTrue(DesktopViewportScript.covers("https://news.example.com/story", origins))
        assertTrue(DesktopViewportScript.covers("https://news.example.com:8443/story", origins))
        assertFalse(DesktopViewportScript.covers("https://news.example.com:9443/story", origins))
        assertFalse(DesktopViewportScript.covers("file:///tmp/page.html", origins))
    }

    @Test
    fun `domains are normalized sorted and bounded to web origins`() {
        assertEquals(
            linkedSetOf(
                "https://example.com",
                "https://*.example.com",
                "http://example.com",
                "http://*.example.com",
                "https://example.org",
                "https://*.example.org",
                "http://example.org",
                "http://*.example.org",
            ),
            DesktopViewportScript.allowedOrigins(
                listOf("EXAMPLE.org", "example.com", "example.org"),
            ),
        )
        val script = DesktopViewportScript.create(
            listOf("EXAMPLE.org", "example.com", "example.org"),
        )

        assertTrue(script.contains("const desktopDomains = [\"example.com\", \"example.org\"]"))
        assertTrue(script.contains("host === domain || host.endsWith"))
        assertTrue(script.contains("window.top !== window"))
        assertTrue(script.contains("width=980"))
        assertTrue(script.contains("user-scalable=yes"))
        assertTrue(script.contains("maximum-scale=10"))
    }
}
