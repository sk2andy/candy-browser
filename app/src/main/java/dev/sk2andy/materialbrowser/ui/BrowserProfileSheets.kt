package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.ProfileLockTrigger
import dev.sk2andy.materialbrowser.browser.ProfileProtection
import dev.sk2andy.materialbrowser.browser.ProfileProtectionRules
import dev.sk2andy.materialbrowser.browser.ProfileWallpaperTarget
import dev.sk2andy.materialbrowser.browser.isSyncLinked
import dev.sk2andy.materialbrowser.shared.ui.BrowserProfileSheetCopy
import dev.sk2andy.materialbrowser.shared.ui.BrowserViewportProfile
import dev.sk2andy.materialbrowser.shared.ui.SharedProfileActionsSheet
import dev.sk2andy.materialbrowser.shared.ui.SharedProfileEmojiPickerSheet
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsSwitch
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor

internal data class ProfileCreationOptions(
    val protection: ProfileProtection? = null,
    val wallpaperTargets: Set<ProfileWallpaperTarget> = emptySet(),
)

@Composable
internal fun ProfileActionsSheet(
    profile: BrowserProfile?,
    canDelete: Boolean,
    isolationSupported: Boolean,
    onChangeEmoji: () -> Unit,
    onCustomizeWallpaper: (ProfileWallpaperTarget) -> Unit,
    onDelete: () -> Unit,
    onIsolationChange: (Boolean) -> Unit,
    profileProtectionSupported: Boolean,
    onConfigureProtection: () -> Unit,
    onDisableProtection: () -> Unit,
    onDismiss: () -> Unit,
) {
    val resolvedProfile = profile ?: return
    SharedProfileActionsSheet(
        profile = resolvedProfile.let {
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
        additionalContent = {
            val protection = resolvedProfile.protection
            SettingsSwitch(
                title = stringResource(R.string.profile_protection_title),
                subtitle = when {
                    !profileProtectionSupported ->
                        stringResource(R.string.profile_protection_unavailable)
                    protection == null ->
                        stringResource(R.string.profile_protection_disabled)
                    else -> profileProtectionSummary(protection)
                },
                checked = protection != null,
                enabled = profileProtectionSupported,
                onCheckedChange = { enabled ->
                    if (enabled) onConfigureProtection() else onDisableProtection()
                },
            )
            if (protection != null && profileProtectionSupported) {
                TextButton(
                    onClick = onConfigureProtection,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.profile_protection_configure))
                }
            }
        },
        onDismiss = onDismiss,
        containerColor = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerLow),
    )
}

@Composable
internal fun EmojiPickerSheet(
    visible: Boolean,
    creatingProfile: Boolean,
    isolationSupported: Boolean,
    profileProtectionSupported: Boolean,
    emojis: List<String>,
    selectedEmoji: String?,
    onCreate: (
        emoji: String,
        isolationEnabled: Boolean,
        options: ProfileCreationOptions,
    ) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draftProtection by remember(creatingProfile) { mutableStateOf<ProfileProtection?>(null) }
    var configureDraftProtection by remember(creatingProfile) { mutableStateOf(false) }
    var draftWallpaperTargets by remember(creatingProfile) {
        mutableStateOf(emptySet<ProfileWallpaperTarget>())
    }
    SharedProfileEmojiPickerSheet(
        visible = visible,
        creatingProfile = creatingProfile,
        isolationSupported = isolationSupported,
        emojis = emojis,
        selectedEmoji = selectedEmoji,
        copy = profileSheetCopy(),
        onCreate = { emoji, isolationEnabled ->
            onCreate(
                emoji,
                isolationEnabled,
                ProfileCreationOptions(draftProtection, draftWallpaperTargets),
            )
        },
        onSelect = onSelect,
        onDismiss = onDismiss,
        creationOptions = if (creatingProfile) {
            {
                SettingsSwitch(
                    title = stringResource(R.string.profile_protection_title),
                    subtitle = when {
                        !profileProtectionSupported ->
                            stringResource(R.string.profile_protection_unavailable)
                        draftProtection == null ->
                            stringResource(R.string.profile_protection_disabled)
                        else -> profileProtectionSummary(requireNotNull(draftProtection))
                    },
                    checked = draftProtection != null,
                    enabled = profileProtectionSupported,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            configureDraftProtection = true
                        } else {
                            draftProtection = null
                        }
                    },
                    modifier = Modifier.testTag(ProfileCreationOptionTestTags.Protection),
                )
                if (draftProtection != null && profileProtectionSupported) {
                    TextButton(
                        onClick = { configureDraftProtection = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.profile_protection_configure))
                    }
                }
                ProfileCreationWallpaperSwitch(
                    target = ProfileWallpaperTarget.NewTab,
                    title = stringResource(R.string.action_customize_new_tab_wallpaper),
                    selectedTargets = draftWallpaperTargets,
                    onSelectedTargetsChange = { draftWallpaperTargets = it },
                )
                ProfileCreationWallpaperSwitch(
                    target = ProfileWallpaperTarget.TabSwitcher,
                    title = stringResource(R.string.action_customize_tab_switcher_wallpaper),
                    selectedTargets = draftWallpaperTargets,
                    onSelectedTargetsChange = { draftWallpaperTargets = it },
                )
            }
        } else {
            null
        },
        containerColor = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerLow),
    )
    if (configureDraftProtection) {
        ProfileProtectionDialog(
            current = draftProtection,
            onSave = { protection ->
                draftProtection = protection
                configureDraftProtection = false
            },
            onDismiss = { configureDraftProtection = false },
        )
    }
}

