package dev.sk2andy.materialbrowser.shared.browser

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.DEFAULT_BROWSER_PROFILE
import dev.sk2andy.materialbrowser.browser.isSynced
import dev.sk2andy.materialbrowser.data.SnoozedTab

/** Versioned durable projection. Engine history, previews and every private tab stay memory-only. */
data class BrowserSessionSnapshot(
    val version: Int = BrowserSessionPersistenceRules.CURRENT_VERSION,
    val tabs: List<BrowserTabState> = emptyList(),
    val selectedTabId: String? = null,
    val activeProfileId: String? = null,
    val snoozedTabs: List<SnoozedTab> = emptyList(),
)

object BrowserSessionPersistenceRules {
    const val CURRENT_VERSION = 1
    const val MAX_TABS = 1_000
    private const val MAX_ID_LENGTH = 256
    private const val MAX_TITLE_LENGTH = 4_096

    fun snapshot(state: BrowserTabsState): BrowserSessionSnapshot {
        val profiles = localProfiles(state.profiles)
        val tabs = persistentTabs(state.tabs, profiles)
        val activeProfileId = state.activeProfileId.takeIf { id -> profiles.any { it.id == id } }
            ?: profiles.first().id
        val selected = tabs.firstOrNull { it.id == state.selectedTabId }
            ?: tabs.lastOrNull { it.profileId == activeProfileId }
        return BrowserSessionSnapshot(
            tabs = tabs,
            selectedTabId = selected?.id,
            activeProfileId = selected?.profileId ?: activeProfileId,
            snoozedTabs = persistentSnoozedTabs(state.snoozedTabs, profiles, tabs),
        )
    }

    fun restore(
        snapshot: BrowserSessionSnapshot,
        profiles: List<BrowserProfile>,
    ): BrowserTabsState? {
        if (snapshot.version != CURRENT_VERSION) return null
        val localProfiles = localProfiles(profiles)
        val tabs = persistentTabs(snapshot.tabs, localProfiles)
        val requestedProfile = snapshot.activeProfileId.takeIf { id ->
            localProfiles.any { it.id == id }
        } ?: localProfiles.first().id
        val selected = tabs.firstOrNull {
            it.id == snapshot.selectedTabId && it.profileId == requestedProfile
        } ?: tabs.lastOrNull { it.profileId == requestedProfile }
        val fallbackId = generateSequence(1) { it + 1 }.map { "tab-$it" }
            .first { id -> tabs.none { it.id == id } && snapshot.snoozedTabs.none { it.tab.id == id } }
        val fallback = BrowserTabState(
            id = fallbackId,
            address = "",
            title = "",
            profileId = requestedProfile,
        )
        val restoredTabs = if (selected == null) tabs.take(MAX_TABS - 1) + fallback else tabs
        val selectedId = selected?.id ?: fallback.id
        return BrowserTabsState(
            tabs = restoredTabs,
            selectedTabId = selectedId,
            profiles = localProfiles.map { profile ->
                val remembered = restoredTabs.firstOrNull {
                    it.id == profile.selectedTabId && it.profileId == profile.id
                } ?: restoredTabs.lastOrNull { it.profileId == profile.id }
                profile.copy(
                    selectedTabId = if (profile.id == requestedProfile) selectedId else remembered?.id,
                )
            },
            activeProfileId = requestedProfile,
            snoozedTabs = persistentSnoozedTabs(snapshot.snoozedTabs, localProfiles, restoredTabs),
        )
    }

    private fun persistentSnoozedTabs(
        snoozedTabs: List<SnoozedTab>,
        profiles: List<BrowserProfile>,
        tabs: List<BrowserTabState>,
    ): List<SnoozedTab> {
        val profileIds = profiles.mapTo(hashSetOf(), BrowserProfile::id)
        val activeIds = tabs.mapTo(hashSetOf(), BrowserTabState::id)
        return snoozedTabs.asSequence()
            .filter { !it.tab.isIncognito && it.tab.profileId in profileIds && it.tab.id !in activeIds }
            .mapNotNull { item -> BrowserSnoozeRules.storedTab(
                id = item.tab.id,
                address = item.tab.url,
                title = item.tab.title,
                profileId = item.tab.profileId,
                isPinned = item.tab.isPinned,
                wakeAtMillis = item.wakeAtMillis,
                createdAtMillis = item.createdAtMillis,
            ) }
            .distinctBy { it.tab.id }
            .sortedWith(compareBy({ it.wakeAtMillis }, { it.tab.id }))
            .take(MAX_TABS)
            .toList()
    }

    private fun localProfiles(profiles: List<BrowserProfile>): List<BrowserProfile> = profiles
        .filterNot(BrowserProfile::isSynced)
        .ifEmpty { listOf(DEFAULT_BROWSER_PROFILE) }

    private fun persistentTabs(
        tabs: List<BrowserTabState>,
        profiles: List<BrowserProfile>,
    ): List<BrowserTabState> {
        val profileIds = profiles.mapTo(hashSetOf(), BrowserProfile::id)
        return tabs.asSequence()
            .filterNot(BrowserTabState::isPrivate)
            .filter { it.profileId in profileIds && validId(it.id) }
            .mapNotNull { tab ->
                val address = if (tab.address.isEmpty()) "" else {
                    BrowserUrlRules.normalizeHttpUrl(tab.address)?.value ?: return@mapNotNull null
                }
                tab.copy(
                    address = address,
                    title = tab.title.take(MAX_TITLE_LENGTH),
                    canGoBack = false,
                    canGoForward = false,
                    isLoading = false,
                    syncCandyId = tab.syncCandyId?.takeIf { id ->
                        dev.sk2andy.materialbrowser.sync.SyncTabRules.isValidCandyId(id)
                    },
                )
            }
            .distinctBy(BrowserTabState::id)
            .take(MAX_TABS)
            .toList()
    }

    private fun validId(id: String): Boolean = id.isNotBlank() && id.length <= MAX_ID_LENGTH &&
        id.none { it.code < 32 || it.code == 127 }
}
