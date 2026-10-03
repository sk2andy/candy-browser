package dev.sk2andy.materialbrowser.browser

internal enum class RootTabBackDecision {
    DelegateToSystem,
    ReturnToHome,
    ReturnToOpener,
    CloseAndReturnToOpener,
    CloseAndShowTabOverview,
}

internal object RootTabBackRules {
    fun decide(
        tabs: List<BrowserTab>,
        selectedTabId: String,
    ): RootTabBackDecision {
        val selectedTab = tabs.firstOrNull { tab -> tab.id == selectedTabId }
            ?: return RootTabBackDecision.DelegateToSystem
        if (tabs.any { tab -> tab.id != selectedTabId && tab.id == selectedTab.openerTabId }) {
            return if (selectedTab.isPinned) {
                RootTabBackDecision.ReturnToOpener
            } else {
                RootTabBackDecision.CloseAndReturnToOpener
            }
        }
        if (tabs.size == 1 || selectedTab.isPinned) {
            return if (selectedTab.url == BLANK_URL) {
                RootTabBackDecision.DelegateToSystem
            } else {
                RootTabBackDecision.ReturnToHome
            }
        }
        return RootTabBackDecision.CloseAndShowTabOverview
    }
}
