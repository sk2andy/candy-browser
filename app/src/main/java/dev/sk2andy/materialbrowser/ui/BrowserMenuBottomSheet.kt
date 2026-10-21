package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import dev.sk2andy.materialbrowser.shared.ui.BrowserCustomAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuItem
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuBottomSheetContentLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuBottomSheetToolbarRules
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuEntry
import dev.sk2andy.materialbrowser.shared.ui.BrowserMainMenuEffects
import dev.sk2andy.materialbrowser.shared.ui.BrowserMainMenuResources
import dev.sk2andy.materialbrowser.shared.ui.BrowserMenuBottomSheetContent
import dev.sk2andy.materialbrowser.ui.theme.browserChromeSurfaceTokens

internal typealias BrowserMenuBottomSheetTestTags =
    dev.sk2andy.materialbrowser.shared.ui.BrowserMenuBottomSheetTestTags

@Composable
internal fun BrowserMenuBottomSheet(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorBounds: Rect?,
    pageSubtitle: String,
    addressBar: (@Composable ((() -> Unit) -> Unit) -> Unit)?,
    items: List<BrowserFeatureMenuItem>,
    contentLayout: BrowserMenuBottomSheetContentLayout,
    resources: BrowserMainMenuResources,
    effects: BrowserMainMenuEffects,
    snoozedTabCount: Int,
    onAction: (BrowserFeatureMenuItem) -> Unit,
    onOpenMenuSettings: () -> Unit,
    onVisibleChange: (Boolean) -> Unit,
    extensionContent: @Composable ColumnScope.((() -> Unit) -> Unit) -> Unit,
    toolbarEntries: List<BrowserMenuEntry?> = BrowserMenuBottomSheetToolbarRules.DefaultEntries,
    customShortcutSlots: Map<Int, BrowserCustomAction?> = emptyMap(),
    customFooterSlots: Map<Int, BrowserCustomAction?> = emptyMap(),
    onCustomAction: (String) -> Unit = {},
    customContent: @Composable ColumnScope.((() -> Unit) -> Unit) -> Unit = {},
) {
    var visible by remember { mutableStateOf(expanded) }
    var actionCommitted by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(1f) }
    val presentedProgress = if (dragging) dragProgress else progress.value.coerceIn(0f, 1f)
    val currentExpanded by rememberUpdatedState(expanded)
    var openingAnchor by remember { mutableStateOf(anchorBounds) }
    var presentedItems by remember { mutableStateOf(items) }
    var presentedToolbarEntries by remember { mutableStateOf(toolbarEntries) }
    if (expanded) {
        presentedItems = items
        presentedToolbarEntries = toolbarEntries
    }
    val animationSpec = effects.spatialAnimationSpec(expanding = expanded)
    val returnAnimationSpec = effects.spatialAnimationSpec(expanding = true)
    LaunchedEffect(expanded) {
        if (expanded) {
            openingAnchor = anchorBounds
            actionCommitted = false
            visible = true
            progress.animateTo(1f, animationSpec)
        } else if (visible) {
            if (dragging) {
                progress.snapTo(dragProgress)
                dragging = false
            }
            progress.animateTo(0f, animationSpec)
            visible = false
        }
    }
    val currentOnVisibleChange by rememberUpdatedState(onVisibleChange)
    LaunchedEffect(expanded, visible) {
        effects.popupState(expanded, visible)
        currentOnVisibleChange(visible)
    }
    DisposableEffect(effects) {
        onDispose {
            effects.popupState(expanded = false, visible = false)
            currentOnVisibleChange(false)
        }
    }
    fun commit(action: () -> Unit) {
        if (!expanded || actionCommitted) return
        actionCommitted = true
        onDismissRequest()
        action()
    }
    if (!visible) return
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = expanded,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val window = (LocalView.current.parent as DialogWindowProvider).window
        SideEffect { window.setDimAmount(0f) }
        val density = LocalDensity.current
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f * presentedProgress))
                    .clickable(
                        onClickLabel = stringResource(R.string.browser_menu_dismiss),
                        role = Role.Button,
                        onClick = onDismissRequest,
                    )
                    .testTag(BrowserMenuBottomSheetTestTags.Scrim),
            )
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                val compactHeight = BrowserMenuBottomSheetMotionRules.isCompactHeight(maxHeight.value)
                val sheetHeight = maxHeight * BrowserMenuBottomSheetMotionRules.heightFraction(maxHeight.value)
                val cornerRadius = browserChromeSurfaceTokens().cornerRadius
                var availableBounds by remember { mutableStateOf<Rect?>(null) }
                val collapsedWidth = openingAnchor?.let { with(density) { it.width.toDp() } }
                    ?.coerceIn(0.dp, maxWidth) ?: (maxWidth - 32.dp).coerceAtLeast(0.dp)
                val horizontalInset = (maxWidth - collapsedWidth) / 2 * (1f - presentedProgress)
                val sheetHeightPx = with(density) { sheetHeight.toPx() }
                val targetTop = availableBounds?.let { it.bottom - sheetHeightPx } ?: 0f
                val collapsedOffsetPx = BrowserMenuBottomSheetMotionRules.translationY(
                    progress = 0f,
                    sheetHeightPx = sheetHeightPx,
                    anchorTopPx = openingAnchor?.top,
                    sheetTopPx = targetTop,
                    density = density.density,
                )
                val surfaceAlpha = BrowserMenuBottomSheetMotionRules.surfaceAlpha(presentedProgress)
                val shape = RoundedCornerShape(
                    topStart = cornerRadius,
                    topEnd = cornerRadius,
                    bottomStart = horizontalInset,
                    bottomEnd = horizontalInset,
                )
                Box(
                    Modifier.fillMaxSize().onGloballyPositioned {
                        availableBounds = it.boundsInWindow()
                    },
                )
                Box(
                    modifier = Modifier
                        .width(maxWidth - horizontalInset * 2)
                        .height(sheetHeight)
                        .graphicsLayer {
                            val anchor = openingAnchor
                            val bounds = availableBounds
                            alpha = if (openingAnchor != null && availableBounds == null) 0f else 1f
                            translationY = BrowserMenuBottomSheetMotionRules.translationY(
                                progress = presentedProgress,
                                sheetHeightPx = size.height,
                                anchorTopPx = openingAnchor?.top,
                                sheetTopPx = targetTop,
                                density = density.density,
                            )
                            translationX = if (anchor != null && bounds != null) {
                                (anchor.center.x - bounds.center.x) * (1f - presentedProgress)
                            } else 0f
                            this.shape = shape
                            clip = true
                        }
                        .testTag(BrowserMainMenuTestTags.Menu),
                ) {
                    effects.menuSurface(
                        modifier = Modifier.matchParentSize().graphicsLayer { alpha = surfaceAlpha },
                        shape = shape,
                    ) { Box(Modifier.fillMaxSize()) }
                    BrowserMenuBottomSheetContent(
                        expanded = expanded,
                        items = presentedItems,
                        toolbarEntries = presentedToolbarEntries,
                        contentLayout = contentLayout,
                        resources = resources,
                        effects = effects,
                        snoozedTabCount = snoozedTabCount,
                        quickActionsTitle = stringResource(R.string.browser_menu_quick_actions),
                        pageActionsTitle = stringResource(R.string.browser_menu_page_actions),
                        pageControlsTitle = stringResource(R.string.browser_menu_page_controls),
                        emptySectionMessage = stringResource(R.string.browser_menu_empty_section_message),
                        emptySectionActionLabel = stringResource(R.string.browser_menu_empty_section_action),
                        onOpenMenuSettings = { commit(onOpenMenuSettings) },
                        handleDescription = stringResource(R.string.browser_menu_dismiss),
                        modifier = Modifier.navigationBarsPadding(),
                        compactHeight = compactHeight,
                        surfaceAlpha = surfaceAlpha,
                        addressBarHorizontalPadding = 16.dp * presentedProgress,
                        addressBar = {
                            val content = addressBar
                            if (content != null) content(::commit)
                            else Text(pageSubtitle, style = MaterialTheme.typography.titleMedium)
                        },
                        onCommand = { item -> commit { onAction(item) } },
                        onToggle = { item -> if (expanded && !actionCommitted) onAction(item) },
                        onExtensionCommit = ::commit,
                        handleModifier = Modifier
                            .clickable(
                                onClickLabel = stringResource(R.string.browser_menu_dismiss),
                                role = Role.Button,
                                onClick = onDismissRequest,
                            )
                            .draggable(
                                state = rememberDraggableState { delta ->
                                    if (dragging && currentExpanded) {
                                        dragProgress = BrowserMenuBottomSheetMotionRules.dragProgress(
                                            progress = dragProgress,
                                            deltaPx = delta,
                                            collapsedOffsetPx = collapsedOffsetPx,
                                        )
                                    }
                                },
                                orientation = Orientation.Vertical,
                                enabled = expanded && !actionCommitted,
                                startDragImmediately = progress.isRunning,
                                onDragStarted = {
                                    if (currentExpanded && !actionCommitted) {
                                        progress.stop()
                                        if (currentExpanded && !actionCommitted) {
                                            dragProgress = progress.value.coerceIn(0f, 1f)
                                            dragging = true
                                        }
                                    }
                                },
                                onDragStopped = { velocity ->
                                    if (!currentExpanded || actionCommitted || !dragging) return@draggable
                                    progress.snapTo(dragProgress)
                                    dragging = false
                                    if (BrowserMenuBottomSheetMotionRules.shouldDismiss(
                                            progress = dragProgress,
                                            velocityDpPerSecond = velocity / density.density,
                                        )
                                    ) {
                                        onDismissRequest()
                                    } else {
                                        progress.animateTo(1f, returnAnimationSpec)
                                    }
                                },
                            ),
                        extensionContent = extensionContent,
                        customShortcutSlots = customShortcutSlots,
                        customFooterSlots = customFooterSlots,
                        onCustomAction = { id -> commit { onCustomAction(id) } },
                        customContent = customContent,
                    )
                }
            }
        }
    }
}
