package dev.sk2andy.materialbrowser.data

data class BrowserMemorySettings(
    val foregroundTabIdleTimeoutMinutes: Int = DEFAULT_FOREGROUND_TAB_IDLE_TIMEOUT_MINUTES,
    val backgroundWarmTabCount: Int = DEFAULT_BACKGROUND_WARM_TAB_COUNT,
    val historyCacheLifetimeMinutes: Int = DEFAULT_HISTORY_CACHE_LIFETIME_MINUTES,
) {
    fun normalized(): BrowserMemorySettings = copy(
        foregroundTabIdleTimeoutMinutes = foregroundTabIdleTimeoutMinutes.coerceIn(
            MIN_FOREGROUND_TAB_IDLE_TIMEOUT_MINUTES,
            MAX_FOREGROUND_TAB_IDLE_TIMEOUT_MINUTES,
        ),
        backgroundWarmTabCount = backgroundWarmTabCount.coerceIn(
            MIN_BACKGROUND_WARM_TAB_COUNT,
            MAX_BACKGROUND_WARM_TAB_COUNT,
        ),
        historyCacheLifetimeMinutes = historyCacheLifetimeMinutes.coerceIn(
            MIN_HISTORY_CACHE_LIFETIME_MINUTES,
            MAX_HISTORY_CACHE_LIFETIME_MINUTES,
        ),
    )

    companion object {
        const val DEFAULT_FOREGROUND_TAB_IDLE_TIMEOUT_MINUTES = 3
        const val MIN_FOREGROUND_TAB_IDLE_TIMEOUT_MINUTES = 1
        const val MAX_FOREGROUND_TAB_IDLE_TIMEOUT_MINUTES = 60
        const val DEFAULT_BACKGROUND_WARM_TAB_COUNT = 0
        const val MIN_BACKGROUND_WARM_TAB_COUNT = 0
        const val MAX_BACKGROUND_WARM_TAB_COUNT = 20
        const val DEFAULT_HISTORY_CACHE_LIFETIME_MINUTES = 5
        const val MIN_HISTORY_CACHE_LIFETIME_MINUTES = 1
        const val MAX_HISTORY_CACHE_LIFETIME_MINUTES = 60
    }
}
