package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPullToRefreshRulesTest {
    @Test
    fun `video feed sites restrict pulls to the top zone including subdomains`() {
        listOf(
            "https://instagram.com/reels/",
            "https://www.instagram.com/",
            "https://tiktok.com/",
            "https://www.tiktok.com/@creator/video/123",
            "https://youtube.com/shorts/123",
            "https://m.youtube.com/",
            "http://WWW.YOUTUBE.COM.:8080/watch?v=123",
        ).forEach { url ->
            assertTrue(url, BrowserPullToRefreshRules.restrictPullToTopZone(url))
        }
    }

    @Test
    fun `other pages keep full page pulls without matching lookalike hosts or URL text`() {
        listOf(
            "https://example.com/",
            "https://example.com/instagram.com?next=https://youtube.com/",
            "https://instagram.com.example.com/",
            "https://notinstagram.com/",
            "https://nottiktok.com/",
            "https://notyoutube.com/",
            "https://youtube.com@other.test/",
        ).forEach { url ->
            assertFalse(url, BrowserPullToRefreshRules.restrictPullToTopZone(url))
        }
    }

    @Test
    fun `missing malformed and non web URLs do not select a site exception`() {
        listOf(null, "", "not a URL", "https://", "about:blank", "file://youtube.com/page")
            .forEach { url ->
                assertFalse(url, BrowserPullToRefreshRules.restrictPullToTopZone(url))
            }
    }

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