@Composable
private fun ProfileCreationWallpaperSwitch(
    target: ProfileWallpaperTarget,
    title: String,
    selectedTargets: Set<ProfileWallpaperTarget>,
    onSelectedTargetsChange: (Set<ProfileWallpaperTarget>) -> Unit,
) {
    val selected = target in selectedTargets
    SettingsSwitch(
        title = title,
        subtitle = stringResource(R.string.profile_creation_wallpaper_subtitle),
        checked = selected,
        onCheckedChange = { enabled ->
            onSelectedTargetsChange(
                if (enabled) selectedTargets + target else selectedTargets - target,
            )
        },
        modifier = Modifier.testTag(ProfileCreationOptionTestTags.wallpaper(target)),
    )
}

internal object ProfileCreationOptionTestTags {
    const val Protection = "profile_creation_protection"
    const val NewTabWallpaper = "profile_creation_new_tab_wallpaper"
    const val TabSwitcherWallpaper = "profile_creation_tab_switcher_wallpaper"

    fun wallpaper(target: ProfileWallpaperTarget): String = when (target) {
        ProfileWallpaperTarget.NewTab -> NewTabWallpaper
        ProfileWallpaperTarget.TabSwitcher -> TabSwitcherWallpaper
    }
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

@Composable
internal fun ProfileProtectionDialog(
    current: ProfileProtection?,
    onSave: (ProfileProtection) -> Unit,
    onDismiss: () -> Unit,
) {
    var trigger by remember(current) {
        mutableStateOf(current?.lockTrigger ?: ProfileLockTrigger.AppBackgrounded)
    }
    var cooldownInput by remember(current) {
        mutableStateOf(
            (current?.cooldownMinutes ?: ProfileProtectionRules.DEFAULT_COOLDOWN_MINUTES)
                .toString(),
        )
    }
    val cooldown = cooldownInput.toIntOrNull()
    val cooldownValid = cooldown != null &&
        cooldown in ProfileProtectionRules.MIN_COOLDOWN_MINUTES..
        ProfileProtectionRules.MAX_COOLDOWN_MINUTES
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_protection_dialog_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.profile_protection_disclosure))
                ProfileProtectionChoice(
                    title = stringResource(R.string.profile_protection_background),
                    selected = trigger == ProfileLockTrigger.AppBackgrounded,
                    onClick = { trigger = ProfileLockTrigger.AppBackgrounded },
                )
                ProfileProtectionChoice(
                    title = stringResource(R.string.profile_protection_closed),
                    selected = trigger == ProfileLockTrigger.AppClosed,
                    onClick = { trigger = ProfileLockTrigger.AppClosed },
                )
                ProfileProtectionChoice(
                    title = stringResource(R.string.profile_protection_cooldown),
                    selected = trigger == ProfileLockTrigger.Cooldown,
                    onClick = { trigger = ProfileLockTrigger.Cooldown },
                )
                if (trigger == ProfileLockTrigger.Cooldown) {
                    OutlinedTextField(
                        value = cooldownInput,
                        onValueChange = { value ->
                            cooldownInput = value.filter(Char::isDigit).take(4)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.profile_protection_minutes)) },
                        supportingText = {
                            Text(
                                stringResource(
                                    R.string.profile_protection_minutes_range,
                                    ProfileProtectionRules.MIN_COOLDOWN_MINUTES,
                                    ProfileProtectionRules.MAX_COOLDOWN_MINUTES,
                                ),
                            )
                        },
                        isError = !cooldownValid,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ProfileProtection(
                            lockTrigger = trigger,
                            cooldownMinutes = cooldown
                                ?: ProfileProtectionRules.DEFAULT_COOLDOWN_MINUTES,
                        ),
                    )
                },
                enabled = trigger != ProfileLockTrigger.Cooldown || cooldownValid,
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun ProfileProtectionChoice(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(title, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun profileProtectionSummary(protection: ProfileProtection): String = when (
    protection.lockTrigger
) {
    ProfileLockTrigger.AppBackgrounded ->
        stringResource(R.string.profile_protection_background)
    ProfileLockTrigger.AppClosed -> stringResource(R.string.profile_protection_closed)
    ProfileLockTrigger.Cooldown -> stringResource(
        R.string.profile_protection_cooldown_summary,
        protection.cooldownMinutes,
    )
}

internal typealias ProfileCreationTestTags =
    dev.sk2andy.materialbrowser.shared.ui.ProfileCreationTestTags
