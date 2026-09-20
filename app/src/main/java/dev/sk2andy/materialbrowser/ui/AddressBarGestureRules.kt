package dev.sk2andy.materialbrowser.ui

internal enum class AddressBarVerticalAction {
    None,
    OpenTabs,
}

internal object AddressBarGestureRules {
    const val OPEN_TABS_THRESHOLD_DP = 56f
    const val PARKED_OPEN_TABS_THRESHOLD_DP = 24f
    const val PARKED_REPOSITION_LONG_PRESS_MILLIS = 320L

    fun action(dragDistance: Float, threshold: Float): AddressBarVerticalAction = when {
        dragDistance <= -threshold -> AddressBarVerticalAction.OpenTabs
        else -> AddressBarVerticalAction.None
    }
}

internal object AddressBarTabSwitchRules {
    const val DISTANCE_FRACTION = 0.24f

    fun hasReachedDistance(dragDistance: Float, viewportWidth: Float): Boolean =
        viewportWidth > 0f && dragDistance >= viewportWidth * DISTANCE_FRACTION
}

internal object AddressBarWideLayoutRules {
    const val MIN_WINDOW_WIDTH_DP = 600f

    fun usesTabStrip(windowWidthDp: Float): Boolean =
        windowWidthDp.isFinite() && windowWidthDp >= MIN_WINDOW_WIDTH_DP
}
