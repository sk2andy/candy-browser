package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Shared screen anatomy with platform-specific visual treatment, never a second screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CandyPageHeader(
    title: String,
    backContentDescription: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val effects = LocalCandyBrowserChromeEffects.current
    if (effects.usesGroupedSettings) {
        effects.floatingSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Box(modifier = Modifier.statusBarsPadding().fillMaxWidth().height(56.dp)) {
                Text(
                    title,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 64.dp)
                        .semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        effects.icon(
                            "chevron.left", Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backContentDescription,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    actions()
                }
            }
        }
    } else {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    effects.icon("chevron.left", Icons.AutoMirrored.Filled.ArrowBack, backContentDescription)
                }
            },
            actions = actions,
        )
    }
}

@Composable
internal fun CandyActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    prominent: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = if (prominent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalCandyBrowserChromeEffects.current.usesGroupedSettings) {
        Row(
            modifier = modifier.graphicsLayer { alpha = if (enabled) 1f else 0.38f }.clip(CircleShape)
                .background(if (prominent) containerColor else Color.Transparent)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .heightIn(min = 44.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides contentColor,
            ) {
                ProvideTextStyle(MaterialTheme.typography.bodyLarge) { content() }
            }
        }
    } else if (prominent) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
            content = content,
        )
    } else {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CandyModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    showPlatformHandle: Boolean = true,
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    content: @Composable ColumnScope.() -> Unit,
) {
    val effects = LocalCandyBrowserChromeEffects.current
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
        shape = if (effects.usesGroupedSettings) RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp) else BottomSheetDefaults.ExpandedShape,
        containerColor = if (effects.usesGroupedSettings) Color.Transparent else containerColor,
        contentColor = contentColor,
        dragHandle = if (effects.usesGroupedSettings) null else dragHandle,
        contentWindowInsets = contentWindowInsets,
    ) {
        if (effects.usesGroupedSettings) {
            effects.floatingSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = containerColor,
                contentColor = contentColor,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column {
                    if (showPlatformHandle) CandySheetHandle(onDismiss = onDismissRequest)
                    content()
                }
            }
        } else {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CandySheetHandle(onDismiss: (() -> Unit)? = null) {
    val effects = LocalCandyBrowserChromeEffects.current
    if (effects.usesGroupedSettings) {
        Box(modifier = Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.size(width = 36.dp, height = 5.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
            )
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
                    effects.icon(
                        "xmark.circle.fill", Icons.Default.Close, contentDescription = "Schließen",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    } else {
        BottomSheetDefaults.DragHandle()
    }
}

@Composable
internal fun CandyAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
) {
    val effects = LocalCandyBrowserChromeEffects.current
    if (effects.usesGroupedSettings) {
        Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            effects.floatingSurface(
                modifier = Modifier.padding(24.dp).widthIn(max = 320.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ProvideTextStyle(MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center)) { title() }
                        ProvideTextStyle(MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Center)) { text() }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        confirmButton()
                        dismissButton()
                    }
                }
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = title,
            text = text,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
        )
    }
}

/** One choice model and callback path, with native-looking segments on Apple platforms. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> CandySegmentedControl(
    options: List<T>,
    selected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    colors: SegmentedButtonColors,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    isSelection: Boolean = true,
    showsSelectionIcon: Boolean = true,
    equalWidths: Boolean = false,
    description: (T) -> String? = { null },
    label: @Composable (T) -> Unit,
) {
    val effects = LocalCandyBrowserChromeEffects.current
    if (effects.usesGroupedSettings) {
        Row(
            modifier = modifier.clip(RoundedCornerShape(12.dp))
                .background(contentColor.copy(alpha = 0.08f)).padding(3.dp)
                .then(if (isSelection) Modifier.selectableGroup() else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { option ->
                val isSelected = selected(option)
                val interaction = remember { MutableInteractionSource() }
                val action = if (isSelection) {
                    Modifier.selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onSelect(option) },
                    )
                } else {
                    Modifier.clickable(
                        role = Role.Button,
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onSelect(option) },
                    )
                }
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).then(action)
                        .semantics {
                            description(option)?.let { contentDescription = it }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val body: @Composable () -> Unit = {
                        Box(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CompositionLocalProvider(LocalContentColor provides contentColor) {
                                ProvideTextStyle(MaterialTheme.typography.labelLarge) { label(option) }
                            }
                        }
                    }
                    if (isSelected) {
                        effects.floatingSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(9.dp),
                            color = containerColor,
                            contentColor = contentColor,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            content = body,
                        )
                    } else {
                        body()
                    }
                }
            }
        }
    } else {
        SingleChoiceSegmentedButtonRow(modifier = modifier) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    modifier = (if (equalWidths) Modifier.weight(1f) else Modifier).then(
                        if (isSelection) {
                            Modifier.semantics { description(option)?.let { contentDescription = it } }
                        } else {
                            Modifier.clearAndSetSemantics {
                                description(option)?.let { contentDescription = it }
                                role = Role.Button
                                onClick { onSelect(option); true }
                            }
                        },
                    ),
                    selected = selected(option),
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    colors = colors,
                    icon = { if (showsSelectionIcon) SegmentedButtonDefaults.Icon(selected(option)) },
                    label = { label(option) },
                )
            }
        }
    }
}
