package dev.sk2andy.materialbrowser.ui

internal enum class AddressBarVerticalAction {
    None,
    OpenTabs,
}

internal object AddressBarGestureRules {
    const val OPEN_TABS_THRESHOLD_DP = 56f

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
