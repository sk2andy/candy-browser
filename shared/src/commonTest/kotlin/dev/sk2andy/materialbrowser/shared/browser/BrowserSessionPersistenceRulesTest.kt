package dev.sk2andy.materialbrowser.shared.browser

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.DEFAULT_BROWSER_PROFILE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowserSessionPersistenceRulesTest {
    @Test
    fun `snapshot strips private and remote profile tabs and runtime navigation state`() {
        val public = tab("regular").copy(
            isPinned = true, canGoBack = true, canGoForward = true, isLoading = true,
        )
        val private = tab("private").copy(isPrivate = true)
        val remote = tab("remote").copy(profileId = "synced:desktop")
        val snapshot = BrowserSessionPersistenceRules.snapshot(
            BrowserTabsState(
                tabs = listOf(public, private, remote),
                selectedTabId = private.id,
                profiles = listOf(
                    DEFAULT_BROWSER_PROFILE,
                    BrowserProfile("synced:desktop", "💻", syncedDeviceId = "desktop"),
                ),
            ),
        )
        val stored = snapshot.tabs.single()
        assertEquals("regular", snapshot.selectedTabId)
        assertTrue(stored.isPinned)
        assertFalse(stored.canGoBack)
        assertFalse(stored.canGoForward)
        assertFalse(stored.isLoading)
    }

    @Test
    fun `restore rejects invalid version duplicates addresses and unknown owners`() {
        val profiles = listOf(DEFAULT_BROWSER_PROFILE)
        assertNull(BrowserSessionPersistenceRules.restore(BrowserSessionSnapshot(version = 42), profiles))
        val restored = assertNotNull(BrowserSessionPersistenceRules.restore(
            BrowserSessionSnapshot(tabs = listOf(
                tab("valid"),
                tab("valid").copy(address = "https://duplicate.example/"),
                tab("unsafe").copy(address = "file:///private"),
                tab("unknown").copy(profileId = "missing"),
                tab("secret").copy(isPrivate = true),
            )),
            profiles,
        ))
        assertEquals(listOf("valid"), restored.tabs.map { it.id })
        assertEquals("valid", restored.selectedTabId)
    }

    @Test
    fun `private only snapshot restores regular blank tab in same local profile`() {
        val work = BrowserProfile("work", "💼")
        val snapshot = BrowserSessionPersistenceRules.snapshot(
            BrowserTabsState(
                tabs = listOf(tab("private").copy(isPrivate = true, profileId = "work")),
                selectedTabId = "private",
                profiles = listOf(DEFAULT_BROWSER_PROFILE, work),
                activeProfileId = "work",
            ),
        )
        assertTrue(snapshot.tabs.isEmpty())
        assertNull(snapshot.selectedTabId)
        val restored = assertNotNull(BrowserSessionPersistenceRules.restore(
            snapshot, listOf(DEFAULT_BROWSER_PROFILE, work),
        ))
        assertEquals("work", restored.activeProfileId)
        assertEquals("", restored.tabs.single().address)
        assertFalse(restored.tabs.single().isPrivate)
    }

    @Test
    fun `empty active profile fallback stays inside session tab bound`() {
        val restored = assertNotNull(BrowserSessionPersistenceRules.restore(
            snapshot = BrowserSessionSnapshot(
                tabs = List(1_000) { tab("tab-${it + 1}") },
                activeProfileId = "work",
            ),
            profiles = listOf(DEFAULT_BROWSER_PROFILE, BrowserProfile("work", "💼")),
        ))
        assertEquals(1_000, restored.tabs.size)
        assertEquals("work", restored.tabs.last().profileId)
        assertEquals("", restored.tabs.last().address)
        assertEquals(restored.tabs.last().id, restored.selectedTabId)
        assertEquals(1_000, restored.tabs.map { it.id }.distinct().size)
    }

    @Test
    fun `session restore precedes attachment and new identity stays unique`() {
        val controller = BrowserSessionController()
        assertTrue(controller.restoreSession(BrowserSessionSnapshot(
            tabs = listOf(tab("tab-8")), selectedTabId = "tab-8", activeProfileId = "candy",
        )))
        assertEquals("tab-9", controller.dispatchTabs(BrowserTabsIntent.NewTab, null).selectedTabId)
        assertTrue(controller.attach(object : BrowserEngineSessionPort {
            override val tabId = "tab-8"
            override fun execute(command: BrowserEngineCommand) = Unit
        }))
        assertFalse(controller.restoreSession(BrowserSessionSnapshot()))
    }

    @Test
    fun `restored numeric identity overflow wraps without reusing live tabs`() {
        val controller = BrowserSessionController()
        assertTrue(controller.restoreSession(BrowserSessionSnapshot(
            tabs = listOf(tab("tab-1"), tab("tab-9223372036854775806")),
            selectedTabId = "tab-1",
            activeProfileId = "candy",
        )))
        assertEquals("tab-9223372036854775807", controller.dispatchTabs(BrowserTabsIntent.NewTab, null).selectedTabId)
        assertEquals("tab-2", controller.dispatchTabs(BrowserTabsIntent.NewTab, null).selectedTabId)
        assertEquals(4, controller.state.tabs.map { it.id }.distinct().size)
        val restarted = BrowserSessionController()
        assertTrue(restarted.restoreSession(controller.persistenceSnapshot()))
        assertEquals("tab-3", restarted.dispatchTabs(BrowserTabsIntent.NewTab, null).selectedTabId)
    }

    @Test
    fun `private creation pin ordering and close protection have common owner`() {
        val controller = BrowserSessionController()
        val first = controller.state.selectedTabId
        val private = controller.newPrivateTab().tabs.last()
        assertTrue(private.isPrivate)
        assertEquals(1, controller.persistenceSnapshot().tabs.size)
        controller.dispatchTabs(BrowserTabsIntent.NewTab, null)
        controller.setTabPinned(private.id, true)
        assertEquals(private.id, controller.state.activeTabs.first().id)
        val before = controller.state
        assertEquals(before, controller.dispatchTabs(BrowserTabsIntent.CloseTab, private.id))
        controller.setTabPinned(private.id, false)
        controller.dispatchTabs(BrowserTabsIntent.CloseTab, private.id)
        assertTrue(controller.state.tabs.none { it.id == private.id })
        assertTrue(controller.state.tabs.any { it.id == first })
    }

    private fun tab(id: String): BrowserTabState = BrowserTabState(
        id = id, address = "https://example.com/", title = "Example",
    )
}
