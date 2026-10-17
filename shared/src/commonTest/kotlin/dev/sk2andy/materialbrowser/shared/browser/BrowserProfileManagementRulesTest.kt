package dev.sk2andy.materialbrowser.shared.browser

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.DEFAULT_BROWSER_PROFILE
import dev.sk2andy.materialbrowser.browser.ProfileLockTrigger
import dev.sk2andy.materialbrowser.browser.ProfileProtection
import dev.sk2andy.materialbrowser.data.SnoozedTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserProfileManagementRulesTest {
    private val work = BrowserProfile("work", "💼")
    private val remote = BrowserProfile("remote", "💻", syncedDeviceId = "remote-device")

    @Test
    fun closeAllPreservesPinsAndOtherProfiles() {
        val controller = controller(listOf(
            tab("pin").copy(isPinned = true), tab("regular"), tab("private").copy(isPrivate = true),
            tab("other", "work"),
        ), selected = "private")
        val state = controller.closeAllTabs()
        assertEquals(listOf("pin", "other"), state.tabs.map { it.id })
        assertEquals("pin", state.selectedTabId)
        assertEquals("candy", state.activeProfileId)
    }

    @Test
    fun closingOnlyPrivateTabCreatesRegularBlankReplacement() {
        val controller = controller(listOf(tab("private").copy(isPrivate = true)))
        val state = controller.closeAllTabs()
        assertEquals(1, state.tabs.size)
        assertEquals("", state.tabs.single().address)
        assertFalse(state.tabs.single().isPrivate)
        assertTrue(controller.persistenceSnapshotForTest().tabs.none { it.id == "private" })
    }

    @Test
    fun movingLastPrivateTabPreservesPrivacyAndLeavesPrivateReplacementInSource() {
        val controller = controller(listOf(tab("private").copy(isPrivate = true)))
        val state = controller.moveTabToProfile("private", "work")
        assertEquals("candy", state.activeProfileId)
        assertTrue(state.tabs.single { it.id == "private" }.isPrivate)
        assertEquals("work", state.tabs.single { it.id == "private" }.profileId)
        assertTrue(state.activeTabs.single().isPrivate)
        assertEquals("", state.activeTabs.single().address)
        assertEquals(state.activeTabs.single().id, state.selectedTabId)
        assertTrue(controller.persistenceSnapshotForTest().tabs.isEmpty())
    }

    @Test
    fun privateRemoteAndProtectedTargetsCannotBeSelected() {
        val protected = BrowserProfile("locked", "🔒", protection = ProfileProtection(ProfileLockTrigger.AppClosed))
        val controller = controller(
            tabs = listOf(tab("private").copy(isPrivate = true)),
            profiles = listOf(DEFAULT_BROWSER_PROFILE, work, remote, protected),
        )
        val before = controller.state
        assertEquals(before, controller.moveTabToProfile("private", "remote"))
        assertEquals(before, controller.moveTabToProfile("private", "locked"))
        assertEquals(listOf("work"), BrowserProfileManagementRules.moveTargets(before, "private").map { it.id })
    }

    @Test
    fun pinnedMoveKeepsTargetPinOrderingAndClearsOldLocalSyncIdentity() {
        val controller = controller(listOf(
            tab("pin").copy(isPinned = true, syncCandyId = "old-sync"),
            tab("remaining"), tab("target", "work"),
        ), selected = "pin")
        val state = controller.moveTabToProfile("pin", "work")
        assertEquals(listOf("pin", "target"), state.tabs.filter { it.profileId == "work" }.map { it.id })
        assertEquals(null, state.tabs.single { it.id == "pin" }.syncCandyId)
        assertEquals("remaining", state.selectedTabId)
        assertEquals("candy", state.activeProfileId)
    }

    @Test
    fun deleteRejectsLastLocalRemoteBoundAndProtectedProfiles() {
        val sole = BrowserTabsController()
        assertEquals(sole.state, sole.deleteProfile("candy"))
        val linked = BrowserProfile("linked", "🔗", linkedSyncDeviceId = "this-device")
        val protected = BrowserProfile("locked", "🔒", protection = ProfileProtection(ProfileLockTrigger.AppClosed))
        val controller = controller(listOf(tab("regular")), profiles = listOf(
            DEFAULT_BROWSER_PROFILE, work, remote, linked, protected,
        ))
        val before = controller.state
        for (id in listOf("remote", "linked", "locked", "missing")) {
            assertEquals(before, controller.deleteProfile(id))
        }
    }

    @Test
    fun deleteMovesOpenPinnedPrivateAndScheduledTabsToSafeLocalFallback() {
        val state = BrowserTabsState(
            tabs = listOf(tab("selected", "work").copy(isPrivate = true), tab("pin", "work").copy(isPinned = true), tab("normal")),
            selectedTabId = "selected",
            profiles = listOf(DEFAULT_BROWSER_PROFILE, work, remote),
            activeProfileId = "work",
            snoozedTabs = listOf(SnoozedTab(BrowserTab("scheduled", 0, profileId = "work", url = "https://example.com"), 20, 10)),
        )
        val controller = BrowserTabsController(state)
        val result = controller.deleteProfile("work")
        assertEquals("candy", result.activeProfileId)
        assertEquals("selected", result.selectedTabId)
        assertEquals("pin", result.activeTabs.first().id)
        assertTrue(result.tabs.single { it.id == "selected" }.isPrivate)
        assertTrue(result.tabs.all { it.profileId == "candy" })
        assertEquals("candy", result.snoozedTabs.single().tab.profileId)
        assertTrue(result.profiles.none { it.id == "work" })
    }

    @Test
    fun deletingInactiveProfileWhileRemoteIsActiveNeverMovesPrivateDataIntoRemote() {
        val controller = controller(
            tabs = listOf(tab("remote-tab", "remote"), tab("private", "work").copy(isPrivate = true)),
            selected = "remote-tab",
            profiles = listOf(DEFAULT_BROWSER_PROFILE, work, remote),
            activeProfile = "remote",
        )
        val result = controller.deleteProfile("work")
        assertEquals("remote", result.activeProfileId)
        assertEquals("remote-tab", result.selectedTabId)
        assertEquals("candy", result.tabs.single { it.id == "private" }.profileId)
    }

    @Test
    fun managementClosesOnlyAffectedEngineSessions() {
        val controller = BrowserSessionController(controller(listOf(tab("moving"), tab("remaining"))))
        val commands = mutableListOf<BrowserEngineCommandType>()
        assertTrue(controller.attach(object : BrowserEngineSessionPort {
            override val tabId = "moving"
            override fun execute(command: BrowserEngineCommand) { commands += command.type }
        }))
        controller.moveTabToProfile("moving", "work")
        assertEquals(BrowserEngineCommandType.Close, commands.last())
        assertTrue(controller.attachedTabIds.isEmpty())
        assertEquals("work", controller.state.tabs.single { it.id == "moving" }.profileId)
        assertEquals("remaining", controller.state.selectedTabId)
    }

    @Test
    fun moveCannotExceedTabLimitWhenSourceNeedsReplacement() {
        val tabs = listOf(tab("source")) + List(BrowserSessionPersistenceRules.MAX_TABS - 1) {
            tab("target-$it", "work")
        }
        val controller = controller(tabs)
        val before = controller.state
        assertTrue(BrowserProfileManagementRules.moveTargets(before, "source").isEmpty())
        assertEquals(before, controller.moveTabToProfile("source", "work"))
    }

    @Test
    fun deletingProfileWithoutUnlockedLocalFallbackKeepsAllState() {
        val locked = DEFAULT_BROWSER_PROFILE.copy(protection = ProfileProtection(ProfileLockTrigger.AppClosed))
        val controller = controller(listOf(tab("selected", "work")), profiles = listOf(locked, work), activeProfile = "work")
        assertEquals(controller.state, controller.deleteProfile("work"))
    }

    @Test
    fun clearingRemovedProfileSettingsPreservesOtherPrivateOverrides() {
        val preferences = BrowserSitePreferenceController()
        val removed = tab("private-work", "work").copy(isPrivate = true)
        val other = tab("private-candy").copy(isPrivate = true)
        preferences.setDesktop(removed, true)
        preferences.setDesktop(other, true)
        preferences.setDesktop(tab("regular", "work"), true)
        preferences.removeProfile("work", listOf(removed.id))
        assertFalse(preferences.isDesktop(removed))
        assertTrue(preferences.isDesktop(other))
        assertTrue(preferences.persistentEntries().isEmpty())
    }

    private fun BrowserTabsController.persistenceSnapshotForTest() = BrowserSessionPersistenceRules.snapshot(state)

    private fun controller(
        tabs: List<BrowserTabState>,
        selected: String = tabs.first().id,
        profiles: List<BrowserProfile> = listOf(DEFAULT_BROWSER_PROFILE, work),
        activeProfile: String = "candy",
    ) = BrowserTabsController(BrowserTabsState(tabs, selected, profiles, activeProfile))

    private fun tab(id: String, profileId: String = "candy") = BrowserTabState(
        id, "https://example.com/", "Example", profileId = profileId,
    )
}
