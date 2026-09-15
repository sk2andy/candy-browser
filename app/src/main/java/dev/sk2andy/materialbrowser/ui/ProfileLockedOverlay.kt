package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sk2andy.materialbrowser.R

@Composable
internal fun ProfileLockedOverlay(
    profileEmoji: String,
    unlockAvailable: Boolean,
    canSwitchProfile: Boolean,
    onUnlock: () -> Unit,
    onSwitchProfile: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ProfileProtectionTestTags.LockedOverlay),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(profileEmoji, fontSize = 52.sp)
            Text(
                text = stringResource(R.string.profile_locked_title),
                modifier = Modifier.padding(top = 20.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    if (unlockAvailable) {
                        R.string.profile_locked_message
                    } else {
                        R.string.profile_protection_unavailable
                    },
                ),
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onUnlock, enabled = unlockAvailable) {
                Text(stringResource(R.string.profile_unlock_action))
            }
            if (canSwitchProfile) {
                TextButton(onClick = onSwitchProfile) {
                    Text(stringResource(R.string.command_switch_profile_name))
                }
            }
        }
    }
}

internal object ProfileProtectionTestTags {
    const val LockedOverlay = "profile_locked_overlay"
}
