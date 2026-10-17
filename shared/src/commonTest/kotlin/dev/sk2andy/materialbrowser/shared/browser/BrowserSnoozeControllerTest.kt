package dev.sk2andy.materialbrowser.shared.browser

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.DEFAULT_BROWSER_PROFILE
import dev.sk2andy.materialbrowser.data.SnoozedTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BrowserSnoozeControllerTest {
    @Test
    fun `snoozing selected last tab closes engine and preserves atomic deferred record`() {
        val controller = BrowserSessionController()
        val id = controller.state.selectedTabId
        val commands = mutableListOf<BrowserEngineCommandType>()
        controller.attach(object : BrowserEngineSessionPort {
            override val tabId = id
            override fun execute(command: BrowserEngineCommand) { commands += command.type }
        })
        assertTrue(controller.snoozeTab(id, 200, 100))
        assertEquals(listOf(BrowserEngineCommandType.Close), commands)
        assertEquals("", controller.state.tabs.single().address)
        assertEquals(id, controller.state.snoozedTabs.single().tab.id)
        assertEquals(1, controller.persistenceSnapshot().snoozedTabs.size)
        assertTrue(controller.openSnoozedTabNow(id, 150))
        assertEquals(id, controller.state.selectedTabId)
        assertTrue(controller.state.snoozedTabs.isEmpty())
    }

    @Test
    fun `private and synced profile tabs cannot snooze`() {
        val controller = BrowserSessionController()
        val private = controller.newPrivateTab().selectedTabId
        assertFalse(controller.canSnoozeTab(private))
        assertFalse(controller.snoozeTab(private, 200, 100))
        val remote = BrowserProfile("synced:desktop", "💻", syncedDeviceId = "desktop")
        val remoteController = BrowserSessionController(BrowserTabsController(BrowserTabsState(
            tabs = listOf(BrowserTabState("remote", "https://example.com/", "Example", profileId = remote.id)),
            selectedTabId = "remote", profiles = listOf(remote), activeProfileId = remote.id,
        )))
        assertFalse(remoteController.canSnoozeTab("remote"))
        assertFalse(remoteController.snoozeTab("remote", 200, 100))
    }

    @Test
    fun `pinned snooze restores pin and profile while resetting native navigation`() {
        val controller = BrowserSessionController()
        val id = controller.createProfile("work", "💼").selectedTabId
        controller.setTabPinned(id, true)
        assertTrue(controller.snoozeTab(id, 200, 100))
        assertTrue(controller.restoreDueSnoozedTabs(199).isEmpty())
        assertEquals(listOf(id), controller.restoreDueSnoozedTabs(200))
        val restored = controller.state.tabs.first { it.id == id }
        assertTrue(restored.isPinned)
        assertEquals("work", restored.profileId)
        assertFalse(restored.isLoading)
        assertFalse(restored.canGoBack)
        assertTrue(controller.restoreDueSnoozedTabs(201).isEmpty())
    }

    @Test
    fun `reschedule delete and stale requests preserve pending identity`() {
        val controller = BrowserSessionController()
        val id = controller.state.selectedTabId
        assertFalse(controller.snoozeTab(id, 100, 100))
        assertTrue(controller.snoozeTab(id, 200, 100))
        assertFalse(controller.rescheduleSnoozedTab(id, 100, 100))
        assertFalse(controller.rescheduleSnoozedTab("stale", 300, 100))
        assertTrue(controller.rescheduleSnoozedTab(id, 300, 100))
        assertEquals(300L, controller.state.snoozedTabs.single().wakeAtMillis)
        assertTrue(controller.deleteSnoozedTab(id))
        assertFalse(controller.deleteSnoozedTab(id))
        assertFalse(controller.openSnoozedTabNow(id, 400))
    }

    @Test
    fun `capacity pressure leaves due tab pending until room exists`() {
        val controller = BrowserSessionController()
        val pending = stored("snoozed", 200)
        assertTrue(controller.restoreSession(BrowserSessionSnapshot(
            tabs = List(1_000) { BrowserTabState("tab-$it", "", "") },
            selectedTabId = "tab-0", activeProfileId = "candy", snoozedTabs = listOf(pending),
        )))
        assertTrue(controller.restoreDueSnoozedTabs(200).isEmpty())
        assertEquals(1, controller.state.snoozedTabs.size)
        controller.dispatchTabs(BrowserTabsIntent.CloseTab, "tab-999")
        assertEquals(listOf("snoozed"), controller.restoreDueSnoozedTabs(200))
        assertEquals(1_000, controller.state.tabs.size)
    }

    @Test
    fun `restored synthetic blank cannot collide with deferred identity`() {
        val controller = BrowserSessionController()
        assertTrue(controller.restoreSession(BrowserSessionSnapshot(
            tabs = emptyList(), activeProfileId = "candy", snoozedTabs = listOf(stored("tab-1", 200)),
        )))
        assertEquals("tab-2", controller.state.selectedTabId)
        assertEquals("tab-1", controller.state.snoozedTabs.single().tab.id)
        assertEquals("tab-3", controller.dispatchTabs(BrowserTabsIntent.NewTab, null).selectedTabId)
    }

    @Test
    fun `scheduled persistence excludes private remote duplicate and unsafe records`() {
        val valid = stored("valid", 200)
        val state = BrowserTabsState(
            tabs = listOf(BrowserTabState("active", "", "")), selectedTabId = "active",
            profiles = listOf(DEFAULT_BROWSER_PROFILE, BrowserProfile("remote", "💻", syncedDeviceId = "desktop")),
            snoozedTabs = listOf(
                valid, valid,
                stored("private", 200).let { it.copy(tab = it.tab.copy(isIncognito = true)) },
                stored("remote-tab", 200).let { it.copy(tab = it.tab.copy(profileId = "remote")) },
                stored("active", 200),
                stored("unsafe", 200).let { it.copy(tab = it.tab.copy(url = "file:///private")) },
            ),
        )
        assertEquals(listOf("valid"), BrowserSessionPersistenceRules.snapshot(state).snoozedTabs.map { it.tab.id })
    }

    @Test
    fun `stored scalar bridge validates untrusted metadata`() {
        assertEquals(null, BrowserSnoozeRules.storedTab("../bad\n", "https://example.com/", "Title", "candy", false, 200, 100))
        assertEquals(null, BrowserSnoozeRules.storedTab("id", "javascript:alert(1)", "Title", "candy", false, 200, 100))
        assertEquals(null, BrowserSnoozeRules.storedTab("id", "https://example.com/", "Title", "candy", false, 0, 100))
        assertNotNull(BrowserSnoozeRules.storedTab("id", "about:blank", "", "candy", true, 200, 100))
    }

    private fun stored(id: String, wakeAt: Long): SnoozedTab = SnoozedTab(
        tab = BrowserTab(id, 100, title = "Example", url = "https://example.com/"),
        wakeAtMillis = wakeAt, createdAtMillis = 100,
    )
}
