package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.DEFAULT_BROWSER_PROFILE
import dev.sk2andy.materialbrowser.sync.SyncConnectionSettings
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconCatalog
import dev.sk2andy.materialbrowser.sync.SyncEnrollmentOutcome
import dev.sk2andy.materialbrowser.sync.SyncRepositoryState
import dev.sk2andy.materialbrowser.sync.SyncStatus

private val SYNC_ACCENT_HUES = listOf(0, 36, 72, 108, 144, 180, 216, 252, 288, 312)

internal object SyncSettingsTestTags {
    const val Endpoint = "sync_settings_endpoint"
    const val Username = "sync_settings_username"
    const val Password = "sync_settings_password"
    const val PasswordVisibility = "sync_settings_password_visibility"
    const val Passphrase = "sync_settings_passphrase"
    const val PassphraseVisibility = "sync_settings_passphrase_visibility"
    const val PassphraseConfirmation = "sync_settings_passphrase_confirmation"
    const val PassphraseConfirmationVisibility =
        "sync_settings_passphrase_confirmation_visibility"
    const val DeviceName = "sync_settings_device_name"
    const val LocalProfile = "sync_settings_local_profile"
    const val Icon = "sync_settings_icon"
    const val AccentColors = "sync_settings_accent_colors"
    const val Enroll = "sync_settings_enroll"
    const val Refresh = "sync_settings_refresh"

    fun accentColor(hue: Int): String = "sync_settings_accent_color:$hue"
}

