package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.blocking.CandyHostCanonicalizer
import java.net.URI

/** Engine-neutral admission rules for a user-initiated pull to refresh. */
internal object BrowserPullToRefreshRules {
    private val topZoneHosts = setOf("instagram.com", "tiktok.com")

    fun restrictPullToTopZone(pageUrl: String?): Boolean {
        val host = CandyHostCanonicalizer.webHost(pageUrl) ?: return false
        if (topZoneHosts.any { CandyHostCanonicalizer.matches(host, it) }) return true
        if (!CandyHostCanonicalizer.matches(host, "youtube.com")) return false
        val path = runCatching { URI(pageUrl).path }.getOrNull() ?: return false
        return path == "/watch" || path == "/shorts" || path.startsWith("/shorts/")
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
