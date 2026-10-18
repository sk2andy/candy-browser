package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.shared.ui.LocalCandyBrowserChromeEffects

@Composable
fun SettingsPage(
    title: String,
    backContentDescription: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val grouped = LocalCandyBrowserChromeEffects.current.usesGroupedSettings
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = if (grouped) 20.dp else 24.dp)
            .padding(bottom = 24.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
    ) {
        Row(
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                LocalCandyBrowserChromeEffects.current.icon(
                    "chevron.left",
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = backContentDescription,
                )
            }
            Text(
                title,
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
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val grouped = LocalCandyBrowserChromeEffects.current.usesGroupedSettings
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .settingsSearchTarget(title)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                enabled = enabled,
                onClickLabel = null,
                role = Role.Button,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f },
        shape = MaterialTheme.shapes.large,
        color = if (grouped) MaterialTheme.colorScheme.surfaceContainer else containerColor,
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
    val grouped = LocalCandyBrowserChromeEffects.current.usesGroupedSettings
    Text(
        text,
        modifier = Modifier.semantics { heading() },
        style = if (grouped) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium,
        color = if (grouped) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        fontWeight = if (grouped) FontWeight.Normal else FontWeight.SemiBold,
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
        shape = if (inPreferenceGroup) RectangleShape else MaterialTheme.shapes.large,
        color = if (inPreferenceGroup) Color.Transparent else if (effects.usesGroupedSettings) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            containerColor
        },
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
        shape = MaterialTheme.shapes.extraLarge,
        content = content,
    )
}

@Composable
fun SettingsDropdownItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        modifier = Modifier.semantics { this.selected = selected },
        trailingIcon = {
            if (selected) Icon(Icons.Default.Check, contentDescription = null)
        },
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
) {
    val effects = LocalCandyBrowserChromeEffects.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .settingsSearchTarget(title)
            .then(
                if (effects.usesGroupedSettings) {
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    )
                } else {
                    Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) }
                },
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 1f else 0.6f,
                ),
            )
        }
        Spacer(Modifier.width(12.dp))
        effects.settingsToggle(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
fun SettingsPageSpacer() {
    Spacer(Modifier.height(12.dp))
}

@Composable
fun SettingsPreferenceGroup(
    containerColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
    ) {
        Column(content = content)
    }
}

@Composable
fun SettingsPreferenceDivider() {
    Spacer(
        Modifier.fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
    )
}
