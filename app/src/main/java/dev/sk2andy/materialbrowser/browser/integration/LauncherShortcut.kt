package dev.sk2andy.materialbrowser.browser.integration

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.isSynced

internal data class LauncherProfileShortcut(
    val profileId: String,
    val emoji: String,
)

internal data class LauncherShortcutState(
    val recentProfiles: List<LauncherProfileShortcut>,
)

internal sealed interface LauncherShortcutTarget {
    data object OpenApp : LauncherShortcutTarget

    data object NewTab : LauncherShortcutTarget

    data object NewPrivateTab : LauncherShortcutTarget

    data object NewPrivateTabInCurrentProfile : LauncherShortcutTarget

    data class Profile(val profileId: String) : LauncherShortcutTarget

    data class NewTabInProfile(val profileId: String) : LauncherShortcutTarget
}

internal object LauncherShortcutRules {
    const val ACTION_OPEN_APP = "dev.sk2andy.materialbrowser.action.OPEN_APP"
    const val ACTION_NEW_TAB = "dev.sk2andy.materialbrowser.action.NEW_TAB"
    const val ACTION_NEW_PRIVATE_TAB = "dev.sk2andy.materialbrowser.action.NEW_PRIVATE_TAB"
    const val ACTION_NEW_PRIVATE_TAB_IN_CURRENT_PROFILE =
        "dev.sk2andy.materialbrowser.action.NEW_PRIVATE_TAB_IN_CURRENT_PROFILE"
    const val ACTION_OPEN_PROFILE = "dev.sk2andy.materialbrowser.action.OPEN_PROFILE"
    const val ACTION_NEW_TAB_IN_PROFILE =
        "dev.sk2andy.materialbrowser.action.NEW_TAB_IN_PROFILE"
    const val EXTRA_PROFILE_ID = "dev.sk2andy.materialbrowser.extra.PROFILE_ID"

    private const val NEW_TAB_SHORTCUT_ID = "launcher_new_tab"
    private const val NEW_PRIVATE_TAB_SHORTCUT_ID = "launcher_new_private_tab"
    private const val PROFILE_SHORTCUT_PREFIX = "launcher_profile_"
    private const val OPEN_APP_WIDGET_TARGET_ID = "widget_open_app"
    private const val MAX_RECENT_PROFILE_SHORTCUTS = 2

    fun state(
        profiles: List<BrowserProfile>,
        tabs: List<BrowserTab>,
        activeProfileId: String,
        profilesEnabled: Boolean,
    ): LauncherShortcutState {
        val recentProfiles = if (profilesEnabled) {
            val lastRegularUseByProfile = tabs.asSequence()
                .filterNot(BrowserTab::isIncognito)
                .groupBy(BrowserTab::profileId)
                .mapValues { (_, profileTabs) ->
                    profileTabs.maxOfOrNull(BrowserTab::lastAccessedAt) ?: Long.MIN_VALUE
                }
            profiles.withIndex()
                .filter { (_, profile) -> profile.id != activeProfileId && !profile.isSynced }
                .sortedWith(
                    compareByDescending<IndexedValue<BrowserProfile>> { indexed ->
                        lastRegularUseByProfile[indexed.value.id] ?: Long.MIN_VALUE
                    }.thenBy(IndexedValue<BrowserProfile>::index),
                )
                .map { (_, profile) ->
                    LauncherProfileShortcut(
                        profileId = profile.id,
                        emoji = profile.emoji,
                    )
                }
                .take(MAX_RECENT_PROFILE_SHORTCUTS)
        } else {
            emptyList()
        }
        return LauncherShortcutState(
            recentProfiles = recentProfiles,
        )
    }

    fun privateTargetProfileId(
        profiles: List<BrowserProfile>,
        activeProfileId: String,
        profileIsolationSupported: Boolean,
        fallbackToFirstLocalProfile: Boolean = true,
    ): String? {
        if (!profileIsolationSupported) return null
        return profiles.firstOrNull { profile ->
            profile.id == activeProfileId && !profile.isSynced
        }?.id ?: if (fallbackToFirstLocalProfile) {
            profiles.firstOrNull { profile -> !profile.isSynced }?.id
        } else {
            null
        }
    }

    fun resolve(
        action: String?,
        profileId: String?,
        availableProfileIds: Set<String>,
        profilesEnabled: Boolean,
    ): LauncherShortcutTarget? = when (action) {
        ACTION_OPEN_APP -> LauncherShortcutTarget.OpenApp
        ACTION_NEW_TAB -> LauncherShortcutTarget.NewTab
        ACTION_NEW_PRIVATE_TAB -> LauncherShortcutTarget.NewPrivateTab
        ACTION_NEW_PRIVATE_TAB_IN_CURRENT_PROFILE ->
            LauncherShortcutTarget.NewPrivateTabInCurrentProfile
        ACTION_OPEN_PROFILE -> profileId
            ?.takeIf { profilesEnabled && it in availableProfileIds }
            ?.let(LauncherShortcutTarget::Profile)
        ACTION_NEW_TAB_IN_PROFILE -> profileId
            ?.takeIf { profilesEnabled && it in availableProfileIds }
            ?.let(LauncherShortcutTarget::NewTabInProfile)
        else -> null
    }

    fun shortcutId(target: LauncherShortcutTarget): String = when (target) {
        LauncherShortcutTarget.OpenApp -> OPEN_APP_WIDGET_TARGET_ID
        LauncherShortcutTarget.NewTab -> NEW_TAB_SHORTCUT_ID
        LauncherShortcutTarget.NewPrivateTab -> NEW_PRIVATE_TAB_SHORTCUT_ID
        LauncherShortcutTarget.NewPrivateTabInCurrentProfile -> NEW_PRIVATE_TAB_SHORTCUT_ID
        is LauncherShortcutTarget.Profile -> PROFILE_SHORTCUT_PREFIX + target.profileId
        is LauncherShortcutTarget.NewTabInProfile -> PROFILE_SHORTCUT_PREFIX + target.profileId
    }
}
