package dev.sk2andy.materialbrowser.shared.browser

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.isSynced

/** The shared host has no profile unlock presenter; protected profiles stay unavailable. */
object BrowserProfileManagementRules {
    fun canDeleteProfile(state: BrowserTabsState, profileId: String): Boolean {
        val profile = state.profiles.firstOrNull { it.id == profileId } ?: return false
        return !profile.isSynced && profile.linkedSyncDeviceId == null && profile.protection == null &&
            state.profiles.count { !it.isSynced } > 1 &&
            state.profiles.any { it.id != profileId && !it.isSynced && it.protection == null }
    }

    fun moveTargets(state: BrowserTabsState, tabId: String): List<BrowserProfile> {
        val tab = state.tabs.firstOrNull { it.id == tabId } ?: return emptyList()
        val source = state.profiles.firstOrNull { it.id == tab.profileId } ?: return emptyList()
        if (source.protection != null) return emptyList()
        if (state.activeTabs.size == 1 && tab.id == state.selectedTabId &&
            state.tabs.size >= BrowserSessionPersistenceRules.MAX_TABS
        ) return emptyList()
        return state.profiles.filter { target ->
            target.id != source.id && target.protection == null && (!tab.isPrivate || !target.isSynced)
        }
    }
}
