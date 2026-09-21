package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.shared.browser.BrowserTabOverviewLayoutRules
import kotlin.math.roundToInt

internal data class TabPreviewQuality(
    val visualRange: Int,
    val nearBlackFraction: Float,
)

internal object TabPreviewCaptureRules {
    const val COMPACT_TARGET_WIDTH_PX = 480
    private const val WIDE_WINDOW_WIDTH_DP = 600f
    private const val MAX_TARGET_WIDTH_PX = 1_280
    private const val MAX_BITMAP_PIXELS = 2_500_000

    fun targetWidthPx(
        sourceWidthPx: Int,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        density: Float,
    ): Int {
        if (
            sourceWidthPx <= 0 ||
            viewportWidthPx <= 0 ||
            viewportHeightPx <= 0 ||
            !density.isFinite() ||
            density <= 0f
        ) return 0
        val viewportWidthDp = viewportWidthPx / density
        if (viewportWidthDp < WIDE_WINDOW_WIDTH_DP) {
            return minOf(COMPACT_TARGET_WIDTH_PX, sourceWidthPx)
        }
        val heroWidthDp = BrowserTabOverviewLayoutRules.heroCard(
            viewportWidth = viewportWidthDp,
            viewportHeight = viewportHeightPx / density,
        ).width
        return minOf(
            sourceWidthPx,
            (heroWidthDp * density).roundToInt()
                .coerceIn(COMPACT_TARGET_WIDTH_PX, MAX_TARGET_WIDTH_PX),
        )
    }

    fun maximumTargetHeightPx(targetWidthPx: Int): Int =
        if (targetWidthPx <= 0) 0 else minOf(
            targetWidthPx * 3,
            MAX_BITMAP_PIXELS / targetWidthPx,
        )

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
