package dev.sk2andy.materialbrowser.browser

internal enum class RootTabBackDecision {
    DelegateToSystem,
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
        if (tabs.size == 1 || selectedTab.isPinned) {
            return RootTabBackDecision.DelegateToSystem
        }
        return if (tabs.any { tab -> tab.id == selectedTab.openerTabId }) {
            RootTabBackDecision.CloseAndReturnToOpener
        } else {
            RootTabBackDecision.CloseAndShowTabOverview
        }
    }
}
