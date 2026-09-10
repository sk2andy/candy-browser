package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object BrowserMenuExpressiveToggleRules {
    val SelectedCornerRadius = 12.dp
    val PressedCornerRadius = 8.dp

    fun cornerRadius(
        checked: Boolean,
        pressed: Boolean,
        buttonHeight: Dp,
    ): Dp = when {
        pressed -> PressedCornerRadius
        checked -> SelectedCornerRadius
        else -> buttonHeight / 2
    }
}

/** Shared rendering primitives extracted from Android's production browser menu. */
@Composable
fun BrowserMenuToolbarAction(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    accessibilityLabel: String? = null,
    horizontalContent: Boolean = false,
    minHeight: Dp = 64.dp,
    verticalLabelFontSize: TextUnit = 11.sp,
    containerColor: Color = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    },
) {
    val colors = MaterialTheme.colorScheme
    val accessibilityModifier = Modifier.semantics(mergeDescendants = true) {
        this.selected = selected
        if (accessibilityLabel != null) {
            contentDescription = accessibilityLabel
        }
    }
    val contentColor = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        selected -> colors.onPrimaryContainer
        else -> colors.onSurface
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = minHeight)
            .then(accessibilityModifier),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
    ) {
        if (horizontalContent) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon()
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
                icon()
                Spacer(Modifier.height(4.dp))
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = verticalLabelFontSize,
                )
            }
        }
    }
}

@Composable
fun BrowserMenuRow(
    label: String,
    icon: @Composable () -> Unit,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    supportingText: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    minHeight: Dp = 44.dp,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 6.dp,
    labelFontSize: TextUnit = TextUnit.Unspecified,
    supportingTextFontSize: TextUnit = TextUnit.Unspecified,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
        enabled = enabled,
        shape = shape,
        color = containerColor,
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.38f),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = horizontalPadding,
                vertical = verticalPadding,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = labelFontSize,
                )
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = supportingTextFontSize,
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
fun BrowserMenuToggleItem(
    label: String,
    supportingText: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    minHeight: Dp = 52.dp,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 6.dp,
    labelFontSize: TextUnit = TextUnit.Unspecified,
    supportingTextFontSize: TextUnit = TextUnit.Unspecified,
    checkedTrackColor: Color? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .semantics(mergeDescendants = true) {}
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        shape = shape,
        color = containerColor,
        contentColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
    ) {
        Row(
            modifier = Modifier.padding(
                start = horizontalPadding,
                end = horizontalPadding - 6.dp,
                top = verticalPadding,
                bottom = verticalPadding,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = labelFontSize,
                )
                Text(
                    text = supportingText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = supportingTextFontSize,
                    color = colors.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics {},
                enabled = enabled,
                colors = if (checkedTrackColor == null) {
                    SwitchDefaults.colors()
                } else {
                    SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = checkedTrackColor,
                    )
                },
            )
        }
    }
}

@Composable
internal fun BrowserMenuExpressiveToggleButton(
    label: String,
    icon: @Composable () -> Unit,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 48.dp,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 0.dp,
    labelFontSize: TextUnit = TextUnit.Unspecified,
    uncheckedContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    uncheckedContentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    checkedContainerColor: Color = MaterialTheme.colorScheme.secondary,
    checkedContentColor: Color = MaterialTheme.colorScheme.onSecondary,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val cornerRadius by animateDpAsState(
        targetValue = BrowserMenuExpressiveToggleRules.cornerRadius(
            checked = checked,
            pressed = pressed,
            buttonHeight = minHeight,
        ),
        label = "browser menu toggle corner radius",
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.onSurface.copy(alpha = 0.1f)
            checked -> checkedContainerColor
            else -> uncheckedContainerColor
        },
        label = "browser menu toggle container color",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.onSurfaceVariant.copy(alpha = 0.38f)
            checked -> checkedContentColor
            else -> uncheckedContentColor
        },
        label = "browser menu toggle content color",
    )
    Surface(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier
            .fillMaxWidth()
            .semantics { role = Role.Checkbox },
        enabled = enabled,
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .padding(
                    horizontal = horizontalPadding,
                    vertical = verticalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = labelFontSize,
            )
        }
    }
}

@Composable
fun BrowserMenuIconToggleItem(
    label: String,
    icon: @Composable () -> Unit,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    minHeight: Dp = 48.dp,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 0.dp,
    labelFontSize: TextUnit = TextUnit.Unspecified,
    checkedTrackColor: Color? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .semantics(mergeDescendants = true) {}
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        shape = shape,
        color = containerColor,
        contentColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
    ) {
        Row(
            modifier = Modifier.padding(
                start = horizontalPadding,
                end = horizontalPadding - 6.dp,
                top = verticalPadding,
                bottom = verticalPadding,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontSize = labelFontSize,
            )
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics {},
                enabled = enabled,
                colors = if (checkedTrackColor == null) {
                    SwitchDefaults.colors()
                } else {
                    SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = checkedTrackColor,
                    )
                },
            )
        }
    }
}
