package dev.sk2andy.materialbrowser.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.ProfileWallpaperTarget
import dev.sk2andy.materialbrowser.browser.isSyncLinked
import dev.sk2andy.materialbrowser.shared.ui.BrowserProfileSheetCopy
import dev.sk2andy.materialbrowser.shared.ui.BrowserViewportProfile
import dev.sk2andy.materialbrowser.shared.ui.SharedProfileActionsSheet
import dev.sk2andy.materialbrowser.shared.ui.SharedProfileEmojiPickerSheet
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor

@Composable
internal fun ProfileActionsSheet(
    profile: BrowserProfile?,
    canDelete: Boolean,
    isolationSupported: Boolean,
    onChangeEmoji: () -> Unit,
    onCustomizeWallpaper: (ProfileWallpaperTarget) -> Unit,
    onDelete: () -> Unit,
    onIsolationChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    SharedProfileActionsSheet(
        profile = profile?.let {
            BrowserViewportProfile(
                id = it.id,
                emoji = it.emoji,
                isolationEnabled = it.isolationEnabled,
                displayName = it.syncedDisplayName,
                syncedIconEmoji = it.syncedIconEmoji,
                syncedIconAccentHue = it.syncedIconAccentHue,
                isSyncLinked = it.isSyncLinked,
            )
        },
        copy = profileSheetCopy(),
        canDelete = canDelete,
        isolationSupported = isolationSupported,
        onChangeEmoji = onChangeEmoji,
        onCustomizeWallpaper = onCustomizeWallpaper,
        onDelete = onDelete,
        onIsolationChange = onIsolationChange,
        onDismiss = onDismiss,
        containerColor = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerLow),
    )
}

@Composable
internal fun EmojiPickerSheet(
    visible: Boolean,
    creatingProfile: Boolean,
    isolationSupported: Boolean,
    emojis: List<String>,
    selectedEmoji: String?,
    onCreate: (String, Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    SharedProfileEmojiPickerSheet(
        visible = visible,
        creatingProfile = creatingProfile,
        isolationSupported = isolationSupported,
        emojis = emojis,
        selectedEmoji = selectedEmoji,
        copy = profileSheetCopy(),
        onCreate = onCreate,
        onSelect = onSelect,
        onDismiss = onDismiss,
        containerColor = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerLow),
    )
}

@Composable
private fun profileSheetCopy(): BrowserProfileSheetCopy = BrowserProfileSheetCopy(
    actionsTitle = stringResource(R.string.profile_actions_title),
    changeIcon = stringResource(R.string.action_change_profile_icon),
    newTabWallpaper = stringResource(R.string.action_customize_new_tab_wallpaper),
    tabSwitcherWallpaper = stringResource(R.string.action_customize_tab_switcher_wallpaper),
    deleteProfile = stringResource(R.string.action_delete_profile_keep_tabs),
    addProfileTitle = stringResource(R.string.add_profile_title),
    changeIconTitle = stringResource(R.string.change_profile_icon_title),
    createProfile = stringResource(R.string.action_create_profile),
    isolationTitle = stringResource(R.string.settings_profile_isolation_title),
    isolationSubtitle = stringResource(R.string.settings_profile_isolation_subtitle),
    isolationUnsupported = stringResource(R.string.settings_profile_isolation_unsupported),
)

internal typealias ProfileCreationTestTags =
    dev.sk2andy.materialbrowser.shared.ui.ProfileCreationTestTags
