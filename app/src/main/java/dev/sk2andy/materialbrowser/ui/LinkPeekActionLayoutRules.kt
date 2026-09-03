package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.geometry.Rect
import kotlin.math.floor
import kotlin.math.min

internal object LinkPeekActionLayoutRules {
    fun horizontalOffsets(
        containerBounds: Rect,
        targetBounds: Rect,
        actionCount: Int,
        preferredSpacingPx: Float,
    ): List<Float> {
        if (
            actionCount <= 0 ||
            targetBounds.width <= 0f ||
            preferredSpacingPx < 0f ||
            targetBounds.left < containerBounds.left ||
            targetBounds.right > containerBounds.right ||
            targetBounds.top < containerBounds.top ||
            targetBounds.bottom > containerBounds.bottom
        ) {
            return emptyList()
        }
        val actionWidth = targetBounds.width
        val leftSpace = targetBounds.left - containerBounds.left
        val rightSpace = containerBounds.right - targetBounds.right
        val actionsWidth = actionCount * actionWidth
        if (leftSpace >= actionsWidth || rightSpace >= actionsWidth) {
            return if (leftSpace >= rightSpace && leftSpace >= actionsWidth) {
                offsetsBefore(
                    edge = targetBounds.left,
                    availableSpace = leftSpace,
                    actionWidth = actionWidth,
                    actionCount = actionCount,
                    preferredSpacingPx = preferredSpacingPx,
                )
            } else {
                offsetsAfter(
                    edge = targetBounds.right,
                    availableSpace = rightSpace,
                    actionWidth = actionWidth,
                    actionCount = actionCount,
                    preferredSpacingPx = preferredSpacingPx,
                )
            }
        }

        val leftCapacity = floor(leftSpace / actionWidth).toInt().coerceIn(0, actionCount)
        val leftCount = (0..leftCapacity).lastOrNull { candidate ->
            rightSpace >= (actionCount - candidate) * actionWidth
        } ?: return emptyList()
        val rightCount = actionCount - leftCount
        return offsetsBefore(
            edge = targetBounds.left,
            availableSpace = leftSpace,
            actionWidth = actionWidth,
            actionCount = leftCount,
            preferredSpacingPx = preferredSpacingPx,
        ) + offsetsAfter(
            edge = targetBounds.right,
            availableSpace = rightSpace,
            actionWidth = actionWidth,
            actionCount = rightCount,
            preferredSpacingPx = preferredSpacingPx,
        )
    }

    private fun offsetsBefore(
        edge: Float,
        availableSpace: Float,
        actionWidth: Float,
        actionCount: Int,
        preferredSpacingPx: Float,
    ): List<Float> {
        if (actionCount == 0) return emptyList()
        val spacing = min(
            preferredSpacingPx,
            (availableSpace - actionCount * actionWidth) / actionCount,
        ).coerceAtLeast(0f)
        val start = edge - actionCount * (actionWidth + spacing)
        return List(actionCount) { index -> start + index * (actionWidth + spacing) }
    }

    private fun offsetsAfter(
        edge: Float,
        availableSpace: Float,
        actionWidth: Float,
        actionCount: Int,
        preferredSpacingPx: Float,
    ): List<Float> {
        if (actionCount == 0) return emptyList()
        val spacing = min(
            preferredSpacingPx,
            (availableSpace - actionCount * actionWidth) / actionCount,
        ).coerceAtLeast(0f)
        return List(actionCount) { index -> edge + spacing + index * (actionWidth + spacing) }
    }
}
