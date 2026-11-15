package dev.sk2andy.materialbrowser.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.sync.MozillaSyncState
import dev.sk2andy.materialbrowser.sync.MozillaSyncStatus

@Composable
internal fun MozillaSyncSettings(
    state: MozillaSyncState,
    localProfiles: List<BrowserProfile>,
    activeProfileId: String,
    onConnect: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var profileId by rememberSaveable(state.localProfileId, activeProfileId) {
        mutableStateOf(
            state.localProfileId ?: localProfiles.firstOrNull { it.id == activeProfileId }?.id
                ?: localProfiles.firstOrNull()?.id,
        )
    }
    var deviceName by rememberSaveable(state.deviceName) {
        mutableStateOf(state.deviceName.ifBlank { "Candy Browser ${Build.MODEL}" })
    }
    var profileMenuExpanded by remember { mutableStateOf(false) }
    val selectedProfile = localProfiles.firstOrNull { it.id == profileId }
    val busy = state.status == MozillaSyncStatus.Connecting
    val connected = state.status == MozillaSyncStatus.Connected || state.currentDeviceId != null

    SettingsPreferenceGroup {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.mozilla_sync_summary))
            Text(
                stringResource(state.status.labelResource()),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.testTag("mozilla_sync_status"),
            )
            if (state.status == MozillaSyncStatus.Unavailable) {
                Text(
                    stringResource(R.string.mozilla_sync_unavailable_summary),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Box {
                    TextButton(
                        onClick = { profileMenuExpanded = true },
                        enabled = !busy && !connected,
                        modifier = Modifier.testTag("mozilla_sync_profile"),
                    ) {
                        Text("${selectedProfile?.emoji.orEmpty()} ${stringResource(R.string.sync_local_profile_label)}")
                    }
                    SettingsDropdown(
                        expanded = profileMenuExpanded,
                        onDismissRequest = { profileMenuExpanded = false },
                    ) {
                        localProfiles.forEach { profile ->
                            SettingsDropdownItem(
                                label = profile.emoji,
                                selected = profile.id == profileId,
                                onClick = {
                                    profileId = profile.id
                                    profileMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                if (!connected || state.status == MozillaSyncStatus.Error) {
                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = { deviceName = it },
                        label = { Text(stringResource(R.string.sync_device_name_label)) },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().testTag("mozilla_sync_device_name"),
                    )
                    Button(
                        onClick = { selectedProfile?.let { onConnect(it.id, deviceName.trim()) } },
                        enabled = !busy && selectedProfile != null && deviceName.isNotBlank(),
                        modifier = Modifier.testTag("mozilla_sync_connect"),
                    ) {
                        Text(stringResource(R.string.mozilla_sync_sign_in))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (connected) {
                        TextButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.testTag("mozilla_sync_refresh")) {
                            Text(stringResource(R.string.sync_action_refresh))
                        }
                    }
                    if (connected || busy || state.status == MozillaSyncStatus.Error) {
                        TextButton(onClick = onDisconnect, modifier = Modifier.testTag("mozilla_sync_disconnect")) {
                            Text(stringResource(R.string.mozilla_sync_disconnect))
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.mozilla_sync_parallel_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun MozillaSyncStatus.labelResource(): Int = when (this) {
    MozillaSyncStatus.Unavailable -> R.string.mozilla_sync_unavailable
    MozillaSyncStatus.SignedOut -> R.string.mozilla_sync_signed_out
    MozillaSyncStatus.Connecting -> R.string.mozilla_sync_connecting
    MozillaSyncStatus.Connected -> R.string.mozilla_sync_connected
    MozillaSyncStatus.Error -> R.string.mozilla_sync_error
}
