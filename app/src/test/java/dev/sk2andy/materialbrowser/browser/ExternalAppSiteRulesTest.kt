package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalAppSiteRulesTest {
    @Test
    fun `website exception covers all subdomains and paths`() {
        assertEquals("github.com", ExternalAppSiteRules.siteForUrl("https://www.github.com/login"))
        assertEquals(
            "example.co.uk",
            ExternalAppSiteRules.siteForUrl("http://mobile.news.example.co.uk:8080/story?q=1#part"),
        )
        assertTrue(ExternalAppSiteRules.contains("https://gist.github.com/123", listOf("github.com")))
        assertTrue(ExternalAppSiteRules.contains("https://github.com/other", listOf("WWW.GITHUB.COM")))
        assertFalse(ExternalAppSiteRules.contains("https://notgithub.com", listOf("github.com")))
        assertFalse(ExternalAppSiteRules.contains("https://github.com.evil.test", listOf("github.com")))
    }

    @Test
    fun `private suffix tenants remain separate websites`() {
        assertEquals(
            "alice.web.app",
            ExternalAppSiteRules.siteForUrl("https://media.alice.web.app/player"),
        )
        assertTrue(
            ExternalAppSiteRules.contains("https://alice.web.app/", listOf("media.alice.web.app")),
        )
        assertFalse(
            ExternalAppSiteRules.contains("https://bob.web.app/", listOf("alice.web.app")),
        )
    }

    @Test
    fun `unsafe and non web links cannot identify exception website`() {
        listOf(
            null,
            "",
            "github.com",
            "https://user@github.com/login",
            "https://github.com/path\nmore",
            "https://git hub.com/",
            "file:///tmp/github.com",
            "javascript:location='https://github.com'",
            "intent://github.com/#Intent;scheme=https;end",
            "https://127.0.0.1/",
        ).forEach { url ->
            assertNull(url, ExternalAppSiteRules.siteForUrl(url))
            assertFalse(ExternalAppSiteRules.contains(url, listOf("github.com")))
        }
    }

    @Test
    fun `stored websites are canonical sorted distinct and bounded`() {
        assertEquals(
            listOf("example.co.uk", "github.com", "xn--bcher-kva.de"),
            ExternalAppSiteRules.normalizedSites(
                listOf(
                    "WWW.GitHub.COM.",
                    "github.com",
                    "mobile.example.co.uk",
                    "bücher.de",
                    "https://github.com/path",
                    "bad host.test",
                    "127.0.0.1",
                    "",
                ),
            ),
        )
        val oversized = (1..80).reversed().map { "site${it.toString().padStart(2, '0')}.example" }
        val normalized = ExternalAppSiteRules.normalizedSites(oversized)

        assertEquals(64, normalized.size)
        assertEquals("site01.example", normalized.first())
        assertEquals("site64.example", normalized.last())
        assertEquals(normalized, ExternalAppSiteRules.normalizedSites(normalized))
    }

    @Test
    fun `enabling at capacity retains new website and reset removes canonical site`() {
        val initial = (1..64).map { "site${it.toString().padStart(2, '0')}.example" }
        val enabled = ExternalAppSiteRules.withState(initial, "www.zebra.com", enabled = true)

        assertEquals(64, enabled.size)
        assertTrue("zebra.com" in enabled)
        assertFalse("site64.example" in enabled)
        assertEquals(enabled.sorted(), enabled)
        assertFalse(
            "zebra.com" in ExternalAppSiteRules.withState(enabled, "m.zebra.com", enabled = false),
        )
        assertEquals(
            listOf("github.com"),
            ExternalAppSiteRules.withState(
                current = listOf("WWW.GITHUB.COM", "bad host.test"),
                domain = "https://evil.test/",
                enabled = true,
            ),
        )
    }
}
