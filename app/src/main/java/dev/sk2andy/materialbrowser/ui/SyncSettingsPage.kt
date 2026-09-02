package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.sync.SyncConnectionSettings
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconCatalog
import dev.sk2andy.materialbrowser.sync.SyncEnrollmentOutcome
import dev.sk2andy.materialbrowser.sync.SyncRepositoryState
import dev.sk2andy.materialbrowser.sync.SyncStatus
import kotlin.math.roundToInt

internal object SyncSettingsTestTags {
    const val Endpoint = "sync_settings_endpoint"
    const val Username = "sync_settings_username"
    const val Password = "sync_settings_password"
    const val Passphrase = "sync_settings_passphrase"
    const val PassphraseConfirmation = "sync_settings_passphrase_confirmation"
    const val DeviceName = "sync_settings_device_name"
    const val Icon = "sync_settings_icon"
    const val Enroll = "sync_settings_enroll"
    const val Refresh = "sync_settings_refresh"
}

@Composable
internal fun SyncSettingsPage(
    state: SyncRepositoryState,
    iconCatalog: SyncDeviceIconCatalog,
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
    var submitting by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<Int?>(null) }
    val selectedIcon = iconCatalog.icons.firstOrNull { it.id == iconCatalogId }
        ?: iconCatalog.icons.first()

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
        OutlinedTextField(
            value = serverPassword,
            onValueChange = { serverPassword = it },
            label = { Text(stringResource(R.string.sync_server_password_label)) },
            supportingText = { Text(stringResource(R.string.sync_server_password_summary)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().testTag(SyncSettingsTestTags.Password),
        )

        Text(
            text = stringResource(R.string.sync_this_device_section),
            modifier = Modifier.padding(start = 18.dp, top = 20.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
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
        Text(
            stringResource(R.string.sync_accent_hue_label, iconAccentHue),
            modifier = Modifier.padding(start = 18.dp, top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = iconAccentHue.toFloat(),
            onValueChange = { iconAccentHue = it.roundToInt() },
            valueRange = 0f..359f,
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
        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text(stringResource(R.string.sync_passphrase_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().testTag(SyncSettingsTestTags.Passphrase),
        )
        OutlinedTextField(
            value = passphraseConfirmation,
            onValueChange = { passphraseConfirmation = it },
            label = { Text(stringResource(R.string.sync_passphrase_confirm_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SyncSettingsTestTags.PassphraseConfirmation),
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
