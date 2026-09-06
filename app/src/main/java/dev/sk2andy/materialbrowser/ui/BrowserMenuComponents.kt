package dev.sk2andy.materialbrowser.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.shared.ui.BrowserMenuIconToggleItem as SharedBrowserMenuIconToggleItem
import dev.sk2andy.materialbrowser.shared.ui.BrowserMenuRow as SharedBrowserMenuRow
import dev.sk2andy.materialbrowser.shared.ui.BrowserMenuToggleItem as SharedBrowserMenuToggleItem
import dev.sk2andy.materialbrowser.shared.ui.BrowserMenuToolbarAction as SharedBrowserMenuToolbarAction
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor

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
    SharedBrowserMenuIconToggleItem(
        label = stringResource(R.string.action_mute_domain),
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_symbol_volume_off),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
        checked = muted,
        enabled = enabled,
        onCheckedChange = onMutedChange,
        modifier = modifier.testTag(DomainMuteMenuTestTags.Item),
        shape = shape,
        containerColor = browserChromeColor(colors.surfaceContainer),
    )
}

internal typealias TabActionsMenuTestTags =
    dev.sk2andy.materialbrowser.shared.ui.TabActionsMenuTestTags
