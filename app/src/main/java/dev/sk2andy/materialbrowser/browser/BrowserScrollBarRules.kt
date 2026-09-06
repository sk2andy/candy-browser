package dev.sk2andy.materialbrowser.browser

import kotlin.math.roundToInt

/** Engine-neutral vertical scroll metrics exposed by a renderer viewport. */
internal data class BrowserEngineScrollMetrics(
    val offsetPx: Int,
    val extentPx: Int,
    val rangePx: Int,
) {
    val scrollRangePx: Int
        get() = (rangePx - extentPx).coerceAtLeast(0)
}

internal data class BrowserScrollBarGeometry(
    val scrollRangePx: Int,
    val thumbLengthPx: Float,
    val thumbTravelPx: Float,
    val thumbOffsetPx: Float,
)

/** Shared geometry and drag mapping with no dependency on a concrete renderer. */
internal object BrowserScrollBarRules {
    fun geometry(
        metrics: BrowserEngineScrollMetrics,
        trackLengthPx: Float,
        minimumThumbLengthPx: Float,
    ): BrowserScrollBarGeometry? {
        val extent = metrics.extentPx.coerceAtLeast(0)
        val range = metrics.rangePx.coerceAtLeast(extent)
        val track = trackLengthPx.coerceAtLeast(0f)
        if (extent <= 0 || range <= extent || track <= 0f) return null
        val thumbLength = (track * extent / range).coerceIn(
            0f,
            track.coerceAtLeast(minimumThumbLengthPx),
        ).coerceAtLeast(minimumThumbLengthPx.coerceAtMost(track))
        val travel = (track - thumbLength).coerceAtLeast(0f)
        val offset = if (metrics.scrollRangePx == 0) 0f else {
            metrics.offsetPx.coerceIn(0, metrics.scrollRangePx).toFloat() /
                metrics.scrollRangePx * travel
        }
        return BrowserScrollBarGeometry(
            scrollRangePx = metrics.scrollRangePx,
            thumbLengthPx = thumbLength,
            thumbTravelPx = travel,
            thumbOffsetPx = offset,
        )
    }

    fun scrollOffsetAfterDrag(
        currentOffsetPx: Int,
        dragDeltaPx: Float,
        geometry: BrowserScrollBarGeometry,
    ): Int {
        if (geometry.thumbTravelPx <= 0f) return 0
        return (currentOffsetPx + dragDeltaPx / geometry.thumbTravelPx * geometry.scrollRangePx)
            .roundToInt()
            .coerceIn(0, geometry.scrollRangePx)
    }
}
