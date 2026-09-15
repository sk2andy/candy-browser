package dev.sk2andy.materialbrowser.browser

/** Engine-neutral admission rules for a user-initiated pull to refresh. */
internal object BrowserPullToRefreshRules {
    fun shouldCollectScrollMetrics(
        isScrollBarEnabled: Boolean,
        tabId: String,
        selectedTabId: String,
    ): Boolean = isScrollBarEnabled || tabId == selectedTabId

    fun canStart(
        isLoading: Boolean,
        scrollMetrics: BrowserEngineScrollMetrics?,
    ): Boolean = !isLoading && scrollMetrics != null && scrollMetrics.offsetPx <= 0

    fun canChildScrollUp(scrollMetrics: BrowserEngineScrollMetrics?): Boolean =
        scrollMetrics == null || scrollMetrics.offsetPx > 0
}
