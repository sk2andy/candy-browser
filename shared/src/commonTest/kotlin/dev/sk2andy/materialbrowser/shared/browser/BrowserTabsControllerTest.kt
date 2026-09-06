package dev.sk2andy.materialbrowser.shared.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BrowserTabsControllerTest {
    @Test
    fun `initial tab matches the blank Candy start surface`() {
        val state = BrowserTabsController().state

        assertEquals("", state.tabs.single().address)
        assertEquals("", state.tabs.single().title)
        assertEquals(state.tabs.single().id, state.selectedTabId)
    }

    @Test
    fun `new tab is blank selected and requests address focus`() {
        val controller = BrowserTabsController()
        val previousId = controller.state.selectedTabId

        val state = controller.dispatch(BrowserTabsIntent.NewTab, tabId = null)

        assertEquals(2, state.tabs.size)
        assertNotEquals(previousId, state.selectedTabId)
        assertEquals("", state.tabs.last().address)
        assertEquals(1, state.addressFocusRequest)
        assertFalse(state.isOverviewVisible)
    }

    @Test
    fun `select and close intents keep a valid selection`() {
        val controller = BrowserTabsController()
        val second = controller.dispatch(BrowserTabsIntent.NewTab, tabId = null).selectedTabId
        controller.dispatch(BrowserTabsIntent.ShowOverview, tabId = null)

        val selected = controller.dispatch(BrowserTabsIntent.SelectTab, tabId = "tab-1")
        val closed = controller.dispatch(BrowserTabsIntent.CloseTab, tabId = "tab-1")

        assertEquals("tab-1", selected.selectedTabId)
        assertFalse(selected.isOverviewVisible)
        assertEquals(second, closed.selectedTabId)
        assertTrue(closed.tabs.any { it.id == closed.selectedTabId })
    }

    @Test
    fun `closing last tab creates a focused blank replacement`() {
        val controller = BrowserTabsController()

        val state = controller.dispatch(BrowserTabsIntent.CloseTab, tabId = "tab-1")

        assertEquals(1, state.tabs.size)
        assertEquals("", state.tabs.single().address)
        assertEquals(state.tabs.single().id, state.selectedTabId)
        assertEquals(1, state.addressFocusRequest)
    }

    @Test
    fun `tab count becomes infinity at one hundred`() {
        val controller = BrowserTabsController()
        repeat(99) {
            controller.dispatch(BrowserTabsIntent.NewTab, tabId = null)
        }

        assertEquals(100, controller.state.tabs.size)
        assertEquals("∞", controller.state.countLabel)
    }
}
