package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserTab
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabHandoffRulesTest {
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
