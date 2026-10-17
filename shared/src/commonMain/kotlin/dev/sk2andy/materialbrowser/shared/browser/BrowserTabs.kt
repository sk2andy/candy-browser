package dev.sk2andy.materialbrowser.shared.browser

import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserProfileDraft
import dev.sk2andy.materialbrowser.browser.BrowserProfileRules
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.DEFAULT_BROWSER_PROFILE
import dev.sk2andy.materialbrowser.browser.DEFAULT_PROFILE_ID
import dev.sk2andy.materialbrowser.browser.MAX_PROFILES
import dev.sk2andy.materialbrowser.browser.SyncedProfileRuntimeRules
import dev.sk2andy.materialbrowser.browser.isSynced
import dev.sk2andy.materialbrowser.data.SnoozeMutationRules
import dev.sk2andy.materialbrowser.data.SnoozeRestoreRules
import dev.sk2andy.materialbrowser.data.SnoozeRules
import dev.sk2andy.materialbrowser.data.SnoozedTab
import dev.sk2andy.materialbrowser.data.TabDeletionRules
import dev.sk2andy.materialbrowser.data.TabPinningRules
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconDefinition
import dev.sk2andy.materialbrowser.sync.SyncProfile

data class BrowserTabState(
    val id: String,
    val address: String,
    val title: String,
    val profileId: String = DEFAULT_PROFILE_ID,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoading: Boolean = false,
    val isPinned: Boolean = false,
    val isPrivate: Boolean = false,
    val syncCandyId: String? = null,
)

data class BrowserTabsState(
    val tabs: List<BrowserTabState>,
    val selectedTabId: String,
    val profiles: List<BrowserProfile> = listOf(DEFAULT_BROWSER_PROFILE),
    val activeProfileId: String = DEFAULT_PROFILE_ID,
    val isOverviewVisible: Boolean = false,
    val addressFocusRequest: Long = 0,
    val snoozedTabs: List<SnoozedTab> = emptyList(),
) {
    val activeTabs: List<BrowserTabState>
        get() = tabs.filter { it.profileId == activeProfileId }

    val countLabel: String
        get() = BrowserTabsRules.countLabel(activeTabs.size)
}

enum class BrowserTabsIntent {
    NewTab,
    DuplicateTab,
    SelectTab,
    SelectTabInOverview,
    CloseTab,
    ShowOverview,
    HideOverview,
}

object BrowserTabsRules {
    private const val INFINITE_TAB_COUNT = 100

    fun countLabel(tabCount: Int): String = if (tabCount >= INFINITE_TAB_COUNT) {
        "∞"
    } else {
        tabCount.coerceAtLeast(0).toString()
    }
}

class BrowserTabsController {
    var state: BrowserTabsState
        private set

    private var nextTabNumber: Long

    constructor() : this(
        initialState = BrowserTabsState(
            tabs = listOf(
                BrowserTabState(
                    id = tabId(1),
                    address = "",
                    title = "",
                ),
            ),
            selectedTabId = tabId(1),
        ),
    )

    constructor(initialState: BrowserTabsState) {
        require(initialState.tabs.isNotEmpty())
        require(initialState.tabs.any { it.id == initialState.selectedTabId })
        require(initialState.tabs.map(BrowserTabState::id).distinct().size == initialState.tabs.size)
        require(initialState.profiles.isNotEmpty())
        require(initialState.profiles.map(BrowserProfile::id).distinct().size == initialState.profiles.size)
        require(initialState.profiles.any { it.id == initialState.activeProfileId })
        require(
            initialState.tabs.first { it.id == initialState.selectedTabId }.profileId ==
                initialState.activeProfileId,
        )
        require(initialState.tabs.all { tab -> initialState.profiles.any { it.id == tab.profileId } })
        state = initialState
        nextTabNumber = nextAvailableTabNumber(initialState.tabs)
    }

    fun dispatch(intent: BrowserTabsIntent, tabId: String?): BrowserTabsState = when (intent) {
        BrowserTabsIntent.NewTab -> addBlankTab()
        BrowserTabsIntent.DuplicateTab -> duplicateSelectedTab(tabId)
        BrowserTabsIntent.SelectTab -> selectTab(tabId)
        BrowserTabsIntent.SelectTabInOverview -> selectTab(tabId, hideOverview = false)
        BrowserTabsIntent.CloseTab -> closeTab(tabId)
        BrowserTabsIntent.ShowOverview -> update(state.copy(isOverviewVisible = true))
        BrowserTabsIntent.HideOverview -> update(state.copy(isOverviewVisible = false))
    }

