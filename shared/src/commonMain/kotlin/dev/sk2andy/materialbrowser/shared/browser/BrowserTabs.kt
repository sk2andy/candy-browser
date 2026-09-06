package dev.sk2andy.materialbrowser.shared.browser

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserProfileDraft
import dev.sk2andy.materialbrowser.browser.BrowserProfileRules
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.DEFAULT_BROWSER_PROFILE
import dev.sk2andy.materialbrowser.browser.DEFAULT_PROFILE_ID
import dev.sk2andy.materialbrowser.browser.MAX_PROFILES
import dev.sk2andy.materialbrowser.browser.SyncedProfileRuntimeRules
import dev.sk2andy.materialbrowser.browser.isSynced
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
) {
    val activeTabs: List<BrowserTabState>
        get() = tabs.filter { it.profileId == activeProfileId }

    val countLabel: String
        get() = BrowserTabsRules.countLabel(activeTabs.size)
}

enum class BrowserTabsIntent {
    NewTab,
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
        nextTabNumber = initialState.tabs
            .mapNotNull { tab -> tab.id.removePrefix(TAB_ID_PREFIX).toLongOrNull() }
            .maxOrNull()
            ?.plus(1)
            ?: 1
    }

    fun dispatch(intent: BrowserTabsIntent, tabId: String?): BrowserTabsState = when (intent) {
        BrowserTabsIntent.NewTab -> addBlankTab()
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

    private fun addBlankTab(): BrowserTabsState {
        val tab = blankTab(profileId = state.activeProfileId)
        return update(
            state.copy(
                tabs = state.tabs + tab,
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
        if (closingIndex < 0) return state

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
        id = tabId(nextTabNumber++),
        address = "",
        title = "",
        profileId = profileId,
    )

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
    }
}
