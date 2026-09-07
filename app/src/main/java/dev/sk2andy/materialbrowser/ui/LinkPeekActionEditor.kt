package dev.sk2andy.materialbrowser.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.LinkPeekAction
import dev.sk2andy.materialbrowser.data.LinkPeekActionLayout
import dev.sk2andy.materialbrowser.data.LinkPeekActionLayoutRules
import dev.sk2andy.materialbrowser.ui.theme.LocalCandyMotionScheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal object LinkPeekActionEditorTestTags {
    const val Page = "link_peek_action_editor"
    const val Preview = "link_peek_action_editor_preview"
    const val Palette = "link_peek_action_editor_palette"
    const val FixedPlus = "link_peek_action_editor_fixed_plus"
    const val FixedPlusPulseRing = "link_peek_action_editor_fixed_plus_pulse_ring"
    const val DragOverlay = "link_peek_action_editor_drag_overlay"

    fun action(action: LinkPeekAction): String =
        "link_peek_action_editor_action:${action.wireValue}"

    fun slot(actionIndex: Int): String = "link_peek_action_editor_slot:$actionIndex"

    fun dropIndicators(targets: List<LinkPeekActionEditorTarget>): String =
        "link_peek_action_editor_drops:" + targets.joinToString(separator = "|") {
            when (it) {
                LinkPeekActionEditorTarget.FixedPlus -> "plus"
                LinkPeekActionEditorTarget.Palette -> "palette"
                is LinkPeekActionEditorTarget.ActionSlot -> "action:${it.actionIndex}"
            }
        }

    fun dropIndicator(target: LinkPeekActionEditorTarget): String = when (target) {
        LinkPeekActionEditorTarget.FixedPlus -> "link_peek_action_editor_drop:plus"
        LinkPeekActionEditorTarget.Palette -> "link_peek_action_editor_drop:palette"
        is LinkPeekActionEditorTarget.ActionSlot ->
            "link_peek_action_editor_drop:action:${target.actionIndex}"
    }
}

private data class ActiveLinkPeekActionDrag(
    val action: LinkPeekAction,
    val sourceBounds: Rect,
    val grabPointInRoot: Offset,
    val rawOffset: Offset = Offset.Zero,
    val visibleOffset: Offset = Offset.Zero,
    val sourceShowsLabel: Boolean,
    val breakawayReleased: Boolean = false,
    val target: LinkPeekActionEditorTarget? = null,
    val targetWidthPx: Float? = null,
    val targetHeightPx: Float? = null,
    val labelVisibleTarget: Boolean = sourceShowsLabel,
    val settling: Boolean = false,
)

private sealed interface LinkPeekActionEditorPlacement {
    data object Palette : LinkPeekActionEditorPlacement

    data class Toolbar(val actionIndex: Int) : LinkPeekActionEditorPlacement
}

private data class LinkPeekActionEditorMeasurement(
    val bounds: Rect,
    val placement: LinkPeekActionEditorPlacement,
)

