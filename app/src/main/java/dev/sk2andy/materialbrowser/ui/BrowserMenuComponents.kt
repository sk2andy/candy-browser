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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor

@Composable
internal fun BrowserMainMenuFavoriteAction(
    presentation: BrowserMainMenuPresentation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MenuToolbarAction(
        label = stringResource(R.string.action_favorite),
        iconRes = if (presentation.isFavorite) {
            R.drawable.ic_symbol_favorite_filled
        } else {
            R.drawable.ic_symbol_favorite
        },
        accessibilityLabel = stringResource(
            if (presentation.isFavorite) {
                R.string.action_remove_favorite
            } else {
                R.string.action_add_favorite
            },
        ),
        enabled = presentation.canToggleFavorite,
        selected = presentation.isFavorite,
        onClick = onClick,
        modifier = modifier.testTag(BrowserMainMenuTestTags.Favorite),
    )
}

@Composable
internal fun BrowserMainMenuPinAction(
    isPinned: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MenuToolbarAction(
        label = stringResource(
            if (isPinned) R.string.action_remove_pin else R.string.action_pin_tab,
        ),
        iconRes = R.drawable.ic_push_pin,
        accessibilityLabel = stringResource(
            if (isPinned) R.string.action_remove_pin else R.string.action_pin_tab,
        ),
        selected = isPinned,
        onClick = onClick,
        modifier = modifier.testTag(BrowserMainMenuTestTags.Pin),
    )
}

@Composable
internal fun MenuToolbarAction(
    label: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    accessibilityLabel: String? = null,
    horizontalContent: Boolean = false,
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
        color = browserChromeColor(containerColor),
        contentColor = contentColor,
    ) {
        if (horizontalContent) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        } else {
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
}

@Composable
internal fun MenuRow(
    label: String,
    @DrawableRes iconRes: Int,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    supportingText: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        enabled = enabled,
        shape = shape,
        color = browserChromeColor(containerColor, frostedAlpha = 0.68f),
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.72f),
                    )
                }
            }
            if (trailingContent != null) {
                Spacer(Modifier.width(12.dp))
                trailingContent()
            }
        }
    }
}

@Composable
internal fun BrowserMenuToggleItem(
    label: String,
    supportingText: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .semantics(mergeDescendants = true) {}
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        shape = shape,
        color = browserChromeColor(colors.surfaceContainer),
        contentColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = supportingText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics {},
                enabled = enabled,
            )
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
        color = browserChromeColor(colors.surfaceContainer),
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
    const val Favorite = "browser_main_menu_favorite"
    const val Pin = "browser_main_menu_pin"
    const val PageGroup = "browser_main_menu_page_group"
    const val Translate = "browser_main_menu_translate"
    const val CandyGroup = "browser_main_menu_candy_group"
    const val ToppingsGroup = "browser_main_menu_toppings_group"
    const val BrowserGroup = "browser_main_menu_browser_group"
    const val History = "browser_main_menu_history"
    const val Settings = "browser_main_menu_settings"
    const val Snooze = "browser_main_menu_snooze"
    const val SnoozedTabs = "browser_main_menu_snoozed_tabs"
    const val DockAddressBar = "browser_main_menu_dock_address_bar"
    const val CookieBannerRemoval = "browser_main_menu_cookie_banner_removal"
    const val AlwaysBlockPopups = "browser_main_menu_always_block_popups"
    const val ForceVerticalScrolling = "browser_main_menu_force_vertical_scrolling"
    const val DesktopView = "browser_main_menu_desktop_view"
    const val FindInPage = "browser_main_menu_find_in_page"
    const val ForcePageZooming = "browser_main_menu_force_page_zooming"
    const val ForceSafeArea = "browser_main_menu_force_safe_area"

    fun userScriptCommand(commandId: String): String =
        "browser_main_menu_topping_command_$commandId"
}

internal object TabActionsMenuTestTags {
    const val Toolbar = "tab_actions_menu_toolbar"
    const val Favorite = "tab_actions_menu_favorite"
    const val Pin = "tab_actions_menu_pin"
    const val PageGroup = "tab_actions_menu_page_group"
    const val CandyGroup = "tab_actions_menu_candy_group"
    const val Trail = "tab_actions_menu_trail"
    const val CloseAllTabs = "tab_actions_menu_close_all_tabs"
}

internal object DomainMuteMenuTestTags {
    const val Item = "domain_mute_menu_item"
}

internal const val BROWSER_MAIN_MENU_MAX_HEIGHT_FRACTION = 0.8f
