package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp

@Composable
internal fun Modifier.linkPeekSwipeGesture(
    enabled: Boolean,
    onDrag: (Float) -> Unit,
    onRelease: (LinkPeekSwipeOutcome) -> Unit,
): Modifier {
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val touchSlop = LocalViewConfiguration.current.touchSlop
    if (!enabled) return this
    return pointerInput(touchSlop) {
        val threshold = LinkPeekSwipeRules.COMMIT_DISTANCE_DP.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var direction = LinkPeekSwipeDirection.Pending
            var dragX = 0f
            var dragY = 0f
            try {
                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (event.changes.size != 1 || change.isConsumed) break
                    dragX += change.position.x - change.previousPosition.x
                    dragY += change.position.y - change.previousPosition.y
                    if (direction == LinkPeekSwipeDirection.Pending) {
                        direction = LinkPeekSwipeRules.direction(dragX, dragY, touchSlop)
                    }
                    if (direction == LinkPeekSwipeDirection.Rejected) break
                    if (direction == LinkPeekSwipeDirection.Horizontal) {
                        change.consume()
                        currentOnDrag(dragX)
                    }
                    if (!change.pressed) {
                        if (direction == LinkPeekSwipeDirection.Horizontal) {
                            currentOnRelease(LinkPeekSwipeRules.release(dragX, threshold))
                        }
                        break
                    }
                }
            } finally {
                currentOnDrag(0f)
            }
        }
    }
}
