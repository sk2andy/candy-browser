package dev.sk2andy.materialbrowser.shared.browser

interface BrowserEngineSessionPort {
    val tabId: String

    fun execute(command: BrowserEngineCommand)
}

enum class BrowserEngineCommandType {
    Load,
    Back,
    Forward,
    Reload,
    Stop,
    Close,
}

data class BrowserEngineCommand(
    val type: BrowserEngineCommandType,
    val address: String?,
) {
    init {
        require((type == BrowserEngineCommandType.Load) == (address != null))
    }
}

object BrowserEngineCommands {
    fun load(address: String): BrowserEngineCommand = BrowserEngineCommand(
        type = BrowserEngineCommandType.Load,
        address = address,
    )

    fun back(): BrowserEngineCommand = withoutAddress(BrowserEngineCommandType.Back)

    fun forward(): BrowserEngineCommand = withoutAddress(BrowserEngineCommandType.Forward)

    fun reload(): BrowserEngineCommand = withoutAddress(BrowserEngineCommandType.Reload)

    fun stop(): BrowserEngineCommand = withoutAddress(BrowserEngineCommandType.Stop)

    fun close(): BrowserEngineCommand = withoutAddress(BrowserEngineCommandType.Close)

    private fun withoutAddress(type: BrowserEngineCommandType): BrowserEngineCommand =
        BrowserEngineCommand(type = type, address = null)
}

enum class BrowserEngineEventType {
    NavigationStarted,
    NavigationCommitted,
    NavigationFailed,
    StateChanged,
    Crashed,
    Closed,
}

data class BrowserEngineEvent(
    val tabId: String,
    val type: BrowserEngineEventType,
    val address: String?,
    val title: String?,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val failureDescription: String?,
)

