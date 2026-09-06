package dev.sk2andy.materialbrowser.browser.gecko

import kotlin.math.roundToInt

internal data class GeckoPreviewBitmapLayout(
    val sourceHeightPx: Int,
    val targetWidthPx: Int,
    val targetHeightPx: Int,
)

internal object GeckoPreviewCaptureRules {
    fun resolveBitmapLayout(
        viewHeightPx: Int,
        visibleViewHeightPx: Int,
        capturedWidthPx: Int,
        capturedHeightPx: Int,
        targetWidthPx: Int,
        maximumTargetHeightPx: Int,
    ): GeckoPreviewBitmapLayout? {
        if (
            viewHeightPx <= 0 ||
            visibleViewHeightPx <= 0 ||
            capturedWidthPx <= 0 ||
            capturedHeightPx <= 0 ||
            targetWidthPx <= 0 ||
            maximumTargetHeightPx <= 0
        ) {
            return null
        }
        val sourceHeight = (
            capturedHeightPx *
                visibleViewHeightPx.coerceAtMost(viewHeightPx).toFloat() /
                viewHeightPx
            ).roundToInt().coerceIn(1, capturedHeightPx)
        val targetHeight = (sourceHeight * targetWidthPx.toFloat() / capturedWidthPx)
            .roundToInt()
            .coerceIn(1, maximumTargetHeightPx)
        return GeckoPreviewBitmapLayout(
            sourceHeightPx = sourceHeight,
            targetWidthPx = targetWidthPx,
            targetHeightPx = targetHeight,
        )
    }
}
