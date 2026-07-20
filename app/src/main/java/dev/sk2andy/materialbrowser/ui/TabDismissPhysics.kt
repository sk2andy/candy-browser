package dev.sk2andy.materialbrowser.ui

internal object TabDismissPhysics {
    const val DEFAULT_RESISTANCE_FRACTION = 0.4f
    private const val RESISTANCE_MULTIPLIER = 0.55f

    fun visualDistance(
        rawDistance: Float,
        dismissThreshold: Float,
        resistanceFraction: Float = DEFAULT_RESISTANCE_FRACTION,
    ): Float {
        if (dismissThreshold <= 0f) return 0f
        val safeRawDistance = rawDistance.coerceAtLeast(0f)
        val resistanceEnd = dismissThreshold * resistanceFraction.coerceIn(0.1f, 0.9f)
        return if (safeRawDistance <= resistanceEnd) {
            safeRawDistance * RESISTANCE_MULTIPLIER
        } else {
            resistanceEnd * RESISTANCE_MULTIPLIER + (safeRawDistance - resistanceEnd)
        }
    }

    fun isInResistanceBand(
        rawDistance: Float,
        dismissThreshold: Float,
        resistanceFraction: Float = DEFAULT_RESISTANCE_FRACTION,
    ): Boolean {
        if (dismissThreshold <= 0f) return false
        val resistanceEnd = dismissThreshold * resistanceFraction.coerceIn(0.1f, 0.9f)
        return rawDistance > 0f && rawDistance < resistanceEnd
    }

    fun isDismissed(
        rawDistance: Float,
        dismissThreshold: Float,
        resistanceFraction: Float = DEFAULT_RESISTANCE_FRACTION,
    ): Boolean = visualDistance(rawDistance, dismissThreshold, resistanceFraction) >= dismissThreshold
}
