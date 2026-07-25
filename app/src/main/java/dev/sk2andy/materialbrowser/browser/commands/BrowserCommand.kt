package dev.sk2andy.materialbrowser.browser.commands

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab

enum class BrowserCommandKind(val executionId: String?) {
    ClearCacheAndReload("clear-cache-and-reload"),
    ClearCookiesAndReload("clear-cookies-and-reload"),
    Reload("reload"),
    StopLoading("stop-loading"),
    PinTab("pin-tab"),
    UnpinTab("unpin-tab"),
    CloseDuplicateTabs("close-duplicate-tabs"),
    MoveTabToProfile(null),
    SwitchProfile(null),
    NewRegularTab("new-regular-tab"),
    NewIncognitoTab("new-incognito-tab"),
    OpenSettings("open-settings"),
}

enum class CommandConfirmation {
    None,
    ClearCookies,
    CloseMultipleDuplicates,
}

data class BrowserCommand(
    val executionId: String,
    val kind: BrowserCommandKind,
    val targetProfileId: String? = null,
    val targetProfileLabel: String? = null,
    val duplicateCount: Int = 0,
    val duplicateTabIds: List<String> = emptyList(),
    val confirmation: CommandConfirmation = CommandConfirmation.None,
)

data class CommandContext(
    val selectedTab: BrowserTab,
    val profiles: List<BrowserProfile>,
    val activeProfileId: String,
    val duplicateTabIds: List<String>,
    val canCreateTab: Boolean,
    val canCreateIncognitoTab: Boolean,
    val canMoveSelectedTab: Boolean,
    val hasLoadedPage: Boolean,
    val canClearCookies: Boolean,
)

enum class CommandCookieScope {
    SharedRegularProfile,
    IsolatedRegularProfile,
    PrivateProfile,
    AllWebViews,
}

object CommandExecutionIds {
    private const val MOVE_TAB_PREFIX = "move-tab-to-profile:"
    private const val SWITCH_PROFILE_PREFIX = "switch-profile:"

    fun moveTabToProfile(profileId: String): String = "$MOVE_TAB_PREFIX$profileId"

    fun switchProfile(profileId: String): String = "$SWITCH_PROFILE_PREFIX$profileId"

}

object BrowserCommandRegistry {
    fun commands(context: CommandContext): List<BrowserCommand> = buildList {
        if (context.hasLoadedPage) {
            add(fixed(BrowserCommandKind.ClearCacheAndReload))
            if (context.canClearCookies) {
                add(
                    fixed(BrowserCommandKind.ClearCookiesAndReload).copy(
                        confirmation = CommandConfirmation.ClearCookies,
                    ),
                )
            }
            add(
                fixed(
                    if (context.selectedTab.isLoading) {
                        BrowserCommandKind.StopLoading
                    } else {
                        BrowserCommandKind.Reload
                    },
                ),
            )
        }
        add(
            fixed(
                if (context.selectedTab.isPinned) {
                    BrowserCommandKind.UnpinTab
                } else {
                    BrowserCommandKind.PinTab
                },
            ),
        )
        if (context.duplicateTabIds.isNotEmpty()) {
            add(
                fixed(BrowserCommandKind.CloseDuplicateTabs).copy(
                    duplicateCount = context.duplicateTabIds.size,
                    duplicateTabIds = context.duplicateTabIds,
                    confirmation = if (context.duplicateTabIds.size > 1) {
                        CommandConfirmation.CloseMultipleDuplicates
                    } else {
                        CommandConfirmation.None
                    },
                ),
            )
        }
        context.profiles
            .mapIndexed { index, profile -> index to profile }
            .filter { (_, profile) -> profile.id != context.activeProfileId }
            .forEach { (index, profile) ->
                val targetLabel = "${index + 1} · ${profile.emoji}"
                if (context.canMoveSelectedTab) {
                    add(
                        BrowserCommand(
                            executionId = CommandExecutionIds.moveTabToProfile(profile.id),
                            kind = BrowserCommandKind.MoveTabToProfile,
                            targetProfileId = profile.id,
                            targetProfileLabel = targetLabel,
                        ),
                    )
                }
                add(
                    BrowserCommand(
                        executionId = CommandExecutionIds.switchProfile(profile.id),
                        kind = BrowserCommandKind.SwitchProfile,
                        targetProfileId = profile.id,
                        targetProfileLabel = targetLabel,
                    ),
                )
            }
        if (context.canCreateTab) {
            add(fixed(BrowserCommandKind.NewRegularTab))
        }
        if (context.canCreateIncognitoTab) {
            add(fixed(BrowserCommandKind.NewIncognitoTab))
        }
        add(fixed(BrowserCommandKind.OpenSettings))
    }

    private fun fixed(kind: BrowserCommandKind): BrowserCommand = BrowserCommand(
        executionId = checkNotNull(kind.executionId),
        kind = kind,
    )
}
