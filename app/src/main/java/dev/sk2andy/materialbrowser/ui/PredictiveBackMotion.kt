package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
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
            scale = 1f - (1f - MIN_SCALE) * clampedProgress,
            translationX = width * MAX_TRANSLATION_FRACTION * clampedProgress * direction,
        )
    }

    fun entryTranslation(progress: Float, width: Float): Float =
        width * (1f - progress.coerceIn(0f, 1f))

    fun remainingDurationMillis(progress: Float): Int =
        ((1f - progress.coerceIn(0f, 1f)) * EXIT_DURATION_MILLIS).roundToInt()

    const val ENTRY_DURATION_MILLIS = 300
    const val EXIT_DURATION_MILLIS = 220
    const val MIN_SCALE = 0.9f
    const val MAX_TRANSLATION_FRACTION = 0.05f
}

internal fun Modifier.predictiveBackSurface(
    progress: Float,
    swipeEdgeSign: Int,
): Modifier = graphicsLayer {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val transform = PredictiveBackMotion.transform(
        progress = clampedProgress,
        width = size.width,
        swipeEdgeSign = swipeEdgeSign,
    )
    translationX = transform.translationX
    scaleX = transform.scale
    scaleY = transform.scale
    shape = RoundedCornerShape((28f * clampedProgress).dp)
    clip = clampedProgress > 0f
}
