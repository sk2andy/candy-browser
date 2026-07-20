package dev.sk2andy.materialbrowser.ui

internal enum class AddressBarVerticalAction {
    None,
    OpenTabs,
    Reload,
}

internal object AddressBarGestureRules {
    fun action(dragDistance: Float, threshold: Float): AddressBarVerticalAction = when {
        dragDistance <= -threshold -> AddressBarVerticalAction.OpenTabs
        dragDistance >= threshold -> AddressBarVerticalAction.Reload
        else -> AddressBarVerticalAction.None
    }
}
