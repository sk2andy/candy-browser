package dev.sk2andy.materialbrowser.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import dev.sk2andy.materialbrowser.data.AddressBarAction
import dev.sk2andy.materialbrowser.data.AddressBarActionLayout
import dev.sk2andy.materialbrowser.data.AddressBarActionLayoutRules
import dev.sk2andy.materialbrowser.data.AddressBarActionSide
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal object AddressBarActionEditorTestTags {
    const val Page = "address_bar_action_editor"
    const val Preview = "address_bar_action_editor_preview"
    const val Address = "address_bar_action_editor_address"
    const val More = "address_bar_action_editor_more"
    const val Palette = "address_bar_action_editor_palette"
    const val FullMessage = "address_bar_action_editor_full_message"
    const val DragOverlay = "address_bar_action_editor_drag_overlay"

    fun dropIndicators(targets: List<AddressBarActionEditorTarget>): String =
        "address_bar_action_editor_drops:" + targets.joinToString(separator = "|") {
            "${it.side.name}:${it.index}"
        }

    fun action(action: AddressBarAction): String =
        "address_bar_action_editor_action:${action.wireValue}"

    fun slot(target: AddressBarActionEditorTarget): String =
        "address_bar_action_editor_slot:${target.side.name}:${target.index}"

    fun dropIndicator(target: AddressBarActionEditorTarget): String =
        "address_bar_action_editor_drop:${target.side.name}:${target.index}"
}

private sealed interface AddressBarActionEditorDestination {
    data object Palette : AddressBarActionEditorDestination

    data class Slot(
        val target: AddressBarActionEditorTarget,
    ) : AddressBarActionEditorDestination
}

private data class ActiveAddressBarActionDrag(
    val action: AddressBarAction,
    val sourceBounds: Rect,
    val grabPointInRoot: Offset,
    val rawOffset: Offset = Offset.Zero,
    val visibleOffset: Offset = Offset.Zero,
    val sourceShowsLabel: Boolean,
    val breakawayReleased: Boolean = false,
    val destination: AddressBarActionEditorDestination? = null,
    val targetWidthPx: Float? = null,
    val targetHeightPx: Float? = null,
    val labelVisibleTarget: Boolean = sourceShowsLabel,
    val settling: Boolean = false,
)

private sealed interface AddressBarActionEditorPlacement {
    data object Palette : AddressBarActionEditorPlacement

    data class Toolbar(
        val side: AddressBarActionSide,
        val index: Int,
    ) : AddressBarActionEditorPlacement
}

private data class AddressBarActionEditorMeasurement(
    val bounds: Rect,
    val placement: AddressBarActionEditorPlacement,
)

