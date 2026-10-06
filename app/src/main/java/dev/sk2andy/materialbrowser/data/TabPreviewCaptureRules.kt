package dev.sk2andy.materialbrowser.data

import kotlin.math.floor
import kotlin.math.sqrt

internal data class TabPreviewQuality(
    val visualRange: Int,
    val nearBlackFraction: Float,
)

internal data class TabPreviewBitmapDimensions(val widthPx: Int, val heightPx: Int)

internal object TabPreviewCaptureRules {
    const val COMPACT_TARGET_WIDTH_PX = 480
    const val MAX_TARGET_WIDTH_PX = 1_280
    const val MAX_BITMAP_PIXELS = 3_000_000
    const val MAX_BITMAP_DIMENSION = 4_096

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
        return minOf(sourceWidthPx, viewportWidthPx, MAX_TARGET_WIDTH_PX)
    }

    fun maximumTargetHeightPx(targetWidthPx: Int): Int =
        if (targetWidthPx <= 0) 0 else minOf(
            targetWidthPx.toLong() * 3,
            MAX_BITMAP_DIMENSION.toLong(),
        ).toInt()

    fun resolveBitmapDimensions(
        sourceWidthPx: Int,
        sourceHeightPx: Int,
        targetWidthPx: Int,
        maximumTargetHeightPx: Int,
    ): TabPreviewBitmapDimensions? {
        if (
            sourceWidthPx <= 0 || sourceHeightPx <= 0 ||
            targetWidthPx <= 0 || maximumTargetHeightPx <= 0
        ) return null
        val scale = minOf(
            1.0,
            targetWidthPx.toDouble() / sourceWidthPx,
            maximumTargetHeightPx.toDouble() / sourceHeightPx,
            MAX_BITMAP_DIMENSION.toDouble() / sourceHeightPx,
            MAX_TARGET_WIDTH_PX.toDouble() / sourceWidthPx,
            sqrt(MAX_BITMAP_PIXELS.toDouble() / (sourceWidthPx.toLong() * sourceHeightPx)),
        )
        return TabPreviewBitmapDimensions(
            widthPx = floor(sourceWidthPx * scale).toInt().coerceAtLeast(1),
            heightPx = floor(sourceHeightPx * scale).toInt().coerceAtLeast(1),
        )
    }

    fun decodeSampleSize(sourceWidthPx: Int, targetWidthPx: Int): Int {
        if (sourceWidthPx <= 0 || targetWidthPx <= 0) return 1
        var sample = 1
        while (sourceWidthPx / (sample * 2L) >= targetWidthPx) sample *= 2
        return sample
    }

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
