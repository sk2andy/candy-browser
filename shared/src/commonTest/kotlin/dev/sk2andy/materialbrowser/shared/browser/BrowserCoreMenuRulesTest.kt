package dev.sk2andy.materialbrowser.shared.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserCoreMenuRulesTest {
    @Test
    fun `core menu reflects selected tab navigation state`() {
        val state = BrowserTabsState(
            tabs = listOf(
                BrowserTabState(
                    id = "selected",
                    address = "https://example.com",
                    title = "Example",
                    canGoBack = true,
                    canGoForward = false,
                ),
            ),
            selectedTabId = "selected",
        )

        val items = BrowserCoreMenuRules.items(
            state = state,
            capabilities = BrowserCoreMenuCapabilities(),
        )

        assertEquals(
            listOf(
                BrowserCoreMenuAction.Back,
                BrowserCoreMenuAction.Forward,
                BrowserCoreMenuAction.Reload,
                BrowserCoreMenuAction.NewTab,
                BrowserCoreMenuAction.CloseTab,
                BrowserCoreMenuAction.ShowTabs,
            ),
            items.map(BrowserCoreMenuItem::action),
        )
        assertTrue(items.first { it.action == BrowserCoreMenuAction.Back }.enabled)
        assertFalse(items.first { it.action == BrowserCoreMenuAction.Forward }.enabled)
    }

    @Test
    fun `loading tab replaces reload with enabled stop`() {
        val state = BrowserTabsState(
            tabs = listOf(
                BrowserTabState(
                    id = "selected",
                    address = "https://example.com",
                    title = "Example",
                    isLoading = true,
                ),
            ),
            selectedTabId = "selected",
        )

        val item = BrowserCoreMenuRules.items(
            state = state,
            capabilities = BrowserCoreMenuCapabilities(),
        )[2]

        assertEquals(BrowserCoreMenuAction.Stop, item.action)
        assertEquals(BrowserCoreMenuLabelKey.StopLoading, item.labelKey)
        assertTrue(item.enabled)
    }

    @Test
    fun `capabilities omit unsupported platform entries`() {
        val state = BrowserTabsController().state

        val actions = BrowserCoreMenuRules.items(
            state = state,
            capabilities = BrowserCoreMenuCapabilities(
                supportsNewTab = false,
                supportsCloseTab = false,
                supportsTabOverview = false,
            ),
        ).map(BrowserCoreMenuItem::action)

        assertEquals(
            listOf(
                BrowserCoreMenuAction.Back,
                BrowserCoreMenuAction.Forward,
                BrowserCoreMenuAction.Reload,
            ),
            actions,
        )
    }
}
