package dev.sk2andy.materialbrowser.browser

import kotlin.math.abs

object TabPreviewViewportRules {
    fun hasAspectRatioChanged(
        previousWidthDp: Int,
        previousHeightDp: Int,
        currentWidthDp: Int,
        currentHeightDp: Int,
    ): Boolean {
        if (
            previousWidthDp <= 0 || previousHeightDp <= 0 ||
            currentWidthDp <= 0 || currentHeightDp <= 0
        ) return false
        val previousAspectRatio = previousWidthDp.toFloat() / previousHeightDp
        val currentAspectRatio = currentWidthDp.toFloat() / currentHeightDp
        return abs(previousAspectRatio - currentAspectRatio) > ASPECT_RATIO_TOLERANCE
    }

    private const val ASPECT_RATIO_TOLERANCE = 0.005f
}
