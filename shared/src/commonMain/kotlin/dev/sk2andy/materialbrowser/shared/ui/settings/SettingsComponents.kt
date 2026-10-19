package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.shared.ui.LocalCandyBrowserChromeEffects

/** Group membership is presentation only; individual controls keep their own semantics. */
val LocalSettingsPreferenceGroup = staticCompositionLocalOf { false }

@Composable
fun settingsPageColor(): Color = MaterialTheme.colorScheme.surfaceContainerLow

@Composable
fun settingsRowColor(): Color = MaterialTheme.colorScheme.surfaceContainerLowest

@Composable
fun settingsRowShape() = if (LocalSettingsPreferenceGroup.current) {
    MaterialTheme.shapes.extraSmall
} else {
    MaterialTheme.shapes.extraLarge
}

@Composable
fun SettingsPage(
    title: String,
    backContentDescription: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(settingsPageColor())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
    ) {
        Row(
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("settings_page_back")) {
                LocalCandyBrowserChromeEffects.current.icon(
                    "chevron.left",
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = backContentDescription,
                )
            }
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsLink(
    title: String,
    subtitle: String,
    containerColor: Color,
    icon: @Composable (Modifier, Color) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val grouped = LocalCandyBrowserChromeEffects.current.usesGroupedSettings
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .settingsSearchTarget(title)
            .clip(settingsRowShape())
            .combinedClickable(
                enabled = enabled,
                onClickLabel = null,
                role = Role.Button,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f },
        shape = settingsRowShape(),
        color = settingsRowColor(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(if (grouped) 32.dp else 40.dp),
                shape = MaterialTheme.shapes.medium,
                color = if (grouped) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    icon(
                        Modifier.size(if (grouped) 20.dp else 22.dp),
                        if (grouped) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            LocalCandyBrowserChromeEffects.current.icon(
                "chevron.right",
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun SettingsSectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
fun SettingsChoice(
    title: String,
    value: String,
    expanded: Boolean,
    onClick: () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    inPreferenceGroup: Boolean = false,
) {
    val effects = LocalCandyBrowserChromeEffects.current
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (effects.reducesMotion()) snap() else spring(dampingRatio = 0.72f, stiffness = 620f),
        label = "Selection indicator",
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().settingsSearchTarget(title),
        shape = settingsRowShape(),
        color = settingsRowColor(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (enabled) 1f else 0.6f,
                    ),
                )
            }
            Spacer(Modifier.width(16.dp))
            Surface(
                modifier = Modifier.size(if (effects.usesGroupedSettings) 28.dp else 42.dp),
                shape = MaterialTheme.shapes.medium,
                color = if (effects.usesGroupedSettings) Color.Transparent else MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { rotationZ = chevronRotation },
                        tint = (if (effects.usesGroupedSettings) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSecondaryContainer).copy(
                            alpha = if (enabled) 1f else 0.38f,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        content = content,
    )
}

@Composable
fun SettingsDropdownItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val contentColor = colors.onSurface
    val secondaryColor = colors.onSurfaceVariant
    val disabledColor = contentColor.copy(alpha = 0.38f)
    DropdownMenuItem(
        text = {
            Column {
                Text(label)
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) secondaryColor else disabledColor,
                    )
                }
            }
        },
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .padding(horizontal = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(Color.Transparent)
            .heightIn(min = 48.dp)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        colors = MenuDefaults.itemColors(
            textColor = contentColor,
            leadingIconColor = secondaryColor,
            disabledTextColor = disabledColor,
            disabledLeadingIconColor = disabledColor,
        ),
        leadingIcon = {
            Box(Modifier.size(24.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
                if (leadingIcon != null) {
                    leadingIcon()
                }
            }
        },
        trailingIcon = {
            androidx.compose.material3.RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
                modifier = Modifier.clearAndSetSemantics { },
            )
        },
    )
}

@Composable
fun SettingsDropdownDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
) {
    val effects = LocalCandyBrowserChromeEffects.current
    val titleColor = if (prominent) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val subtitleColor = if (prominent) {
        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .settingsSearchTarget(title)
            .clip(if (prominent) RoundedCornerShape(48.dp) else settingsRowShape())
            .background(if (prominent) MaterialTheme.colorScheme.secondaryContainer else settingsRowColor())
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 18.dp, vertical = if (prominent) 24.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) {
                    titleColor
                } else {
                    titleColor.copy(alpha = 0.38f)
                },
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor.copy(
                    alpha = subtitleColor.alpha * if (enabled) 1f else 0.6f,
                ),
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.clearAndSetSemantics {}) {
            effects.settingsToggle(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@Composable
fun SettingsPageSpacer() {
    if (LocalSettingsPreferenceGroup.current) SettingsPreferenceDivider()
    else Spacer(Modifier.height(16.dp))
}

@Composable
fun SettingsPreferenceGroup(
    containerColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalSettingsPreferenceGroup.current) {
        Column(content = content)
        return
    }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("settings_preference_group"),
        shape = MaterialTheme.shapes.extraLarge,
        color = settingsRowColor(),
    ) {
        CompositionLocalProvider(LocalSettingsPreferenceGroup provides true) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsPreferenceDivider() {
    Spacer(
        Modifier.fillMaxWidth()
            .height(2.dp)
            .background(settingsPageColor()),
    )
}

/** First/last rows provide the exterior of a lazy connected group. */
@Composable
fun settingsConnectedShape(index: Int, count: Int): RoundedCornerShape {
    val outer = MaterialTheme.shapes.extraLarge
    val inner = MaterialTheme.shapes.extraSmall
    return RoundedCornerShape(
        topStart = if (index == 0) outer.topStart else inner.topStart,
        topEnd = if (index == 0) outer.topEnd else inner.topEnd,
        bottomStart = if (index == count - 1) outer.bottomStart else inner.bottomStart,
        bottomEnd = if (index == count - 1) outer.bottomEnd else inner.bottomEnd,
    )
}
