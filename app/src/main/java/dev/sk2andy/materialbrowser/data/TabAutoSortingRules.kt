package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BrowserTab

internal object TabAutoSortingRules {
    fun orderedTabs(
        tabs: List<BrowserTab>,
        selectedTabId: String,
    ): List<BrowserTab> = tabs.sortedWith(
        compareByDescending<BrowserTab>(BrowserTab::isPinned)
            .thenBy(BrowserTab::lastAccessedAt)
            .thenBy { tab -> tab.id == selectedTabId }
            .thenBy(BrowserTab::id),
    )
}
