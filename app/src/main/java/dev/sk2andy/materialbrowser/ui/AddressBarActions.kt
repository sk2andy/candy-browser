package dev.sk2andy.materialbrowser.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.AddressBarAction

internal data class AddressBarActionState(
    val tabCount: Int,
    val isLoading: Boolean,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val canToggleFavorite: Boolean,
    val isFavorite: Boolean,
    val isPinned: Boolean,
    val canToggleDesktopView: Boolean,
    val isDesktopView: Boolean,
    val canToggleForceVerticalScrolling: Boolean,
    val isForceVerticalScrollingEnabled: Boolean,
    val canUsePageActions: Boolean,
    val canOpenReader: Boolean,
    val canCloseTab: Boolean,
    val canParkRight: Boolean,
    val newTabPulseScale: Float,
)

internal data class AddressBarActionCallbacks(
    val onTabs: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onTogglePinned: () -> Unit,
    val onDesktopViewChange: (Boolean) -> Unit,
    val onForceVerticalScrollingChange: (Boolean) -> Unit,
    val onReaderStudio: () -> Unit,
    val onFindInPage: () -> Unit,
    val onShare: () -> Unit,
    val onPrint: () -> Unit,
    val onNewTab: () -> Unit,
    val onReloadOrStop: () -> Unit,
    val onCloseTab: () -> Unit,
    val onBack: () -> Unit,
    val onForward: () -> Unit,
    val onParkRight: () -> Unit,
    val onNewTabButtonBounds: (Rect?) -> Unit,
)

@Composable
internal fun AddressBarActionButton(
    action: AddressBarAction,
    state: AddressBarActionState,
    callbacks: AddressBarActionCallbacks,
    modifier: Modifier = Modifier,
) {
    val currentOnNewTabButtonBounds by rememberUpdatedState(callbacks.onNewTabButtonBounds)
    if (action == AddressBarAction.NewTab) {
        DisposableEffect(Unit) {
            onDispose { currentOnNewTabButtonBounds(null) }
        }
    }
    when (action) {
        AddressBarAction.Tabs -> Box(modifier) {
            AddressBarTabCounterButton(
                tabCount = state.tabCount,
                onClick = callbacks.onTabs,
            )
        }
        AddressBarAction.Favorite -> AddressBarToggleActionButton(
            checked = state.isFavorite,
            enabled = state.canToggleFavorite,
            onCheckedChange = { callbacks.onToggleFavorite() },
            actionDescription = stringResource(
                if (state.isFavorite) R.string.action_remove_favorite
                else R.string.action_add_favorite,
            ),
            modifier = modifier.testTag(AddressBarActionTestTags.action(action)),
        ) {
            Icon(
                painter = painterResource(
                    if (state.isFavorite) R.drawable.ic_symbol_favorite_filled
                    else R.drawable.ic_symbol_favorite,
                ),
                contentDescription = null,
            )
        }
        AddressBarAction.Pin -> AddressBarToggleActionButton(
            checked = state.isPinned,
            enabled = true,
            onCheckedChange = { callbacks.onTogglePinned() },
            actionDescription = stringResource(
                if (state.isPinned) R.string.action_remove_pin else R.string.action_pin_tab,
            ),
            modifier = modifier.testTag(AddressBarActionTestTags.action(action)),
        ) {
            Icon(painterResource(R.drawable.ic_push_pin), contentDescription = null)
        }
        AddressBarAction.Desktop -> AddressBarToggleActionButton(
            checked = state.isDesktopView,
            enabled = state.canToggleDesktopView,
            onCheckedChange = callbacks.onDesktopViewChange,
            actionDescription = stringResource(R.string.action_desktop_view),
            modifier = modifier.testTag(AddressBarActionTestTags.action(action)),
        ) {
            Icon(painterResource(R.drawable.ic_symbol_desktop), contentDescription = null)
        }
        AddressBarAction.ForceVerticalScroll -> AddressBarToggleActionButton(
            checked = state.isForceVerticalScrollingEnabled,
            enabled = state.canToggleForceVerticalScrolling,
            onCheckedChange = callbacks.onForceVerticalScrollingChange,
            actionDescription = stringResource(R.string.privacy_force_vertical_scrolling),
            modifier = modifier.testTag(AddressBarActionTestTags.action(action)),
        ) {
            Icon(painterResource(R.drawable.ic_symbol_vertical_scroll), contentDescription = null)
        }
        else -> AddressBarRegularActionButton(
            action = action,
            state = state,
            callbacks = callbacks,
            modifier = modifier.testTag(AddressBarActionTestTags.action(action)),
        )
    }
}

