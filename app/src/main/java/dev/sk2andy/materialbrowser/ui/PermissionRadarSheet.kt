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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor

@Composable
internal fun PermissionRadarSheet(
    snapshot: PermissionRadarSnapshot,
    profileEmoji: String,
    websiteNotificationsSupported: Boolean,
    onOriginSelected: (String) -> Unit,
    onDecisionChanged: (SitePermission, SitePermissionDecision) -> Unit,
    onResetSite: () -> Unit,
    onDismiss: () -> Unit,
) {
    val site = snapshot.site
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(PermissionRadarTestTags.Sheet),
        containerColor = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerLow),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val isHttps = site.origin.startsWith("https://")
                    Icon(
                        if (isHttps) Icons.Default.Lock else Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(
                            if (isHttps) R.string.permission_site_https
                            else R.string.permission_site_http,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                snapshot.entries.forEach { entry ->
                    PermissionRadarRow(
                        entry,
                        snapshot.isPrivate,
                        websiteNotificationsSupported,
                        onDecisionChanged,
                    )
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
    isPrivate: Boolean,
    websiteNotificationsSupported: Boolean,
    onDecisionChanged: (SitePermission, SitePermissionDecision) -> Unit,
) {
    val notificationsUnavailable =
        entry.permission == SitePermission.Notifications && !websiteNotificationsSupported
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
                            notificationsUnavailable ->
                                stringResource(
                                    R.string.permission_notifications_system_webview_unavailable,
                                )
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
                if (!notificationsUnavailable) PermissionActivityDot(entry.activity)
            }
            if (isPrivate && entry.permission == SitePermission.Notifications &&
                !notificationsUnavailable
            ) {
                Text(
                    stringResource(R.string.permission_notifications_private_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (!notificationsUnavailable) {
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
    siteAvailable: Boolean,
    activityVisible: Boolean,
    isHttps: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!siteAvailable) return
    val description = stringResource(
        if (activityVisible) R.string.permission_radar_activity_cd
        else R.string.permission_radar_site_action,
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .semantics { contentDescription = description }
            .testTag(PermissionRadarTestTags.ActivityBadge),
        shape = CircleShape,
        color = if (activityVisible) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Icon(
                if (isHttps) Icons.Default.Lock else Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (activityVisible) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun PermissionPromptDialog(
    prompt: PermissionPrompt,
    onChoice: (PermissionPromptChoice) -> Unit,
    onShown: () -> Unit = {},
) {
    LaunchedEffect(prompt.id) { onShown() }
    val persistentNotificationPermission = SitePermission.Notifications in prompt.permissions
    val permissionNamesByType = mapOf(
        SitePermission.Camera to stringResource(R.string.permission_camera),
        SitePermission.Microphone to stringResource(R.string.permission_microphone),
        SitePermission.Location to stringResource(R.string.permission_location),
        SitePermission.Notifications to stringResource(R.string.permission_notifications),
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
                if (!persistentNotificationPermission) {
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
            }
        },
        confirmButton = {
            Button(onClick = {
                onChoice(
                    if (persistentNotificationPermission) PermissionPromptChoice.AllowAlways
                    else PermissionPromptChoice.AllowOnce,
                )
            }) {
                Text(
                    stringResource(
                        if (persistentNotificationPermission) R.string.permission_radar_allow_always
                        else R.string.permission_radar_allow_once,
                    ),
                )
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
    SitePermission.Notifications -> stringResource(R.string.permission_notifications)
    SitePermission.MidiSysex -> stringResource(R.string.permission_midi)
    SitePermission.ProtectedMedia -> stringResource(R.string.permission_protected_media)
}

private fun SitePermission.symbol(): String = when (this) {
    SitePermission.Camera -> "◉"
    SitePermission.Microphone -> "●"
    SitePermission.Location -> "⌖"
    SitePermission.Notifications -> "●"
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
