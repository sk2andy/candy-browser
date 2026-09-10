package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import dev.sk2andy.materialbrowser.data.AddressBarDockPlacement
import dev.sk2andy.materialbrowser.ui.theme.LocalCandyMotionScheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class AddressBarDockInteractionState(
    val position: Offset,
    val normalAnchorResistanceProgress: Float,
    val onDragStarted: () -> Unit,
    val onDrag: (Offset) -> Unit,
    val onDragStopped: () -> Unit,
    val onDragCancelled: () -> Unit,
    val onRestoreClick: () -> Unit,
)

internal data class AddressBarDockHaptics(
    val startMovement: () -> Unit,
    val stopMovement: () -> Unit,
    val confirm: () -> Unit,
)

@Composable
internal fun rememberAddressBarDockInteractionState(
    presentation: AddressBarPresentation,
    placement: AddressBarDockPlacement?,
    enabled: Boolean,
    horizontalTravelPx: Float,
    verticalTravelPx: Float,
    density: Density,
    onPlacementChanged: (AddressBarDockPlacement) -> Unit,
    onRestore: () -> Unit,
    haptics: AddressBarDockHaptics? = null,
): AddressBarDockInteractionState {
    val motionScheme = LocalCandyMotionScheme.current
    val hapticView = LocalView.current
    val hapticScope = rememberCoroutineScope()
    val platformHaptics = remember(hapticView) {
        AddressBarDockHaptics(
            startMovement = hapticView::startRubberbandHaptic,
            stopMovement = hapticView::stopRubberbandHaptic,
            confirm = hapticView::performConfirmHaptic,
        )
    }
    val currentHaptics by rememberUpdatedState(haptics ?: platformHaptics)
    val currentPlacement by rememberUpdatedState(placement)
    val currentHorizontalTravelPx by rememberUpdatedState(horizontalTravelPx)
    val currentVerticalTravelPx by rememberUpdatedState(verticalTravelPx)
    val currentDensity by rememberUpdatedState(density)
    val currentOnPlacementChanged by rememberUpdatedState(onPlacementChanged)
    val currentOnRestore by rememberUpdatedState(onRestore)
    var position by remember {
        mutableStateOf(AddressBarDockingRules.positionForPlacement(placement))
    }
    var dragActive by remember { mutableStateOf(false) }
    var dragStartPosition by remember { mutableStateOf(Offset.Zero) }
    var dragDistancePx by remember { mutableStateOf(Offset.Zero) }
    var startedAtNormalAnchor by remember { mutableStateOf(false) }
    var normalAnchorReleased by remember { mutableStateOf(false) }
    var normalSnapZoneActive by remember { mutableStateOf(false) }
    var normalAnchorResistanceProgress by remember { mutableStateOf(0f) }
    var movementHapticActive by remember { mutableStateOf(false) }
    var movementHapticStopJob by remember { mutableStateOf<Job?>(null) }
    var breakawaySpringOffset by remember { mutableStateOf(Offset.Zero) }
    var breakawaySpringJob by remember { mutableStateOf<Job?>(null) }

    fun positionInPixels(value: Offset): Offset = Offset(
        x = value.x * currentHorizontalTravelPx,
        y = -value.y * currentVerticalTravelPx,
    )

    fun stopMovementHaptic() {
        movementHapticStopJob?.cancel()
        movementHapticStopJob = null
        if (movementHapticActive) currentHaptics.stopMovement()
        movementHapticActive = false
    }

    fun emitMovementHaptic(previousPosition: Offset, currentPosition: Offset) {
        if (
            !AddressBarDockingRules.offsetMoved(
                previousOffsetPx = positionInPixels(previousPosition),
                currentOffsetPx = positionInPixels(currentPosition),
            )
        ) {
            return
        }
        if (!movementHapticActive) {
            currentHaptics.startMovement()
            movementHapticActive = true
        }
        movementHapticStopJob?.cancel()
        movementHapticStopJob = hapticScope.launch {
            delay(DOCK_MOVEMENT_HAPTIC_IDLE_MILLIS)
            if (movementHapticActive) currentHaptics.stopMovement()
            movementHapticActive = false
            movementHapticStopJob = null
        }
    }

    DisposableEffect(hapticView) {
        onDispose {
            stopMovementHaptic()
            breakawaySpringJob?.cancel()
        }
    }

    val targetPosition = if (
        presentation == AddressBarPresentation.Docked && enabled && placement != null
    ) {
        AddressBarDockingRules.positionForPlacement(placement)
    } else {
        Offset.Zero
    }
    LaunchedEffect(targetPosition, dragActive) {
        if (dragActive) return@LaunchedEffect
        val initialPosition = position
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = AddressBarMotion.dockProgressAnimationSpec(motionScheme),
        ) { progress, _ ->
            position = initialPosition + (targetPosition - initialPosition) * progress
        }
    }

    fun startDrag() {
        stopMovementHaptic()
        breakawaySpringJob?.cancel()
        breakawaySpringJob = null
        breakawaySpringOffset = Offset.Zero
        dragStartPosition = position
        dragDistancePx = Offset.Zero
        startedAtNormalAnchor = currentPlacement?.verticalFraction == 0f
        normalAnchorReleased = !startedAtNormalAnchor
        normalSnapZoneActive = startedAtNormalAnchor
        normalAnchorResistanceProgress = 0f
        dragActive = true
    }

    fun dragBy(delta: Offset) {
        val previousPosition = position
        var emittedConfirmHaptic = false
        var brokeAwayFromNormalAnchor = false
        dragDistancePx += Offset(
            x = delta.x.takeIf(Float::isFinite) ?: 0f,
            y = delta.y.takeIf(Float::isFinite) ?: 0f,
        )
        normalAnchorResistanceProgress = if (
            startedAtNormalAnchor && !normalAnchorReleased
        ) {
            AddressBarDockingRules.normalAnchorResistanceProgress(
                dragDistanceYpx = dragDistancePx.y,
                density = currentDensity.density,
            )
        } else {
            0f
        }
        if (
            startedAtNormalAnchor &&
            !normalAnchorReleased &&
            AddressBarDockingRules.normalAnchorBreakawayReached(
                dragDistanceYpx = dragDistancePx.y,
                density = currentDensity.density,
            )
        ) {
            stopMovementHaptic()
            currentHaptics.confirm()
            normalAnchorReleased = true
            normalAnchorResistanceProgress = 0f
            emittedConfirmHaptic = true
            brokeAwayFromNormalAnchor = true
        }
        val unsnappedPosition = AddressBarDockingRules.positionAfterDrag(
            startPosition = dragStartPosition,
            dragDistancePx = dragDistancePx,
            horizontalTravelPx = currentHorizontalTravelPx,
            verticalTravelPx = currentVerticalTravelPx,
            resistNormalAnchor = startedAtNormalAnchor && !normalAnchorReleased,
            density = currentDensity.density,
        )
        val normalAnchorSnap = AddressBarDockingRules.normalAnchorSnap(
            position = unsnappedPosition,
            verticalTravelPx = currentVerticalTravelPx,
            density = currentDensity.density,
            enabled = normalAnchorReleased,
        )
        val nextPosition = normalAnchorSnap.position
        position = nextPosition
        if (brokeAwayFromNormalAnchor) {
            breakawaySpringJob?.cancel()
            val initialSpringOffset = previousPosition - nextPosition
            breakawaySpringOffset = initialSpringOffset
            breakawaySpringJob = hapticScope.launch {
                animate(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = AddressBarMotion.dockBreakawayAnimationSpec(motionScheme),
                ) { progress, _ ->
                    breakawaySpringOffset = initialSpringOffset * (1f - progress)
                }
                breakawaySpringOffset = Offset.Zero
                breakawaySpringJob = null
            }
        }
        val inSnapZone = normalAnchorSnap.snappedToNormalAnchor
        if (normalAnchorReleased && inSnapZone && !normalSnapZoneActive) {
            stopMovementHaptic()
            currentHaptics.confirm()
            emittedConfirmHaptic = true
        }
        normalSnapZoneActive = inSnapZone
        if (!emittedConfirmHaptic) emitMovementHaptic(previousPosition, position)
    }

    fun stopDrag() {
        stopMovementHaptic()
        val snapToNormalAnchor = if (startedAtNormalAnchor) {
            !normalAnchorReleased || normalSnapZoneActive
        } else {
            normalSnapZoneActive
        }
        val settledPlacement = AddressBarDockingRules.placementAfterDrop(
            position = position,
            snapToNormalAnchor = snapToNormalAnchor,
        )
        currentHaptics.confirm()
        currentOnPlacementChanged(settledPlacement)
        normalAnchorResistanceProgress = 0f
        dragActive = false
    }

    fun cancelDrag() {
        stopMovementHaptic()
        breakawaySpringJob?.cancel()
        breakawaySpringJob = null
        breakawaySpringOffset = Offset.Zero
        normalAnchorResistanceProgress = 0f
        dragActive = false
    }

    fun restoreFromClick() {
        stopMovementHaptic()
        breakawaySpringJob?.cancel()
        breakawaySpringJob = null
        breakawaySpringOffset = Offset.Zero
        normalAnchorResistanceProgress = 0f
        currentHaptics.confirm()
        currentOnRestore()
    }

    return AddressBarDockInteractionState(
        position = position + breakawaySpringOffset,
        normalAnchorResistanceProgress = normalAnchorResistanceProgress,
        onDragStarted = ::startDrag,
        onDrag = ::dragBy,
        onDragStopped = ::stopDrag,
        onDragCancelled = ::cancelDrag,
        onRestoreClick = ::restoreFromClick,
    )
}

private const val DOCK_MOVEMENT_HAPTIC_IDLE_MILLIS = 48L
