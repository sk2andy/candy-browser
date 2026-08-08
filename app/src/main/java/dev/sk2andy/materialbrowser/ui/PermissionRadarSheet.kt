@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.permissions.PermissionPrompt
import dev.sk2andy.materialbrowser.browser.permissions.PermissionPromptChoice
import dev.sk2andy.materialbrowser.browser.permissions.PermissionRadarEntry
import dev.sk2andy.materialbrowser.browser.permissions.PermissionRadarSnapshot
import dev.sk2andy.materialbrowser.browser.permissions.SitePermission
import dev.sk2andy.materialbrowser.browser.permissions.SitePermissionActivity
import dev.sk2andy.materialbrowser.browser.permissions.SitePermissionDecision

@Composable
internal fun PermissionRadarSheet(
    snapshot: PermissionRadarSnapshot,
    profileEmoji: String,
    onOriginSelected: (String) -> Unit,
    onDecisionChanged: (SitePermission, SitePermissionDecision) -> Unit,
    onResetSite: () -> Unit,
    onDismiss: () -> Unit,
) {
    val site = snapshot.site
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(PermissionRadarTestTags.Sheet),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.permission_radar_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(
                    if (snapshot.isPrivate) {
                        R.string.permission_radar_private_summary
                    } else {
                        R.string.permission_radar_profile_summary
                    },
                    profileEmoji,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            if (snapshot.knownOrigins.size > 1) {
                Text(
                    stringResource(R.string.permission_radar_sites),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    snapshot.knownOrigins.forEach { origin ->
                        FilterChip(
                            selected = origin == site?.origin,
                            onClick = { onOriginSelected(origin) },
                            label = {
                                Text(
                                    origin,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (site == null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        stringResource(R.string.permission_radar_no_site),
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    site.origin,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(10.dp))
                snapshot.entries.forEach { entry ->
                    PermissionRadarRow(entry, onDecisionChanged)
                    Spacer(Modifier.height(8.dp))
                }
                TextButton(
                    onClick = onResetSite,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.permission_radar_reset_site))
                }
            }
        }
    }
}

@Composable
private fun PermissionRadarRow(
    entry: PermissionRadarEntry,
    onDecisionChanged: (SitePermission, SitePermissionDecision) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = when (entry.activity) {
            SitePermissionActivity.Active -> MaterialTheme.colorScheme.primaryContainer
            SitePermissionActivity.Pending -> MaterialTheme.colorScheme.tertiaryContainer
            SitePermissionActivity.Idle -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.permission.symbol(), style = MaterialTheme.typography.titleLarge)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(
                        entry.permission.displayName(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        when {
                            entry.activity == SitePermissionActivity.Active ->
                                stringResource(R.string.permission_radar_active)
                            entry.activity == SitePermissionActivity.Pending ->
                                stringResource(R.string.permission_radar_pending)
                            entry.allowedForSession ->
                                stringResource(R.string.permission_radar_session_allowed)
                            else -> entry.decision.displayName()
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PermissionActivityDot(entry.activity)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SitePermissionDecision.entries.forEach { decision ->
                    FilterChip(
                        selected = entry.decision == decision && !entry.allowedForSession,
                        onClick = { onDecisionChanged(entry.permission, decision) },
                        label = { Text(decision.displayName()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionActivityDot(activity: SitePermissionActivity) {
    if (activity == SitePermissionActivity.Idle) return
    val description = stringResource(
        if (activity == SitePermissionActivity.Active) {
            R.string.permission_radar_active
        } else {
            R.string.permission_radar_pending
        },
    )
    Surface(
        modifier = Modifier
            .size(12.dp)
            .semantics { contentDescription = description },
        shape = CircleShape,
        color = if (activity == SitePermissionActivity.Active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.tertiary
        },
    ) {}
}

@Composable
internal fun PermissionRadarBadge(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val description = stringResource(R.string.permission_radar_activity_cd)
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .semantics { contentDescription = description }
            .testTag(PermissionRadarTestTags.ActivityBadge),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Text("◉", color = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun PermissionPromptDialog(
    prompt: PermissionPrompt,
    onChoice: (PermissionPromptChoice) -> Unit,
) {
    val permissionNamesByType = mapOf(
        SitePermission.Camera to stringResource(R.string.permission_camera),
        SitePermission.Microphone to stringResource(R.string.permission_microphone),
        SitePermission.Location to stringResource(R.string.permission_location),
        SitePermission.MidiSysex to stringResource(R.string.permission_midi),
        SitePermission.ProtectedMedia to stringResource(R.string.permission_protected_media),
    )
    val permissionNames = prompt.permissions.joinToString { permission ->
        permissionNamesByType.getValue(permission)
    }
    AlertDialog(
        onDismissRequest = { onChoice(PermissionPromptChoice.Block) },
        modifier = Modifier.testTag(PermissionRadarTestTags.Prompt),
        title = { Text(stringResource(R.string.permission_radar_request_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(
                        R.string.permission_radar_request_message,
                        prompt.site.origin,
                        permissionNames,
                    ),
                )
                if (prompt.isPrivate) {
                    Text(
                        stringResource(R.string.permission_radar_private_request_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onChoice(PermissionPromptChoice.AllowAlways) }) {
                    Text(
                        stringResource(
                            if (prompt.isPrivate) {
                                R.string.permission_radar_allow_private
                            } else {
                                R.string.permission_radar_allow_always
                            },
                        ),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onChoice(PermissionPromptChoice.AllowOnce) }) {
                Text(stringResource(R.string.permission_radar_allow_once))
            }
        },
        dismissButton = {
            TextButton(onClick = { onChoice(PermissionPromptChoice.Block) }) {
                Text(stringResource(R.string.permission_radar_block))
            }
        },
    )
}

@Composable
private fun SitePermission.displayName(): String = when (this) {
    SitePermission.Camera -> stringResource(R.string.permission_camera)
    SitePermission.Microphone -> stringResource(R.string.permission_microphone)
    SitePermission.Location -> stringResource(R.string.permission_location)
    SitePermission.MidiSysex -> stringResource(R.string.permission_midi)
    SitePermission.ProtectedMedia -> stringResource(R.string.permission_protected_media)
}

private fun SitePermission.symbol(): String = when (this) {
    SitePermission.Camera -> "◉"
    SitePermission.Microphone -> "●"
    SitePermission.Location -> "⌖"
    SitePermission.MidiSysex -> "♫"
    SitePermission.ProtectedMedia -> "◆"
}

@Composable
private fun SitePermissionDecision.displayName(): String = when (this) {
    SitePermissionDecision.Ask -> stringResource(R.string.permission_decision_ask)
    SitePermissionDecision.Allow -> stringResource(R.string.permission_decision_allow)
    SitePermissionDecision.Block -> stringResource(R.string.permission_decision_block)
}

internal object PermissionRadarTestTags {
    const val Sheet = "permission_radar_sheet"
    const val Prompt = "permission_radar_prompt"
    const val ActivityBadge = "permission_radar_activity_badge"
}
