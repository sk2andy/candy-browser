@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.sk2andy.materialbrowser.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.SiteConnectionKind
import dev.sk2andy.materialbrowser.browser.permissions.PermissionPrompt
import dev.sk2andy.materialbrowser.browser.permissions.PermissionPromptChoice
import dev.sk2andy.materialbrowser.browser.permissions.PermissionRadarEntry
import dev.sk2andy.materialbrowser.browser.permissions.PermissionRadarSnapshot
import dev.sk2andy.materialbrowser.browser.permissions.SitePermission
import dev.sk2andy.materialbrowser.browser.permissions.SitePermissionActivity
import dev.sk2andy.materialbrowser.browser.permissions.SitePermissionDecision
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor
import kotlinx.coroutines.delay

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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(PermissionRadarTestTags.Sheet),
        containerColor = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        PermissionRadarContent(
            snapshot = snapshot,
            profileEmoji = profileEmoji,
            websiteNotificationsSupported = websiteNotificationsSupported,
            onOriginSelected = onOriginSelected,
            onDecisionChanged = onDecisionChanged,
            onResetSite = onResetSite,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        )
    }
}

@Composable
internal fun PermissionRadarContent(
    snapshot: PermissionRadarSnapshot,
    profileEmoji: String,
    websiteNotificationsSupported: Boolean,
    onOriginSelected: (String) -> Unit,
    onDecisionChanged: (SitePermission, SitePermissionDecision) -> Unit,
    onResetSite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val site = snapshot.site
    Column(modifier = modifier.fillMaxWidth()) {
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
    connectionKind: SiteConnectionKind,
    blockedCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tabId: String? = null,
) {
    if (!siteAvailable) return
    val currentBlockedCount by rememberUpdatedState(blockedCount)
    val initialBlockedCount = remember(tabId) { blockedCount }
    val pulseScale = remember(tabId) { Animatable(1f) }
    LaunchedEffect(tabId) {
        var previousCount = initialBlockedCount
        var lastPulseCompletedAtMillis: Long? = null
        snapshotFlow { currentBlockedCount }.collect { count ->
            val previous = previousCount
            previousCount = count
            val now = SystemClock.uptimeMillis()
            val elapsedSinceLastPulse = lastPulseCompletedAtMillis?.let { now - it }
                ?: Long.MAX_VALUE
            val pulseDelay = PrivacyXRayMotionRules.badgePulseDelayMillis(
                previousCount = previous,
                currentCount = count,
                elapsedSinceLastPulseMillis = elapsedSinceLastPulse,
            ) ?: return@collect

            delay(pulseDelay)
            val batchedCount = currentBlockedCount
            previousCount = batchedCount
            if (!PrivacyXRayMotionRules.shouldRunBatchedPulse(count, batchedCount)) {
                return@collect
            }
            pulseScale.animateTo(
                targetValue = 1.1f,
                animationSpec = tween(110, easing = FastOutSlowInEasing),
            )
            pulseScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(190, easing = FastOutSlowInEasing),
            )
            lastPulseCompletedAtMillis = SystemClock.uptimeMillis()
        }
    }
    val connectionLabel = stringResource(
        when (connectionKind) {
            SiteConnectionKind.Https -> R.string.site_connection_https
            SiteConnectionKind.Http -> R.string.site_connection_http
            SiteConnectionKind.Unavailable -> R.string.site_connection_unavailable
            SiteConnectionKind.Other -> R.string.site_connection_other
        },
    )
    val siteDescription = stringResource(
        R.string.site_info_badge_cd,
        connectionLabel,
        blockedCount,
    )
    val description = if (activityVisible) {
        "$siteDescription. ${stringResource(R.string.permission_radar_activity_cd)}"
    } else {
        siteDescription
    }
    IconButton(
        onClick = onClick,
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .graphicsLayer {
                scaleX = pulseScale.value
                scaleY = pulseScale.value
            }
            .semantics { contentDescription = description }
            .testTag(PermissionRadarTestTags.ActivityBadge),
    ) {
        Icon(
            when (connectionKind) {
                SiteConnectionKind.Https -> Icons.Default.Lock
                SiteConnectionKind.Http -> Icons.Default.Warning
                SiteConnectionKind.Unavailable, SiteConnectionKind.Other -> Icons.Default.Info
            },
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (activityVisible) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
