package dev.sk2andy.materialbrowser.ui

internal enum class FullscreenVideoGestureKind {
    Brightness,
    Dismiss,
    Volume,
}

internal data class FullscreenVideoGestureTransform(
    val translationY: Float,
    val scale: Float,
    val cornerRadiusDp: Float,
)

internal object FullscreenVideoGestureRules {
    fun kind(
        pointerX: Float,
        viewportWidth: Float,
    ): FullscreenVideoGestureKind {
        if (viewportWidth <= 0f || !viewportWidth.isFinite()) {
            return FullscreenVideoGestureKind.Dismiss
        }
        val fraction = (pointerX / viewportWidth).coerceIn(0f, 1f)
        return when {
            fraction < SIDE_REGION_FRACTION -> FullscreenVideoGestureKind.Brightness
            fraction > 1f - SIDE_REGION_FRACTION -> FullscreenVideoGestureKind.Volume
            else -> FullscreenVideoGestureKind.Dismiss
        }
    }

    fun adjustedLevel(
        startLevel: Float,
        dragY: Float,
        viewportHeight: Float,
    ): Float {
        if (viewportHeight <= 0f || !viewportHeight.isFinite()) {
            return startLevel.coerceIn(0f, 1f)
        }
        return (startLevel - dragY / viewportHeight).coerceIn(0f, 1f)
    }

    fun dismissOffset(dragY: Float): Float = dragY.coerceAtLeast(0f)

    fun shouldDismiss(
        offsetY: Float,
        viewportHeight: Float,
    ): Boolean = viewportHeight > 0f &&
        viewportHeight.isFinite() &&
        offsetY >= viewportHeight * DISMISS_THRESHOLD_FRACTION

    fun transform(
        offsetY: Float,
        viewportHeight: Float,
    ): FullscreenVideoGestureTransform {
        if (viewportHeight <= 0f || !viewportHeight.isFinite()) {
            return FullscreenVideoGestureTransform(
                translationY = 0f,
                scale = 1f,
                cornerRadiusDp = 0f,
            )
        }
        val safeOffset = dismissOffset(offsetY)
        val linearProgress = (safeOffset / (viewportHeight * TRANSFORM_DISTANCE_FRACTION))
            .coerceIn(0f, 1f)
        val expressiveProgress = 1f - (1f - linearProgress) * (1f - linearProgress)
        return FullscreenVideoGestureTransform(
            translationY = safeOffset,
            scale = 1f - MAX_SCALE_REDUCTION * expressiveProgress,
            cornerRadiusDp = MAX_CORNER_RADIUS_DP * expressiveProgress,
        )
    }

    private const val SIDE_REGION_FRACTION = 0.34f
    private const val DISMISS_THRESHOLD_FRACTION = 0.22f
    private const val TRANSFORM_DISTANCE_FRACTION = 0.45f
    private const val MAX_SCALE_REDUCTION = 0.12f
    private const val MAX_CORNER_RADIUS_DP = 32f
}
