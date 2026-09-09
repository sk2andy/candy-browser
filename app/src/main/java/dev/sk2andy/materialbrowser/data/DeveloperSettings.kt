package dev.sk2andy.materialbrowser.data

data class DeveloperSettings(
    val safeAreaLayoutQuietPeriodMillis: Int = DEFAULT_SAFE_AREA_LAYOUT_QUIET_PERIOD_MILLIS,
    val safeAreaRequiredFailureCount: Int = DEFAULT_SAFE_AREA_REQUIRED_FAILURE_COUNT,
) {
    fun normalized(): DeveloperSettings = copy(
        safeAreaLayoutQuietPeriodMillis = normalizedLayoutQuietPeriodMillis(),
        safeAreaRequiredFailureCount = safeAreaRequiredFailureCount.coerceIn(
            MIN_SAFE_AREA_REQUIRED_FAILURE_COUNT,
            MAX_SAFE_AREA_REQUIRED_FAILURE_COUNT,
        ),
    )

    private fun normalizedLayoutQuietPeriodMillis(): Int {
        val bounded = safeAreaLayoutQuietPeriodMillis.coerceIn(
            MIN_SAFE_AREA_LAYOUT_QUIET_PERIOD_MILLIS,
            MAX_SAFE_AREA_LAYOUT_QUIET_PERIOD_MILLIS,
        )
        val offset = bounded - MIN_SAFE_AREA_LAYOUT_QUIET_PERIOD_MILLIS
        val roundedSteps =
            (offset + SAFE_AREA_LAYOUT_QUIET_PERIOD_STEP_MILLIS / 2) /
                SAFE_AREA_LAYOUT_QUIET_PERIOD_STEP_MILLIS
        return MIN_SAFE_AREA_LAYOUT_QUIET_PERIOD_MILLIS +
            roundedSteps * SAFE_AREA_LAYOUT_QUIET_PERIOD_STEP_MILLIS
    }

    companion object {
        const val DEFAULT_SAFE_AREA_LAYOUT_QUIET_PERIOD_MILLIS = 400
        const val MIN_SAFE_AREA_LAYOUT_QUIET_PERIOD_MILLIS = 100
        const val MAX_SAFE_AREA_LAYOUT_QUIET_PERIOD_MILLIS = 800
        const val SAFE_AREA_LAYOUT_QUIET_PERIOD_STEP_MILLIS = 50
        const val DEFAULT_SAFE_AREA_REQUIRED_FAILURE_COUNT = 3
        const val MIN_SAFE_AREA_REQUIRED_FAILURE_COUNT = 2
        const val MAX_SAFE_AREA_REQUIRED_FAILURE_COUNT = 5
    }
}
