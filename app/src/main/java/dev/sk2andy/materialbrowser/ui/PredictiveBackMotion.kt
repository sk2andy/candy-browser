package dev.sk2andy.materialbrowser.ui

import kotlin.math.roundToInt

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
            scale = 1f,
            translationX = width * clampedProgress * direction,
        )
    }

    fun entryTranslation(progress: Float, width: Float): Float =
        width * (1f - progress.coerceIn(0f, 1f))

    fun remainingDurationMillis(progress: Float): Int =
        ((1f - progress.coerceIn(0f, 1f)) * EXIT_DURATION_MILLIS).roundToInt()

    const val ENTRY_DURATION_MILLIS = 300
    const val EXIT_DURATION_MILLIS = 220
}
