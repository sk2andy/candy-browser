package dev.sk2andy.materialbrowser.ui

internal data class PredictiveBackTransform(
    val scale: Float,
    val translationX: Float,
)

internal object PredictiveBackMotion {
    fun transform(
        progress: Float,
        width: Float,
        swipeEdgeSign: Int,
    ): PredictiveBackTransform {
        val clampedProgress = progress.coerceIn(0f, 1f)
        val direction = swipeEdgeSign.coerceIn(-1, 1)

        return PredictiveBackTransform(
            scale = START_SCALE + (END_SCALE - START_SCALE) * clampedProgress,
            translationX = width * MAX_TRANSLATION_FRACTION * clampedProgress * direction,
        )
    }

    private const val START_SCALE = 1f
    private const val END_SCALE = 0.96f
    private const val MAX_TRANSLATION_FRACTION = 0.04f
}