class BrowserSessionController(
    private val tabsController: BrowserTabsController,
) {
    private val sessions = mutableMapOf<String, BrowserEngineSessionPort>()

    constructor() : this(BrowserTabsController())

    val state: BrowserTabsState
        get() = tabsController.state

    val attachedTabIds: List<String>
        get() = sessions.keys.sorted()

    fun menuItems(capabilities: BrowserCoreMenuCapabilities): List<BrowserCoreMenuItem> =
        BrowserCoreMenuRules.items(state = state, capabilities = capabilities)

    fun attach(session: BrowserEngineSessionPort): Boolean {
        val tab = state.tabs.firstOrNull { it.id == session.tabId } ?: return false
        val existing = sessions[session.tabId]
        if (existing === session) return true
        if (existing != null) return false

        sessions[session.tabId] = session
        if (tab.address.isNotBlank()) {
            session.execute(BrowserEngineCommands.load(tab.address))
        }
        return true
    }

    fun dispatchTabs(intent: BrowserTabsIntent, tabId: String?): BrowserTabsState {
        val previousTabIds = state.tabs.map(BrowserTabState::id).toSet()
        val nextState = tabsController.dispatch(intent = intent, tabId = tabId)
        val nextTabIds = nextState.tabs.map(BrowserTabState::id).toSet()
        (previousTabIds - nextTabIds).forEach(::closeSession)
        return nextState
    }

    fun selectAdjacentTab(target: BrowserTabSwitchTarget): BrowserTabsState =
        tabsController.selectAdjacentTab(target)

    fun dispatchTabSwitchGesture(
        dragX: Double,
        dragY: Double,
        velocityX: Double,
        viewportWidth: Double,
        isAddressEditing: Boolean,
    ): BrowserTabsState {
        val selectedIndex = state.tabs.indexOfFirst { it.id == state.selectedTabId }
        val target = BrowserTabSwitchGestureRules.target(
            dragX = dragX,
            dragY = dragY,
            velocityX = velocityX,
            viewportWidth = viewportWidth,
            hasPreviousTab = selectedIndex > 0,
            hasNextTab = selectedIndex in 0 until state.tabs.lastIndex,
            isAddressEditing = isAddressEditing,
        )
        return tabsController.selectAdjacentTab(target)
    }

    fun executeSelected(command: BrowserEngineCommand): Boolean =
        execute(tabId = state.selectedTabId, command = command)

    fun execute(tabId: String, command: BrowserEngineCommand): Boolean {
        val session = sessions[tabId] ?: return false
        session.execute(command)
        if (command.type == BrowserEngineCommandType.Stop) {
            updateTabLoading(tabId = tabId, isLoading = false)
        }
        return true
    }

    fun navigateSelected(input: String): AddressResolution {
        val resolution = BrowserUrlRules.resolve(input)
        val address = resolution.url?.value ?: return resolution
        val selected = state.tabs.first { it.id == state.selectedTabId }
        if (sessions[selected.id] == null) return resolution
        tabsController.updateTab(
            tabId = selected.id,
            address = address,
            title = selected.title,
            canGoBack = selected.canGoBack,
            canGoForward = selected.canGoForward,
            isLoading = true,
        )
        sessions.getValue(selected.id).execute(BrowserEngineCommands.load(address))
        return resolution
    }

    fun onEngineEvent(event: BrowserEngineEvent): BrowserTabsState {
        if (sessions[event.tabId] == null) return state
        val previous = state.tabs.firstOrNull { it.id == event.tabId } ?: return state
        return when (event.type) {
            BrowserEngineEventType.NavigationStarted -> tabsController.updateTab(
                tabId = event.tabId,
                address = normalizedAddress(event.address) ?: previous.address,
                title = previous.title,
                canGoBack = previous.canGoBack,
                canGoForward = previous.canGoForward,
                isLoading = true,
            )
            BrowserEngineEventType.NavigationCommitted -> tabsController.updateTab(
                tabId = event.tabId,
                address = normalizedAddress(event.address) ?: previous.address,
                title = event.title?.take(MAX_TITLE_LENGTH) ?: previous.title,
                canGoBack = event.canGoBack,
                canGoForward = event.canGoForward,
                isLoading = false,
            )
            BrowserEngineEventType.NavigationFailed -> tabsController.updateTab(
                tabId = event.tabId,
                address = previous.address,
                title = previous.title,
                canGoBack = event.canGoBack,
                canGoForward = event.canGoForward,
                isLoading = false,
            )
            BrowserEngineEventType.StateChanged -> tabsController.updateTab(
                tabId = event.tabId,
                address = normalizedAddress(event.address) ?: previous.address,
                title = event.title?.take(MAX_TITLE_LENGTH) ?: previous.title,
                canGoBack = event.canGoBack,
                canGoForward = event.canGoForward,
                isLoading = previous.isLoading,
            )
            BrowserEngineEventType.Crashed -> {
                sessions.remove(event.tabId)
                tabsController.updateTab(
                    tabId = event.tabId,
                    address = previous.address,
                    title = previous.title,
                    canGoBack = event.canGoBack,
                    canGoForward = event.canGoForward,
                    isLoading = false,
                )
            }
            BrowserEngineEventType.Closed -> {
                sessions.remove(event.tabId)
                tabsController.dispatch(BrowserTabsIntent.CloseTab, tabId = event.tabId)
            }
        }
    }

    fun close() {
        val attachedSessions = sessions.values.toList()
        sessions.clear()
        attachedSessions.forEach { session ->
            session.execute(BrowserEngineCommands.close())
        }
    }

    private fun closeSession(tabId: String) {
        val session = sessions.remove(tabId) ?: return
        session.execute(BrowserEngineCommands.close())
    }

    private fun updateTabLoading(tabId: String, isLoading: Boolean) {
        val tab = state.tabs.firstOrNull { it.id == tabId } ?: return
        tabsController.updateTab(
            tabId = tab.id,
            address = tab.address,
            title = tab.title,
            canGoBack = tab.canGoBack,
            canGoForward = tab.canGoForward,
            isLoading = isLoading,
        )
    }

    private fun normalizedAddress(address: String?): String? = address
        ?.takeIf(String::isNotBlank)
        ?.let(BrowserUrlRules::normalizeHttpUrl)
        ?.value

    private companion object {
        const val MAX_TITLE_LENGTH = 4_096
    }
}
