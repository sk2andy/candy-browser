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

    fun isLikelyFailedCapture(quality: TabPreviewQuality): Boolean =
        quality.visualRange < MINIMUM_VISUAL_RANGE &&
            quality.nearBlackFraction >= FAILED_CAPTURE_BLACK_FRACTION

    fun shouldStorePixelCopy(candidate: TabPreviewQuality): Boolean =
        !isLikelyFailedCapture(candidate)

    private const val MINIMUM_VISUAL_RANGE = 12
    private const val FAILED_CAPTURE_BLACK_FRACTION = 0.95f
}