@Composable
internal fun LinkPeekActionEditorPage(
    layout: LinkPeekActionLayout,
    onLayoutChanged: (LinkPeekActionLayout) -> Unit,
    onBack: () -> Unit,
    backLabel: String,
    title: String,
    instructions: String,
    availableTitle: String,
    fixedPlusLabel: String,
    emptySlotLabel: String,
    moveToSlotLabel: String,
    moveToAvailableLabel: String,
    actionLabel: (LinkPeekAction) -> String,
) {
    val motionScheme = LocalCandyMotionScheme.current
    val view = LocalView.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val compactLayout = configuration.screenHeightDp / density.fontScale < 520f
    val scope = rememberCoroutineScope()
    val normalizedLayout = LinkPeekActionLayoutRules.normalize(layout)
    val currentLayout by rememberUpdatedState(normalizedLayout)
    val currentOnLayoutChanged by rememberUpdatedState(onLayoutChanged)
    val currentActionLabel by rememberUpdatedState(actionLabel)
    val actionMeasurements = remember {
        mutableStateMapOf<LinkPeekAction, LinkPeekActionEditorMeasurement>()
    }
    val actionSlotBounds = remember { mutableStateMapOf<Int, Rect>() }
    var fixedPlusBounds by remember { mutableStateOf<Rect?>(null) }
    var rootBounds by remember { mutableStateOf<Rect?>(null) }
    var paletteBounds by remember { mutableStateOf<Rect?>(null) }
    var activeDrag by remember { mutableStateOf<ActiveLinkPeekActionDrag?>(null) }
    var breakawayCorrection by remember { mutableStateOf(Offset.Zero) }
    var rubberbandActive by remember { mutableStateOf(false) }
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

    fun slots(): List<LinkPeekActionEditorSlot> {
        val actionBounds = currentLayout.actions.indices.map { index ->
            actionSlotBounds[index]?.toEditorRect() ?: return emptyList()
        }
        return LinkPeekActionEditorRules.availableDropSlots(
            layout = currentLayout,
            slots = LinkPeekActionEditorRules.actionSlots(
                actionBounds = actionBounds,
                fixedPlusBounds = fixedPlusBounds?.toEditorRect(),
            ),
        )
    }

    fun resolvedTarget(
        pointerInRoot: Offset,
        current: LinkPeekActionEditorTarget?,
    ): LinkPeekActionEditorTarget? {
        val palette = paletteBounds
        val paletteRetained = current == LinkPeekActionEditorTarget.Palette &&
            palette?.containsExpanded(pointerInRoot, snapRetainPaddingPx) == true
        if (paletteRetained || palette?.contains(pointerInRoot) == true) {
            return LinkPeekActionEditorTarget.Palette
        }
        return LinkPeekActionEditorRules.snappedTarget(
            pointerX = pointerInRoot.x,
            pointerY = pointerInRoot.y,
            slots = slots(),
            currentTarget = current.takeUnless { it == LinkPeekActionEditorTarget.Palette },
            enterPaddingPx = snapEnterPaddingPx,
            retainPaddingPx = snapRetainPaddingPx,
        )
    }

    fun startBreakawaySpring(initialCorrection: Offset) {
        breakawayJob?.cancel()
        breakawayCorrection = initialCorrection
        breakawayJob = scope.launch {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = AddressBarMotion.dockBreakawayAnimationSpec(motionScheme),
            ) { progress, _ ->
                breakawayCorrection = initialCorrection * (1f - progress)
            }
            breakawayCorrection = Offset.Zero
            breakawayJob = null
        }
    }

    fun beginDrag(
        action: LinkPeekAction,
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
        activeDrag = ActiveLinkPeekActionDrag(
            action = action,
            sourceBounds = sourceBounds,
            grabPointInRoot = sourceBounds.topLeft + grabPointInSource,
            sourceShowsLabel = sourceBounds.width > with(density) { 64.dp.toPx() },
        )
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        return true
    }

    fun updateDrag(delta: Offset) {
        val current = activeDrag?.takeUnless(ActiveLinkPeekActionDrag::settling) ?: return
        val rawOffset = current.rawOffset + Offset(
            x = delta.x.takeIf(Float::isFinite) ?: 0f,
            y = delta.y.takeIf(Float::isFinite) ?: 0f,
        )
        val resistance = LinkPeekActionEditorRules.dragResistance(
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
        val target = if (released) {
            resolvedTarget(
                pointerInRoot = current.grabPointInRoot + rawOffset,
                current = current.target,
            )
        } else {
            null
        }
        if (target != null && target != current.target) playSegmentHaptic()
        activeDrag = current.copy(
            rawOffset = rawOffset,
            visibleOffset = visibleOffset,
            breakawayReleased = released,
            target = target,
            targetWidthPx = if (
                target is LinkPeekActionEditorTarget.ActionSlot ||
                target == LinkPeekActionEditorTarget.FixedPlus
            ) {
                with(density) { 48.dp.toPx() }
            } else {
                current.sourceBounds.width
            },
            targetHeightPx = if (
                target is LinkPeekActionEditorTarget.ActionSlot ||
                target == LinkPeekActionEditorTarget.FixedPlus
            ) {
                with(density) { 48.dp.toPx() }
            } else {
                current.sourceBounds.height
            },
            labelVisibleTarget = target == LinkPeekActionEditorTarget.Palette ||
                target == null && current.sourceShowsLabel,
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
            target = null,
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
                    ?.takeIf { drag -> drag.action == current.action }
                    ?.copy(visibleOffset = value)
            }
            if (activeDrag?.action == current.action) activeDrag = null
            settleJob = null
        }
    }

    fun finishAcceptedDrag(
        drag: ActiveLinkPeekActionDrag,
        target: LinkPeekActionEditorTarget.ActionSlot,
        updated: LinkPeekActionLayout,
    ) {
        view.performConfirmHaptic()
        activeDrag = drag.copy(
            target = target,
            targetWidthPx = with(density) { 48.dp.toPx() },
            targetHeightPx = with(density) { 48.dp.toPx() },
            labelVisibleTarget = false,
            settling = true,
        )
        currentOnLayoutChanged(updated)
        scope.launch {
            var targetBounds: Rect? = null
            var remainingFrames = TOOLBAR_MEASUREMENT_FRAMES
            val targetPlacement = LinkPeekActionEditorPlacement.Toolbar(target.actionIndex)
            while (targetBounds == null && remainingFrames > 0) {
                withFrameNanos { }
                targetBounds = actionMeasurements[drag.action]
                    ?.takeIf { measurement -> measurement.placement == targetPlacement }
                    ?.bounds
                remainingFrames -= 1
            }
            settleDrag(
                targetOffset = targetBounds?.center
                    ?.minus(drag.sourceBounds.center)
                    ?: drag.visibleOffset,
                targetBounds = targetBounds,
                compactTarget = true,
            )
        }
    }

    fun finishPaletteDrag(
        drag: ActiveLinkPeekActionDrag,
        updated: LinkPeekActionLayout,
    ) {
        view.performConfirmHaptic()
        activeDrag = drag.copy(
            target = LinkPeekActionEditorTarget.Palette,
            settling = true,
        )
        currentOnLayoutChanged(updated)
        scope.launch {
            var targetBounds: Rect? = null
            var remainingFrames = PALETTE_MEASUREMENT_FRAMES
            while (targetBounds == null && remainingFrames > 0) {
                withFrameNanos { }
                val candidate = actionMeasurements[drag.action]
                    ?.takeIf { measurement ->
                        measurement.placement == LinkPeekActionEditorPlacement.Palette
                    }
                    ?.bounds
                if (candidate != null && candidate.width > with(density) { 64.dp.toPx() }) {
                    targetBounds = candidate
                }
                remainingFrames -= 1
            }
            val resolvedBounds = targetBounds ?: paletteFallbackBounds(
                paletteBounds = paletteBounds,
                density = density,
            )
            settleDrag(
                targetOffset = resolvedBounds?.center
                    ?.minus(drag.sourceBounds.center)
                    ?: Offset.Zero,
                targetBounds = resolvedBounds,
                labelVisibleTarget = true,
            )
        }
    }

    fun finishDrag(cancelled: Boolean) {
        val current = activeDrag?.takeUnless(ActiveLinkPeekActionDrag::settling) ?: return
        stopRubberband()
        if (cancelled || !current.breakawayReleased) {
            settleDrag(Offset.Zero)
            return
        }
        val target = current.target
        if (target == null) {
            settleDrag(Offset.Zero)
            return
        }
        when (
            val decision = LinkPeekActionEditorRules.drop(
                layout = currentLayout,
                action = current.action,
                target = target,
            )
        ) {
            is LinkPeekActionEditorDropDecision.Accepted -> {
                when {
                    !decision.changed -> settleDrag(Offset.Zero)
                    target is LinkPeekActionEditorTarget.ActionSlot ->
                        finishAcceptedDrag(current, target, decision.layout)
                    target == LinkPeekActionEditorTarget.Palette ->
                        finishPaletteDrag(current, decision.layout)
                    else -> settleDrag(Offset.Zero)
                }
            }

            is LinkPeekActionEditorDropDecision.Rejected -> {
                view.performEditorRejectHaptic()
                settleDrag(Offset.Zero)
            }
        }
    }

    fun placeWithAccessibility(action: LinkPeekAction, targetActionIndex: Int): Boolean {
        return when (
            val decision = LinkPeekActionEditorRules.drop(
                layout = currentLayout,
                action = action,
                target = LinkPeekActionEditorTarget.ActionSlot(targetActionIndex),
            )
        ) {
            is LinkPeekActionEditorDropDecision.Accepted -> {
                if (decision.changed) {
                    currentOnLayoutChanged(decision.layout)
                    view.performConfirmHaptic()
                }
                true
            }

            is LinkPeekActionEditorDropDecision.Rejected -> {
                view.performEditorRejectHaptic()
                true
            }
        }
    }

    fun removeWithAccessibility(action: LinkPeekAction): Boolean {
        val updated = LinkPeekActionLayoutRules.remove(currentLayout, action)
        if (updated != currentLayout) {
            currentOnLayoutChanged(updated)
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

    val available = LinkPeekActionLayoutRules.available(normalizedLayout)
    val emptyActionIndexes = normalizedLayout.actions.mapIndexedNotNull { index, action ->
        index.takeIf { action == null }
    }
    val draggedAction = activeDrag?.action
    val currentTarget = activeDrag?.target
    val lift by animateFloatAsState(
        targetValue = if (activeDrag?.settling == false) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 620f),
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(LinkPeekActionEditorTestTags.Page)
            .onGloballyPositioned { coordinates -> rootBounds = coordinates.boundsInRoot() },
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
                    overflow = TextOverflow.Ellipsis,
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
                    .testTag(LinkPeekActionEditorTestTags.Preview),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    normalizedLayout.actions.forEachIndexed { actionIndex, action ->
                        if (actionIndex == LinkPeekActionLayoutRules.FIXED_PLUS_SLOT_INDEX) {
                            FixedPlusSlot(
                                label = fixedPlusLabel,
                                onBounds = { bounds -> fixedPlusBounds = bounds },
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .testTag(LinkPeekActionEditorTestTags.slot(actionIndex))
                                .onGloballyPositioned { coordinates ->
                                    actionSlotBounds[actionIndex] = coordinates.boundsInRoot()
                                }
                                .semantics {
                                    if (action == null) {
                                        val visualSlot = actionIndex.toVisualSlotNumber()
                                        contentDescription = "$emptySlotLabel $visualSlot"
                                    }
                                },
                        ) {
                            action?.let { placedAction ->
                                LinkPeekActionEditorItem(
                                    action = placedAction,
                                    label = currentActionLabel(placedAction),
                                    compact = true,
                                    dragged = placedAction == draggedAction,
                                    highlighted = currentTarget ==
                                        LinkPeekActionEditorTarget.ActionSlot(actionIndex),
                                    customActions = actionSlotAccessibilityActions(
                                        action = placedAction,
                                        actionLabel = currentActionLabel(placedAction),
                                        targetActionIndexes = emptyActionIndexes,
                                        onPlace = ::placeWithAccessibility,
                                        onRemove = ::removeWithAccessibility,
                                        moveToSlotLabel = moveToSlotLabel,
                                        moveToAvailableLabel = moveToAvailableLabel,
                                    ),
                                    sourceBounds = actionMeasurements[placedAction]?.bounds,
                                    onBounds = { bounds ->
                                        actionMeasurements[placedAction] =
                                            LinkPeekActionEditorMeasurement(
                                                bounds = bounds,
                                                placement = LinkPeekActionEditorPlacement.Toolbar(
                                                    actionIndex,
                                                ),
                                            )
                                    },
                                    onStartDrag = { bounds, point ->
                                        beginDrag(placedAction, bounds, point)
                                    },
                                    onDrag = ::updateDrag,
                                    onDragEnd = { finishDrag(cancelled = false) },
                                    onDragCancel = { finishDrag(cancelled = true) },
                                )
                            }
                        }
                    }
                }
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
                    .testTag(LinkPeekActionEditorTestTags.Palette)
                    .onGloballyPositioned { coordinates ->
                        paletteBounds = coordinates.boundsInRoot()
                    }
                    .semantics { contentDescription = availableTitle },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                available.chunked(PALETTE_COLUMNS).forEach { rowActions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowActions.forEach { action ->
                            Box(modifier = Modifier.weight(1f)) {
                                LinkPeekActionEditorItem(
                                    action = action,
                                    label = currentActionLabel(action),
                                    compact = false,
                                    dragged = action == draggedAction,
                                    highlighted = false,
                                    customActions = actionSlotAccessibilityActions(
                                        action = action,
                                        actionLabel = currentActionLabel(action),
                                        targetActionIndexes = emptyActionIndexes,
                                        onPlace = ::placeWithAccessibility,
                                        onRemove = null,
                                        moveToSlotLabel = moveToSlotLabel,
                                        moveToAvailableLabel = moveToAvailableLabel,
                                    ),
                                    sourceBounds = actionMeasurements[action]?.bounds,
                                    onBounds = { bounds ->
                                        actionMeasurements[action] =
                                            LinkPeekActionEditorMeasurement(
                                                bounds = bounds,
                                                placement = LinkPeekActionEditorPlacement.Palette,
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

        activeDrag?.takeIf { drag -> drag.breakawayReleased && !drag.settling }?.let {
            LinkPeekActionDropIndicators(
                slots = slots(),
                rootBounds = rootBounds,
                currentTarget = currentTarget,
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
                    .testTag(LinkPeekActionEditorTestTags.DragOverlay),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LinkPeekActionGlyph(
                        action = drag.action,
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
private fun FixedPlusSlot(
    label: String,
    onBounds: (Rect) -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "linkPeekEditorPlusPulse")
    val ringProgress by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 920, easing = FastOutSlowInEasing),
        ),
        label = "linkPeekEditorPlusRing",
    )
    val breathProgress by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 560, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "linkPeekEditorPlusBreath",
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .onGloballyPositioned { coordinates -> onBounds(coordinates.boundsInRoot()) }
            .semantics { contentDescription = label }
            .testTag(LinkPeekActionEditorTestTags.FixedPlus),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = androidx.compose.ui.util.lerp(1.02f, 1.28f, ringProgress)
                    scaleX = scale
                    scaleY = scale
                    alpha = (1f - ringProgress) * 0.3f
                }
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                )
                .testTag(LinkPeekActionEditorTestTags.FixedPlusPulseRing),
        )
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = androidx.compose.ui.util.lerp(1f, 1.02f, breathProgress)
                    scaleX = scale
                    scaleY = scale
                }
                .clearAndSetSemantics { },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 3.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Composable
private fun LinkPeekActionDropIndicators(
    slots: List<LinkPeekActionEditorSlot>,
    rootBounds: Rect?,
    currentTarget: LinkPeekActionEditorTarget?,
) {
    val density = LocalDensity.current
    val rootTopLeft = rootBounds?.topLeft ?: Offset.Zero
    val motion = rememberInfiniteTransition(label = "Link Peek action drop targets")
    val wiggle by motion.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "Drop target wiggle",
    )
    val morph by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 690, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "Drop target shape",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(LinkPeekActionEditorTestTags.dropIndicators(slots.map { it.target })),
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
                    x = (slot.bounds.left + slot.bounds.right) / 2f,
                    y = (slot.bounds.top + slot.bounds.bottom) / 2f,
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
                        .testTag(LinkPeekActionEditorTestTags.dropIndicator(slot.target)),
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
private fun LinkPeekActionEditorItem(
    action: LinkPeekAction,
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
            .onGloballyPositioned { coordinates -> onBounds(coordinates.boundsInRoot()) }
            .then(semanticsModifier)
            .testTag(LinkPeekActionEditorTestTags.action(action))
            .linkPeekActionEditorDragSource(
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
            modifier = Modifier.padding(
                horizontal = if (compact) 12.dp else 10.dp,
                vertical = 12.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LinkPeekActionGlyph(
                action = action,
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

@Composable
private fun LinkPeekActionGlyph(
    action: LinkPeekAction,
    contentDescription: String?,
    modifier: Modifier,
) {
    val icon = when (action) {
        LinkPeekAction.ReaderLater -> R.drawable.ic_reader_download
        LinkPeekAction.OpenPrivate -> R.drawable.ic_incognito_outline
        LinkPeekAction.Copy -> R.drawable.ic_content_copy
        LinkPeekAction.Share -> R.drawable.ic_symbol_share
        LinkPeekAction.Favorite -> R.drawable.ic_symbol_favorite
        LinkPeekAction.Snooze -> R.drawable.ic_snooze
        LinkPeekAction.OpenForeground -> R.drawable.ic_switch_to_tab
    }
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

private fun actionSlotAccessibilityActions(
    action: LinkPeekAction,
    actionLabel: String,
    targetActionIndexes: List<Int>,
    onPlace: (LinkPeekAction, Int) -> Boolean,
    onRemove: ((LinkPeekAction) -> Boolean)?,
    moveToSlotLabel: String,
    moveToAvailableLabel: String,
): List<CustomAccessibilityAction> = buildList {
    targetActionIndexes.forEach { actionIndex ->
        val visualSlot = actionIndex.toVisualSlotNumber()
        add(
            CustomAccessibilityAction("$moveToSlotLabel $visualSlot: $actionLabel") {
                onPlace(action, actionIndex)
            },
        )
    }
    onRemove?.let { remove ->
        add(CustomAccessibilityAction("$moveToAvailableLabel: $actionLabel") { remove(action) })
    }
}

private fun Int.toVisualSlotNumber(): Int =
    if (this < LinkPeekActionLayoutRules.FIXED_PLUS_SLOT_INDEX) this + 1 else this + 2

private fun Modifier.linkPeekActionEditorDragSource(
    action: LinkPeekAction,
    sourceBounds: Rect?,
    onStartDrag: (Rect, Offset) -> Boolean,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
): Modifier = pointerInput(action, sourceBounds) {
    var accepted = false
    detectDragGesturesAfterLongPress(
        onDragStart = { point ->
            accepted = sourceBounds?.let { bounds -> onStartDrag(bounds, point) } == true
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

private fun Rect.toEditorRect(): LinkPeekActionEditorRect = LinkPeekActionEditorRect(
    left = left,
    top = top,
    right = right,
    bottom = bottom,
)

private fun Rect.containsExpanded(point: Offset, paddingPx: Float): Boolean =
    point.x >= left - paddingPx &&
        point.x <= right + paddingPx &&
        point.y >= top - paddingPx &&
        point.y <= bottom + paddingPx

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
private const val PALETTE_MEASUREMENT_FRAMES = 6
private const val TOOLBAR_MEASUREMENT_FRAMES = 6
