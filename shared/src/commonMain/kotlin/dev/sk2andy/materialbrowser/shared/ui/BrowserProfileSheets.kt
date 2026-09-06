@file:OptIn(ExperimentalMaterial3Api::class)

package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sk2andy.materialbrowser.browser.ProfileWallpaperTarget
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsSwitch

data class BrowserProfileSheetCopy(
    val actionsTitle: String,
    val changeIcon: String,
    val newTabWallpaper: String,
    val tabSwitcherWallpaper: String,
    val deleteProfile: String,
    val addProfileTitle: String,
    val changeIconTitle: String,
    val createProfile: String,
    val isolationTitle: String,
    val isolationSubtitle: String,
    val isolationUnsupported: String,
)

@Composable
fun SharedProfileActionsSheet(
    profile: BrowserViewportProfile?,
    copy: BrowserProfileSheetCopy,
    canDelete: Boolean,
    isolationSupported: Boolean,
    onChangeEmoji: () -> Unit,
    onCustomizeWallpaper: ((ProfileWallpaperTarget) -> Unit)?,
    onDelete: (() -> Unit)?,
    onIsolationChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    if (profile == null) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = containerColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlatformProfileEmoji(
                    emoji = profile.emoji,
                    fontSize = 30.sp,
                    modifier = Modifier.size(42.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    copy.actionsTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = onChangeEmoji,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(copy.changeIcon)
            }
            onCustomizeWallpaper?.let { customizeWallpaper ->
                TextButton(
                    onClick = { customizeWallpaper(ProfileWallpaperTarget.NewTab) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(copy.newTabWallpaper)
                }
                TextButton(
                    onClick = { customizeWallpaper(ProfileWallpaperTarget.TabSwitcher) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(copy.tabSwitcherWallpaper)
                }
            }
            SettingsSwitch(
                title = copy.isolationTitle,
                subtitle = if (isolationSupported) {
                    copy.isolationSubtitle
                } else {
                    copy.isolationUnsupported
                },
                checked = profile.isolationEnabled && isolationSupported,
                enabled = isolationSupported,
                onCheckedChange = onIsolationChange,
            )
            if (canDelete && onDelete != null) {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(copy.deleteProfile, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun SharedProfileEmojiPickerSheet(
    visible: Boolean,
    creatingProfile: Boolean,
    isolationSupported: Boolean,
    emojis: List<String>,
    selectedEmoji: String?,
    copy: BrowserProfileSheetCopy,
    onCreate: (String, Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    if (!visible) return
    var draftEmoji by remember(creatingProfile, selectedEmoji) { mutableStateOf(selectedEmoji) }
    var draftIsolationEnabled by remember(creatingProfile) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = creatingProfile)
    val dragHandle: @Composable (() -> Unit)? = if (creatingProfile) {
        null
    } else {
        { BottomSheetDefaults.DragHandle() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(ProfileCreationTestTags.Sheet),
        containerColor = containerColor,
        dragHandle = dragHandle,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (creatingProfile) Modifier.fillMaxHeight(0.66f) else Modifier)
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
                        title = copy.isolationTitle,
                        subtitle = if (isolationSupported) {
                            copy.isolationSubtitle
                        } else {
                            copy.isolationUnsupported
                        },
                        checked = draftIsolationEnabled && isolationSupported,
                        enabled = isolationSupported,
                        onCheckedChange = { draftIsolationEnabled = it },
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            Text(
                if (creatingProfile) copy.addProfileTitle else copy.changeIconTitle,
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
                emojis.chunked(PROFILE_ICON_COLUMNS).forEach { rowEmojis ->
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
                                    .semantics { contentDescription = emoji }
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
                                    PlatformProfileEmoji(
                                        emoji = emoji,
                                        fontSize = 23.sp,
                                        modifier = Modifier.size(32.dp),
                                    )
                                }
                            }
                        }
                        repeat(PROFILE_ICON_COLUMNS - rowEmojis.size) {
                            Spacer(Modifier.size(48.dp))
                        }
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
                    Text(copy.createProfile)
                }
            }
        }
    }
}

object ProfileCreationTestTags {
    const val Sheet = "profile_creation_sheet"
    const val Isolation = "profile_creation_isolation"
    const val IconScroll = "profile_creation_icon_scroll"
    const val CreateButton = "profile_creation_create_button"
}

private const val PROFILE_ICON_COLUMNS = 6
