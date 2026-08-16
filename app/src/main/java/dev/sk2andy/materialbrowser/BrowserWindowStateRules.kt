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
    fun resolve(
        isWebContentFullscreen: Boolean,
        isBrowserFullscreen: Boolean,
        isTabOverviewPortraitLocked: Boolean,
    ): BrowserWindowState = BrowserWindowState(
        isImmersive = isWebContentFullscreen || isBrowserFullscreen,
        requestedOrientation = when {
            isWebContentFullscreen -> BrowserRequestedOrientation.Sensor
            isTabOverviewPortraitLocked -> BrowserRequestedOrientation.Portrait
            else -> BrowserRequestedOrientation.Unspecified
        },
    )
}
