package dev.sk2andy.materialbrowser.shared.ui

data class AddressMenuMorphFrame(
    val menuExpansionProgress: Float,
    val addressContentAlpha: Float,
    val addressSurfaceScale: Float,
    val addressCornerMorphProgress: Float,
)

/** Shared timeline for the address pill disappearing into the expanding overflow menu. */
object AddressMenuMorphRules {
    const val DURATION_MILLIS = 160

    fun frame(
        animatedProgress: Float,
        expanded: Boolean,
        reduceMotion: Boolean,
    ): AddressMenuMorphFrame {
        val targetProgress = if (expanded) 1f else 0f
        val progress = if (reduceMotion) targetProgress else {
            animatedProgress.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        }
        return AddressMenuMorphFrame(
            menuExpansionProgress = progress,
            addressContentAlpha = 1f - progress,
            addressSurfaceScale = 1f - ADDRESS_SURFACE_SCALE_DELTA * progress,
            addressCornerMorphProgress = progress,
        )
    }

    private const val ADDRESS_SURFACE_SCALE_DELTA = 0.06f
}