    fun updateTab(
        tabId: String,
        address: String,
        title: String,
        canGoBack: Boolean,
        canGoForward: Boolean,
        isLoading: Boolean,
    ): BrowserTabsState {
        if (state.tabs.none { it.id == tabId }) return state
        return update(
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) {
                        tab.copy(
                            address = address,
                            title = title,
                            canGoBack = canGoBack,
                            canGoForward = canGoForward,
                            isLoading = isLoading,
                        )
                    } else {
                        tab
                    }
                },
            ),
        )
    }

    fun newPrivateTab(): BrowserTabsState {
        if (state.tabs.size >= BrowserSessionPersistenceRules.MAX_TABS) return state
        val profile = state.profiles.firstOrNull {
            it.id == state.activeProfileId && !it.isSynced && it.protection == null
        } ?: state.profiles.firstOrNull { !it.isSynced && it.protection == null } ?: return state
        return addBlankTab(isPrivate = true, profileId = profile.id)
    }

    fun setTabPinned(tabId: String, pinned: Boolean): BrowserTabsState {
        val target = state.tabs.firstOrNull { it.id == tabId } ?: return state
        if (target.isPinned == pinned) return state
        val sourceTabs = state.tabs.filter { it.profileId == target.profileId }
        val byId = sourceTabs.associateBy(BrowserTabState::id)
        val profileTabs = TabPinningRules.withPinnedState(
            tabs = sourceTabs.map { it.toBrowserTab() },
            tabId = tabId,
            isPinned = pinned,
        ).map { tab -> byId.getValue(tab.id).copy(isPinned = tab.isPinned) }.iterator()
        return update(
            state.copy(tabs = state.tabs.map {
                if (it.profileId == target.profileId) profileTabs.next() else it
            }),
        )
    }

    fun restoreSession(snapshot: BrowserSessionSnapshot): Boolean {
        val restored = BrowserSessionPersistenceRules.restore(snapshot, state.profiles) ?: return false
        state = restored
        nextTabNumber = nextAvailableTabNumber(restored.tabs)
        return true
    }

    fun selectAdjacentTab(target: BrowserTabSwitchTarget): BrowserTabsState {
        val activeTabs = state.activeTabs
        val selectedIndex = activeTabs.indexOfFirst { it.id == state.selectedTabId }
        val targetIndex = when (target) {
            BrowserTabSwitchTarget.Stay -> return state
            BrowserTabSwitchTarget.Previous -> selectedIndex - 1
            BrowserTabSwitchTarget.Next -> selectedIndex + 1
        }
        val targetTab = activeTabs.getOrNull(targetIndex) ?: return state
        return selectTab(targetTab.id)
    }

    fun createProfile(
        profileId: String,
        emoji: String,
        isolationEnabled: Boolean = false,
        isolationSupported: Boolean = true,
    ): BrowserTabsState {
        if (
            state.profiles.size >= MAX_PROFILES ||
            state.profiles.any { it.id == profileId.trim() }
        ) {
            return state
        }
        val profile = BrowserProfileRules.create(
            draft = BrowserProfileDraft(
                emoji = emoji,
                isolationRequested = isolationEnabled,
            ),
            profileId = profileId,
            isolationSupported = isolationSupported,
        ) ?: return state
        val rememberedProfiles = rememberSelectedTab(
            profiles = state.profiles,
            profileId = state.activeProfileId,
            tabId = state.selectedTabId,
        )
        val tab = blankTab(profileId = profile.id)
        return update(
            state.copy(
                tabs = state.tabs + tab,
                selectedTabId = tab.id,
                profiles = rememberedProfiles + profile.copy(selectedTabId = tab.id),
                activeProfileId = profile.id,
                addressFocusRequest = state.addressFocusRequest + 1,
            ),
        )
    }

    fun updateProfileEmoji(profileId: String, emoji: String): BrowserTabsState {
        val index = state.profiles.indexOfFirst { it.id == profileId }
        if (index < 0) return state
        val updatedProfile = BrowserProfileRules.updateEmoji(
            profile = state.profiles[index],
            emoji = emoji,
        ) ?: return state
        return update(
            state.copy(
                profiles = state.profiles.toMutableList().also { it[index] = updatedProfile },
            ),
        )
    }

    fun updateProfileIsolation(
        profileId: String,
        enabled: Boolean,
        isolationSupported: Boolean = true,
    ): BrowserTabsState {
        val index = state.profiles.indexOfFirst { it.id == profileId }
        if (index < 0) return state
        val updatedProfile = BrowserProfileRules.updateIsolation(
            profile = state.profiles[index],
            enabled = enabled,
            isolationSupported = isolationSupported,
        ) ?: return state
        return update(
            state.copy(
                profiles = state.profiles.toMutableList().also { it[index] = updatedProfile },
            ),
        )
    }

    fun closeAllTabs(): BrowserTabsState {
        val profile = state.profiles.firstOrNull { it.id == state.activeProfileId } ?: return state
        if (profile.protection != null) return state
        val closingIds = TabDeletionRules.deletableTabIds(state.activeTabs.map { it.toBrowserTab() })
        if (closingIds.isEmpty()) return state
        val remaining = state.tabs.filterNot { it.id in closingIds }
        val active = remaining.filter { it.profileId == state.activeProfileId }
        if (active.isEmpty()) return addBlankTabAfterClosingLast(remaining)
        val selectedId = state.selectedTabId.takeUnless { it in closingIds } ?: active.last().id
        return update(state.copy(
            tabs = remaining,
            selectedTabId = selectedId,
            profiles = rememberSelectedTab(state.profiles, state.activeProfileId, selectedId),
        ))
    }

    fun moveTabToProfile(tabId: String, profileId: String): BrowserTabsState {
        val tab = state.tabs.firstOrNull { it.id == tabId } ?: return state
        val target = BrowserProfileManagementRules.moveTargets(state, tabId)
            .firstOrNull { it.id == profileId } ?: return state
        val sourceIndex = state.activeTabs.indexOfFirst { it.id == tabId }
        val moved = tab.copy(
            profileId = target.id,
            canGoBack = false,
            canGoForward = false,
            isLoading = false,
            syncCandyId = tab.syncCandyId.takeIf {
                !tab.isPrivate && (target.isSynced || target.linkedSyncDeviceId != null)
            },
        )
        var tabs = state.tabs.map { if (it.id == tabId) moved else it }
        val orderedTargetTabs = tabs.filter { it.profileId == profileId }
            .sortedByDescending(BrowserTabState::isPinned).iterator()
        tabs = tabs.map { if (it.profileId == profileId) orderedTargetTabs.next() else it }
        val sourceTabs = tabs.filter { it.profileId == tab.profileId }
        var selectedId = state.selectedTabId
        if (tabId == selectedId) {
            selectedId = sourceTabs.getOrNull(sourceIndex.coerceAtMost(sourceTabs.lastIndex))?.id
                ?: blankTab(tab.profileId).copy(isPrivate = tab.isPrivate).also { tabs = tabs + it }.id
        }
        val profiles = state.profiles.map { profile ->
            val remembered = profile.selectedTabId?.takeIf { id ->
                tabs.any { it.id == id && it.profileId == profile.id }
            } ?: tabs.lastOrNull { it.profileId == profile.id }?.id
            profile.copy(selectedTabId = if (profile.id == state.activeProfileId) selectedId else remembered)
        }
        return update(state.copy(tabs = tabs, selectedTabId = selectedId, profiles = profiles))
    }

    fun deleteProfile(profileId: String): BrowserTabsState {
        if (!BrowserProfileManagementRules.canDeleteProfile(state, profileId)) return state
        val fallback = state.profiles.firstOrNull {
            it.id == state.activeProfileId && it.id != profileId && !it.isSynced && it.protection == null
        } ?: state.profiles.first { it.id != profileId && !it.isSynced && it.protection == null }
        val tabs = state.tabs.map { tab ->
            if (tab.profileId == profileId) tab.copy(
                profileId = fallback.id,
                canGoBack = false,
                canGoForward = false,
                isLoading = false,
                syncCandyId = null,
            ) else tab
        }
        val orderedFallbackTabs = tabs.filter { it.profileId == fallback.id }
            .sortedByDescending(BrowserTabState::isPinned).iterator()
        val orderedTabs = tabs.map { if (it.profileId == fallback.id) orderedFallbackTabs.next() else it }
        val activeProfileId = if (state.activeProfileId == profileId) fallback.id else state.activeProfileId
        val selectedId = state.selectedTabId.takeIf { id ->
            orderedTabs.any { it.id == id && it.profileId == activeProfileId }
        } ?: fallback.selectedTabId?.takeIf { id ->
            orderedTabs.any { it.id == id && it.profileId == activeProfileId }
        } ?: orderedTabs.first { it.profileId == activeProfileId }.id
        val profiles = state.profiles.filterNot { it.id == profileId }.map { profile ->
            profile.copy(selectedTabId = if (profile.id == activeProfileId) selectedId else {
                profile.selectedTabId?.takeIf { id -> orderedTabs.any { it.id == id && it.profileId == profile.id } }
            })
        }
        return update(state.copy(
            tabs = orderedTabs,
            selectedTabId = selectedId,
            profiles = profiles,
            activeProfileId = activeProfileId,
            snoozedTabs = state.snoozedTabs.map { snoozed ->
                if (snoozed.tab.profileId == profileId) snoozed.copy(
                    tab = snoozed.tab.copy(profileId = fallback.id, syncCandyId = null),
                ) else snoozed
            },
        ))
    }

    fun selectProfile(profileId: String): BrowserTabsState {
        if (profileId == state.activeProfileId) return state
        val profile = state.profiles.firstOrNull { it.id == profileId } ?: return state
        val rememberedProfiles = rememberSelectedTab(
            profiles = state.profiles,
            profileId = state.activeProfileId,
            tabId = state.selectedTabId,
        )
        val targetTab = profile.selectedTabId
            ?.let { selectedId ->
                state.tabs.firstOrNull { it.id == selectedId && it.profileId == profileId }
            }
            ?: state.tabs.lastOrNull { it.profileId == profileId }
        if (targetTab != null) {
            return update(
                state.copy(
                    selectedTabId = targetTab.id,
                    profiles = rememberSelectedTab(
                        profiles = rememberedProfiles,
                        profileId = profileId,
                        tabId = targetTab.id,
                    ),
                    activeProfileId = profileId,
                ),
            )
        }
        val tab = blankTab(profileId = profileId)
        return update(
            state.copy(
                tabs = state.tabs + tab,
                selectedTabId = tab.id,
                profiles = rememberSelectedTab(
                    profiles = rememberedProfiles,
                    profileId = profileId,
                    tabId = tab.id,
                ),
                activeProfileId = profileId,
                addressFocusRequest = state.addressFocusRequest + 1,
            ),
        )
    }

    fun assignSyncCandyId(
        tabId: String,
        candyId: String,
    ): BrowserTabsState {
        if (!dev.sk2andy.materialbrowser.sync.SyncTabRules.isValidCandyId(candyId)) return state
        if (state.tabs.any { it.syncCandyId == candyId && it.id != tabId }) return state
        return update(
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId && !tab.isPrivate) tab.copy(syncCandyId = candyId) else tab
                },
            ),
        )
    }

    /** Projects remote Candy devices into the existing profile/tab runtime. */
    fun reconcileSyncProfiles(
        syncedProfiles: List<SyncProfile>,
        currentDeviceId: String?,
        localProfileId: String?,
        icons: List<SyncDeviceIconDefinition>,
    ): BrowserTabsState {
        val localProfiles = state.profiles.filterNot { it.isSynced }.map { profile ->
            if (profile.id == localProfileId) profile.copy(linkedSyncDeviceId = currentDeviceId) else {
                profile.copy(linkedSyncDeviceId = null)
            }
        }
        val currentProfile = syncedProfiles.firstOrNull { it.deviceId == currentDeviceId }
        val linkedTabs = if (currentProfile != null && localProfileId != null) {
            val existingById = state.tabs.associateBy(BrowserTabState::id)
            SyncedProfileRuntimeRules.reconcileLinkedProfile(
                profile = currentProfile,
                localProfileId = localProfileId,
                existingTabs = state.tabs.map { tab -> tab.toBrowserTab() },
                nowMillis = 0L,
            ).tabs.map { tab ->
                val existing = existingById[tab.id]
                BrowserTabState(
                    id = tab.id,
                    address = tab.url.takeUnless { it == BLANK_URL }.orEmpty(),
                    title = tab.title,
                    profileId = tab.profileId,
                    canGoBack = existing?.canGoBack ?: false,
                    canGoForward = existing?.canGoForward ?: false,
                    isLoading = existing?.isLoading ?: tab.isLoading,
                    isPinned = tab.isPinned,
                    isPrivate = tab.isIncognito,
                    syncCandyId = tab.syncCandyId,
                )
            }
        } else {
            state.tabs.filter { it.profileId == localProfileId }
        }
        val remoteProfiles = syncedProfiles
            .filterNot { it.deviceId == currentDeviceId }
            .take((MAX_PROFILES - localProfiles.size).coerceAtLeast(0))
            .map { profile ->
                val emoji = icons.firstOrNull { it.id == profile.icon.catalogId }?.emoji ?: "🍬"
                SyncedProfileRuntimeRules.runtimeProfile(profile, emoji).copy(isolationEnabled = true)
            }
        val remoteIds = remoteProfiles.mapTo(hashSetOf()) { it.id }
        val localTabs = state.tabs.filter { tab ->
            state.profiles.firstOrNull { it.id == tab.profileId }?.let { profile ->
                !profile.isSynced && profile.id != localProfileId
            } == true
        } + linkedTabs
        val remoteTabs = syncedProfiles
            .filter { SyncedProfileRuntimeRules.profileId(it.deviceId) in remoteIds }
            .flatMap { profile ->
                profile.tabs.sortedWith(compareBy({ it.windowId }, { it.index }, { it.candyId }))
                    .map { tab ->
                        BrowserTabState(
                            id = "sync-tab:${profile.deviceId}:${tab.candyId}",
                            address = tab.url,
                            title = tab.title,
                            profileId = SyncedProfileRuntimeRules.profileId(profile.deviceId),
                            isPinned = tab.pinned,
                            syncCandyId = tab.candyId,
                        )
                    }
            }
        val profiles = localProfiles + remoteProfiles.map { remote ->
            remote.copy(
                selectedTabId = remoteTabs.firstOrNull { it.profileId == remote.id }?.id,
            )
        }
        val tabs = localTabs + remoteTabs
        val activeProfileId = state.activeProfileId.takeIf { id -> profiles.any { it.id == id } }
            ?: localProfiles.first().id
        val selectedTabId = state.selectedTabId.takeIf { id ->
            tabs.any { it.id == id && it.profileId == activeProfileId }
        } ?: tabs.firstOrNull { it.profileId == activeProfileId }?.id
            ?: return state
        return update(
            state.copy(
                tabs = tabs,
                selectedTabId = selectedTabId,
                profiles = profiles,
                activeProfileId = activeProfileId,
            ),
        )
    }

    private fun BrowserTabState.toBrowserTab(): BrowserTab = BrowserTab(
        id = id,
        lastAccessedAt = 0L,
        profileId = profileId,
        isIncognito = isPrivate,
        isPinned = isPinned,
        title = title,
        url = address.ifBlank { BLANK_URL },
        isLoading = isLoading,
        canGoBack = canGoBack,
        canGoForward = canGoForward,
        syncCandyId = syncCandyId,
    )

    fun snoozeCandidate(tabId: String): BrowserTab? {
        val tab = state.tabs.firstOrNull { it.id == tabId } ?: return null
        val profile = state.profiles.firstOrNull { it.id == tab.profileId } ?: return null
        if (tab.isPrivate || profile.isSynced || profile.protection != null) return null
        return tab.toBrowserTab()
    }

    fun snoozeTab(tabId: String, wakeAtMillis: Long, nowMillis: Long): Boolean {
        val source = snoozeCandidate(tabId) ?: return false
        if (!SnoozeRules.canSnooze(source, wakeAtMillis, nowMillis) || nowMillis < 0) return false
        if (state.snoozedTabs.size >= BrowserSessionPersistenceRules.MAX_TABS) return false
        val remaining = state.tabs.filterNot { it.id == tabId }
        val activeRemaining = remaining.filter { it.profileId == state.activeProfileId }
        val replacement = if (activeRemaining.isEmpty()) blankTab(state.activeProfileId) else null
        val tabs = if (replacement != null) remaining + replacement else remaining
        val selected = if (state.selectedTabId != tabId) state.selectedTabId else {
            replacement?.id ?: activeRemaining[
                state.activeTabs.indexOfFirst { it.id == tabId }.coerceIn(0, activeRemaining.lastIndex)
            ].id
        }
        update(state.copy(
            tabs = tabs,
            selectedTabId = selected,
            profiles = rememberSelectedTab(state.profiles, state.activeProfileId, selected),
            snoozedTabs = (state.snoozedTabs + SnoozedTab(source, wakeAtMillis, nowMillis))
                .sortedWith(compareBy({ it.wakeAtMillis }, { it.tab.id })),
            addressFocusRequest = state.addressFocusRequest + if (replacement != null) 1 else 0,
            isOverviewVisible = state.isOverviewVisible && replacement == null,
        ))
        return true
    }

    fun rescheduleSnoozedTab(tabId: String, wakeAtMillis: Long, nowMillis: Long): Boolean {
        val updated = SnoozeMutationRules.rescheduled(state.snoozedTabs, tabId, wakeAtMillis, nowMillis)
            ?: return false
        update(state.copy(snoozedTabs = updated))
        return true
    }

    fun deleteSnoozedTab(tabId: String): Boolean {
        val updated = SnoozeMutationRules.deleted(state.snoozedTabs, tabId) ?: return false
        update(state.copy(snoozedTabs = updated))
        return true
    }

    fun restoreDueSnoozedTabs(nowMillis: Long): List<String> = restoreSnoozedTabs(
        pending = state.snoozedTabs,
        nowMillis = nowMillis,
    )

    fun openSnoozedTabNow(tabId: String, nowMillis: Long): Boolean {
        val pending = state.snoozedTabs.firstOrNull { it.tab.id == tabId } ?: return false
        val restored = restoreSnoozedTabs(listOf(pending.copy(wakeAtMillis = nowMillis)), nowMillis)
        if (tabId !in restored) return false
        val tab = state.tabs.first { it.id == tabId }
        selectProfile(tab.profileId)
        selectTab(tabId)
        return true
    }

    private fun restoreSnoozedTabs(pending: List<SnoozedTab>, nowMillis: Long): List<String> {
        if (nowMillis < 0) return emptyList()
        val result = SnoozeRestoreRules.restoreDue(
            tabs = state.tabs.map { it.toBrowserTab() },
            snoozedTabs = pending,
            profiles = state.profiles,
            activeProfileId = state.activeProfileId,
            nowMillis = nowMillis,
            maxTabs = BrowserSessionPersistenceRules.MAX_TABS,
        )
        if (result.completedTabIds.isEmpty()) return emptyList()
        val existing = state.tabs.associateBy(BrowserTabState::id)
        update(state.copy(
            tabs = result.tabs.map { tab -> existing[tab.id] ?: BrowserTabState(
                id = tab.id,
                address = tab.url.takeUnless { it == BLANK_URL }.orEmpty(),
                title = tab.title,
                profileId = tab.profileId,
                isPinned = tab.isPinned,
                syncCandyId = tab.syncCandyId,
            ) },
            snoozedTabs = state.snoozedTabs.filterNot { it.tab.id in result.completedTabIds },
        ))
        return result.restoredTabIds.toList()
    }

    private fun addBlankTab(
        isPrivate: Boolean = false,
        profileId: String = state.activeProfileId,
    ): BrowserTabsState {
        val tab = blankTab(profileId = profileId).copy(isPrivate = isPrivate)
        val rememberedProfiles = rememberSelectedTab(
            profiles = state.profiles,
            profileId = state.activeProfileId,
            tabId = state.selectedTabId,
        )
        return update(
            state.copy(
                tabs = state.tabs + tab,
                selectedTabId = tab.id,
                profiles = rememberSelectedTab(
                    profiles = rememberedProfiles,
                    profileId = profileId,
                    tabId = tab.id,
                ),
                activeProfileId = profileId,
                isOverviewVisible = false,
                addressFocusRequest = state.addressFocusRequest + 1,
            ),
        )
    }

    private fun duplicateSelectedTab(tabId: String?): BrowserTabsState {
        val source = state.tabs.firstOrNull { tab ->
            tab.id == tabId && tab.id == state.selectedTabId && tab.address.isNotBlank()
        } ?: return state
        val duplicate = BrowserTabState(
            id = newTabId(),
            address = source.address,
            title = "",
            profileId = source.profileId,
            isPrivate = source.isPrivate,
        )
        return update(
            state.copy(
                tabs = state.tabs + duplicate,
                selectedTabId = duplicate.id,
                profiles = rememberSelectedTab(
                    profiles = state.profiles,
                    profileId = state.activeProfileId,
                    tabId = duplicate.id,
                ),
                isOverviewVisible = false,
            ),
        )
    }

    private fun selectTab(
        tabId: String?,
        hideOverview: Boolean = true,
    ): BrowserTabsState {
        val target = state.tabs.firstOrNull { it.id == tabId } ?: return state
        if (target.profileId != state.activeProfileId) return state
        return update(
            state.copy(
                selectedTabId = target.id,
                profiles = rememberSelectedTab(
                    profiles = state.profiles,
                    profileId = state.activeProfileId,
                    tabId = target.id,
                ),
                isOverviewVisible = if (hideOverview) false else state.isOverviewVisible,
            ),
        )
    }

    private fun closeTab(tabId: String?): BrowserTabsState {
        val closingIndex = state.tabs.indexOfFirst { it.id == tabId }
        if (closingIndex < 0 || state.tabs[closingIndex].isPinned) return state

        val remainingTabs = state.tabs.filterNot { it.id == tabId }
        val remainingActiveTabs = remainingTabs.filter { it.profileId == state.activeProfileId }
        if (remainingActiveTabs.isEmpty()) return addBlankTabAfterClosingLast(remainingTabs)

        val selectedTabId = if (state.selectedTabId == tabId) {
            val activeClosingIndex = state.activeTabs.indexOfFirst { it.id == tabId }
            remainingActiveTabs[minOf(activeClosingIndex, remainingActiveTabs.lastIndex)].id
        } else {
            state.selectedTabId
        }
        return update(
            state.copy(
                tabs = remainingTabs,
                selectedTabId = selectedTabId,
                profiles = rememberSelectedTab(
                    profiles = state.profiles,
                    profileId = state.activeProfileId,
                    tabId = selectedTabId,
                ),
            ),
        )
    }

    private fun addBlankTabAfterClosingLast(remainingTabs: List<BrowserTabState>): BrowserTabsState {
        val tab = blankTab(profileId = state.activeProfileId)
        return update(
            state.copy(
                tabs = remainingTabs + tab,
                selectedTabId = tab.id,
                profiles = rememberSelectedTab(
                    profiles = state.profiles,
                    profileId = state.activeProfileId,
                    tabId = tab.id,
                ),
                isOverviewVisible = false,
                addressFocusRequest = state.addressFocusRequest + 1,
            ),
        )
    }

    private fun blankTab(profileId: String): BrowserTabState = BrowserTabState(
        id = newTabId(),
        address = "",
        title = "",
        profileId = profileId,
    )

    private fun newTabId(): String {
        while (true) {
            val candidate = tabId(nextTabNumber)
            nextTabNumber = if (nextTabNumber == Long.MAX_VALUE) 1 else nextTabNumber + 1
            if (state.tabs.none { it.id == candidate } && state.snoozedTabs.none { it.tab.id == candidate }) return candidate
        }
    }

    private fun rememberSelectedTab(
        profiles: List<BrowserProfile>,
        profileId: String,
        tabId: String,
    ): List<BrowserProfile> = profiles.map { profile ->
        if (profile.id == profileId) profile.copy(selectedTabId = tabId) else profile
    }

    private fun update(value: BrowserTabsState): BrowserTabsState {
        state = value
        return value
    }

    private companion object {
        const val TAB_ID_PREFIX = "tab-"

        fun tabId(number: Long): String = "$TAB_ID_PREFIX$number"

        fun nextAvailableTabNumber(tabs: List<BrowserTabState>): Long {
            val highest = tabs.mapNotNull { tab ->
                tab.id.takeIf { it.startsWith(TAB_ID_PREFIX) }
                    ?.removePrefix(TAB_ID_PREFIX)?.toLongOrNull()?.takeIf { it > 0 }
            }.maxOrNull() ?: return 1
            return if (highest == Long.MAX_VALUE) 1 else highest + 1
        }
    }
}
