package dev.sk2andy.materialbrowser.ui

import kotlin.math.floor

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

    fun levelHapticStep(level: Float): Int {
        val bounded = level.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        return floor(
            bounded * LEVEL_HAPTIC_BASE_STEPS +
                bounded * bounded * LEVEL_HAPTIC_ADDITIONAL_STEPS,
        ).toInt()
    }

    fun levelHapticStrength(level: Float): Float {
        val bounded = level.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        return MIN_LEVEL_HAPTIC_STRENGTH +
            (1f - MIN_LEVEL_HAPTIC_STRENGTH) * bounded
    }

    fun dismissDragDistance(dragY: Float): Float =
        dragY.takeIf(Float::isFinite)?.coerceAtLeast(0f) ?: 0f

    fun dismissOffset(
        dragY: Float,
        viewportHeight: Float,
    ): Float {
        val distance = dismissDragDistance(dragY)
        val threshold = dismissThreshold(viewportHeight)
        if (threshold <= 0f) return 0f
        val stickyOffset = threshold * DISMISS_STICKY_OFFSET_FRACTION
        if (distance >= threshold) return stickyOffset + distance - threshold
        val progress = (distance / threshold).coerceIn(0f, 1f)
        val rubberbandProgress = 1f - (1f - progress) * (1f - progress)
        return stickyOffset * rubberbandProgress
    }

    fun shouldDismiss(
        dragY: Float,
        viewportHeight: Float,
    ): Boolean {
        val threshold = dismissThreshold(viewportHeight)
        return threshold > 0f && dismissDragDistance(dragY) >= threshold
    }

    fun enteredDismissThreshold(
        previousDragY: Float,
        currentDragY: Float,
        viewportHeight: Float,
    ): Boolean = !shouldDismiss(previousDragY, viewportHeight) &&
        shouldDismiss(currentDragY, viewportHeight)

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
        val safeOffset = dismissDragDistance(offsetY)
        val linearProgress = (safeOffset / (viewportHeight * TRANSFORM_DISTANCE_FRACTION))
            .coerceIn(0f, 1f)
        val expressiveProgress = 1f - (1f - linearProgress) * (1f - linearProgress)
        return FullscreenVideoGestureTransform(
            translationY = safeOffset,
            scale = 1f - MAX_SCALE_REDUCTION * expressiveProgress,
            cornerRadiusDp = MAX_CORNER_RADIUS_DP * expressiveProgress,
        )
    }

    private fun dismissThreshold(viewportHeight: Float): Float =
        if (viewportHeight > 0f && viewportHeight.isFinite()) {
            viewportHeight * DISMISS_THRESHOLD_FRACTION
        } else {
            0f
        }

    private const val SIDE_REGION_FRACTION = 0.34f
    private const val DISMISS_THRESHOLD_FRACTION = 0.13f
    private const val DISMISS_STICKY_OFFSET_FRACTION = 0.18f
    private const val TRANSFORM_DISTANCE_FRACTION = 0.45f
    private const val MAX_SCALE_REDUCTION = 0.12f
    private const val MAX_CORNER_RADIUS_DP = 32f
    private const val LEVEL_HAPTIC_BASE_STEPS = 8f
    private const val LEVEL_HAPTIC_ADDITIONAL_STEPS = 16f
    private const val MIN_LEVEL_HAPTIC_STRENGTH = 0.18f
}
