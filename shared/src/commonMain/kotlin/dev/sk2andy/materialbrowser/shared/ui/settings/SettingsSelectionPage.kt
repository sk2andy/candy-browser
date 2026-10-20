package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

@Composable
fun SettingsSelectionPage(
    title: String,
    backContentDescription: String,
    onBack: () -> Unit,
    summary: String? = null,
    preview: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsPage(title = title, backContentDescription = backContentDescription, onBack = onBack) {
        Box(Modifier.fillMaxWidth().settingsSearchTarget(title), contentAlignment = Alignment.Center) {
            Box(Modifier.widthIn(max = 400.dp).fillMaxWidth().clearAndSetSemantics { }) { preview() }
        }
        Spacer(Modifier.height(24.dp))
        if (summary != null) {
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
        }
        Column(Modifier.selectableGroup()) {
            SettingsPreferenceGroup(containerColor = settingsRowColor(), content = content)
        }
    }
}

@Composable
fun SettingsRadioOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(settingsRowShape())
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f },
        color = settingsRowColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = settingsRowShape(),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.clearAndSetSemantics { },
            )
            Spacer(Modifier.width(16.dp))
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                if (supportingText != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(supportingText, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun SettingsSelectionLink(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(settingsRowShape())
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .settingsSearchTarget(title)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f },
        color = settingsRowColor(),
        shape = settingsRowShape(),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(16.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