@Composable
internal fun AddressBarActionEditorPage(
    layout: AddressBarActionLayout,
    tabCount: Int,
    onLayoutChanged: (AddressBarActionLayout) -> Unit,
    onBack: () -> Unit,
    backLabel: String,
    title: String,
    instructions: String,
    availableTitle: String,
    beforeLabel: String,
    afterLabel: String,
    moreLabel: String,
    fullMessage: String,
    actionLabel: (AddressBarAction) -> String,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val configuration = LocalConfiguration.current
    val compactLayout = configuration.screenHeightDp / density.fontScale < 520f
    val scope = rememberCoroutineScope()
    val currentLayout by rememberUpdatedState(layout)
    val currentOnLayoutChanged by rememberUpdatedState(onLayoutChanged)
    val currentActionLabel by rememberUpdatedState(actionLabel)
    val actionMeasurements = remember {
        mutableStateMapOf<AddressBarAction, AddressBarActionEditorMeasurement>()
    }
    val emptySideBounds = remember { mutableStateMapOf<AddressBarActionSide, Rect>() }
    var rootBounds by remember { mutableStateOf<Rect?>(null) }
    var paletteBounds by remember { mutableStateOf<Rect?>(null) }
    var activeDrag by remember { mutableStateOf<ActiveAddressBarActionDrag?>(null) }
    var breakawayCorrection by remember { mutableStateOf(Offset.Zero) }
    var rubberbandActive by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var breakawayJob by remember { mutableStateOf<Job?>(null) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val breakawayDistancePx = with(density) { ACTION_BREAKAWAY_DISTANCE.toPx() }
    val snapEnterPaddingPx = with(density) { ACTION_SNAP_ENTER_PADDING.toPx() }
    val snapRetainPaddingPx = with(density) { ACTION_SNAP_RETAIN_PADDING.toPx() }

    fun stopRubberband() {
        if (rubberbandActive) view.stopRubberbandHaptic()
        rubberbandActive = false
    }

    fun playSegmentHaptic() {
        if (!view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    fun rawSlots(
        side: AddressBarActionSide,
        actions: List<AddressBarAction>,
    ): List<AddressBarActionEditorSlot> {
        val measuredBounds = actions.mapIndexed { index, action ->
            actionMeasurements[action]
                ?.takeIf {
                    it.placement == AddressBarActionEditorPlacement.Toolbar(side, index)
                }
                ?.bounds
                ?.toEditorRect()
                ?: return emptyList()
        }
        return AddressBarActionEditorRules.insertionSlots(
            side = side,
            actionBounds = measuredBounds,
            emptyBounds = emptySideBounds[side]?.toEditorRect(),
            isRtl = isRtl,
        )
    }

    fun slots(): List<AddressBarActionEditorSlot> {
        val rawSlots = rawSlots(
            side = AddressBarActionSide.BeforeAddress,
            actions = currentLayout.beforeAddress,
        ) + rawSlots(
            side = AddressBarActionSide.AfterAddress,
            actions = currentLayout.afterAddress,
        )
        val drag = activeDrag ?: return rawSlots
        val removedSide = when {
            drag.action in currentLayout.beforeAddress -> AddressBarActionSide.BeforeAddress
            drag.action in currentLayout.afterAddress -> AddressBarActionSide.AfterAddress
            else -> return rawSlots
        }
        val removedIndex = when (removedSide) {
            AddressBarActionSide.BeforeAddress -> currentLayout.beforeAddress.indexOf(drag.action)
            AddressBarActionSide.AfterAddress -> currentLayout.afterAddress.indexOf(drag.action)
        }
        return AddressBarActionEditorRules.canonicalSlotsAfterRemoving(
            slots = rawSlots,
            removedSide = removedSide,
            removedIndex = removedIndex,
            removedBounds = drag.sourceBounds.toEditorRect(),
        )
    }

    fun canUseToolbarSlots(action: AddressBarAction): Boolean =
        action in currentLayout.beforeAddress ||
            action in currentLayout.afterAddress ||
            currentLayout.beforeAddress.size + currentLayout.afterAddress.size <
            AddressBarActionLayoutRules.MAX_CONFIGURABLE_ACTIONS

    fun resolvedDestination(
        pointerInRoot: Offset,
        current: AddressBarActionEditorDestination?,
    ): AddressBarActionEditorDestination? {
        val palette = paletteBounds
        val paletteRetained = current == AddressBarActionEditorDestination.Palette &&
            palette?.containsExpanded(pointerInRoot, snapRetainPaddingPx) == true
        if (paletteRetained || palette?.contains(pointerInRoot) == true) {
            return AddressBarActionEditorDestination.Palette
        }
        val currentTarget = (current as? AddressBarActionEditorDestination.Slot)?.target
        return AddressBarActionEditorRules.snappedTarget(
            pointerX = pointerInRoot.x,
            pointerY = pointerInRoot.y,
            slots = slots(),
            currentTarget = currentTarget,
            enterPaddingPx = snapEnterPaddingPx,
            retainPaddingPx = snapRetainPaddingPx,
        )?.let(AddressBarActionEditorDestination::Slot)
    }

    fun startBreakawaySpring(initialCorrection: Offset) {
        breakawayJob?.cancel()
        breakawayCorrection = initialCorrection
        breakawayJob = scope.launch {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = AddressBarMotion.dockBreakawayAnimationSpec,
            ) { progress, _ ->
                breakawayCorrection = initialCorrection * (1f - progress)
            }
            breakawayCorrection = Offset.Zero
            breakawayJob = null
        }
    }

    fun beginDrag(
        action: AddressBarAction,
        sourceBounds: Rect,
        grabPointInSource: Offset,
    ): Boolean {
        if (activeDrag != null || sourceBounds.width <= 0f || sourceBounds.height <= 0f) {
            return false
        }
        settleJob?.cancel()
        breakawayJob?.cancel()
        stopRubberband()
        breakawayCorrection = Offset.Zero
        statusMessage = null
        activeDrag = ActiveAddressBarActionDrag(
            action = action,
            sourceBounds = sourceBounds,
            grabPointInRoot = sourceBounds.topLeft + grabPointInSource,
            sourceShowsLabel = sourceBounds.width > with(density) { 64.dp.toPx() },
        )
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        return true
    }

    fun updateDrag(delta: Offset) {
        val current = activeDrag?.takeUnless(ActiveAddressBarActionDrag::settling) ?: return
        val rawOffset = current.rawOffset + Offset(
            x = delta.x.takeIf(Float::isFinite) ?: 0f,
            y = delta.y.takeIf(Float::isFinite) ?: 0f,
        )
        val resistance = AddressBarActionEditorRules.dragResistance(
            dragX = rawOffset.x,
            dragY = rawOffset.y,
            breakawayDistancePx = breakawayDistancePx,
        )
        val released = current.breakawayReleased || resistance.breakawayReached
        val visibleOffset = if (released) {
            rawOffset
        } else {
            Offset(resistance.visibleX, resistance.visibleY)
        }
        if (!released && rawOffset.getDistance() > 0.01f && !rubberbandActive) {
            view.startRubberbandHaptic()
            rubberbandActive = true
        }
        if (!current.breakawayReleased && released) {
            stopRubberband()
            view.performConfirmHaptic()
            startBreakawaySpring(current.visibleOffset - rawOffset)
        }
        val destination = if (released) {
            resolvedDestination(
                pointerInRoot = current.grabPointInRoot + rawOffset,
                current = current.destination,
            ).takeUnless { candidate ->
                candidate is AddressBarActionEditorDestination.Slot &&
                    !canUseToolbarSlots(current.action)
            }
        } else {
            null
        }
        if (destination != null && destination != current.destination) playSegmentHaptic()
        activeDrag = current.copy(
            rawOffset = rawOffset,
            visibleOffset = visibleOffset,
            breakawayReleased = released,
            destination = destination,
            targetWidthPx = if (destination is AddressBarActionEditorDestination.Slot) {
                with(density) { 48.dp.toPx() }
            } else {
                current.sourceBounds.width
            },
            targetHeightPx = if (destination is AddressBarActionEditorDestination.Slot) {
                with(density) { 48.dp.toPx() }
            } else {
                current.sourceBounds.height
            },
            labelVisibleTarget = destination !is AddressBarActionEditorDestination.Slot &&
                current.sourceShowsLabel,
        )
    }

    fun settleDrag(
        targetOffset: Offset,
        targetBounds: Rect? = null,
        compactTarget: Boolean = false,
        labelVisibleTarget: Boolean? = null,
    ) {
        val current = activeDrag ?: return
        stopRubberband()
        breakawayJob?.cancel()
        breakawayJob = null
        val initialOffset = current.visibleOffset + breakawayCorrection
        breakawayCorrection = Offset.Zero
        activeDrag = current.copy(
            visibleOffset = initialOffset,
            destination = null,
            targetWidthPx = targetBounds?.width ?: if (compactTarget) {
                with(density) { 48.dp.toPx() }
            } else {
                current.sourceBounds.width
            },
            targetHeightPx = targetBounds?.height ?: if (compactTarget) {
                with(density) { 48.dp.toPx() }
            } else {
                current.sourceBounds.height
            },
            labelVisibleTarget = labelVisibleTarget
                ?: (!compactTarget && current.sourceShowsLabel),
            settling = true,
        )
        settleJob?.cancel()
        settleJob = scope.launch {
            val offset = Animatable(initialOffset, Offset.VectorConverter)
            offset.animateTo(
                targetValue = targetOffset,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 620f),
            ) {
                activeDrag = activeDrag
                    ?.takeIf { it.action == current.action }
                    ?.copy(visibleOffset = value)
            }
            if (activeDrag?.action == current.action) activeDrag = null
            settleJob = null
        }
    }

    fun finishDrag(cancelled: Boolean) {
        val current = activeDrag?.takeUnless(ActiveAddressBarActionDrag::settling) ?: return
        stopRubberband()
        if (cancelled || !current.breakawayReleased) {
            settleDrag(Offset.Zero)
            return
        }
        when (val destination = current.destination) {
            AddressBarActionEditorDestination.Palette -> {
                val updated = AddressBarActionLayoutRules.remove(currentLayout, current.action)
                if (updated == currentLayout) {
                    settleDrag(Offset.Zero)
                } else {
                    currentOnLayoutChanged(updated)
                    statusMessage = null
                    view.performConfirmHaptic()
                    activeDrag = current.copy(
                        destination = AddressBarActionEditorDestination.Palette,
                        settling = true,
                    )
                    scope.launch {
                        var targetBounds: Rect? = null
                        var remainingMeasurementFrames = PALETTE_MEASUREMENT_FRAMES
                        while (targetBounds == null && remainingMeasurementFrames > 0) {
                            withFrameNanos { }
                            val candidate = actionMeasurements[current.action]
                                ?.takeIf {
                                    it.placement == AddressBarActionEditorPlacement.Palette
                                }
                                ?.bounds
                            if (
                                candidate != null &&
                                candidate.width > with(density) { 64.dp.toPx() }
                            ) {
                                targetBounds = candidate
                            }
                            remainingMeasurementFrames -= 1
                        }
                        val resolvedBounds = targetBounds ?: paletteFallbackBounds(
                            paletteBounds = paletteBounds,
                            density = density,
                        )
                        settleDrag(
                            targetOffset = resolvedBounds?.center
                                ?.minus(current.sourceBounds.center)
                                ?: Offset.Zero,
                            targetBounds = resolvedBounds,
                            labelVisibleTarget = true,
                        )
                    }
                }
            }
            is AddressBarActionEditorDestination.Slot -> {
                when (
                    val decision = AddressBarActionEditorRules.drop(
                        layout = currentLayout,
                        action = current.action,
                        target = destination.target,
                    )
                ) {
                    is AddressBarActionDropDecision.Accepted -> {
                        if (decision.changed) {
                            statusMessage = null
                            view.performConfirmHaptic()
                            activeDrag = current.copy(
                                destination = destination,
                                targetWidthPx = with(density) { 48.dp.toPx() },
                                targetHeightPx = with(density) { 48.dp.toPx() },
                                labelVisibleTarget = false,
                                settling = true,
                            )
                            currentOnLayoutChanged(decision.layout)
                            scope.launch {
                                var targetBounds: Rect? = null
                                var remainingMeasurementFrames =
                                    TOOLBAR_MEASUREMENT_FRAMES
                                val targetPlacement = AddressBarActionEditorPlacement.Toolbar(
                                    side = destination.target.side,
                                    index = destination.target.index,
                                )
                                while (
                                    targetBounds == null &&
                                    remainingMeasurementFrames > 0
                                ) {
                                    withFrameNanos { }
                                    targetBounds = actionMeasurements[current.action]
                                        ?.takeIf { it.placement == targetPlacement }
                                        ?.bounds
                                    remainingMeasurementFrames -= 1
                                }
                                settleDrag(
                                    targetOffset = targetBounds?.center
                                        ?.minus(current.sourceBounds.center)
                                        ?: current.visibleOffset,
                                    targetBounds = targetBounds,
                                    compactTarget = true,
                                )
                            }
                        } else {
                            settleDrag(Offset.Zero)
                        }
                    }
                    is AddressBarActionDropDecision.Rejected -> {
                        if (decision.reason == AddressBarActionDropRejection.Full) {
                            statusMessage = fullMessage
                        }
                        view.performEditorRejectHaptic()
                        settleDrag(Offset.Zero)
                    }
                }
            }
            null -> settleDrag(Offset.Zero)
        }
    }

    fun targetAtEnd(
        action: AddressBarAction,
        side: AddressBarActionSide,
    ): AddressBarActionEditorTarget {
        val actions = when (side) {
            AddressBarActionSide.BeforeAddress -> currentLayout.beforeAddress
            AddressBarActionSide.AfterAddress -> currentLayout.afterAddress
        }
        return AddressBarActionEditorTarget(
            side = side,
            index = actions.count { it != action },
        )
    }

    fun moveWithAccessibility(
        action: AddressBarAction,
        side: AddressBarActionSide,
    ): Boolean {
        return when (
            val decision = AddressBarActionEditorRules.drop(
                layout = currentLayout,
                action = action,
                target = targetAtEnd(action, side),
            )
        ) {
            is AddressBarActionDropDecision.Accepted -> {
                if (decision.changed) {
                    currentOnLayoutChanged(decision.layout)
                    statusMessage = null
                    view.performConfirmHaptic()
                }
                true
            }
            is AddressBarActionDropDecision.Rejected -> {
                if (decision.reason == AddressBarActionDropRejection.Full) {
                    statusMessage = fullMessage
                }
                view.performEditorRejectHaptic()
                true
            }
        }
    }

    fun removeWithAccessibility(action: AddressBarAction): Boolean {
        val updated = AddressBarActionLayoutRules.remove(currentLayout, action)
        if (updated != currentLayout) {
            currentOnLayoutChanged(updated)
            statusMessage = null
            view.performConfirmHaptic()
        }
        return true
    }

    DisposableEffect(view) {
        onDispose {
            settleJob?.cancel()
            breakawayJob?.cancel()
            if (rubberbandActive) view.stopRubberbandHaptic()
        }
    }

    val available = AddressBarActionLayoutRules.available(layout)
    val draggedAction = activeDrag?.action
    val destination = activeDrag?.destination
    val lift by animateFloatAsState(
        targetValue = if (activeDrag?.settling == false) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 620f),
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(AddressBarActionEditorTestTags.Page)
            .onGloballyPositioned { rootBounds = it.boundsInRoot() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = backLabel,
                    )
                }
                Text(
                    text = title,
                    maxLines = 1,
                    style = if (compactLayout) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.headlineSmall
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (!compactLayout) {
                Text(
                    text = instructions,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(16.dp))
            } else {
                Spacer(Modifier.size(4.dp))
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(AddressBarActionEditorTestTags.Preview),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AddressBarActionEditorSideContent(
                        actions = layout.beforeAddress,
                        tabCount = tabCount,
                        side = AddressBarActionSide.BeforeAddress,
                        sideLabel = beforeLabel,
                        draggedAction = draggedAction,
                        destination = destination,
                        actionLabel = currentActionLabel,
                        actionMeasurements = actionMeasurements,
                        emptySideBounds = emptySideBounds,
                        onStartDrag = ::beginDrag,
                        onDrag = ::updateDrag,
                        onDragEnd = { finishDrag(cancelled = false) },
                        onDragCancel = { finishDrag(cancelled = true) },
                        onMoveBefore = {
                            moveWithAccessibility(it, AddressBarActionSide.BeforeAddress)
                        },
                        onMoveAfter = {
                            moveWithAccessibility(it, AddressBarActionSide.AfterAddress)
                        },
                        onRemove = ::removeWithAccessibility,
                        beforeLabel = beforeLabel,
                        afterLabel = afterLabel,
                        removeLabel = availableTitle,
                    )
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag(AddressBarActionEditorTestTags.Address)
                            .clearAndSetSemantics { },
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ) {}
                    AddressBarActionEditorSideContent(
                        actions = layout.afterAddress,
                        tabCount = tabCount,
                        side = AddressBarActionSide.AfterAddress,
                        sideLabel = afterLabel,
                        draggedAction = draggedAction,
                        destination = destination,
                        actionLabel = currentActionLabel,
                        actionMeasurements = actionMeasurements,
                        emptySideBounds = emptySideBounds,
                        onStartDrag = ::beginDrag,
                        onDrag = ::updateDrag,
                        onDragEnd = { finishDrag(cancelled = false) },
                        onDragCancel = { finishDrag(cancelled = true) },
                        onMoveBefore = {
                            moveWithAccessibility(it, AddressBarActionSide.BeforeAddress)
                        },
                        onMoveAfter = {
                            moveWithAccessibility(it, AddressBarActionSide.AfterAddress)
                        },
                        onRemove = ::removeWithAccessibility,
                        beforeLabel = beforeLabel,
                        afterLabel = afterLabel,
                        removeLabel = availableTitle,
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .testTag(AddressBarActionEditorTestTags.More)
                            .semantics { contentDescription = moreLabel },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            statusMessage?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag(AddressBarActionEditorTestTags.FullMessage)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.size(if (compactLayout) 8.dp else 20.dp))
            if (!compactLayout) {
                Text(
                    text = availableTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(8.dp))
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .testTag(AddressBarActionEditorTestTags.Palette)
                    .semantics { contentDescription = availableTitle }
                    .onGloballyPositioned { paletteBounds = it.boundsInRoot() },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                available.chunked(PALETTE_COLUMNS).forEach { rowActions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowActions.forEach { action ->
                            Box(modifier = Modifier.weight(1f)) {
                                AddressBarActionEditorItem(
                                    action = action,
                                    tabCount = tabCount,
                                    label = currentActionLabel(action),
                                    compact = false,
                                    dragged = action == draggedAction,
                                    highlighted = destination ==
                                        AddressBarActionEditorDestination.Palette &&
                                        action == draggedAction,
                                    customActions = listOf(
                                        CustomAccessibilityAction(
                                            "$beforeLabel: ${currentActionLabel(action)}",
                                        ) {
                                            moveWithAccessibility(
                                                action,
                                                AddressBarActionSide.BeforeAddress,
                                            )
                                        },
                                        CustomAccessibilityAction(
                                            "$afterLabel: ${currentActionLabel(action)}",
                                        ) {
                                            moveWithAccessibility(
                                                action,
                                                AddressBarActionSide.AfterAddress,
                                            )
                                        },
                                    ),
                                    sourceBounds = actionMeasurements[action]?.bounds,
                                    onBounds = { bounds ->
                                        actionMeasurements[action] =
                                            AddressBarActionEditorMeasurement(
                                                bounds = bounds,
                                                placement =
                                                    AddressBarActionEditorPlacement.Palette,
                                            )
                                    },
                                    onStartDrag = { bounds, point ->
                                        beginDrag(action, bounds, point)
                                    },
                                    onDrag = ::updateDrag,
                                    onDragEnd = { finishDrag(cancelled = false) },
                                    onDragCancel = { finishDrag(cancelled = true) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        repeat(PALETTE_COLUMNS - rowActions.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        activeDrag?.takeIf { drag ->
            drag.breakawayReleased &&
                !drag.settling &&
                canUseToolbarSlots(drag.action)
        }?.let { drag ->
            AddressBarActionDropIndicators(
                slots = slots(),
                rootBounds = rootBounds,
                currentTarget = (
                    drag.destination as? AddressBarActionEditorDestination.Slot
                    )?.target,
            )
        }

        activeDrag?.let { drag ->
            val rootTopLeft = rootBounds?.topLeft ?: Offset.Zero
            val width by animateDpAsState(
                targetValue = with(density) {
                    (drag.targetWidthPx ?: drag.sourceBounds.width).toDp()
                },
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 700f),
            )
            val height by animateDpAsState(
                targetValue = with(density) {
                    (drag.targetHeightPx ?: drag.sourceBounds.height).toDp()
                },
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 700f),
            )
            val labelAlpha by animateFloatAsState(
                targetValue = if (drag.labelVisibleTarget) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.86f, stiffness = 760f),
            )
            val sizeCorrection = with(density) {
                Offset(
                    x = (drag.sourceBounds.width - width.toPx()) / 2f,
                    y = (drag.sourceBounds.height - height.toPx()) / 2f,
                )
            }
            val topLeft = drag.sourceBounds.topLeft - rootTopLeft +
                drag.visibleOffset + breakawayCorrection + sizeCorrection
            Surface(
                modifier = Modifier
                    .offset {
                        IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt())
                    }
                    .size(width = width, height = height)
                    .zIndex(30f)
                    .graphicsLayer {
                        val scale = 1f + lift * 0.04f
                        scaleX = scale
                        scaleY = scale
                    }
                    .shadow(
                        elevation = (2f + lift * 7f).dp,
                        shape = MaterialTheme.shapes.large,
                    )
                    .clearAndSetSemantics { }
                    .testTag(AddressBarActionEditorTestTags.DragOverlay),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AddressBarActionGlyph(
                        action = drag.action,
                        tabCount = tabCount,
                        selected = true,
                        isLoading = false,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = 12.dp)
                            .size(24.dp),
                    )
                    Text(
                        text = currentActionLabel(drag.action),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth()
                            .padding(start = 44.dp, end = 10.dp)
                            .graphicsLayer { alpha = labelAlpha },
                        maxLines = 2,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddressBarActionDropIndicators(
    slots: List<AddressBarActionEditorSlot>,
    rootBounds: Rect?,
    currentTarget: AddressBarActionEditorTarget?,
) {
    val density = LocalDensity.current
    val rootTopLeft = rootBounds?.topLeft ?: Offset.Zero
    val motion = rememberInfiniteTransition(label = "Address action drop targets")
    val wiggle by motion.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 820,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "Drop target wiggle",
    )
    val morph by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 690,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "Drop target shape",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(AddressBarActionEditorTestTags.dropIndicators(slots.map { it.target })),
    ) {
        slots.forEachIndexed { index, slot ->
            key(slot.target) {
                val selectedProgress by animateFloatAsState(
                    targetValue = if (slot.target == currentTarget) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 620f),
                )
                val size = 24.dp + 6.dp * selectedProgress
                val sizePx = with(density) { size.toPx() }
                val freeMotion = 1f - selectedProgress
                val wigglePx = with(density) { 2.dp.toPx() } * wiggle * freeMotion *
                    if (index % 2 == 0) 1f else -1f
                val center = Offset(
                    x = slot.indicatorCenter.x,
                    y = slot.indicatorCenter.y,
                ) - rootTopLeft
                val cornerRadius = 9.dp + 3.dp * morph
                Surface(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (center.x - sizePx / 2f + wigglePx).roundToInt(),
                                y = (center.y - sizePx / 2f - wigglePx * 0.35f)
                                    .roundToInt(),
                            )
                        }
                        .size(size)
                        .graphicsLayer {
                            rotationZ = wiggle * freeMotion *
                                if (index % 2 == 0) 3.5f else -3.5f
                        }
                        .border(
                            width = 1.dp,
                            color = if (slot.target == currentTarget) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = RoundedCornerShape(cornerRadius),
                        )
                        .zIndex(20f)
                        .clearAndSetSemantics { }
                        .testTag(AddressBarActionEditorTestTags.dropIndicator(slot.target)),
                    shape = RoundedCornerShape(cornerRadius),
                    color = if (slot.target == currentTarget) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (slot.target == currentTarget) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddressBarActionEditorSideContent(
    actions: List<AddressBarAction>,
    tabCount: Int,
    side: AddressBarActionSide,
    sideLabel: String,
    draggedAction: AddressBarAction?,
    destination: AddressBarActionEditorDestination?,
    actionLabel: (AddressBarAction) -> String,
    actionMeasurements: MutableMap<AddressBarAction, AddressBarActionEditorMeasurement>,
    emptySideBounds: MutableMap<AddressBarActionSide, Rect>,
    onStartDrag: (AddressBarAction, Rect, Offset) -> Boolean,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onMoveBefore: (AddressBarAction) -> Boolean,
    onMoveAfter: (AddressBarAction) -> Boolean,
    onRemove: (AddressBarAction) -> Boolean,
    beforeLabel: String,
    afterLabel: String,
    removeLabel: String,
) {
    Row(
        modifier = Modifier.semantics { contentDescription = sideLabel },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (actions.isEmpty()) {
            val target = AddressBarActionEditorTarget(side, 0)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .testTag(AddressBarActionEditorTestTags.slot(target))
                    .onGloballyPositioned { emptySideBounds[side] = it.boundsInRoot() }
                    .clearAndSetSemantics { },
            )
        } else {
            actions.forEachIndexed { index, action ->
                val beforeTarget = AddressBarActionEditorTarget(side, index)
                val afterTarget = AddressBarActionEditorTarget(side, index + 1)
                val highlightedTarget =
                    (destination as? AddressBarActionEditorDestination.Slot)?.target
                AddressBarActionEditorItem(
                    action = action,
                    tabCount = tabCount,
                    label = actionLabel(action),
                    compact = true,
                    dragged = action == draggedAction,
                    highlighted = highlightedTarget == beforeTarget ||
                        highlightedTarget == afterTarget,
                    customActions = listOf(
                        CustomAccessibilityAction(
                            "$beforeLabel: ${actionLabel(action)}",
                        ) { onMoveBefore(action) },
                        CustomAccessibilityAction(
                            "$afterLabel: ${actionLabel(action)}",
                        ) { onMoveAfter(action) },
                        CustomAccessibilityAction(
                            "$removeLabel: ${actionLabel(action)}",
                        ) { onRemove(action) },
                    ),
                    sourceBounds = actionMeasurements[action]?.bounds,
                    onBounds = { bounds ->
                        actionMeasurements[action] = AddressBarActionEditorMeasurement(
                            bounds = bounds,
                            placement = AddressBarActionEditorPlacement.Toolbar(
                                side = side,
                                index = index,
                            ),
                        )
                    },
                    onStartDrag = { bounds, point -> onStartDrag(action, bounds, point) },
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                    modifier = Modifier,
                )
            }
        }
    }
}

@Composable
private fun AddressBarActionEditorItem(
    action: AddressBarAction,
    tabCount: Int,
    label: String,
    compact: Boolean,
    dragged: Boolean,
    highlighted: Boolean,
    customActions: List<CustomAccessibilityAction>,
    sourceBounds: Rect?,
    onBounds: (Rect) -> Unit,
    onStartDrag: (Rect, Offset) -> Boolean,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semanticsModifier = if (dragged) {
        Modifier.clearAndSetSemantics { }
    } else {
        Modifier.semantics {
            contentDescription = label
            this.customActions = customActions
        }
    }
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(if (compact) Modifier.width(48.dp) else Modifier)
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .then(semanticsModifier)
            .testTag(AddressBarActionEditorTestTags.action(action))
            .addressBarActionEditorDragSource(
                action = action,
                sourceBounds = sourceBounds,
                onStartDrag = onStartDrag,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
            )
            .graphicsLayer { alpha = if (dragged) 0f else 1f },
        shape = MaterialTheme.shapes.large,
        color = if (highlighted) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 12.dp else 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AddressBarActionGlyph(
                action = action,
                tabCount = tabCount,
                selected = highlighted,
                isLoading = false,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            if (!compact) {
                Text(
                    text = label,
                    maxLines = 2,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

private fun Modifier.addressBarActionEditorDragSource(
    action: AddressBarAction,
    sourceBounds: Rect?,
    onStartDrag: (Rect, Offset) -> Boolean,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
): Modifier = pointerInput(action, sourceBounds) {
    var accepted = false
    detectDragGesturesAfterLongPress(
        onDragStart = { point ->
            accepted = sourceBounds?.let { onStartDrag(it, point) } == true
        },
        onDrag = { change, amount ->
            if (accepted) {
                change.consume()
                onDrag(amount)
            }
        },
        onDragEnd = {
            if (accepted) onDragEnd()
            accepted = false
        },
        onDragCancel = {
            if (accepted) onDragCancel()
            accepted = false
        },
    )
}

private fun Rect.containsExpanded(point: Offset, paddingPx: Float): Boolean =
    point.x >= left - paddingPx &&
        point.x <= right + paddingPx &&
        point.y >= top - paddingPx &&
        point.y <= bottom + paddingPx

private fun Rect.toEditorRect(): AddressBarActionEditorRect = AddressBarActionEditorRect(
    left = left,
    top = top,
    right = right,
    bottom = bottom,
)

private fun paletteFallbackBounds(
    paletteBounds: Rect?,
    density: Density,
): Rect? {
    val palette = paletteBounds ?: return null
    val gapPx = with(density) { 8.dp.toPx() }
    val itemWidth = ((palette.width - gapPx) / PALETTE_COLUMNS).coerceAtLeast(1f)
    val itemHeight = with(density) { 48.dp.toPx() }
    return Rect(
        left = palette.center.x - itemWidth / 2f,
        top = palette.center.y - itemHeight / 2f,
        right = palette.center.x + itemWidth / 2f,
        bottom = palette.center.y + itemHeight / 2f,
    )
}

private fun View.performEditorRejectHaptic() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        },
    )
}

private val ACTION_BREAKAWAY_DISTANCE = 36.dp
private val ACTION_SNAP_ENTER_PADDING = 8.dp
private val ACTION_SNAP_RETAIN_PADDING = 16.dp
private const val PALETTE_COLUMNS = 2
private const val PALETTE_MEASUREMENT_FRAMES = 3
private const val TOOLBAR_MEASUREMENT_FRAMES = 6
