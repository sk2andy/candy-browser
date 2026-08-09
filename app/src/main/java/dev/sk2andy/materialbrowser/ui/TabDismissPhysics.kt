package dev.sk2andy.materialbrowser.ui

import kotlin.math.absoluteValue
import kotlin.math.sign

internal object TabDismissPhysics {
    const val DEFAULT_RESISTANCE_FRACTION = 0.4f
    const val CARD_DISMISS_THRESHOLD_FRACTION = 0.53f
    private const val RESISTANCE_MULTIPLIER = 0.55f
    private const val MAX_RELEASE_PROGRESS = 1.1f

    fun visualDistance(
        rawDistance: Float,
        releaseProgress: Float = 0f,
    ): Float {
        val safeRawDistance = rawDistance.coerceAtLeast(0f)
        val resistedDistance = safeRawDistance * RESISTANCE_MULTIPLIER
        return resistedDistance +
            (safeRawDistance - resistedDistance) *
            releaseProgress.coerceIn(0f, MAX_RELEASE_PROGRESS)
    }

    fun signedVisualDistance(
        rawDistance: Float,
        releaseProgress: Float = 0f,
    ): Float = rawDistance.sign * visualDistance(
        rawDistance = rawDistance.absoluteValue,
        releaseProgress = releaseProgress,
    )

    fun hasClearedResistance(
        rawDistance: Float,
        dismissThreshold: Float,
        resistanceFraction: Float = DEFAULT_RESISTANCE_FRACTION,
    ): Boolean {
        if (dismissThreshold <= 0f) return false
        return rawDistance >= resistanceEnd(dismissThreshold, resistanceFraction)
    }

    fun isInResistancePhase(
        rawDistance: Float,
        dismissThreshold: Float,
        resistanceFraction: Float = DEFAULT_RESISTANCE_FRACTION,
    ): Boolean {
        if (dismissThreshold <= 0f) return false
        return rawDistance > 0f &&
            !hasClearedResistance(rawDistance, dismissThreshold, resistanceFraction)
    }

    private fun resistanceEnd(
        dismissThreshold: Float,
        resistanceFraction: Float,
    ): Float = dismissThreshold * resistanceFraction.coerceIn(0.1f, 0.9f)
}
