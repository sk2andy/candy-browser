package dev.sk2andy.materialbrowser.capsule

object CapsuleIconUpdateRules {
    fun resolveMode(
        requestedMode: CapsuleIconMode,
        hasSourceFavicon: Boolean,
        hasCustomIcon: Boolean,
        hasRenderedIcon: Boolean,
        customizationChanged: Boolean,
    ): CapsuleIconMode {
        if (requestedMode == CapsuleIconMode.Custom) {
            return if (hasCustomIcon) CapsuleIconMode.Custom else CapsuleIconMode.ProfileFallback
        }
        if (requestedMode == CapsuleIconMode.ProfileFallback) return requestedMode
        if (hasSourceFavicon) return CapsuleIconMode.Favicon
        return if (hasRenderedIcon && !customizationChanged) {
            CapsuleIconMode.Favicon
        } else {
            CapsuleIconMode.ProfileFallback
        }
    }
}
