package dev.sk2andy.materialbrowser

internal enum class BrowserRequestedOrientation {
    Sensor,
    Portrait,
    Unspecified,
}

internal data class BrowserWindowState(
    val isImmersive: Boolean,
    val requestedOrientation: BrowserRequestedOrientation,
)

internal object BrowserWindowStateRules {
    const val LARGE_SCREEN_MIN_WIDTH_DP = 600

    fun supportsTabOverviewPortraitLock(smallestScreenWidthDp: Int): Boolean =
        smallestScreenWidthDp < LARGE_SCREEN_MIN_WIDTH_DP

    fun resolve(
        isWebContentFullscreen: Boolean,
        isBrowserFullscreen: Boolean,
        isTabOverviewPortraitLocked: Boolean,
        supportsTabOverviewPortraitLock: Boolean = true,
    ): BrowserWindowState = BrowserWindowState(
        isImmersive = isWebContentFullscreen || isBrowserFullscreen,
        requestedOrientation = when {
            isWebContentFullscreen -> BrowserRequestedOrientation.Sensor
            isTabOverviewPortraitLocked && supportsTabOverviewPortraitLock ->
                BrowserRequestedOrientation.Portrait
            else -> BrowserRequestedOrientation.Unspecified
        },
    )
}
