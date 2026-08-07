package dev.sk2andy.materialbrowser.blocking

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledRequestRulesTest {
    @Test
    fun `blocks literal same-site path only on scoped page`() {
        val rules = rules(
            "block\tnews.example\tcdn.news.example\tL2Fkcy8\tnews-ads",
        )

        assertEquals(
            BundledRequestAction.Block,
            rules.decide(URI("https://cdn.news.example/ads/banner.js"), "www.news.example"),
        )
        assertNull(
            rules.decide(URI("https://cdn.news.example/assets/app.js"), "www.news.example"),
        )
        assertNull(
            rules.decide(URI("https://cdn.news.example/ads/banner.js"), "other.example"),
        )
    }

    @Test
    fun `more specific allow wins over block`() {
        val rules = rules(
            "block\tnews.example\tcdn.news.example\tL2Fkcy8\tnews-ads",
            "allow\tnews.example\tcdn.news.example\tL2Fkcy9hbGxvd2VkLw\tnews-ads-allowed",
        )

        assertEquals(
            BundledRequestAction.Allow,
            rules.decide(
                URI("https://cdn.news.example/ads/allowed/player.js"),
                "news.example",
            ),
        )
    }

    @Test
    fun `request blocker lets explicit path rule override same-site escape`() {
        val blocker = RequestBlocker(
            hostRules = emptySequence(),
            bundledRequestRules = rules(
                "block\tnews.example\tcdn.news.example\tL2Fkcy8\tnews-ads",
            ),
        )

        assertTrue(blocker.shouldBlock(
            "https://cdn.news.example/ads/banner.js",
            "https://www.news.example/article",
        ))
        assertFalse(blocker.shouldBlock(
            "https://cdn.news.example/assets/app.js",
            "https://www.news.example/article",
        ))
    }

    @Test
    fun `upstream exception wins over bundled path block`() {
        val blocker = RequestBlocker(
            hostRules = emptySequence(),
            allowedHostPairs = sequenceOf("cdn.news.example\tnews.example"),
            bundledRequestRules = rules(
                "block\tnews.example\tcdn.news.example\tL2Fkcy8\tnews-ads",
            ),
        )

        assertFalse(blocker.shouldBlock(
            "https://cdn.news.example/ads/banner.js",
            "https://news.example/article",
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects query matching and wildcard syntax`() {
        rules("block\tnews.example\tcdn.news.example\tL2Fkcy8_ZmxhZz0x\tunsafe")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects whole host root prefix`() {
        rules("block\tnews.example\tcdn.news.example\tLw\tunsafe-root")
    }

    @Test
    fun `path prefixes honor segment boundaries`() {
        val rules = rules(
            "block\tnews.example\tcdn.news.example\tL2Fkcw\tnews-ads",
        )

        assertEquals(
            BundledRequestAction.Block,
            rules.decide(URI("https://cdn.news.example/ads/banner.js"), "news.example"),
        )
        assertNull(
            rules.decide(URI("https://cdn.news.example/adsense/app.js"), "news.example"),
        )
    }

    private fun rules(vararg lines: String): BundledRequestRules =
        BundledRequestRules.parse(
            (listOf(BundledRequestRules.HEADER) + lines).joinToString("\n"),
        )
}
