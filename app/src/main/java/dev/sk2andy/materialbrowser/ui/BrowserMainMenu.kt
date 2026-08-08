package dev.sk2andy.materialbrowser.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sk2andy.materialbrowser.R

@Composable
internal fun BrowserMainMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    pageSubtitle: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isLoading: Boolean,
    canToggleFavorite: Boolean,
    isFavorite: Boolean,
    canUsePageActions: Boolean,
    canToggleDomainMute: Boolean,
    isDomainMuted: Boolean,
    canAddSiteCapsule: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReloadOrStop: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit,
    onPrint: () -> Unit,
    onDomainMutedChange: (Boolean) -> Unit,
    onOpenCandyTrail: () -> Unit,
    onAddSiteCapsule: () -> Unit,
    onSummarize: () -> Unit,
    onSettings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val menuShape = MaterialTheme.shapes.large
    val outerCorners = MaterialTheme.shapes.medium
    val innerCorners = MaterialTheme.shapes.extraSmall
    val firstItemShape = RoundedCornerShape(
        topStart = outerCorners.topStart,
        topEnd = outerCorners.topEnd,
        bottomEnd = innerCorners.bottomEnd,
        bottomStart = innerCorners.bottomStart,
    )
    val lastItemShape = RoundedCornerShape(
        topStart = innerCorners.topStart,
        topEnd = innerCorners.topEnd,
        bottomEnd = outerCorners.bottomEnd,
        bottomStart = outerCorners.bottomStart,
    )
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val menuWidth = minOf(360.dp, screenWidth - 32.dp)
    fun dismissThen(action: () -> Unit) {
        onDismissRequest()
        action()
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .width(menuWidth)
            .clip(menuShape)
            .testTag(BrowserMainMenuTestTags.Menu),
        offset = DpOffset(x = 0.dp, y = (-10).dp),
        shape = menuShape,
        containerColor = colors.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.browser_menu_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = pageSubtitle,
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(BrowserMainMenuTestTags.Toolbar),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MenuToolbarAction(
                    label = stringResource(R.string.action_back),
                    iconRes = R.drawable.ic_symbol_arrow_back,
                    enabled = canGoBack,
                    onClick = { dismissThen(onBack) },
                    modifier = Modifier.weight(1f),
                )
                MenuToolbarAction(
                    label = stringResource(R.string.action_forward),
                    iconRes = R.drawable.ic_symbol_arrow_forward,
                    enabled = canGoForward,
                    onClick = { dismissThen(onForward) },
                    modifier = Modifier.weight(1f),
                )
                MenuToolbarAction(
                    label = stringResource(
                        if (isLoading) R.string.action_stop_loading else R.string.action_reload,
                    ),
                    iconRes = if (isLoading) {
                        R.drawable.ic_symbol_close
                    } else {
                        R.drawable.ic_symbol_refresh
                    },
                    onClick = { dismissThen(onReloadOrStop) },
                    modifier = Modifier.weight(1f),
                )
                MenuToolbarAction(
                    label = stringResource(R.string.action_favorite),
                    iconRes = if (isFavorite) {
                        R.drawable.ic_symbol_favorite_filled
                    } else {
                        R.drawable.ic_symbol_favorite
                    },
                    accessibilityLabel = stringResource(
                        if (isFavorite) {
                            R.string.action_remove_favorite
                        } else {
                            R.string.action_add_favorite
                        },
                    ),
                    enabled = canToggleFavorite,
                    selected = isFavorite,
                    onClick = { dismissThen(onToggleFavorite) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.browser_menu_page_group),
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Column(
                modifier = Modifier.testTag(BrowserMainMenuTestTags.PageGroup),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                MenuRow(
                    label = stringResource(R.string.action_share),
                    iconRes = R.drawable.ic_symbol_share,
                    enabled = canUsePageActions,
                    shape = firstItemShape,
                    onClick = { dismissThen(onShare) },
                )
                MenuRow(
                    label = stringResource(R.string.action_open_in_app),
                    iconRes = R.drawable.ic_symbol_open_in_new,
                    enabled = canUsePageActions,
                    shape = innerCorners,
                    onClick = { dismissThen(onOpenExternal) },
                )
                MenuRow(
                    label = stringResource(R.string.action_print),
                    iconRes = R.drawable.ic_symbol_print,
                    enabled = canUsePageActions,
                    shape = innerCorners,
                    onClick = { dismissThen(onPrint) },
                )
                DomainMuteMenuItem(
                    enabled = canToggleDomainMute,
                    muted = isDomainMuted,
                    onMutedChange = onDomainMutedChange,
                    shape = lastItemShape,
                )
            }

            Spacer(Modifier.height(2.dp))
            Column(
                modifier = Modifier.testTag(BrowserMainMenuTestTags.CandyGroup),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                MenuRow(
                    label = stringResource(R.string.action_open_candy_trail),
                    iconRes = R.drawable.ic_symbol_route,
                    enabled = canUsePageActions,
                    shape = firstItemShape,
                    containerColor = colors.tertiaryContainer,
                    contentColor = colors.onTertiaryContainer,
                    onClick = { dismissThen(onOpenCandyTrail) },
                )
                MenuRow(
                    label = stringResource(R.string.action_add_site_capsule),
                    iconRes = R.drawable.ic_symbol_add_to_home_screen,
                    enabled = canAddSiteCapsule,
                    shape = innerCorners,
                    containerColor = colors.tertiaryContainer,
                    contentColor = colors.onTertiaryContainer,
                    onClick = { dismissThen(onAddSiteCapsule) },
                )
                MenuRow(
                    label = stringResource(R.string.action_summarize),
                    iconRes = R.drawable.ic_symbol_auto_awesome,
                    enabled = canUsePageActions,
                    shape = lastItemShape,
                    containerColor = colors.tertiaryContainer,
                    contentColor = colors.onTertiaryContainer,
                    onClick = { dismissThen(onSummarize) },
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.browser_menu_browser_group),
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                fontWeight = FontWeight.SemiBold,
            )
            MenuRow(
                label = stringResource(R.string.action_settings),
                iconRes = R.drawable.ic_symbol_settings,
                shape = outerCorners,
                modifier = Modifier.testTag(BrowserMainMenuTestTags.Settings),
                onClick = { dismissThen(onSettings) },
                trailingContent = {
                    Icon(
                        painter = painterResource(R.drawable.ic_symbol_chevron_right),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun MenuToolbarAction(
    label: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    accessibilityLabel: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val containerColor = if (selected) colors.primaryContainer else colors.surfaceContainerHighest
    val accessibilityModifier = if (accessibilityLabel != null) {
        Modifier.semantics(mergeDescendants = true) {
            this.selected = selected
            contentDescription = accessibilityLabel
        }
    } else {
        Modifier
    }
    val contentColor = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        selected -> colors.onPrimaryContainer
        else -> colors.onSurface
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 64.dp)
            .then(accessibilityModifier),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun MenuRow(
    label: String,
    @DrawableRes iconRes: Int,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        enabled = enabled,
        shape = shape,
        color = containerColor,
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.38f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (trailingContent != null) {
                Spacer(Modifier.width(12.dp))
                trailingContent()
            }
        }
    }
}

@Composable
internal fun DomainMuteMenuItem(
    enabled: Boolean,
    muted: Boolean,
    onMutedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(DomainMuteMenuTestTags.Item)
            .semantics(mergeDescendants = true) {}
            .toggleable(
                value = muted,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onMutedChange,
            ),
        shape = shape,
        color = colors.surfaceContainer,
        contentColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_symbol_volume_off),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.action_mute_domain),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Switch(
                checked = muted,
                onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics {},
                enabled = enabled,
            )
        }
    }
}

internal object BrowserMainMenuTestTags {
    const val Menu = "browser_main_menu"
    const val Toolbar = "browser_main_menu_toolbar"
    const val PageGroup = "browser_main_menu_page_group"
    const val CandyGroup = "browser_main_menu_candy_group"
    const val Settings = "browser_main_menu_settings"
}

internal object DomainMuteMenuTestTags {
    const val Item = "domain_mute_menu_item"
}
