package dev.sk2andy.materialbrowser.shared.browser

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.DEFAULT_PROFILE_ID
import dev.sk2andy.materialbrowser.browser.ProfileLockTrigger
import dev.sk2andy.materialbrowser.browser.ProfileProtection
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
    fun `duplicate selected tab copies page identity without runtime state`() {
        val source = BrowserTabState(
            id = "tab-7",
            address = "https://example.com/article",
            title = "Article",
            canGoBack = true,
            canGoForward = true,
            isLoading = true,
            isPinned = true,
            isPrivate = true,
            syncCandyId = "candy-source",
        )
        val controller = BrowserTabsController(
            initialState = BrowserTabsState(
                tabs = listOf(source),
                selectedTabId = source.id,
            ),
        )
        controller.dispatch(BrowserTabsIntent.ShowOverview, tabId = null)

        val state = controller.dispatch(BrowserTabsIntent.DuplicateTab, tabId = source.id)
        val duplicate = state.tabs.last()

        assertEquals(2, state.tabs.size)
        assertNotEquals(source.id, duplicate.id)
        assertEquals(duplicate.id, state.selectedTabId)
        assertEquals(source.address, duplicate.address)
        assertEquals("", duplicate.title)
        assertEquals(source.profileId, duplicate.profileId)
        assertTrue(duplicate.isPrivate)
        assertFalse(duplicate.isPinned)
        assertFalse(duplicate.canGoBack)
        assertFalse(duplicate.canGoForward)
        assertFalse(duplicate.isLoading)
        assertEquals(null, duplicate.syncCandyId)
        assertFalse(state.isOverviewVisible)
        assertEquals(0, state.addressFocusRequest)
    }

    @Test
    fun `duplicate rejects blank stale and non-selected sources`() {
        val controller = BrowserTabsController()
        val blankState = controller.state

        assertEquals(
            blankState,
            controller.dispatch(BrowserTabsIntent.DuplicateTab, tabId = blankState.selectedTabId),
        )

        controller.updateTab(
            tabId = blankState.selectedTabId,
            address = "https://example.com/",
            title = "Example",
            canGoBack = false,
            canGoForward = false,
            isLoading = false,
        )
        val secondId = controller.dispatch(BrowserTabsIntent.NewTab, tabId = null).selectedTabId
        val beforeRejectedDuplicates = controller.state

        assertEquals(
            beforeRejectedDuplicates,
            controller.dispatch(BrowserTabsIntent.DuplicateTab, tabId = "missing"),
        )
        assertEquals(
            beforeRejectedDuplicates,
            controller.dispatch(BrowserTabsIntent.DuplicateTab, tabId = blankState.selectedTabId),
        )
        assertEquals(secondId, controller.state.selectedTabId)
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
    fun `private creation from remote profile survives linked sync reconciliation`() {
        val controller = BrowserTabsController()
        val current = SyncProfile(
            deviceId = "phone",
            displayName = "iPhone",
            icon = SyncDeviceIconDescriptor("phone", 120),
            revision = 1,
            tabs = emptyList(),
            lastSeenAt = "2026-09-06T12:00:00Z",
        )
        val remote = current.copy(
            deviceId = "desktop",
            displayName = "Mac",
            tabs = listOf(SyncTab("remote-tab", 0, 0, null, true, false, "Candy", "https://example.com/")),
        )
        controller.reconcileSyncProfiles(listOf(current, remote), "phone", DEFAULT_PROFILE_ID, emptyList())
        val remoteState = controller.selectProfile("synced:desktop")
        controller.dispatch(BrowserTabsIntent.ShowOverview, null)

        val created = controller.newPrivateTab()
        val privateId = created.selectedTabId
        val privateTab = created.tabs.single { it.id == privateId }
        assertEquals(DEFAULT_PROFILE_ID, created.activeProfileId)
        assertEquals(DEFAULT_PROFILE_ID, privateTab.profileId)
        assertTrue(privateTab.isPrivate)
        assertEquals(null, privateTab.syncCandyId)
        assertEquals(remoteState.tabs.size + 1, created.tabs.size)
        assertEquals(remoteState.addressFocusRequest + 1, created.addressFocusRequest)
        assertFalse(created.isOverviewVisible)
        assertEquals(remoteState.selectedTabId, created.profiles.single { it.id == "synced:desktop" }.selectedTabId)

        val reconciled = controller.reconcileSyncProfiles(
            listOf(current, remote), "phone", DEFAULT_PROFILE_ID, emptyList(),
        )
        assertEquals(privateId, reconciled.selectedTabId)
        assertEquals(privateId, reconciled.profiles.single { it.id == DEFAULT_PROFILE_ID }.selectedTabId)
        assertEquals(privateTab, reconciled.tabs.single { it.id == privateId })
        assertTrue(BrowserSessionPersistenceRules.snapshot(reconciled).tabs.none { it.id == privateId })
        assertEquals(remoteState.selectedTabId, controller.selectProfile("synced:desktop").selectedTabId)
    }

    @Test
    fun `private fallback skips protected profiles without creating extra regular tab`() {
        val locked = BrowserProfile(
            "candy", "🍬", protection = ProfileProtection(ProfileLockTrigger.AppClosed),
        )
        val remote = BrowserProfile("remote", "💻", syncedDeviceId = "desktop")
        val source = BrowserTabState("remote-tab", "https://example.com/", "Candy", profileId = remote.id)
        val controller = BrowserTabsController(BrowserTabsState(
            tabs = listOf(source),
            selectedTabId = source.id,
            profiles = listOf(locked, BrowserProfile("work", "💼"), remote),
            activeProfileId = remote.id,
        ))

        val state = controller.newPrivateTab()

        assertEquals("work", state.activeProfileId)
        assertEquals(2, state.tabs.size)
        assertTrue(state.activeTabs.single().isPrivate)
        assertEquals(state.activeTabs.single().id, state.selectedTabId)
    }

    @Test
    fun `private creation retains active local profile instead of default fallback`() {
        val controller = BrowserTabsController()
        controller.createProfile(profileId = "work", emoji = "💼")

        val state = controller.newPrivateTab()

        assertEquals("work", state.activeProfileId)
        assertEquals("work", state.tabs.single { it.id == state.selectedTabId }.profileId)
        assertTrue(state.tabs.single { it.id == state.selectedTabId }.isPrivate)
    }

    @Test
    fun `private creation without eligible local profile leaves remote selection unchanged`() {
        val remote = BrowserProfile("remote", "💻", syncedDeviceId = "desktop")
        val source = BrowserTabState("remote-tab", "https://example.com/", "Candy", profileId = remote.id)
        val controller = BrowserTabsController(BrowserTabsState(
            tabs = listOf(source), selectedTabId = source.id, profiles = listOf(remote), activeProfileId = remote.id,
        ))
        val before = controller.state

        assertEquals(before, controller.newPrivateTab())
    }

    @Test
    fun `private creation at tab capacity does not switch remote profile or allocate tab`() {
        val remote = BrowserProfile("remote", "💻", syncedDeviceId = "desktop")
        val controller = BrowserTabsController(BrowserTabsState(
            tabs = List(1_000) { BrowserTabState("tab-$it", "", "", profileId = remote.id) },
            selectedTabId = "tab-0",
            profiles = listOf(BrowserProfile(DEFAULT_PROFILE_ID, "🍬"), remote),
            activeProfileId = remote.id,
        ))
        val before = controller.state

        assertEquals(before, controller.newPrivateTab())
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
