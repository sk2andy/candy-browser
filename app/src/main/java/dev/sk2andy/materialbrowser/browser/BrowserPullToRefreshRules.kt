package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.blocking.CandyHostCanonicalizer

/** Engine-neutral admission rules for a user-initiated pull to refresh. */
internal object BrowserPullToRefreshRules {
    private val topZoneHosts = setOf("instagram.com", "tiktok.com", "youtube.com")

    fun restrictPullToTopZone(pageUrl: String?): Boolean {
        val host = CandyHostCanonicalizer.webHost(pageUrl) ?: return false
        return topZoneHosts.any { CandyHostCanonicalizer.matches(host, it) }
    }

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
