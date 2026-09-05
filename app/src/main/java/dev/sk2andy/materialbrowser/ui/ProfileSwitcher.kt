@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.isSyncLinked
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor

internal object ProfileSwitcherTestTags {
    const val Switcher = "profile_switcher"
    const val Add = "profile_switcher_add"

    fun profile(profileId: String): String = "profile_switcher_profile:$profileId"
    fun syncedBadge(profileId: String): String = "profile_switcher_synced_badge:$profileId"
}

@Composable
internal fun ProfileSwitcher(
    profiles: List<BrowserProfile>,
    activeProfileId: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onLongClick: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profileDescription = stringResource(R.string.cd_profile)
    val scrollState = rememberScrollState()
    val activeIndex = profiles.indexOfFirst { it.id == activeProfileId }.coerceAtLeast(0)
    val indicatorSlotOffset by animateDpAsState(
        targetValue = (activeIndex * PROFILE_SLOT_WIDTH).dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 430f),
        label = "profile-indicator-offset",
    )
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(PROFILE_SWITCHER_LAYOUT_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        val profileContentWidth = (profiles.size * PROFILE_SLOT_WIDTH).dp
        val barWidth = (profileContentWidth + PROFILE_ACTION_SECTION_WIDTH.dp)
            .coerceAtMost(maxWidth - 24.dp)
            .coerceAtLeast(PROFILE_SWITCHER_MIN_WIDTH.dp)
        val profileViewportWidth = barWidth - PROFILE_ACTION_SECTION_WIDTH.dp
        val density = LocalDensity.current
        LaunchedEffect(activeIndex, profiles.size, profileViewportWidth) {
            withFrameNanos { }
            val slotWidthPx = with(density) { PROFILE_SLOT_WIDTH.dp.roundToPx() }
            val viewportWidthPx = with(density) { profileViewportWidth.roundToPx() }
            val selectedStart = activeIndex * slotWidthPx
            val selectedEnd = selectedStart + slotWidthPx
            val targetScroll = when {
                selectedStart < scrollState.value -> selectedStart
                selectedEnd > scrollState.value + viewportWidthPx ->
                    selectedEnd - viewportWidthPx
                else -> scrollState.value
            }.coerceIn(0, scrollState.maxValue)
            if (targetScroll != scrollState.value) scrollState.animateScrollTo(targetScroll)
        }
        Surface(
            modifier = Modifier
                .testTag(ProfileSwitcherTestTags.Switcher)
                .width(barWidth)
                .height(60.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = browserChromeColor(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                frostedAlpha = 0.88f,
            ),
            tonalElevation = 6.dp,
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(profileViewportWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(28.dp))
                        .horizontalScroll(scrollState),
                ) {
                    Box(
                        modifier = Modifier
                            .width(profileContentWidth)
                            .fillMaxHeight(),
                    ) {
                        Row(modifier = Modifier.fillMaxHeight()) {
                            profiles.forEach { profile ->
                                val isSelected = profile.id == activeProfileId
                                val syncedAccent = profile.syncedIconAccentHue?.let { hue ->
                                    Color.hsv(hue.toFloat(), 0.42f, 0.86f)
                                }
                                val profileContainerColor by animateColorAsState(
                                    targetValue = when {
                                        !enabled -> MaterialTheme.colorScheme
                                            .surfaceContainerHighest
                                            .copy(alpha = 0.38f)
                                        syncedAccent != null -> syncedAccent.copy(
                                            alpha = if (isSelected) 0.42f else 0.24f,
                                        )
                                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                                    animationSpec = tween(durationMillis = 180),
                                    label = "profile-container-color",
                                )
                                val profileElevation by animateDpAsState(
                                    targetValue = if (isSelected) 2.dp else 0.dp,
                                    animationSpec = tween(durationMillis = 180),
                                    label = "profile-container-elevation",
                                )
                                val profileContainerSize by animateDpAsState(
                                    targetValue = if (isSelected) 48.dp else 44.dp,
                                    animationSpec = spring(
                                        dampingRatio = 0.72f,
                                        stiffness = 430f,
                                    ),
                                    label = "profile-container-size",
                                )
                                val scale by animateFloatAsState(
                                    targetValue = if (isSelected) 1.02f else 0.92f,
                                    animationSpec = spring(
                                        dampingRatio = 0.68f,
                                        stiffness = 540f,
                                    ),
                                    label = "profile-emoji-scale",
                                )
                                Box(
                                    modifier = Modifier
                                        .width(PROFILE_SLOT_WIDTH.dp)
                                        .fillMaxHeight()
                                        .testTag(
                                            ProfileSwitcherTestTags.profile(profile.id),
                                        )
                                        .semantics {
                                            val profileLabel = profile.syncedDisplayName
                                                ?: profile.syncedIconEmoji
                                                ?: profile.emoji
                                            contentDescription = "$profileDescription $profileLabel"
                                            selected = isSelected
                                        }
                                        .clip(CircleShape)
                                        .combinedClickable(
                                            enabled = enabled,
                                            role = Role.Tab,
                                            onClick = { onSelect(profile.id) },
                                            onLongClick = { onLongClick(profile.id) },
                                            onLongClickLabel = stringResource(
                                                R.string.action_edit_profile,
                                            ),
                                    ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Surface(
                                        modifier = Modifier.size(profileContainerSize),
                                        shape = CircleShape,
                                        color = profileContainerColor,
                                        tonalElevation = profileElevation,
                                        shadowElevation = profileElevation,
                                    ) {}
                                    AnimatedContent(
                                        targetState = profile.syncedIconEmoji ?: profile.emoji,
                                        transitionSpec = {
                                            fadeIn(tween(150)) togetherWith fadeOut(tween(90))
                                        },
                                        label = "profile-emoji",
                                    ) { emoji ->
                                        Text(
                                            text = emoji,
                                            modifier = Modifier.graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
                                                alpha = if (enabled) 1f else 0.38f
                                            },
                                            fontSize = 25.sp,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                    if (profile.isSyncLinked) {
                                        Surface(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .offset(x = (-6).dp, y = (-6).dp)
                                                .size(17.dp)
                                                .testTag(
                                                    ProfileSwitcherTestTags.syncedBadge(profile.id),
                                                ),
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                            tonalElevation = 2.dp,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.padding(3.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset {
                                    IntOffset(
                                        x = (indicatorSlotOffset + 2.dp).roundToPx(),
                                        y = 0,
                                    )
                                }
                                .size(48.dp)
                                .border(
                                    width = 2.dp,
                                    color = if (enabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                    },
                                    shape = CircleShape,
                                ),
                        )
                    }
                }
                Spacer(Modifier.width(5.dp))
                VerticalDivider(
                    modifier = Modifier.height(32.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(
                        alpha = if (enabled) 0.62f else 0.22f,
                    ),
                )
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier.size(52.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.38f)
                        },
                        tonalElevation = 1.dp,
                    ) {}
                    IconButton(
                        onClick = onAdd,
                        enabled = enabled,
                        modifier = Modifier
                            .size(52.dp)
                            .testTag(ProfileSwitcherTestTags.Add),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_person_add_outline),
                            contentDescription = stringResource(R.string.cd_add_profile),
                            tint = if (enabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        )
                    }
                }
            }
        }
    }
}

internal fun Modifier.allowTopOverflow(topOverflow: Dp): Modifier = layout { measurable, constraints ->
    val overflowPx = topOverflow.roundToPx().coerceAtLeast(0)
    if (overflowPx == 0 || !constraints.hasBoundedHeight) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }
    val expandedHeight = (constraints.maxHeight.toLong() + overflowPx)
        .coerceAtMost(Constraints.Infinity.toLong())
        .toInt()
    val placeable = measurable.measure(
        constraints.copy(
            minHeight = expandedHeight,
            maxHeight = expandedHeight,
        ),
    )
    layout(
        width = placeable.width,
        height = constraints.maxHeight,
    ) {
        placeable.placeRelative(0, -overflowPx)
    }
}

private const val PROFILE_SLOT_WIDTH = 52
private const val PROFILE_ACTION_SECTION_WIDTH = 70
private const val PROFILE_SWITCHER_MIN_WIDTH = 122

