package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEvent

internal object BrowserLoadingProgressRules {
    fun apply(tab: BrowserTab, event: BrowserEngineEvent): BrowserTab {
        if (!tab.isLoading || event.tabId != tab.id) return tab
        if (event.address != null && event.address != tab.url) return tab
        return tab.copy(
            isLoading = event.isLoading ?: tab.isLoading,
            progress = event.progress?.coerceIn(0, 100) ?: tab.progress,
        )
    }
}
