package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupSiteRulesTest {
    @Test
    fun `subdomains share registrable always block state`() {
        assertEquals(
            "example.co.uk",
            PopupSiteRules.domainForUrl("https://video.news.example.co.uk/watch"),
        )
        assertEquals(
            "alice.web.app",
            PopupSiteRules.domainForUrl("https://media.alice.web.app/player"),
        )
        assertFalse(
            PopupSiteRules.shouldAlwaysBlock(
                "https://bob.web.app/player",
                setOf("alice.web.app"),
            ),
        )
        assertTrue(
            PopupSiteRules.shouldAlwaysBlock(
                "https://www.example.co.uk/watch",
                setOf("example.co.uk"),
            ),
        )
    }

    @Test
    fun `non web pages cannot enable always block`() {
        assertNull(PopupSiteRules.domainForUrl(BLANK_URL))
        assertNull(PopupSiteRules.domainForUrl("file:///tmp/page.html"))
        assertFalse(PopupSiteRules.shouldAlwaysBlock("https://other.example", emptySet()))
    }

    @Test
    fun `always block state is canonical bounded and reversible`() {
        val initial = (1..64).map { index -> "site$index.example" }

        val enabled = PopupSiteRules.withAlwaysBlockState(
            current = initial,
            domain = "VIDEO.Example",
            enabled = true,
        )
        val disabled = PopupSiteRules.withAlwaysBlockState(
            current = enabled,
            domain = "video.example",
            enabled = false,
        )

        assertEquals(64, enabled.size)
        assertEquals("video.example", enabled.last())
        assertFalse("site64.example" in enabled)
        assertFalse("video.example" in disabled)
        assertTrue(
            PopupSiteRules.withAlwaysBlockState(
                current = initial,
                domain = "video.example",
                enabled = true,
                limit = 0,
            ).isEmpty(),
        )
    }
}