@Composable
private fun AddressBarRegularActionButton(
    action: AddressBarAction,
    state: AddressBarActionState,
    callbacks: AddressBarActionCallbacks,
    modifier: Modifier,
) {
    val enabled = when (action) {
        AddressBarAction.Reader -> state.canOpenReader
        AddressBarAction.FindInPage,
        AddressBarAction.Share,
        AddressBarAction.Print,
        AddressBarAction.Reload,
        -> state.canUsePageActions
        AddressBarAction.CloseTab -> state.canCloseTab
        AddressBarAction.Back -> state.canGoBack
        AddressBarAction.Forward -> state.canGoForward
        AddressBarAction.ParkRight -> state.canParkRight
        else -> true
    }
    val onClick = when (action) {
        AddressBarAction.Reader -> callbacks.onReaderStudio
        AddressBarAction.FindInPage -> callbacks.onFindInPage
        AddressBarAction.Share -> callbacks.onShare
        AddressBarAction.Print -> callbacks.onPrint
        AddressBarAction.NewTab -> callbacks.onNewTab
        AddressBarAction.Reload -> callbacks.onReloadOrStop
        AddressBarAction.CloseTab -> callbacks.onCloseTab
        AddressBarAction.Back -> callbacks.onBack
        AddressBarAction.Forward -> callbacks.onForward
        AddressBarAction.ParkRight -> callbacks.onParkRight
        else -> return
    }
    val description = when (action) {
        AddressBarAction.Reader -> stringResource(R.string.reader_open_action)
        AddressBarAction.FindInPage -> stringResource(R.string.action_find_in_page)
        AddressBarAction.Share -> stringResource(R.string.action_share)
        AddressBarAction.Print -> stringResource(R.string.action_print)
        AddressBarAction.NewTab -> stringResource(R.string.cd_new_tab)
        AddressBarAction.Reload -> stringResource(
            if (state.isLoading) R.string.action_stop_loading else R.string.action_reload,
        )
        AddressBarAction.CloseTab -> stringResource(R.string.cd_close_tab)
        AddressBarAction.Back -> stringResource(R.string.action_back)
        AddressBarAction.Forward -> stringResource(R.string.action_forward)
        AddressBarAction.ParkRight -> stringResource(R.string.action_park_address_pill_right)
        else -> ""
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(
                if (action == AddressBarAction.NewTab) {
                    Modifier
                        .onGloballyPositioned { coordinates ->
                            callbacks.onNewTabButtonBounds(coordinates.boundsInRoot())
                        }
                        .graphicsLayer {
                            scaleX = state.newTabPulseScale
                            scaleY = state.newTabPulseScale
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        AddressBarActionGlyph(
            action = action,
            selected = false,
            isLoading = state.isLoading,
            contentDescription = description,
            enabled = enabled,
        )
    }
}

@Composable
private fun AddressBarToggleActionButton(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    actionDescription: String,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    IconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = actionDescription },
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer {
                    alpha = if (!enabled || checked) 1f else 0.78f
                },
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    checked -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    content()
                }
            }
        }
    }
}

@Composable
internal fun AddressBarActionGlyph(
    action: AddressBarAction,
    tabCount: Int = 1,
    selected: Boolean,
    isLoading: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    when (action) {
        AddressBarAction.Tabs -> AddressBarTabCounterGlyph(
            tabCount = tabCount,
            modifier = modifier,
        )
        AddressBarAction.FindInPage -> Icon(
            Icons.Default.Search,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint,
        )
        AddressBarAction.NewTab -> Icon(
            Icons.Default.Add,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint,
        )
        AddressBarAction.CloseTab -> Icon(
            Icons.Default.Close,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint,
        )
        else -> Icon(
            painter = painterResource(
                when (action) {
                    AddressBarAction.Favorite -> if (selected) {
                        R.drawable.ic_symbol_favorite_filled
                    } else {
                        R.drawable.ic_symbol_favorite
                    }
                    AddressBarAction.Pin -> R.drawable.ic_push_pin
                    AddressBarAction.Desktop -> R.drawable.ic_symbol_desktop
                    AddressBarAction.ForceVerticalScroll ->
                        R.drawable.ic_symbol_vertical_scroll
                    AddressBarAction.Reader -> R.drawable.ic_reader_align_start
                    AddressBarAction.Share -> R.drawable.ic_symbol_share
                    AddressBarAction.Print -> R.drawable.ic_symbol_print
                    AddressBarAction.Reload -> if (isLoading) {
                        R.drawable.ic_symbol_close
                    } else {
                        R.drawable.ic_symbol_refresh
                    }
                    AddressBarAction.Back -> R.drawable.ic_symbol_arrow_back
                    AddressBarAction.Forward -> R.drawable.ic_symbol_arrow_forward
                    AddressBarAction.ParkRight ->
                        R.drawable.ic_symbol_chevron_physical_right
                    AddressBarAction.FindInPage,
                    AddressBarAction.NewTab,
                    AddressBarAction.CloseTab,
                    AddressBarAction.Tabs,
                    -> error("Vector icon handled above")
                },
            ),
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint,
        )
    }
}

@Composable
internal fun AddressBarTabCounterGlyph(
    tabCount: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(25.dp)
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(6.dp),
            )
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = AddressBarControlRules.tabCountLabel(tabCount),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

internal object AddressBarActionTestTags {
    fun action(action: AddressBarAction): String = "address_bar_action:${action.wireValue}"
}

@StringRes
internal fun AddressBarAction.labelRes(): Int = when (this) {
    AddressBarAction.Favorite -> R.string.action_favorite
    AddressBarAction.Pin -> R.string.action_pin_tab
    AddressBarAction.Desktop -> R.string.action_desktop_view
    AddressBarAction.ForceVerticalScroll -> R.string.privacy_force_vertical_scrolling
    AddressBarAction.Reader -> R.string.reader_open_action
    AddressBarAction.FindInPage -> R.string.action_find_in_page
    AddressBarAction.Tabs -> R.string.address_bar_action_tabs
    AddressBarAction.Share -> R.string.action_share
    AddressBarAction.Print -> R.string.action_print
    AddressBarAction.NewTab -> R.string.cd_new_tab
    AddressBarAction.Reload -> R.string.action_reload
    AddressBarAction.CloseTab -> R.string.cd_close_tab
    AddressBarAction.Back -> R.string.action_back
    AddressBarAction.Forward -> R.string.action_forward
    AddressBarAction.ParkRight -> R.string.action_park_address_pill_right
}
