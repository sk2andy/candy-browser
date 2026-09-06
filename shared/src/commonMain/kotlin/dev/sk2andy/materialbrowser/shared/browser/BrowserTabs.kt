package dev.sk2andy.materialbrowser.shared.browser

data class BrowserTabState(
    val id: String,
    val address: String,
    val title: String,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoading: Boolean = false,
)

data class BrowserTabsState(
    val tabs: List<BrowserTabState>,
    val selectedTabId: String,
    val isOverviewVisible: Boolean = false,
    val addressFocusRequest: Long = 0,
) {
    val countLabel: String
        get() = BrowserTabsRules.countLabel(tabs.size)
}

enum class BrowserTabsIntent {
    NewTab,
    SelectTab,
    CloseTab,
    ShowOverview,
    HideOverview,
}

object BrowserTabsRules {
    private const val INFINITE_TAB_COUNT = 100

    fun countLabel(tabCount: Int): String = if (tabCount >= INFINITE_TAB_COUNT) {
        "∞"
    } else {
        tabCount.coerceAtLeast(0).toString()
    }
}

class BrowserTabsController {
    var state: BrowserTabsState
        private set

    private var nextTabNumber: Long

    constructor() : this(
        initialState = BrowserTabsState(
            tabs = listOf(
                BrowserTabState(
                    id = tabId(1),
                    address = "",
                    title = "",
                ),
            ),
            selectedTabId = tabId(1),
        ),
    )

    constructor(initialState: BrowserTabsState) {
        require(initialState.tabs.isNotEmpty())
        require(initialState.tabs.any { it.id == initialState.selectedTabId })
        require(initialState.tabs.map(BrowserTabState::id).distinct().size == initialState.tabs.size)
        state = initialState
        nextTabNumber = initialState.tabs
            .mapNotNull { tab -> tab.id.removePrefix(TAB_ID_PREFIX).toLongOrNull() }
            .maxOrNull()
            ?.plus(1)
            ?: 1
    }

    fun dispatch(intent: BrowserTabsIntent, tabId: String?): BrowserTabsState = when (intent) {
        BrowserTabsIntent.NewTab -> addBlankTab()
        BrowserTabsIntent.SelectTab -> selectTab(tabId)
        BrowserTabsIntent.CloseTab -> closeTab(tabId)
        BrowserTabsIntent.ShowOverview -> update(state.copy(isOverviewVisible = true))
        BrowserTabsIntent.HideOverview -> update(state.copy(isOverviewVisible = false))
    }

    fun updateTab(
        tabId: String,
        address: String,
        title: String,
        canGoBack: Boolean,
        canGoForward: Boolean,
        isLoading: Boolean,
    ): BrowserTabsState {
        if (state.tabs.none { it.id == tabId }) return state
        return update(
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) {
                        tab.copy(
                            address = address,
                            title = title,
                            canGoBack = canGoBack,
                            canGoForward = canGoForward,
                            isLoading = isLoading,
                        )
                    } else {
                        tab
                    }
                },
            ),
        )
    }

    fun selectAdjacentTab(target: BrowserTabSwitchTarget): BrowserTabsState {
        val selectedIndex = state.tabs.indexOfFirst { it.id == state.selectedTabId }
        val targetIndex = when (target) {
            BrowserTabSwitchTarget.Stay -> return state
            BrowserTabSwitchTarget.Previous -> selectedIndex - 1
            BrowserTabSwitchTarget.Next -> selectedIndex + 1
        }
        val targetTab = state.tabs.getOrNull(targetIndex) ?: return state
        return selectTab(targetTab.id)
    }

    private fun addBlankTab(): BrowserTabsState {
        val tab = BrowserTabState(
            id = tabId(nextTabNumber++),
            address = "",
            title = "",
        )
        return update(
            state.copy(
                tabs = state.tabs + tab,
                selectedTabId = tab.id,
                isOverviewVisible = false,
                addressFocusRequest = state.addressFocusRequest + 1,
            ),
        )
    }

    private fun selectTab(tabId: String?): BrowserTabsState {
        if (tabId == null || state.tabs.none { it.id == tabId }) return state
        return update(
            state.copy(
                selectedTabId = tabId,
                isOverviewVisible = false,
            ),
        )
    }

    private fun closeTab(tabId: String?): BrowserTabsState {
        val closingIndex = state.tabs.indexOfFirst { it.id == tabId }
        if (closingIndex < 0) return state

        val remainingTabs = state.tabs.filterNot { it.id == tabId }
        if (remainingTabs.isEmpty()) return addBlankTabAfterClosingLast()

        val selectedTabId = if (state.selectedTabId == tabId) {
            remainingTabs[minOf(closingIndex, remainingTabs.lastIndex)].id
        } else {
            state.selectedTabId
        }
        return update(
            state.copy(
                tabs = remainingTabs,
                selectedTabId = selectedTabId,
            ),
        )
    }

    private fun addBlankTabAfterClosingLast(): BrowserTabsState {
        val tab = BrowserTabState(
            id = tabId(nextTabNumber++),
            address = "",
            title = "",
        )
        return update(
            state.copy(
                tabs = listOf(tab),
                selectedTabId = tab.id,
                isOverviewVisible = false,
                addressFocusRequest = state.addressFocusRequest + 1,
            ),
        )
    }

    private fun update(value: BrowserTabsState): BrowserTabsState {
        state = value
        return value
    }

    private companion object {
        const val TAB_ID_PREFIX = "tab-"

        fun tabId(number: Long): String = "$TAB_ID_PREFIX$number"
    }
}
