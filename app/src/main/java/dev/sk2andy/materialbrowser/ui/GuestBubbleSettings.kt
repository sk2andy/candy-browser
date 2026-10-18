package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.guest.GuestBubbleAuthenticator
import dev.sk2andy.materialbrowser.browser.guest.GuestBubbleSessionStore
import dev.sk2andy.materialbrowser.shared.ui.settings.settingsSearchTarget

internal object GuestBubbleSettingsTestTags {
    const val Toggle = "guest_bubble_settings_toggle"
    const val Tutorial = "guest_bubble_settings_tutorial"
    const val Activate = "guest_bubble_settings_activate"
    const val Cancel = "guest_bubble_settings_cancel"
}

@Composable
internal fun GuestBubbleSettings(profilesEnabled: Boolean) {
    val context = LocalContext.current
    val store = remember(context) { GuestBubbleSessionStore(context) }
    val enabled by rememberGuestBubbleEnabled(store)
    val authenticationAvailable = GuestBubbleAuthenticator.isAvailable(context)
    val available = profilesEnabled && authenticationAvailable && !store.isLocked()
    GuestBubbleSettingsControl(
        enabled = enabled,
        available = available,
        summary = stringResource(
            when {
                !profilesEnabled -> R.string.guest_bubble_requires_profiles
                !authenticationAvailable -> R.string.guest_bubble_requires_authentication
                else -> R.string.guest_bubble_settings_summary
            },
        ),
        onEnabledChanged = { nextEnabled ->
            store.setEnabled(nextEnabled)
        },
    )
}

@Composable
internal fun rememberGuestBubbleEnabled(store: GuestBubbleSessionStore): State<Boolean> {
    val enabled = remember(store) { mutableStateOf(store.isEnabled()) }
    DisposableEffect(store) {
        val observer = store.observeEnabled { enabled.value = it }
        enabled.value = store.isEnabled()
        onDispose { observer.close() }
    }
    return enabled
}

@Composable
internal fun GuestBubbleSettingsControl(
    enabled: Boolean,
    available: Boolean,
    summary: String,
    onEnabledChanged: (Boolean) -> Unit,
) {
    var tutorialVisible by remember { mutableStateOf(false) }
    SettingsSwitch(
        title = stringResource(R.string.guest_bubble_title),
        subtitle = summary,
        checked = enabled,
        enabled = available,
        onCheckedChange = { nextEnabled ->
            if (nextEnabled) {
                tutorialVisible = true
            } else {
                onEnabledChanged(false)
            }
        },
        modifier = Modifier.testTag(GuestBubbleSettingsTestTags.Toggle)
            .padding(horizontal = 18.dp)
            .settingsSearchTarget(stringResource(R.string.guest_bubble_title)),
    )
    if (tutorialVisible && available) {
        AlertDialog(
            onDismissRequest = { tutorialVisible = false },
            modifier = Modifier.testTag(GuestBubbleSettingsTestTags.Tutorial),
            title = { Text(stringResource(R.string.guest_bubble_tutorial_title)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(stringResource(R.string.guest_bubble_tutorial_start))
                    Text(stringResource(R.string.guest_bubble_tutorial_isolation))
                    Text(stringResource(R.string.guest_bubble_tutorial_exit))
                    Text(
                        stringResource(R.string.guest_bubble_tutorial_limits),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEnabledChanged(true)
                        tutorialVisible = false
                    },
                    modifier = Modifier.testTag(GuestBubbleSettingsTestTags.Activate),
                ) {
                    Text(stringResource(R.string.guest_bubble_activate))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { tutorialVisible = false },
                    modifier = Modifier.testTag(GuestBubbleSettingsTestTags.Cancel),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