@Composable
internal fun SyncSettingsPage(
    state: SyncRepositoryState,
    iconCatalog: SyncDeviceIconCatalog,
    localProfiles: List<BrowserProfile> = listOf(DEFAULT_BROWSER_PROFILE),
    activeProfileId: String = localProfiles.first().id,
    onConfigure: (SyncConnectionSettings) -> Boolean,
    onEnroll: (CharArray, CharArray, (SyncEnrollmentOutcome) -> Unit) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val configured = state.settings
    var endpoint by rememberSaveable(configured?.endpoint) {
        mutableStateOf(configured?.endpoint.orEmpty())
    }
    var username by rememberSaveable(configured?.username) {
        mutableStateOf(configured?.username.orEmpty())
    }
    var deviceName by rememberSaveable(configured?.deviceName) {
        mutableStateOf(configured?.deviceName.orEmpty())
    }
    var localProfileId by rememberSaveable(configured?.localProfileId, activeProfileId) {
        mutableStateOf(
            configured?.localProfileId
                ?.takeIf { candidate -> localProfiles.any { it.id == candidate } }
                ?: activeProfileId.takeIf { candidate -> localProfiles.any { it.id == candidate } }
                ?: localProfiles.first().id,
        )
    }
    var iconCatalogId by rememberSaveable(configured?.iconCatalogId) {
        mutableStateOf(configured?.iconCatalogId ?: "phone")
    }
    var iconAccentHue by rememberSaveable(configured?.iconAccentHue) {
        mutableStateOf(configured?.iconAccentHue ?: 312)
    }
    var serverPassword by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var passphraseConfirmation by remember { mutableStateOf("") }
    var iconMenuExpanded by remember { mutableStateOf(false) }
    var profileMenuExpanded by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<Int?>(null) }
    val selectedIcon = iconCatalog.icons.firstOrNull { it.id == iconCatalogId }
        ?: iconCatalog.icons.first()
    val selectedProfile = localProfiles.firstOrNull { it.id == localProfileId }
        ?: localProfiles.first()

    SettingsPage(
        title = stringResource(R.string.sync_settings_title),
        onBack = onBack,
    ) {
        SyncStatusCard(state)
        Text(
            text = stringResource(R.string.sync_connection_section),
            modifier = Modifier.padding(start = 18.dp, top = 20.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it },
            label = { Text(stringResource(R.string.sync_endpoint_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().testTag(SyncSettingsTestTags.Endpoint),
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(R.string.sync_username_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(SyncSettingsTestTags.Username),
        )
        SecretTextField(
            value = serverPassword,
            onValueChange = { serverPassword = it },
            label = stringResource(R.string.sync_server_password_label),
            supportingText = stringResource(R.string.sync_server_password_summary),
            fieldTestTag = SyncSettingsTestTags.Password,
            visibilityTestTag = SyncSettingsTestTags.PasswordVisibility,
        )

        Text(
            text = stringResource(R.string.sync_this_device_section),
            modifier = Modifier.padding(start = 18.dp, top = 20.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Box {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { profileMenuExpanded = true }
                    .testTag(SyncSettingsTestTags.LocalProfile),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(selectedProfile.emoji, style = MaterialTheme.typography.headlineSmall)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(stringResource(R.string.sync_local_profile_label))
                        Text(
                            if (selectedProfile.id == activeProfileId) {
                                stringResource(R.string.sync_local_profile_current)
                            } else {
                                stringResource(R.string.sync_local_profile_existing)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            DropdownMenu(
                expanded = profileMenuExpanded,
                onDismissRequest = { profileMenuExpanded = false },
            ) {
                localProfiles.forEach { profile ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (profile.id == activeProfileId) {
                                    "${profile.emoji}  ${stringResource(R.string.sync_local_profile_current)}"
                                } else {
                                    "${profile.emoji}  ${stringResource(R.string.sync_local_profile_existing)}"
                                },
                            )
                        },
                        onClick = {
                            localProfileId = profile.id
                            profileMenuExpanded = false
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = deviceName,
            onValueChange = { deviceName = it },
            label = { Text(stringResource(R.string.sync_device_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(SyncSettingsTestTags.DeviceName),
        )
        Box {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { iconMenuExpanded = true }
                    .testTag(SyncSettingsTestTags.Icon),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(selectedIcon.emoji, style = MaterialTheme.typography.headlineSmall)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(stringResource(R.string.sync_device_icon_label))
                        Text(
                            selectedIcon.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            DropdownMenu(
                expanded = iconMenuExpanded,
                onDismissRequest = { iconMenuExpanded = false },
            ) {
                iconCatalog.icons.forEach { icon ->
                    DropdownMenuItem(
                        text = { Text("${icon.emoji}  ${icon.label}") },
                        onClick = {
                            iconCatalogId = icon.id
                            iconMenuExpanded = false
                        },
                    )
                }
            }
        }
        SyncAccentColorPicker(
            selectedHue = iconAccentHue,
            onHueSelected = { iconAccentHue = it },
        )

        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    stringResource(R.string.sync_passphrase_warning_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.sync_passphrase_warning_message),
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        SecretTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = stringResource(R.string.sync_passphrase_label),
            fieldTestTag = SyncSettingsTestTags.Passphrase,
            visibilityTestTag = SyncSettingsTestTags.PassphraseVisibility,
        )
        SecretTextField(
            value = passphraseConfirmation,
            onValueChange = { passphraseConfirmation = it },
            label = stringResource(R.string.sync_passphrase_confirm_label),
            fieldTestTag = SyncSettingsTestTags.PassphraseConfirmation,
            visibilityTestTag = SyncSettingsTestTags.PassphraseConfirmationVisibility,
        )
        feedback?.let { message ->
            Text(
                stringResource(message),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onRefresh,
                enabled = state.status != SyncStatus.Unconfigured && !submitting,
                modifier = Modifier.testTag(SyncSettingsTestTags.Refresh),
            ) {
                Text(stringResource(R.string.sync_action_refresh))
            }
            Button(
                onClick = {
                    feedback = when {
                        passphrase != passphraseConfirmation ->
                            R.string.sync_error_passphrase_mismatch
                        passphrase.length < 16 -> R.string.sync_error_passphrase_too_short
                        passphrase == serverPassword -> R.string.sync_error_password_reuse
                        endpoint.isBlank() || username.isBlank() || serverPassword.isBlank() ||
                            passphrase.isBlank() || deviceName.isBlank() ->
                            R.string.sync_error_required_fields
                        else -> null
                    }
                    if (feedback != null) return@Button
                    val settings = SyncConnectionSettings(
                        endpoint = endpoint,
                        username = username,
                        deviceName = deviceName,
                        iconCatalogId = iconCatalogId,
                        iconAccentHue = iconAccentHue,
                        localProfileId = localProfileId,
                    )
                    if (!onConfigure(settings)) {
                        feedback = R.string.sync_error_invalid_configuration
                        return@Button
                    }
                    submitting = true
                    val passwordChars = serverPassword.toCharArray()
                    val passphraseChars = passphrase.toCharArray()
                    serverPassword = ""
                    passphrase = ""
                    passphraseConfirmation = ""
                    onEnroll(passwordChars, passphraseChars) { outcome ->
                        submitting = false
                        feedback = outcome.feedbackMessage()
                    }
                },
                enabled = !submitting,
                modifier = Modifier.testTag(SyncSettingsTestTags.Enroll),
            ) {
                Text(stringResource(R.string.sync_action_connect))
            }
        }

        if (state.profiles.isNotEmpty()) {
            Text(
                text = stringResource(R.string.sync_devices_section),
                modifier = Modifier.padding(start = 18.dp, top = 24.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            state.profiles.forEach { profile ->
                val icon = iconCatalog.icons.firstOrNull { it.id == profile.icon.catalogId }
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(icon?.emoji ?: "🍬", modifier = Modifier.size(32.dp))
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(profile.displayName, fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(
                                    R.string.sync_device_tabs_last_seen,
                                    profile.tabs.size,
                                    profile.lastSeenAt,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SyncAccentColorPicker(
    selectedHue: Int,
    onHueSelected: (Int) -> Unit,
) {
    val accentLabel = stringResource(R.string.sync_accent_color_label)
    val hues = if (selectedHue in SYNC_ACCENT_HUES) {
        SYNC_ACCENT_HUES
    } else {
        SYNC_ACCENT_HUES.dropLast(1) + selectedHue.coerceIn(0, 359)
    }
    Text(
        text = accentLabel,
        modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
    )
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SyncSettingsTestTags.AccentColors),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        hues.forEachIndexed { index, hue ->
            val selected = hue == selectedHue
            val color = Color.hsv(hue.toFloat(), 0.42f, 0.86f)
            val optionDescription = stringResource(
                R.string.sync_accent_color_option,
                index + 1,
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onHueSelected(hue) },
                    )
                    .semantics { contentDescription = optionDescription }
                    .testTag(SyncSettingsTestTags.accentColor(hue)),
                contentAlignment = Alignment.Center,
            ) {
                SyncAccentColorSwatch(
                    color = color,
                    selected = selected,
                )
            }
        }
    }
}

@Composable
private fun SyncAccentColorSwatch(
    color: Color,
    selected: Boolean,
) {
    Surface(
        modifier = Modifier
            .size(34.dp)
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                },
            ),
        shape = CircleShape,
        color = color,
        contentColor = Color.Transparent,
    ) {}
}

@Composable
private fun SecretTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    fieldTestTag: String,
    visibilityTestTag: String,
    supportingText: String? = null,
) {
    var visible by remember { mutableStateOf(false) }
    val visibilityDescription = stringResource(
        if (visible) R.string.sync_hide_password else R.string.sync_show_password,
    )
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supportingText?.let { text -> { Text(text) } },
        trailingIcon = {
            IconButton(
                onClick = { visible = !visible },
                modifier = Modifier.testTag(visibilityTestTag),
            ) {
                Icon(
                    painter = painterResource(
                        if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                    ),
                    contentDescription = visibilityDescription,
                )
            }
        },
        singleLine = true,
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(fieldTestTag),
    )
}

@Composable
private fun SyncStatusCard(state: SyncRepositoryState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                stringResource(R.string.sync_status_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(state.status.labelResource()),
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(
                    R.string.sync_status_details,
                    state.pendingCount,
                    state.lastSuccessAt ?: stringResource(R.string.sync_never),
                ),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun SyncStatus.labelResource(): Int = when (this) {
    SyncStatus.Unconfigured -> R.string.sync_status_unconfigured
    SyncStatus.Enrolling -> R.string.sync_status_enrolling
    SyncStatus.Ready -> R.string.sync_status_ready
    SyncStatus.Syncing -> R.string.sync_status_syncing
    SyncStatus.Offline -> R.string.sync_status_offline
    SyncStatus.AuthError -> R.string.sync_status_auth_error
    SyncStatus.CryptoError -> R.string.sync_status_crypto_error
    SyncStatus.Incompatible -> R.string.sync_status_incompatible
}

private fun SyncEnrollmentOutcome.feedbackMessage(): Int? = when (this) {
    SyncEnrollmentOutcome.Enrolled -> null
    SyncEnrollmentOutcome.InvalidConfiguration -> R.string.sync_error_invalid_configuration
    SyncEnrollmentOutcome.AuthenticationFailed -> R.string.sync_error_authentication
    SyncEnrollmentOutcome.WrongPassphrase -> R.string.sync_error_wrong_passphrase
    SyncEnrollmentOutcome.IncompatibleServer -> R.string.sync_error_incompatible
    SyncEnrollmentOutcome.Failed -> R.string.sync_error_failed
}
