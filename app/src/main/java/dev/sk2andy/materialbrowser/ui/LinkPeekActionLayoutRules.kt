package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.geometry.Rect
import kotlin.math.min

internal object LinkPeekActionLayoutRules {
    fun actionBarSlots(
        containerBounds: Rect,
        newTabTargetBounds: Rect,
        slotCount: Int,
        fixedNewTabSlotIndex: Int,
        preferredSpacingPx: Float,
    ): List<Rect> {
        if (
            slotCount <= 0 ||
            fixedNewTabSlotIndex !in 0 until slotCount ||
            newTabTargetBounds.width <= 0f ||
            newTabTargetBounds.height <= 0f ||
            preferredSpacingPx < 0f ||
            newTabTargetBounds.top < containerBounds.top ||
            newTabTargetBounds.bottom > containerBounds.bottom
        ) {
            return emptyList()
        }
        val slotWidth = newTabTargetBounds.width
        val minimumSingleRowWidth = slotCount * slotWidth
        if (minimumSingleRowWidth > containerBounds.width) {
            return stackedActionBarSlots(
                containerBounds = containerBounds,
                newTabTargetBounds = newTabTargetBounds,
                slotCount = slotCount,
                fixedNewTabSlotIndex = fixedNewTabSlotIndex,
                preferredSpacingPx = preferredSpacingPx,
            )
        }
        val spacing = if (slotCount == 1) {
            0f
        } else {
            min(
                preferredSpacingPx,
                (containerBounds.width - minimumSingleRowWidth) / (slotCount - 1),
            )
        }
        val totalWidth = minimumSingleRowWidth + (slotCount - 1) * spacing

        val minimumStart = containerBounds.left
        val maximumStart = containerBounds.right - totalWidth
        val preferredStart = newTabTargetBounds.left -
            fixedNewTabSlotIndex * (slotWidth + spacing)
        val start = preferredStart.coerceIn(minimumStart, maximumStart)
        return List(slotCount) { index ->
            val left = start + index * (slotWidth + spacing)
            Rect(
                left = left,
                top = newTabTargetBounds.top,
                right = left + slotWidth,
                bottom = newTabTargetBounds.bottom,
            )
        }
    }

    private fun stackedActionBarSlots(
        containerBounds: Rect,
        newTabTargetBounds: Rect,
        slotCount: Int,
        fixedNewTabSlotIndex: Int,
        preferredSpacingPx: Float,
    ): List<Rect> {
        if (slotCount != 4 || fixedNewTabSlotIndex != 2) return emptyList()
        val slotWidth = newTabTargetBounds.width
        val slotHeight = newTabTargetBounds.height
        if (containerBounds.width < slotWidth * 2f || containerBounds.height < slotHeight * 2f) {
            return emptyList()
        }
        val horizontalSpacing = min(
            preferredSpacingPx,
            containerBounds.width - slotWidth * 2f,
        )
        val verticalSpacing = min(
            preferredSpacingPx,
            containerBounds.height - slotHeight * 2f,
        )
        val totalWidth = slotWidth * 2f + horizontalSpacing
        val start = newTabTargetBounds.left.coerceIn(
            containerBounds.left,
            containerBounds.right - totalWidth,
        )
        val lowerTop = newTabTargetBounds.top.coerceIn(
            containerBounds.top + slotHeight + verticalSpacing,
            containerBounds.bottom - slotHeight,
        )
        val upperTop = lowerTop - slotHeight - verticalSpacing
        return List(slotCount) { index ->
            val row = index / 2
            val column = index % 2
            val left = start + column * (slotWidth + horizontalSpacing)
            val top = if (row == 0) upperTop else lowerTop
            Rect(left, top, left + slotWidth, top + slotHeight)
        }
    }

}
