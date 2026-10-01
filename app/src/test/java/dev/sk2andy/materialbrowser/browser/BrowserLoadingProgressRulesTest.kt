package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEvent
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BrowserLoadingProgressRulesTest {
    private val loadingTab = BrowserTab(
        id = "tab",
        lastAccessedAt = 0L,
        url = "https://example.com/current",
        isLoading = true,
        progress = 42,
    )

    @Test
    fun `active navigation accepts bounded progress and terminal loading state`() {
        assertEquals(
            80,
            BrowserLoadingProgressRules.apply(loadingTab, event(progress = 80)).progress,
        )
        assertEquals(
            100,
            BrowserLoadingProgressRules.apply(loadingTab, event(progress = 120)).progress,
        )
        assertEquals(
            0,
            BrowserLoadingProgressRules.apply(loadingTab, event(progress = -5)).progress,
        )
        assertFalse(
            BrowserLoadingProgressRules.apply(loadingTab, event(isLoading = false)).isLoading,
        )
    }

    @Test
    fun `unrelated state event preserves measured progress`() {
        assertEquals(loadingTab, BrowserLoadingProgressRules.apply(loadingTab, event()))
    }

    @Test
    fun `stale address cannot change progress or end newer navigation`() {
        assertEquals(
            loadingTab,
            BrowserLoadingProgressRules.apply(
                loadingTab,
                event(progress = 99, isLoading = false).copy(address = "https://example.com/old"),
            ),
        )
    }

    @Test
    fun `different tab cannot change active navigation`() {
        assertEquals(
            loadingTab,
            BrowserLoadingProgressRules.apply(
                loadingTab,
                event(progress = 99).copy(tabId = "other"),
            ),
        )
    }

    @Test
    fun `stopped navigation ignores late progress and cannot restart loading`() {
        val stoppedTab = loadingTab.copy(isLoading = false)
        assertEquals(
            stoppedTab,
            BrowserLoadingProgressRules.apply(stoppedTab, event(progress = 99, isLoading = true)),
        )
    }

    private fun event(progress: Int? = null, isLoading: Boolean? = null) = BrowserEngineEvent(
        tabId = "tab",
        type = BrowserEngineEventType.StateChanged,
        address = loadingTab.url,
        title = null,
        canGoBack = false,
        canGoForward = false,
        failureDescription = null,
        isLoading = isLoading,
        progress = progress,
    )
}
