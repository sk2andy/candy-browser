package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserTab
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabHandoffRulesTest {
    @Test
    fun `only unloaded regular pages use restoration presentation`() {
        val page = handoff(url = "https://example.com/").tab

        assertTrue(TabHandoffRules.isRestoring(page, sessionResident = false))
        assertFalse(TabHandoffRules.isRestoring(page, sessionResident = true))
        assertFalse(TabHandoffRules.isRestoring(page.copy(isIncognito = true), sessionResident = false))
        assertFalse(TabHandoffRules.isRestoring(page.copy(url = BLANK_URL), sessionResident = false))
    }

    @Test
    fun `restoration snapshot gains color while smoothly revealing live page`() {
        val restored = handoff(url = "https://example.com/").copy(isRestoring = true)

        assertEquals(250, TabHandoffRules.revealDurationMillis(restored))
        assertEquals(0f, TabHandoffRules.previewSaturation(restored, alpha = 1f), 0f)
        assertEquals(0.5f, TabHandoffRules.previewSaturation(restored, alpha = 0.5f), 0f)
        assertEquals(1f, TabHandoffRules.previewSaturation(restored, alpha = 0f), 0f)
        assertEquals(0f, TabHandoffRules.previewSaturation(restored, alpha = 2f), 0f)
        assertEquals(1f, TabHandoffRules.previewSaturation(restored, alpha = -1f), 0f)
    }

    @Test
    fun `warm switch keeps color throughout its short reveal`() {
        val warm = handoff(url = "https://example.com/")

        assertEquals(110, TabHandoffRules.revealDurationMillis(warm))
        assertEquals(1f, TabHandoffRules.previewSaturation(warm, alpha = 1f), 0f)
        assertEquals(1f, TabHandoffRules.previewSaturation(warm, alpha = 0.5f), 0f)
    }

    @Test
    fun `repeated handoff to same tab owns a new visual timeout`() {
        val first = handoff(url = "https://example.com/")
        val second = handoff(url = "https://example.com/")

        assertNotSame(first.visualIdentity, second.visualIdentity)
        assertNotEquals(first, second)
    }

    @Test
    fun `restored page waits for matching live frame after overview closes`() {
        val handoff = handoff(url = "https://example.com/")

        assertFalse(
            TabHandoffRules.shouldRevealLiveContent(
                handoff = handoff,
                tabOverviewVisible = true,
                liveFrameTabId = handoff.tabId,
            ),
        )
        assertFalse(
            TabHandoffRules.shouldRevealLiveContent(
                handoff = handoff,
                tabOverviewVisible = false,
                liveFrameTabId = null,
            ),
        )
        assertTrue(
            TabHandoffRules.shouldRevealLiveContent(
                handoff = handoff,
                tabOverviewVisible = false,
                liveFrameTabId = handoff.tabId,
            ),
        )
    }

    @Test
    fun `blank page can reveal after overview closes without engine frame`() {
        val handoff = handoff(url = BLANK_URL)

        assertFalse(
            TabHandoffRules.shouldRevealLiveContent(
                handoff = handoff,
                tabOverviewVisible = true,
                liveFrameTabId = null,
            ),
        )
        assertTrue(
            TabHandoffRules.shouldRevealLiveContent(
                handoff = handoff,
                tabOverviewVisible = false,
                liveFrameTabId = null,
            ),
        )
    }

    private fun handoff(url: String) = TabHandoff(
        tab = BrowserTab(
            id = "target",
            lastAccessedAt = 1L,
            title = "Target",
            url = url,
        ),
        preview = null,
        favicon = null,
        previewTopInsetPx = 0,
    )
}
