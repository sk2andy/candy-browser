package dev.sk2andy.materialbrowser.ui

import kotlin.math.absoluteValue

internal enum class LinkPeekSwipeDirection {
    Pending,
    Horizontal,
    Rejected,
}

internal enum class LinkPeekSwipeOutcome {
    Cancel,
    Close,
    RightAction,
}

internal object LinkPeekSwipeRules {
    const val COMMIT_DISTANCE_DP = 96f

    fun direction(dragX: Float, dragY: Float, touchSlop: Float): LinkPeekSwipeDirection = when {
        !dragX.isFinite() || !dragY.isFinite() -> LinkPeekSwipeDirection.Rejected
        maxOf(dragX.absoluteValue, dragY.absoluteValue) < touchSlop ->
            LinkPeekSwipeDirection.Pending
        dragX.absoluteValue > dragY.absoluteValue -> LinkPeekSwipeDirection.Horizontal
        else -> LinkPeekSwipeDirection.Rejected
    }

    fun release(dragX: Float, threshold: Float): LinkPeekSwipeOutcome = when {
        !dragX.isFinite() || !threshold.isFinite() || threshold <= 0f ->
            LinkPeekSwipeOutcome.Cancel
        dragX <= -threshold -> LinkPeekSwipeOutcome.Close
        dragX >= threshold -> LinkPeekSwipeOutcome.RightAction
        else -> LinkPeekSwipeOutcome.Cancel
    }

    fun progress(dragX: Float, threshold: Float): Float =
        if (dragX.isFinite() && threshold.isFinite() && threshold > 0f) {
            (dragX.absoluteValue / threshold).coerceIn(0f, 1f)
        } else {
            0f
        }
}
