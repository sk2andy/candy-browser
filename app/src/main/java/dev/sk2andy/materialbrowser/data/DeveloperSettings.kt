package dev.sk2andy.materialbrowser.data

enum class BrowserChromeScrollDispatchMode(
    val stableId: String,
    val fixedDispatchesPerSecond: Long?,
) {
    Optimized("optimized", null),
    Fixed120Hz("fixed_120_hz", 120L),
    Fixed60Hz("fixed_60_hz", 60L),
    Fixed30Hz("fixed_30_hz", 30L),
    Fixed15Hz("fixed_15_hz", 15L),
    ;

    companion object {
        val Default = Optimized

        fun fromStableId(value: String?): BrowserChromeScrollDispatchMode =
            entries.firstOrNull { mode -> mode.stableId == value } ?: Default
    }
}

data class DeveloperSettings(
    val browserChromeScrollDispatchMode: BrowserChromeScrollDispatchMode =
        BrowserChromeScrollDispatchMode.Default,
    val safeAreaLayoutQuietPeriodMillis: Int = DEFAULT_SAFE_AREA_LAYOUT_QUIET_PERIOD_MILLIS,
    val safeAreaRequiredFailureCount: Int = DEFAULT_SAFE_AREA_REQUIRED_FAILURE_COUNT,
    val forceSafeAreaFallback: Boolean = false,
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
