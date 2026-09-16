package dev.sk2andy.materialbrowser.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull

private enum class ParkedPillGestureResolution {
    Released,
    Rejected,
    SwipeUp,
    LongPress,
}
@Composable
internal fun Modifier.addressBarParkedPillGesture(
    dockDragEnabled: Boolean,
    overviewGestureEnabled: Boolean,
    initialProgress: FloatState?,
    dragCoordinates: () -> LayoutCoordinates?,
    onDockDragStarted: () -> Unit,
    onDockDrag: (Offset) -> Unit,
    onDockDragStopped: () -> Unit,
    onDockDragCancelled: () -> Unit,
    onOverviewGestureProgress: (Float) -> Unit,
    onOverviewGestureStarted: () -> Unit,
    onOverviewGestureCancelled: () -> Unit,
    onSwipeUp: () -> Unit,
): Modifier {
    val currentInitialProgress by rememberUpdatedState(initialProgress)
    val currentDragCoordinates by rememberUpdatedState(dragCoordinates)
    val currentOnDockDragStarted by rememberUpdatedState(onDockDragStarted)
    val currentOnDockDrag by rememberUpdatedState(onDockDrag)
    val currentOnDockDragStopped by rememberUpdatedState(onDockDragStopped)
    val currentOnDockDragCancelled by rememberUpdatedState(onDockDragCancelled)
    val currentOnOverviewGestureProgress by rememberUpdatedState(onOverviewGestureProgress)
    val currentOnOverviewGestureStarted by rememberUpdatedState(onOverviewGestureStarted)
    val currentOnOverviewGestureCancelled by rememberUpdatedState(onOverviewGestureCancelled)
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val gestureView = LocalView.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    if (!dockDragEnabled && !overviewGestureEnabled) return this
    return pointerInput(dockDragEnabled, overviewGestureEnabled, touchSlop) {
        val threshold = AddressBarGestureRules.PARKED_OPEN_TABS_THRESHOLD_DP.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            var accumulatedX = 0f
            var accumulatedY = 0f
            var lastPointerPosition = down.position
            var swipeChange: PointerInputChange? = null
            val resolution = withTimeoutOrNull(
                AddressBarGestureRules.PARKED_REPOSITION_LONG_PRESS_MILLIS,
            ) {
                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: return@withTimeoutOrNull ParkedPillGestureResolution.Rejected
                    if (change.isConsumed) {
                        return@withTimeoutOrNull ParkedPillGestureResolution.Rejected
                    }
                    val delta = change.position - change.previousPosition
                    accumulatedX += delta.x
                    accumulatedY += delta.y
                    lastPointerPosition = change.position
                    if (!change.pressed) {
                        return@withTimeoutOrNull ParkedPillGestureResolution.Released
                    }
                    when (
                        AddressBarOverviewGestureRules.direction(
                            dragX = accumulatedX,
                            dragY = accumulatedY,
                            touchSlop = touchSlop,
                        )
                    ) {
                        AddressBarOverviewGestureDirection.Pending -> Unit
                        AddressBarOverviewGestureDirection.Rejected -> {
                            return@withTimeoutOrNull ParkedPillGestureResolution.Rejected
                        }
                        AddressBarOverviewGestureDirection.Upward -> {
                            if (!overviewGestureEnabled) {
                                return@withTimeoutOrNull ParkedPillGestureResolution.Rejected
                            }
                            swipeChange = change
                            return@withTimeoutOrNull ParkedPillGestureResolution.SwipeUp
                        }
                    }
                }
            } ?: if (dockDragEnabled) {
                ParkedPillGestureResolution.LongPress
            } else {
                ParkedPillGestureResolution.Rejected
            }

            when (resolution) {
                ParkedPillGestureResolution.Released,
                ParkedPillGestureResolution.Rejected,
                -> Unit
                ParkedPillGestureResolution.SwipeUp -> {
                    var state = AddressBarOverviewGestureRules.stateForProgress(
                        progress = currentInitialProgress?.floatValue ?: 0f,
                        threshold = threshold,
                    )
                    var lastY = down.position.y
                    var committed = false
                    currentOnOverviewGestureStarted()
                    try {
                        var change = swipeChange
                        while (change != null) {
                            val update = AddressBarOverviewGestureRules.update(
                                state = state,
                                deltaY = change.position.y - lastY,
                                threshold = threshold,
                            )
                            state = update.state
                            lastY = change.position.y
                            change.consume()
                            currentOnOverviewGestureProgress(update.progress)
                            if (!change.pressed) {
                                val release = AddressBarOverviewGestureRules.release(state)
                                if (release.shouldCommit) {
                                    committed = true
                                    currentOnOverviewGestureProgress(release.progress)
                                    gestureView.performHapticFeedback(
                                        HapticFeedbackConstants.VIRTUAL_KEY,
                                    )
                                    currentOnSwipeUp()
                                }
                                break
                            }
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            change = event.changes.firstOrNull { it.id == down.id }
                        }
                    } finally {
                        if (!committed) currentOnOverviewGestureCancelled()
                    }
                }
                ParkedPillGestureResolution.LongPress -> {
                    var previousPointerInRoot = currentDragCoordinates()
                        ?.localToRoot(lastPointerPosition)
                    var moved = false
                    var finished = false
                    currentOnDockDragStarted()
                    try {
                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val pointerInRoot = currentDragCoordinates()
                                ?.localToRoot(change.position)
                            val rootDragAmount = previousPointerInRoot
                                ?.let { previous -> pointerInRoot?.minus(previous) }
                                ?: (change.position - lastPointerPosition)
                            previousPointerInRoot = pointerInRoot
                            lastPointerPosition = change.position
                            change.consume()
                            if (!change.pressed) {
                                finished = true
                                if (moved) {
                                    currentOnDockDragStopped()
                                } else {
                                    currentOnDockDragCancelled()
                                }
                                break
                            }
                            if (
                                AddressBarDockingRules.offsetMoved(
                                    previousOffsetPx = Offset.Zero,
                                    currentOffsetPx = rootDragAmount,
                                )
                            ) {
                                moved = true
                                currentOnDockDrag(rootDragAmount)
                            }
                        }
                    } finally {
                        if (!finished) currentOnDockDragCancelled()
                    }
                }
            }
        }
    }
}
