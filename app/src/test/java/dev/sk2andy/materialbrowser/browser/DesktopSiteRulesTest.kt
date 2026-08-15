package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopSiteRulesTest {
    @Test
    fun `subdomains share registrable desktop view state`() {
        assertEquals(
            "example.co.uk",
            DesktopSiteRules.domainForUrl("https://mobile.news.example.co.uk/story"),
        )
        assertEquals(
            "alice.web.app",
            DesktopSiteRules.domainForUrl("https://media.alice.web.app/player"),
        )
        assertFalse(
            DesktopSiteRules.isDesktopView(
                "https://bob.web.app/player",
                setOf("alice.web.app"),
            ),
        )
        assertTrue(
            DesktopSiteRules.isDesktopView(
                "https://www.example.co.uk/story",
                setOf("example.co.uk"),
            ),
        )
    }

    @Test
    fun `non web pages cannot enable desktop view`() {
        assertNull(DesktopSiteRules.domainForUrl(BLANK_URL))
        assertNull(DesktopSiteRules.domainForUrl("file:///tmp/page.html"))
        assertFalse(DesktopSiteRules.isDesktopView("https://other.example", emptySet()))
    }

    @Test
    fun `desktop state is canonical bounded and reversible`() {
        val initial = (1..64).map { index -> "site$index.example" }

        val enabled = DesktopSiteRules.withDesktopViewState(
            initial,
            "MOBILE.Example",
            enabled = true,
        )
        val disabled = DesktopSiteRules.withDesktopViewState(
            enabled,
            "mobile.example",
            enabled = false,
        )

        assertEquals(64, enabled.size)
        assertEquals("mobile.example", enabled.last())
        assertFalse("site64.example" in enabled)
        assertFalse("mobile.example" in disabled)
        assertTrue(
            DesktopSiteRules.withDesktopViewState(
                current = initial,
                domain = "mobile.example",
                enabled = true,
                limit = 0,
            ).isEmpty(),
        )
    }

    @Test
    fun `desktop user agent keeps engine version and removes mobile identity`() {
        val mobileUserAgent = "Mozilla/5.0 (Linux; Android 15; Pixel 9 Build/AP4A; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
            "Chrome/138.0.0.0 Mobile Safari/537.36"

        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/138.0.0.0 Safari/537.36",
            DesktopSiteRules.desktopUserAgent(mobileUserAgent),
        )
        assertEquals(
            DesktopSiteRules.desktopUserAgent(mobileUserAgent),
            DesktopSiteRules.desktopUserAgent(
                DesktopSiteRules.desktopUserAgent(mobileUserAgent),
            ),
        )
    }
}
