package dev.sk2andy.materialbrowser.ui

import kotlin.math.roundToInt

internal data class WebViewScrollBarGeometry(
    val thumbTopPx: Float,
    val thumbHeightPx: Float,
    val thumbTravelPx: Float,
    val scrollRangePx: Int,
)

internal object WebViewScrollBarRules {
    fun geometry(
        scrollY: Int,
        viewportHeightPx: Int,
        contentHeightPx: Int,
        trackHeightPx: Float,
        minimumThumbHeightPx: Float,
    ): WebViewScrollBarGeometry? {
        if (
            viewportHeightPx <= 0 ||
            contentHeightPx <= viewportHeightPx ||
            trackHeightPx <= 0f
        ) {
            return null
        }
        val scrollRangePx = contentHeightPx - viewportHeightPx
        val thumbHeightPx = (
            trackHeightPx * viewportHeightPx.toFloat() / contentHeightPx.toFloat()
            ).coerceIn(minimumThumbHeightPx.coerceAtMost(trackHeightPx), trackHeightPx)
        val thumbTravelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
        val clampedScrollY = scrollY.coerceIn(0, scrollRangePx)
        val thumbTopPx = if (thumbTravelPx == 0f) {
            0f
        } else {
            clampedScrollY.toFloat() / scrollRangePx.toFloat() * thumbTravelPx
        }
        return WebViewScrollBarGeometry(
            thumbTopPx = thumbTopPx,
            thumbHeightPx = thumbHeightPx,
            thumbTravelPx = thumbTravelPx,
            scrollRangePx = scrollRangePx,
        )
    }

    fun scrollYAfterDrag(
        currentScrollY: Int,
        dragDeltaPx: Float,
        geometry: WebViewScrollBarGeometry,
    ): Int {
        if (geometry.thumbTravelPx <= 0f) return currentScrollY
        val scrollDelta = dragDeltaPx / geometry.thumbTravelPx * geometry.scrollRangePx
        return (currentScrollY + scrollDelta)
            .roundToInt()
            .coerceIn(0, geometry.scrollRangePx)
    }
}
