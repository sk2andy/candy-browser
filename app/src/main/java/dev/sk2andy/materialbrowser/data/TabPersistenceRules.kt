package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BrowserTab

internal object TabPersistenceRules {
    fun persistentTabs(tabs: List<BrowserTab>): List<BrowserTab> =
        tabs.filterNot(BrowserTab::isIncognito)

    fun persistentSelection(tabs: List<BrowserTab>, selectedTabId: String): String? {
        val persistentTabs = persistentTabs(tabs)
        return selectedTabId
            .takeIf { selectedId -> persistentTabs.any { it.id == selectedId } }
            ?: persistentTabs.maxByOrNull(BrowserTab::lastAccessedAt)?.id
    }
}
