package dev.sk2andy.materialbrowser.shared.browser

import dev.sk2andy.materialbrowser.browser.SearchEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserSessionControllerTest {
    @Test
    fun `attaching initial blank tab does not ask engine to load`() {
        val controller = BrowserSessionController()
        val session = FakeBrowserEngineSessionPort(tabId = "tab-1")

        assertTrue(controller.attach(session))
        assertTrue(session.commands.isEmpty())
        assertFalse(controller.attach(FakeBrowserEngineSessionPort(tabId = "missing")))
    }

    @Test
    fun `navigation command and events update shared tab state`() {
        val controller = BrowserSessionController()
        val session = FakeBrowserEngineSessionPort(tabId = "tab-1")
        controller.attach(session)
        session.commands.clear()

        val resolution = controller.navigateSelected("example.com")
        val loading = controller.state.tabs.single()
        controller.onEngineEvent(
            BrowserEngineEvent(
                tabId = session.tabId,
                type = BrowserEngineEventType.NavigationCommitted,
                address = "https://example.com/",
                title = "Example",
                canGoBack = true,
                canGoForward = false,
                failureDescription = null,
            ),
        )
        val committed = controller.state.tabs.single()

        assertEquals(AddressResolutionKind.WebUrl, resolution.kind)
        assertEquals(BrowserEngineCommands.load("https://example.com"), session.commands.single())
        assertTrue(loading.isLoading)
        assertEquals("https://example.com/", committed.address)
        assertEquals("Example", committed.title)
        assertTrue(committed.canGoBack)
        assertFalse(committed.isLoading)
    }

    @Test
    fun `selected search provider is applied before the engine load adapter`() {
        val controller = BrowserSessionController()
        val session = FakeBrowserEngineSessionPort(tabId = "tab-1")
        controller.attach(session)
        controller.updateSearchSettings(
            searchEngine = SearchEngine.Kagi,
            searxngInstanceUrl = "",
        )

        val resolution = controller.navigateSelected("candy browser")

        assertEquals(AddressResolutionKind.Search, resolution.kind)
        assertEquals(
            BrowserEngineCommands.load("https://kagi.com/search?q=candy%20browser"),
            session.commands.single(),
        )
    }

    @Test
    fun `synchronous load failure is not overwritten by loading state`() {
        lateinit var controller: BrowserSessionController
        val session = CallbackBrowserEngineSessionPort(tabId = "tab-1") { command ->
            if (command.address == "https://example.com") {
                controller.onEngineEvent(
                    BrowserEngineEvent(
                        tabId = "tab-1",
                        type = BrowserEngineEventType.NavigationFailed,
                        address = command.address,
                        title = null,
                        canGoBack = false,
                        canGoForward = false,
                        failureDescription = "Rejected",
                    ),
                )
            }
        }
        controller = BrowserSessionController()
        controller.attach(session)

        controller.navigateSelected("example.com")

        assertFalse(controller.state.tabs.single().isLoading)
    }

    @Test
    fun `closing tab closes port and rejects stale engine event`() {
        val controller = BrowserSessionController()
        val first = FakeBrowserEngineSessionPort(tabId = "tab-1")
        controller.attach(first)
        controller.dispatchTabs(BrowserTabsIntent.NewTab, tabId = null)
        val secondId = controller.state.selectedTabId
        val second = FakeBrowserEngineSessionPort(tabId = secondId)
        controller.attach(second)

        controller.dispatchTabs(BrowserTabsIntent.CloseTab, tabId = "tab-1")
        val beforeStaleEvent = controller.state
        val afterStaleEvent = controller.onEngineEvent(
            BrowserEngineEvent(
                tabId = "tab-1",
                type = BrowserEngineEventType.NavigationCommitted,
                address = "https://stale.example/",
                title = "Stale",
                canGoBack = false,
                canGoForward = false,
                failureDescription = null,
            ),
        )

        assertEquals(BrowserEngineCommands.close(), first.commands.last())
        assertEquals(beforeStaleEvent, afterStaleEvent)
        assertEquals(listOf(secondId), controller.attachedTabIds)
    }

    @Test
    fun `state-only engine event refreshes history without changing loading`() {
        val controller = BrowserSessionController()
        val session = FakeBrowserEngineSessionPort(tabId = "tab-1")
        controller.attach(session)
        controller.onEngineEvent(
            BrowserEngineEvent(
                tabId = session.tabId,
                type = BrowserEngineEventType.NavigationStarted,
                address = "https://example.com/start",
                title = null,
                canGoBack = false,
                canGoForward = false,
                failureDescription = null,
            ),
        )

        val state = controller.onEngineEvent(
            BrowserEngineEvent(
                tabId = session.tabId,
                type = BrowserEngineEventType.StateChanged,
                address = "https://example.com/history",
                title = "History entry",
                canGoBack = true,
                canGoForward = true,
                failureDescription = null,
            ),
        ).tabs.single()

        assertEquals("https://example.com/history", state.address)
        assertEquals("History entry", state.title)
        assertTrue(state.canGoBack)
        assertTrue(state.canGoForward)
        assertTrue(state.isLoading)
    }

    @Test
    fun `engine closed event creates blank replacement for last tab`() {
        val controller = BrowserSessionController()
        val session = FakeBrowserEngineSessionPort(tabId = "tab-1")
        controller.attach(session)

        val state = controller.onEngineEvent(
            BrowserEngineEvent(
                tabId = session.tabId,
                type = BrowserEngineEventType.Closed,
                address = null,
                title = null,
                canGoBack = false,
                canGoForward = false,
                failureDescription = null,
            ),
        )

        assertEquals(1, state.tabs.size)
        assertEquals("", state.tabs.single().address)
        assertTrue(controller.attachedTabIds.isEmpty())
    }

    @Test
    fun `controller close detaches and closes every engine session`() {
        val controller = BrowserSessionController()
        val first = FakeBrowserEngineSessionPort(tabId = "tab-1")
        controller.attach(first)
        controller.dispatchTabs(BrowserTabsIntent.NewTab, tabId = null)
        val second = FakeBrowserEngineSessionPort(tabId = controller.state.selectedTabId)
        controller.attach(second)

        controller.close()

        assertTrue(controller.attachedTabIds.isEmpty())
        assertEquals(BrowserEngineCommands.close(), first.commands.last())
        assertEquals(BrowserEngineCommands.close(), second.commands.last())
    }

    @Test
    fun `detaching a session preserves tab state for isolated storage recreation`() {
        val controller = BrowserSessionController()
        val original = FakeBrowserEngineSessionPort(tabId = "tab-1")
        controller.attach(original)

        assertTrue(controller.detachSession("tab-1"))
        assertEquals(listOf("tab-1"), controller.state.tabs.map(BrowserTabState::id))
        assertTrue(controller.attachedTabIds.isEmpty())
        assertEquals(BrowserEngineCommands.close(), original.commands.last())

        val replacement = FakeBrowserEngineSessionPort(tabId = "tab-1")
        assertTrue(controller.attach(replacement))
    }

    @Test
    fun `engine crash detaches session but preserves tab for recovery`() {
        val controller = BrowserSessionController()
        val crashed = FakeBrowserEngineSessionPort(tabId = "tab-1")
        controller.attach(crashed)

        val state = controller.onEngineEvent(
            BrowserEngineEvent(
                tabId = crashed.tabId,
                type = BrowserEngineEventType.Crashed,
                address = null,
                title = null,
                canGoBack = false,
                canGoForward = false,
                failureDescription = "Content process crashed",
            ),
        )
        val replacement = FakeBrowserEngineSessionPort(tabId = crashed.tabId)

        assertEquals(listOf("tab-1"), state.tabs.map(BrowserTabState::id))
        assertFalse(state.tabs.single().isLoading)
        assertTrue(controller.attachedTabIds.isEmpty())
        assertTrue(controller.attach(replacement))
        assertTrue(replacement.commands.isEmpty())
    }

    @Test
    fun `address swipe selects adjacent tab unless editor is active`() {
        val controller = BrowserSessionController()
        controller.dispatchTabs(BrowserTabsIntent.NewTab, tabId = null)
        val middleTabId = controller.state.selectedTabId
        controller.dispatchTabs(BrowserTabsIntent.NewTab, tabId = null)

        controller.dispatchTabSwitchGesture(
            dragX = 240.0,
            dragY = 0.0,
            velocityX = 0.0,
            viewportWidth = 1_000.0,
            isAddressEditing = false,
        )
        assertEquals(middleTabId, controller.state.selectedTabId)

        controller.dispatchTabSwitchGesture(
            dragX = 240.0,
            dragY = 0.0,
            velocityX = 1_000.0,
            viewportWidth = 1_000.0,
            isAddressEditing = true,
        )
        assertEquals(middleTabId, controller.state.selectedTabId)
    }
}

private class FakeBrowserEngineSessionPort(
    override val tabId: String,
) : BrowserEngineSessionPort {
    val commands = mutableListOf<BrowserEngineCommand>()

    override fun execute(command: BrowserEngineCommand) {
        commands += command
    }
}

private class CallbackBrowserEngineSessionPort(
    override val tabId: String,
    private val onCommand: (BrowserEngineCommand) -> Unit,
) : BrowserEngineSessionPort {
    override fun execute(command: BrowserEngineCommand) {
        onCommand(command)
    }
}
