package dev.sk2andy.materialbrowser.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.shared.ui.BrowserMenuRow as SharedBrowserMenuRow
import dev.sk2andy.materialbrowser.shared.ui.BrowserMenuToggleItem as SharedBrowserMenuToggleItem
import dev.sk2andy.materialbrowser.shared.ui.BrowserMenuToolbarAction as SharedBrowserMenuToolbarAction
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
    SharedBrowserMenuToolbarAction(
        label = label,
        icon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
        },
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        selected = selected,
        accessibilityLabel = accessibilityLabel,
        horizontalContent = horizontalContent,
        containerColor = browserChromeColor(containerColor),
    )
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
    SharedBrowserMenuRow(
        label = label,
        icon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        containerColor = browserChromeColor(containerColor, frostedAlpha = 0.68f),
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.38f),
        supportingText = supportingText,
        trailingContent = trailingContent,
    )
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
    SharedBrowserMenuToggleItem(
        label = label,
        supportingText = supportingText,
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        shape = shape,
        containerColor = browserChromeColor(colors.surfaceContainer),
    )
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
