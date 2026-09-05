package dev.sk2andy.materialbrowser.browser

internal object StartupHomeRules {
    fun reusableBlankTabId(tabs: List<BrowserTab>): String? =
        tabs.firstOrNull { tab -> !tab.isIncognito && tab.isFreshBlankTab }?.id
}
