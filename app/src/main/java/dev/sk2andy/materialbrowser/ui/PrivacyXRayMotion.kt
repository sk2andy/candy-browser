package dev.sk2andy.materialbrowser.ui

internal enum class PrivacyCountDirection {
    Increasing,
    Decreasing,
    Unchanged,
}

internal object PrivacyXRayMotionRules {
    const val BADGE_BATCH_WINDOW_MILLIS = 160L
    const val BADGE_PULSE_COOLDOWN_MILLIS = 640L

    fun countDirection(previousCount: Int, currentCount: Int): PrivacyCountDirection = when {
        currentCount > previousCount -> PrivacyCountDirection.Increasing
        currentCount < previousCount -> PrivacyCountDirection.Decreasing
        else -> PrivacyCountDirection.Unchanged
    }

    fun badgePulseDelayMillis(
        previousCount: Int,
        currentCount: Int,
        elapsedSinceLastPulseMillis: Long,
    ): Long? {
        if (countDirection(previousCount, currentCount) != PrivacyCountDirection.Increasing) {
            return null
        }
        val elapsed = elapsedSinceLastPulseMillis.coerceAtLeast(0L)
        val cooldownRemaining = (BADGE_PULSE_COOLDOWN_MILLIS - elapsed).coerceAtLeast(0L)
        return maxOf(BADGE_BATCH_WINDOW_MILLIS, cooldownRemaining)
    }

    fun shouldRunBatchedPulse(triggerCount: Int, currentCount: Int): Boolean =
        currentCount >= triggerCount

    fun categoryFraction(count: Int, totalCount: Int): Float {
        if (count <= 0 || totalCount <= 0) return 0f
        return count.coerceAtMost(totalCount).toFloat() / totalCount
    }
}
