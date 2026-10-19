package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaviconFetchRulesTest {
    @Test
    fun `accepts HTTPS Mozilla add-on image URLs only`() {
        assertEquals(
            "https://addons.mozilla.org/user-media/addon_icons/a.png",
            FaviconFetchRules.allowedMozillaAddonImageUrl(
                "https://addons.mozilla.org/user-media/addon_icons/a.png",
            ),
        )
        assertEquals(
            "https://addons.cdn.mozilla.net/a.png",
            FaviconFetchRules.allowedMozillaAddonImageUrl("https://addons.cdn.mozilla.net/a.png"),
        )
        assertNull(FaviconFetchRules.allowedMozillaAddonImageUrl("http://addons.mozilla.org/a.png"))
        assertNull(FaviconFetchRules.allowedMozillaAddonImageUrl("https://eviladdons.mozilla.org/a.png"))
        assertNull(FaviconFetchRules.allowedMozillaAddonImageUrl("https://addons.mozilla.org.evil/a.png"))
        assertNull(FaviconFetchRules.allowedMozillaAddonImageUrl("https://user@addons.mozilla.org/a.png"))
    }

    @Test
    fun `builds first party icon url from page origin`() {
        assertEquals(
            "https://example.com/favicon.ico",
            FaviconFetchRules.originIconUrl("https://example.com/a?q=1#section"),
        )
        assertEquals(
            "http://example.com:8080/favicon.ico",
            FaviconFetchRules.originIconUrl("http://example.com:8080/page"),
        )
    }

    @Test
    fun `rejects unsupported or credentialed page urls`() {
        assertNull(FaviconFetchRules.originIconUrl("file:///tmp/page.html"))
        assertNull(FaviconFetchRules.originIconUrl("https://user@example.com/page"))
        assertNull(FaviconFetchRules.originIconUrl("not a url"))
    }

    @Test
    fun `allows only same origin redirects`() {
        val iconUrl = "https://example.com/favicon.ico"

        assertEquals(
            "https://example.com/assets/icon.png",
            FaviconFetchRules.allowedRedirect(iconUrl, "/assets/icon.png"),
        )
        assertNull(FaviconFetchRules.allowedRedirect(iconUrl, "https://images.example.com/icon.png"))
        assertNull(FaviconFetchRules.allowedRedirect(iconUrl, "http://example.com/icon.png"))
        assertNull(FaviconFetchRules.allowedRedirect(iconUrl, "https://example.com:8443/icon.png"))
    }

    @Test
    fun `uses one stable cache id per origin`() {
        assertEquals(
            FaviconFetchRules.cacheId("https://example.com/one"),
            FaviconFetchRules.cacheId("https://example.com/two?q=1"),
        )
        assertNotEquals(
            FaviconFetchRules.cacheId("https://example.com/one"),
            FaviconFetchRules.cacheId("https://example.com:8443/one"),
        )
    }
}
