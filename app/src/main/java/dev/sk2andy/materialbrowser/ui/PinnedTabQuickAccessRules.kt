package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.browser.BrowserTab

internal object PinnedTabQuickAccessRules {
    fun eligibleTabs(
        tabs: List<BrowserTab>,
        profileId: String,
        isIncognito: Boolean,
    ): List<BrowserTab> = tabs.filter { tab ->
        tab.isPinned && tab.profileId == profileId && tab.isIncognito == isIncognito
    }
}
