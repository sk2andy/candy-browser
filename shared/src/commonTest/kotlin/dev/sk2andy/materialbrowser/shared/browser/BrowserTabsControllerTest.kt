package dev.sk2andy.materialbrowser.shared.browser

import dev.sk2andy.materialbrowser.browser.DEFAULT_PROFILE_ID
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconDefinition
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconDescriptor
import dev.sk2andy.materialbrowser.sync.SyncProfile
import dev.sk2andy.materialbrowser.sync.SyncTab
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
    fun `select in overview keeps overview visible`() {
        val controller = BrowserTabsController()
        controller.dispatch(BrowserTabsIntent.NewTab, tabId = null)
        controller.dispatch(BrowserTabsIntent.ShowOverview, tabId = null)

        val selected = controller.dispatch(
            BrowserTabsIntent.SelectTabInOverview,
            tabId = "tab-1",
        )

        assertEquals("tab-1", selected.selectedTabId)
        assertTrue(selected.isOverviewVisible)
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

    @Test
    fun `profile creation and switching use canonical profile tab ownership`() {
        val controller = BrowserTabsController()
        val candyTabId = controller.state.selectedTabId

        val created = controller.createProfile(profileId = "work", emoji = "💼")
        val workTabId = created.selectedTabId

        assertEquals("work", created.activeProfileId)
        assertEquals("work", created.tabs.single { it.id == workTabId }.profileId)
        assertEquals(workTabId, created.profiles.single { it.id == "work" }.selectedTabId)
        assertEquals(1, created.activeTabs.size)
        assertEquals("1", created.countLabel)

        val candy = controller.selectProfile(DEFAULT_PROFILE_ID)
        assertEquals(candyTabId, candy.selectedTabId)
        assertEquals(DEFAULT_PROFILE_ID, candy.activeProfileId)
        assertEquals(candyTabId, candy.profiles.single { it.id == DEFAULT_PROFILE_ID }.selectedTabId)

        val work = controller.selectProfile("work")
        assertEquals(workTabId, work.selectedTabId)
        assertEquals("work", work.activeProfileId)
    }

    @Test
    fun `closing the last tab only replaces it inside the active profile`() {
        val controller = BrowserTabsController()
        val candyTabId = controller.state.selectedTabId
        val workTabId = controller.createProfile(profileId = "work", emoji = "💼").selectedTabId

        val state = controller.dispatch(BrowserTabsIntent.CloseTab, tabId = workTabId)

        assertEquals("work", state.activeProfileId)
        assertEquals(2, state.tabs.size)
        assertTrue(state.tabs.any { it.id == candyTabId && it.profileId == DEFAULT_PROFILE_ID })
        assertTrue(state.tabs.any { it.id == state.selectedTabId && it.profileId == "work" })
    }

    @Test
    fun `profile creation and editing preserve canonical icon and isolation`() {
        val controller = BrowserTabsController()

        controller.createProfile(
            profileId = "work",
            emoji = " 💼 ",
            isolationEnabled = true,
        )
        assertEquals("💼", controller.state.profiles.last().emoji)
        assertTrue(controller.state.profiles.last().isolationEnabled)

        controller.updateProfileEmoji(profileId = "work", emoji = "⭐")
        controller.updateProfileIsolation(profileId = "work", enabled = false)

        assertEquals("⭐", controller.state.profiles.last().emoji)
        assertFalse(controller.state.profiles.last().isolationEnabled)
    }

    @Test
    fun `sync candy ids are unique and private tabs stay untracked`() {
        val controller = BrowserTabsController()
        val first = controller.state.selectedTabId
        val second = controller.dispatch(BrowserTabsIntent.NewTab, null).selectedTabId

        controller.assignSyncCandyId(first, "candy-1")
        controller.assignSyncCandyId(second, "candy-1")

        assertEquals("candy-1", controller.state.tabs.single { it.id == first }.syncCandyId)
        assertEquals(null, controller.state.tabs.single { it.id == second }.syncCandyId)
    }

    @Test
    fun `remote sync devices reuse canonical profiles and isolated tab runtime`() {
        val controller = BrowserTabsController()
        val remote = SyncProfile(
            deviceId = "desktop",
            displayName = "Mac",
            icon = SyncDeviceIconDescriptor("computer", 216),
            revision = 1,
            tabs = listOf(
                SyncTab("remote-tab", 0, 0, null, true, true, "Candy", "https://example.com/"),
            ),
            lastSeenAt = "2026-09-06T12:00:00Z",
        )

        val state = controller.reconcileSyncProfiles(
            syncedProfiles = listOf(remote),
            currentDeviceId = "phone",
            localProfileId = DEFAULT_PROFILE_ID,
            icons = listOf(SyncDeviceIconDefinition("computer", "🖥️", "Computer")),
        )

        val profile = state.profiles.single { it.id == "synced:desktop" }
        val tab = state.tabs.single { it.profileId == profile.id }
        assertEquals("🖥️", profile.emoji)
        assertTrue(profile.isolationEnabled)
        assertEquals("remote-tab", tab.syncCandyId)
        assertTrue(tab.isPinned)
    }

    @Test
    fun `current sync device restores tabs into linked local profile`() {
        val controller = BrowserTabsController()
        val current = SyncProfile(
            deviceId = "phone",
            displayName = "iPhone",
            icon = SyncDeviceIconDescriptor("phone", 120),
            revision = 2,
            tabs = listOf(
                SyncTab("restored-tab", 0, 0, null, true, true, "Candy", "https://example.com/"),
            ),
            lastSeenAt = "2026-09-06T12:00:00Z",
        )

        val state = controller.reconcileSyncProfiles(
            syncedProfiles = listOf(current),
            currentDeviceId = "phone",
            localProfileId = DEFAULT_PROFILE_ID,
            icons = emptyList(),
        )

        assertEquals("phone", state.profiles.single().linkedSyncDeviceId)
        val restored = state.tabs.single { it.syncCandyId == "restored-tab" }
        assertEquals(DEFAULT_PROFILE_ID, restored.profileId)
        assertEquals("https://example.com/", restored.address)
        assertTrue(restored.isPinned)
    }
}
