package dev.sk2andy.materialbrowser.ui

internal object TabReorderMotion {
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
