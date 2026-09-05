@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.ProfileWallpaperTarget
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
    if (profile == null) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile.emoji, fontSize = 30.sp)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.profile_actions_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = onChangeEmoji,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_change_profile_icon))
            }
            TextButton(
                onClick = { onCustomizeWallpaper(ProfileWallpaperTarget.NewTab) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_customize_new_tab_wallpaper))
            }
            TextButton(
                onClick = { onCustomizeWallpaper(ProfileWallpaperTarget.TabSwitcher) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_customize_tab_switcher_wallpaper))
            }
            SettingsSwitch(
                title = stringResource(R.string.settings_profile_isolation_title),
                subtitle = stringResource(
                    if (isolationSupported) R.string.settings_profile_isolation_subtitle
                    else R.string.settings_profile_isolation_unsupported,
                ),
                checked = profile.isolationEnabled && isolationSupported,
                enabled = isolationSupported,
                onCheckedChange = onIsolationChange,
            )
            if (canDelete) {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.action_delete_profile_keep_tabs),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
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
    if (!visible) return
    var draftEmoji by remember(creatingProfile, selectedEmoji) { mutableStateOf(selectedEmoji) }
    var draftIsolationEnabled by remember(creatingProfile) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = creatingProfile)
    val creationSheetHeight = (
        LocalConfiguration.current.screenHeightDp * PROFILE_CREATION_SHEET_HEIGHT_FRACTION
    ).dp
    val dragHandle: @Composable (() -> Unit)? = if (creatingProfile) {
        null
    } else {
        { BottomSheetDefaults.DragHandle() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(ProfileCreationTestTags.Sheet),
        containerColor = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerLow),
        dragHandle = dragHandle,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (creatingProfile) Modifier.height(creationSheetHeight) else Modifier)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            if (creatingProfile) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BottomSheetDefaults.DragHandle()
                }
                Box(modifier = Modifier.testTag(ProfileCreationTestTags.Isolation)) {
                    SettingsSwitch(
                        title = stringResource(R.string.settings_profile_isolation_title),
                        subtitle = stringResource(
                            if (isolationSupported) R.string.settings_profile_isolation_subtitle
                            else R.string.settings_profile_isolation_unsupported,
                        ),
                        checked = draftIsolationEnabled && isolationSupported,
                        enabled = isolationSupported,
                        onCheckedChange = { draftIsolationEnabled = it },
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            Text(
                stringResource(
                    if (creatingProfile) R.string.add_profile_title
                    else R.string.change_profile_icon_title,
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (creatingProfile) Modifier.weight(1f) else Modifier)
                    .verticalScroll(rememberScrollState())
                    .testTag(ProfileCreationTestTags.IconScroll),
            ) {
                emojis.chunked(6).forEach { rowEmojis ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        rowEmojis.forEach { emoji ->
                            val isSelected = emoji == draftEmoji
                            Surface(
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .size(48.dp)
                                    .clickable(
                                        role = Role.Button,
                                        onClick = {
                                            if (creatingProfile) {
                                                draftEmoji = emoji
                                            } else {
                                                onSelect(emoji)
                                            }
                                        },
                                    ),
                                shape = CircleShape,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                tonalElevation = if (isSelected) 5.dp else 0.dp,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(emoji, fontSize = 23.sp)
                                }
                            }
                        }
                        repeat(6 - rowEmojis.size) { Spacer(Modifier.size(48.dp)) }
                    }
                }
            }
            if (creatingProfile) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        draftEmoji?.let { emoji -> onCreate(emoji, draftIsolationEnabled) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ProfileCreationTestTags.CreateButton),
                    enabled = draftEmoji != null,
                ) {
                    Text(stringResource(R.string.action_create_profile))
                }
            }
        }
    }
}

internal object ProfileCreationTestTags {
    const val Sheet = "profile_creation_sheet"
    const val Isolation = "profile_creation_isolation"
    const val IconScroll = "profile_creation_icon_scroll"
    const val CreateButton = "profile_creation_create_button"
}

private const val PROFILE_CREATION_SHEET_HEIGHT_FRACTION = 0.66f

