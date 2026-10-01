package dev.sk2andy.materialbrowser.browser.gecko

import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.CandyTrail
import dev.sk2andy.materialbrowser.browser.CandyTrailNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeckoBootstrapRecoveryRulesTest {
    @Test
    fun `poisoned stored tab becomes reusable blank tab`() {
        val tab = BrowserTab(
            id = "tab",
            lastAccessedAt = 1L,
            title = "Old page",
            url = STALE_BOOTSTRAP_URL,
        )

        assertEquals(BLANK_URL, GeckoBootstrapRecoveryRules.resetPoisonedTab(tab).url)
        assertEquals("", GeckoBootstrapRecoveryRules.resetPoisonedTab(tab).title)
        assertEquals(
            "https://example.com/article",
            GeckoBootstrapRecoveryRules.resetPoisonedTab(
                tab.copy(url = "https://example.com/article"),
            ).url,
        )
    }

    @Test
    fun `current trail page recovers poisoned tab before older visits`() {
        val trail = CandyTrail(
            tabId = "tab",
            nodes = listOf(
                CandyTrailNode("old", null, "https://example.com/old", "Old", 30L),
                CandyTrailNode("current", "old", "https://example.com/current", "Current", 20L),
            ),
            currentNodeId = "current",
        )

        assertEquals("https://example.com/current", GeckoBootstrapRecoveryRules.recoverUrl(trail))
    }

    @Test
    fun `missing trail current falls back to latest valid web visit`() {
        val trail = CandyTrail(
            tabId = "tab",
            nodes = listOf(
                CandyTrailNode("internal", null, STALE_BOOTSTRAP_URL, "", 30L),
                CandyTrailNode("web", null, "https://example.com/recover", "", 20L),
            ),
            currentNodeId = "missing",
        )

        assertEquals("https://example.com/recover", GeckoBootstrapRecoveryRules.recoverUrl(trail))
        assertNull(GeckoBootstrapRecoveryRules.recoverUrl(CandyTrail(tabId = "tab")))
    }

    private companion object {
        const val STALE_BOOTSTRAP_URL =
            "moz-extension://5e6344e7-68a0-4a80-863c-0123456789ab/" +
                "bootstrap.html?token=11111111-2222-4333-8444-555555555555"
    }
}
