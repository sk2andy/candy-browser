package dev.sk2andy.materialbrowser.data

internal data class TabPreviewQuality(
    val visualRange: Int,
    val nearBlackFraction: Float,
)

internal object TabPreviewCaptureRules {
    fun sourceBottomPx(
        viewTopPx: Int,
        viewHeightPx: Int,
        decorHeightPx: Int,
        contentBottomPx: Int?,
    ): Int = minOf(
        viewTopPx + viewHeightPx,
        decorHeightPx,
        contentBottomPx?.takeIf { it > 0 } ?: decorHeightPx,
    )

    fun shouldStore(
        candidate: TabPreviewQuality,
        previous: TabPreviewQuality?,
        isSamePage: Boolean,
    ): Boolean {
        if (candidate.visualRange < MINIMUM_VISUAL_RANGE) return false
        if (!isSamePage || previous == null) return true
        val addedBlackFraction = candidate.nearBlackFraction - previous.nearBlackFraction
        return candidate.nearBlackFraction < LIKELY_BLACK_SURFACE_FRACTION ||
            addedBlackFraction < MAXIMUM_ADDED_BLACK_FRACTION
    }

    private const val MINIMUM_VISUAL_RANGE = 12
    private const val LIKELY_BLACK_SURFACE_FRACTION = 0.55f
    private const val MAXIMUM_ADDED_BLACK_FRACTION = 0.3f
}
