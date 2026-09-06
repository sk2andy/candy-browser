package dev.sk2andy.materialbrowser.capsule

object CapsuleIconUpdateRules {
    fun resolveMode(
        requestedMode: CapsuleIconMode,
        hasSourceFavicon: Boolean,
        hasRenderedIcon: Boolean,
        customizationChanged: Boolean,
    ): CapsuleIconMode {
        if (requestedMode != CapsuleIconMode.Favicon) return requestedMode
        if (hasSourceFavicon) return CapsuleIconMode.Favicon
        return if (hasRenderedIcon && !customizationChanged) {
            CapsuleIconMode.Favicon
        } else {
            CapsuleIconMode.ProfileFallback
        }
    }
}
