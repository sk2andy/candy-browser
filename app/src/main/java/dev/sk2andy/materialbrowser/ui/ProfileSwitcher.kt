package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.painterResource
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.isSyncLinked
import dev.sk2andy.materialbrowser.shared.ui.BrowserViewportProfile
import dev.sk2andy.materialbrowser.shared.ui.ProfileSwitcher as SharedProfileSwitcher
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
    SharedProfileSwitcher(
        profiles = profiles.map { profile ->
            BrowserViewportProfile(
                id = profile.id,
                emoji = profile.emoji,
                displayName = profile.syncedDisplayName,
                syncedIconEmoji = profile.syncedIconEmoji,
                syncedIconAccentHue = profile.syncedIconAccentHue,
                isSyncLinked = profile.isSyncLinked,
            )
        },
        activeProfileId = activeProfileId,
        enabled = enabled,
        onSelect = onSelect,
        onLongClick = onLongClick,
        onAdd = onAdd,
        modifier = modifier,
        profileDescription = stringResource(R.string.cd_profile),
        editProfileDescription = stringResource(R.string.action_edit_profile),
        addProfileDescription = stringResource(R.string.cd_add_profile),
        containerColor = browserChromeColor(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            frostedAlpha = 0.88f,
        ),
        addIcon = { isEnabled ->
            Icon(
                painter = painterResource(R.drawable.ic_person_add_outline),
                contentDescription = stringResource(R.string.cd_add_profile),
                tint = if (isEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        },
    )
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
