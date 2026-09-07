package dev.sk2andy.materialbrowser.capsule

import kotlin.math.max

data class CapsuleIconCrop(
    val zoom: Float = 1f,
    val normalizedPanX: Float = 0f,
    val normalizedPanY: Float = 0f,
)

internal data class CapsuleIconCropLayout(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val maxPanX: Float,
    val maxPanY: Float,
)

internal object CapsuleIconCropRules {
    const val MIN_ZOOM = 1f
    const val MAX_ZOOM = 4f

    fun sanitize(crop: CapsuleIconCrop): CapsuleIconCrop = CapsuleIconCrop(
        zoom = crop.zoom.finiteOr(MIN_ZOOM).coerceIn(MIN_ZOOM, MAX_ZOOM),
        normalizedPanX = crop.normalizedPanX.finiteOr(0f).coerceIn(-1f, 1f),
        normalizedPanY = crop.normalizedPanY.finiteOr(0f).coerceIn(-1f, 1f),
    )

    fun layout(
        imageWidth: Float,
        imageHeight: Float,
        viewportSize: Float,
        crop: CapsuleIconCrop,
    ): CapsuleIconCropLayout? {
        if (
            !imageWidth.isFinite() ||
            !imageHeight.isFinite() ||
            !viewportSize.isFinite() ||
            imageWidth <= 0f ||
            imageHeight <= 0f ||
            viewportSize <= 0f
        ) return null

        val safe = sanitize(crop)
        val coverScale = max(viewportSize / imageWidth, viewportSize / imageHeight)
        val scaledWidth = imageWidth * coverScale * safe.zoom
        val scaledHeight = imageHeight * coverScale * safe.zoom
        val maxPanX = ((scaledWidth - viewportSize) / 2f).coerceAtLeast(0f)
        val maxPanY = ((scaledHeight - viewportSize) / 2f).coerceAtLeast(0f)
        return CapsuleIconCropLayout(
            left = (viewportSize - scaledWidth) / 2f + maxPanX * safe.normalizedPanX,
            top = (viewportSize - scaledHeight) / 2f + maxPanY * safe.normalizedPanY,
            width = scaledWidth,
            height = scaledHeight,
            maxPanX = maxPanX,
            maxPanY = maxPanY,
        )
    }

    fun transformed(
        crop: CapsuleIconCrop,
        zoomChange: Float,
        panX: Float,
        panY: Float,
        centroidX: Float,
        centroidY: Float,
        imageWidth: Float,
        imageHeight: Float,
        viewportSize: Float,
    ): CapsuleIconCrop {
        val safe = sanitize(crop)
        val oldLayout = layout(imageWidth, imageHeight, viewportSize, safe) ?: return safe
        val safeZoomChange = zoomChange.finiteOr(1f).coerceAtLeast(0f)
        val zoomed = safe.copy(
            zoom = (safe.zoom * safeZoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM),
        )
        val newLayout = checkNotNull(layout(imageWidth, imageHeight, viewportSize, zoomed))
        val scaleChange = zoomed.zoom / safe.zoom
        val focalX = centroidX.finiteOr(viewportSize / 2f)
        val focalY = centroidY.finiteOr(viewportSize / 2f)
        val zoomedLeft = focalX - (focalX - oldLayout.left) * scaleChange
        val zoomedTop = focalY - (focalY - oldLayout.top) * scaleChange
        val centeredLeft = (viewportSize - newLayout.width) / 2f
        val centeredTop = (viewportSize - newLayout.height) / 2f
        return zoomed.copy(
            normalizedPanX = (zoomedLeft - centeredLeft + panX.finiteOr(0f))
                .normalizedBy(newLayout.maxPanX),
            normalizedPanY = (zoomedTop - centeredTop + panY.finiteOr(0f))
                .normalizedBy(newLayout.maxPanY),
        )
    }

    private fun Float.normalizedBy(maximum: Float): Float =
        if (maximum <= 0f) 0f else (this / maximum).coerceIn(-1f, 1f)

    private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback
}
