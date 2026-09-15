package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPullToRefreshRulesTest {
    @Test
    fun `scroll metrics stay limited to selected tab without page scrollbar`() {
        assertTrue(
            BrowserPullToRefreshRules.shouldCollectScrollMetrics(
                isScrollBarEnabled = false,
                tabId = "selected",
                selectedTabId = "selected",
            ),
        )
        assertFalse(
            BrowserPullToRefreshRules.shouldCollectScrollMetrics(
                isScrollBarEnabled = false,
                tabId = "background",
                selectedTabId = "selected",
            ),
        )
        assertTrue(
            BrowserPullToRefreshRules.shouldCollectScrollMetrics(
                isScrollBarEnabled = true,
                tabId = "background",
                selectedTabId = "selected",
            ),
        )
    }

    @Test
    fun `top of idle page admits refresh`() {
        assertTrue(
            BrowserPullToRefreshRules.canStart(
                isLoading = false,
                scrollMetrics = metrics(offsetPx = 0),
            ),
        )
    }

    @Test
    fun `overscroll at top admits refresh`() {
        assertTrue(
            BrowserPullToRefreshRules.canStart(
                isLoading = false,
                scrollMetrics = metrics(offsetPx = -1),
            ),
        )
    }

    @Test
    fun `scrolled page keeps pull gesture with renderer`() {
        val metrics = metrics(offsetPx = 1)

        assertFalse(
            BrowserPullToRefreshRules.canStart(
                isLoading = false,
                scrollMetrics = metrics,
            ),
        )
        assertTrue(BrowserPullToRefreshRules.canChildScrollUp(metrics))
    }

    @Test
    fun `loading or unavailable metrics reject refresh`() {
        assertFalse(
            BrowserPullToRefreshRules.canStart(
                isLoading = true,
                scrollMetrics = metrics(offsetPx = 0),
            ),
        )
        assertFalse(
            BrowserPullToRefreshRules.canStart(
                isLoading = false,
                scrollMetrics = null,
            ),
        )
        assertTrue(BrowserPullToRefreshRules.canChildScrollUp(null))
    }

    private fun metrics(offsetPx: Int) = BrowserEngineScrollMetrics(
        offsetPx = offsetPx,
        extentPx = 1_000,
        rangePx = 2_000,
    )
}
