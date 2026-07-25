@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.sk2andy.materialbrowser.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.blocking.BlockerSettings
import dev.sk2andy.materialbrowser.blocking.PrivacyDomainSummary
import dev.sk2andy.materialbrowser.blocking.PrivacyPartyRelation
import dev.sk2andy.materialbrowser.blocking.PrivacyRequestCategory
import dev.sk2andy.materialbrowser.blocking.PrivacyXRaySnapshot
import dev.sk2andy.materialbrowser.blocking.SiteProtectionState

internal object PrivacyXRayTestTags {
    const val Counter = "privacy_xray_counter"
    const val SettingsCounter = "privacy_xray_settings_counter"
    const val Sheet = "privacy_xray_sheet"
    const val Total = "privacy_xray_total"
    const val Domains = "privacy_xray_domains"
    const val ToggleDetails = "privacy_xray_toggle_details"
    const val Pause = "privacy_xray_pause"
    const val Warning = "privacy_xray_warning"
    const val PauseTemporary = "privacy_xray_pause_temporary"
    const val PausePersistent = "privacy_xray_pause_persistent"
}

@Composable
internal fun PrivacyXRayBadge(
    blockedCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.privacy_xray_counter_cd, blockedCount)
    Surface(
        onClick = onClick,
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .testTag(PrivacyXRayTestTags.Counter)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "◈ $blockedCount",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun PrivacyXRaySheet(
    snapshot: PrivacyXRaySnapshot,
    blockerSettings: BlockerSettings,
    siteState: SiteProtectionState,
    onPause: (persistently: Boolean) -> Unit,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(R.string.privacy_xray_title)
    var pauseWarningVisible by remember(siteState.host) { mutableStateOf(false) }
    val view = LocalView.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .testTag(PrivacyXRayTestTags.Sheet)
            .semantics { paneTitle = title },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        PrivacyXRayContent(
            snapshot = snapshot,
            blockerSettings = blockerSettings,
            siteState = siteState,
            onPauseClick = { pauseWarningVisible = true },
            onResumeClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                onResume()
            },
        )
    }

    if (pauseWarningVisible) {
        AlertDialog(
            onDismissRequest = { pauseWarningVisible = false },
            modifier = Modifier.testTag(PrivacyXRayTestTags.Warning),
            title = { Text(stringResource(R.string.privacy_pause_warning_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.privacy_pause_warning_message,
                        siteState.host.orEmpty(),
                    ),
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(
                        onClick = {
                            pauseWarningVisible = false
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            onPause(false)
                        },
                        modifier = Modifier.testTag(PrivacyXRayTestTags.PauseTemporary),
                    ) {
                        Text(stringResource(R.string.privacy_pause_temporary))
                    }
                    if (siteState.canPersist) {
                        TextButton(
                            onClick = {
                                pauseWarningVisible = false
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                onPause(true)
                            },
                            modifier = Modifier.testTag(PrivacyXRayTestTags.PausePersistent),
                        ) {
                            Text(stringResource(R.string.privacy_pause_persistent))
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pauseWarningVisible = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
internal fun PrivacyXRayContent(
    snapshot: PrivacyXRaySnapshot,
    blockerSettings: BlockerSettings,
    siteState: SiteProtectionState,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
) {
    var detailsExpanded by remember { mutableStateOf(false) }
    val visibleDomains = if (detailsExpanded) snapshot.domains else snapshot.domains.take(3)
    val categoryColors = mapOf(
        PrivacyRequestCategory.Advertising to MaterialTheme.colorScheme.primary,
        PrivacyRequestCategory.Analytics to MaterialTheme.colorScheme.tertiary,
        PrivacyRequestCategory.Social to MaterialTheme.colorScheme.secondary,
        PrivacyRequestCategory.Other to MaterialTheme.colorScheme.outline,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 720.dp)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
    ) {
        Text(
            stringResource(R.string.privacy_xray_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        siteState.host?.let { host ->
            Text(
                stringResource(R.string.privacy_xray_subtitle, host),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(18.dp))
        PrivacyHero(snapshot, categoryColors)
        Spacer(Modifier.height(20.dp))

        SectionTitle(stringResource(R.string.privacy_xray_categories))
        Spacer(Modifier.height(8.dp))
        PrivacyCategoryRows(snapshot, categoryColors)
        Spacer(Modifier.height(20.dp))

        SectionTitle(stringResource(R.string.privacy_xray_domains))
        Spacer(Modifier.height(8.dp))
        if (snapshot.domains.isEmpty()) {
            Text(
                stringResource(R.string.privacy_xray_no_requests),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(modifier = Modifier.testTag(PrivacyXRayTestTags.Domains)) {
                val maximum = snapshot.domains.maxOf(PrivacyDomainSummary::blockedCount)
                visibleDomains.forEach { domain ->
                    PrivacyDomainBar(domain, maximum, categoryColors.getValue(domain.category))
                    Spacer(Modifier.height(10.dp))
                }
            }
            if (snapshot.domains.size > 3) {
                val chevronRotation by animateFloatAsState(
                    targetValue = if (detailsExpanded) 180f else 0f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 620f),
                    label = "Privacy details chevron",
                )
                Surface(
                    onClick = { detailsExpanded = !detailsExpanded },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PrivacyXRayTestTags.ToggleDetails),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(
                                if (detailsExpanded) {
                                    R.string.privacy_xray_hide_details
                                } else {
                                    R.string.privacy_xray_show_details
                                },
                            ),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = snapshot.omittedDomainRequests > 0,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(90)),
            ) {
                Text(
                    stringResource(
                        R.string.privacy_xray_omitted_domains,
                        snapshot.omittedDomainRequests,
                    ),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        PrivacyPolicyCard(blockerSettings, siteState)
        siteState.host?.let {
            Spacer(Modifier.height(18.dp))
            SiteProtectionCard(siteState, onPauseClick, onResumeClick)
        }
    }
}

@Composable
private fun PrivacyHero(
    snapshot: PrivacyXRaySnapshot,
    categoryColors: Map<PrivacyRequestCategory, Color>,
) {
    val contentColor = TabOverviewContrastRules.titleContentColor(
        primaryContainer = MaterialTheme.colorScheme.primaryContainer,
        tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer,
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ),
                )
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(106.dp)
                    .testTag(PrivacyXRayTestTags.Total),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(100.dp)) {
                    val stroke = 8.dp.toPx()
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(stroke / 2f, stroke / 2f)
                    var start = -90f
                    val total = snapshot.totalBlocked.coerceAtLeast(1)
                    PrivacyRequestCategory.entries.forEach { category ->
                        val count = snapshot.categoryCounts[category] ?: 0
                        if (count > 0) {
                            val sweep = 360f * count / total
                            drawArc(
                                color = categoryColors.getValue(category),
                                startAngle = start,
                                sweepAngle = (sweep - 3f).coerceAtLeast(1f),
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                            )
                            start += sweep
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedContent(
                        targetState = snapshot.totalBlocked,
                        transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(90)) },
                        label = "Privacy blocked total",
                    ) { count ->
                        Text(
                            count.toString(),
                            color = contentColor,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        stringResource(R.string.privacy_xray_total_label),
                        color = contentColor,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.privacy_xray_live_note),
                    color = contentColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.privacy_xray_scope_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                )
            }
        }
    }
}

@Composable
private fun PrivacyCategoryRows(
    snapshot: PrivacyXRaySnapshot,
    colors: Map<PrivacyRequestCategory, Color>,
) {
    val categories = PrivacyRequestCategory.entries.filter { snapshot.categoryCounts[it] != null }
    if (categories.isEmpty()) {
        Text(
            stringResource(R.string.privacy_xray_no_requests),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        categories.forEach { category ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(colors.getValue(category)),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    category.label(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    snapshot.categoryCounts.getValue(category).toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun PrivacyDomainBar(
    domain: PrivacyDomainSummary,
    maximumCount: Int,
    color: Color,
) {
    val targetProgress = domain.blockedCount.toFloat() / maximumCount.coerceAtLeast(1)
    val progress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(180),
        label = "Privacy domain bar",
    )
    val party = domain.partyRelation.label()
    val description = stringResource(
        R.string.privacy_domain_cd,
        domain.host,
        domain.blockedCount,
        party,
    )
    Column(
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                domain.host,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                domain.blockedCount.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .graphicsLayer {
                        scaleX = progress
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .background(color),
            )
        }
        Text(
            party,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PrivacyPolicyCard(
    settings: BlockerSettings,
    siteState: SiteProtectionState,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionTitle(stringResource(R.string.privacy_policy_title))
            Spacer(Modifier.height(10.dp))
            PolicyRow(
                stringResource(R.string.privacy_ads_tracker_protection),
                active = settings.blockAdsAndTrackers && !siteState.isPaused,
                activeLabel = stringResource(R.string.privacy_policy_active),
            )
            HorizontalDivider(Modifier.padding(vertical = 9.dp))
            PolicyRow(
                stringResource(R.string.privacy_third_party_cookie_policy),
                active = settings.blockThirdPartyCookies && !siteState.isPaused,
                activeLabel = stringResource(R.string.privacy_policy_blocked),
                inactiveLabel = stringResource(R.string.privacy_policy_allowed),
            )
            HorizontalDivider(Modifier.padding(vertical = 9.dp))
            PolicyRow(
                stringResource(R.string.privacy_cookie_banner_protection),
                active = settings.hideCookieConsent && !siteState.isPaused,
                activeLabel = stringResource(R.string.privacy_policy_active),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.privacy_policy_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PolicyRow(
    title: String,
    active: Boolean,
    activeLabel: String,
    inactiveLabel: String = stringResource(R.string.privacy_policy_off),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            if (active) activeLabel else inactiveLabel,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SiteProtectionCard(
    siteState: SiteProtectionState,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (siteState.isPaused) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.privacy_site_protection),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(
                    when {
                        siteState.isPersistent -> R.string.privacy_site_paused_persistent
                        siteState.isPaused -> R.string.privacy_site_paused_temporary
                        else -> R.string.privacy_site_active
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            if (siteState.isPaused) {
                Button(onClick = onResumeClick, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.privacy_resume_site))
                }
            } else {
                OutlinedButton(
                    onClick = onPauseClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PrivacyXRayTestTags.Pause),
                ) {
                    Text(stringResource(R.string.privacy_pause_site))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun PrivacyRequestCategory.label(): String = stringResource(
    when (this) {
        PrivacyRequestCategory.Advertising -> R.string.privacy_category_advertising
        PrivacyRequestCategory.Analytics -> R.string.privacy_category_analytics
        PrivacyRequestCategory.Social -> R.string.privacy_category_social
        PrivacyRequestCategory.Other -> R.string.privacy_category_other
    },
)

@Composable
private fun PrivacyPartyRelation.label(): String = stringResource(
    when (this) {
        PrivacyPartyRelation.FirstParty -> R.string.privacy_party_first
        PrivacyPartyRelation.ThirdParty -> R.string.privacy_party_third
        PrivacyPartyRelation.Unknown -> R.string.privacy_party_unknown
    },
)
