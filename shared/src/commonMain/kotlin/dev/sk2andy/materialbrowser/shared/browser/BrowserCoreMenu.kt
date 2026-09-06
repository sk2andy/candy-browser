package dev.sk2andy.materialbrowser.shared.browser

enum class BrowserCoreMenuAction {
    Back,
    Forward,
    Reload,
    Stop,
    NewTab,
    CloseTab,
    ShowTabs,
}

enum class BrowserCoreMenuLabelKey {
    Back,
    Forward,
    Reload,
    StopLoading,
    NewTab,
    CloseTab,
    Tabs,
}

data class BrowserCoreMenuItem(
    val action: BrowserCoreMenuAction,
    val labelKey: BrowserCoreMenuLabelKey,
    val enabled: Boolean,
)

data class BrowserCoreMenuCapabilities(
    val supportsNewTab: Boolean = true,
    val supportsCloseTab: Boolean = true,
    val supportsTabOverview: Boolean = true,
)

object BrowserCoreMenuRules {
    fun items(
        state: BrowserTabsState,
        capabilities: BrowserCoreMenuCapabilities,
    ): List<BrowserCoreMenuItem> {
        val selected = state.tabs.first { it.id == state.selectedTabId }
        val reloadOrStop = if (selected.isLoading) {
            BrowserCoreMenuItem(
                action = BrowserCoreMenuAction.Stop,
                labelKey = BrowserCoreMenuLabelKey.StopLoading,
                enabled = true,
            )
        } else {
            BrowserCoreMenuItem(
                action = BrowserCoreMenuAction.Reload,
                labelKey = BrowserCoreMenuLabelKey.Reload,
                enabled = selected.address.isNotBlank(),
            )
        }
        return buildList {
            add(
                BrowserCoreMenuItem(
                    action = BrowserCoreMenuAction.Back,
                    labelKey = BrowserCoreMenuLabelKey.Back,
                    enabled = selected.canGoBack,
                ),
            )
            add(
                BrowserCoreMenuItem(
                    action = BrowserCoreMenuAction.Forward,
                    labelKey = BrowserCoreMenuLabelKey.Forward,
                    enabled = selected.canGoForward,
                ),
            )
            add(reloadOrStop)
            if (capabilities.supportsNewTab) {
                add(
                    BrowserCoreMenuItem(
                        action = BrowserCoreMenuAction.NewTab,
                        labelKey = BrowserCoreMenuLabelKey.NewTab,
                        enabled = true,
                    ),
                )
            }
            if (capabilities.supportsCloseTab) {
                add(
                    BrowserCoreMenuItem(
                        action = BrowserCoreMenuAction.CloseTab,
                        labelKey = BrowserCoreMenuLabelKey.CloseTab,
                        enabled = true,
                    ),
                )
            }
            if (capabilities.supportsTabOverview) {
                add(
                    BrowserCoreMenuItem(
                        action = BrowserCoreMenuAction.ShowTabs,
                        labelKey = BrowserCoreMenuLabelKey.Tabs,
                        enabled = true,
                    ),
                )
            }
        }
    }
}
