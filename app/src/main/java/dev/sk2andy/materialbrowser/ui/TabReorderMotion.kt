package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.geometry.Offset

internal object TabReorderMotion {
    fun edgeScrollSpeed(
        pointerPx: Float,
        viewportStartPx: Float,
        viewportEndPx: Float,
        edgeSizePx: Float,
        maxSpeedPxPerSecond: Float,
    ): Float {
        if (
            viewportEndPx <= viewportStartPx ||
            edgeSizePx <= 0f ||
            maxSpeedPxPerSecond <= 0f
        ) {
            return 0f
        }
        val effectiveEdge = edgeSizePx.coerceAtMost(
            (viewportEndPx - viewportStartPx) / 2f,
        )
        val startEdgeEnd = viewportStartPx + effectiveEdge
        val endEdgeStart = viewportEndPx - effectiveEdge
        return when {
            pointerPx < startEdgeEnd -> {
                val strength = ((startEdgeEnd - pointerPx) / effectiveEdge).coerceIn(0f, 1f)
                val easedStrength = strength * strength * (3f - 2f * strength)
                -maxSpeedPxPerSecond * easedStrength
            }
            pointerPx > endEdgeStart -> {
                val strength = ((pointerPx - endEdgeStart) / effectiveEdge).coerceIn(0f, 1f)
                val easedStrength = strength * strength * (3f - 2f * strength)
                maxSpeedPxPerSecond * easedStrength
            }
            else -> 0f
        }
    }

    fun shiftedIndex(
        index: Int,
        sourceIndex: Int,
        destinationIndex: Int,
    ): Int = when {
        index == sourceIndex -> destinationIndex
        destinationIndex > sourceIndex && index in (sourceIndex + 1)..destinationIndex -> index - 1
        destinationIndex < sourceIndex && index in destinationIndex until sourceIndex -> index + 1
        else -> index
    }

    fun horizontalDestinationIndex(
        sourceIndex: Int,
        dragOffsetPx: Float,
        slotWidthPx: Float,
        allowedRange: IntRange,
    ): Int {
        if (allowedRange.isEmpty() || slotWidthPx <= 0f) return sourceIndex
        val slotDelta = kotlin.math.round(dragOffsetPx / slotWidthPx).toInt()
        return (sourceIndex + slotDelta).coerceIn(allowedRange.first, allowedRange.last)
    }

    fun horizontalDestinationIndexWithViewportOffset(
        sourceIndex: Int,
        dragOffsetPx: Float,
        viewportOffsetPx: Float,
        slotWidthPx: Float,
        allowedRange: IntRange,
    ): Int {
        if (allowedRange.isEmpty() || slotWidthPx <= 0f) return sourceIndex
        val dragSlotDelta = kotlin.math.round(dragOffsetPx / slotWidthPx).toInt()
        val viewportSlotDelta = kotlin.math.round(viewportOffsetPx / slotWidthPx).toInt()
        return (sourceIndex + dragSlotDelta + viewportSlotDelta)
            .coerceIn(allowedRange.first, allowedRange.last)
    }

    fun heroPagerAnchorIndex(sourceIndex: Int, destinationIndex: Int): Int = when {
        destinationIndex > sourceIndex -> destinationIndex - 1
        destinationIndex < sourceIndex -> destinationIndex + 1
        else -> sourceIndex
    }

    fun heroDestinationIndexForDrag(
        sourceIndex: Int,
        currentDestinationIndex: Int,
        edgeStepping: Boolean,
        dragOffsetPx: Float,
        viewportOffsetPx: Float,
        slotWidthPx: Float,
        allowedRange: IntRange,
    ): Int {
        if (allowedRange.isEmpty()) return sourceIndex
        if (edgeStepping) {
            return currentDestinationIndex.coerceIn(allowedRange.first, allowedRange.last)
        }

        val adjacentStart = maxOf(allowedRange.first, sourceIndex - 1)
        val adjacentEnd = minOf(allowedRange.last, sourceIndex + 1)
        return horizontalDestinationIndexWithViewportOffset(
            sourceIndex = sourceIndex,
            dragOffsetPx = dragOffsetPx,
            viewportOffsetPx = viewportOffsetPx,
            slotWidthPx = slotWidthPx,
            allowedRange = allowedRange,
        ).coerceIn(adjacentStart, adjacentEnd)
    }

    fun gridDestinationIndex(
        sourceIndex: Int,
        dragOffsetPx: Offset,
        columnPitchPx: Float,
        rowPitchPx: Float,
        columnCount: Int,
        allowedRange: IntRange,
    ): Int {
        if (
            allowedRange.isEmpty() ||
            columnPitchPx <= 0f ||
            rowPitchPx <= 0f ||
            columnCount <= 0
        ) {
            return sourceIndex
        }
        val sourceRow = sourceIndex / columnCount
        val sourceColumn = sourceIndex % columnCount
        val targetRow = sourceRow + kotlin.math.round(dragOffsetPx.y / rowPitchPx).toInt()
        val targetColumn = (sourceColumn +
            kotlin.math.round(dragOffsetPx.x / columnPitchPx).toInt())
            .coerceIn(0, columnCount - 1)
        return (targetRow * columnCount + targetColumn)
            .coerceIn(allowedRange.first, allowedRange.last)
    }

    fun indexDeltas(
        oldOrder: List<String>,
        newOrder: List<String>,
    ): Map<String, Int> {
        val oldIndices = oldOrder.withIndex().associate { (index, id) -> id to index }
        return newOrder.withIndex().associate { (newIndex, id) ->
            id to ((oldIndices[id] ?: newIndex) - newIndex)
        }
    }

    fun translationX(
        indexDelta: Int,
        pageSlotWidthPx: Float,
        progress: Float,
    ): Float = indexDelta * pageSlotWidthPx * (1f - progress.coerceIn(0f, 1f))
}
