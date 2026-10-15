package dev.sk2andy.materialbrowser.browser.sitewidget

import kotlin.math.ceil
import kotlin.math.floor

internal data class SiteWidgetFormat(val columns: Int, val rows: Int) {
    val isValid: Boolean get() = columns in CELL_RANGE && rows in CELL_RANGE
    val name: String get() = "Grid${columns}x$rows"

    val aspectRatio: Float get() = columns.toFloat() / rows

    companion object {
        val CELL_RANGE = 2..6
        val FourByThree = SiteWidgetFormat(columns = 4, rows = 3)
        val FourByFour = SiteWidgetFormat(columns = 4, rows = 4)
        val entries = CELL_RANGE.flatMap { columns -> CELL_RANGE.map { rows -> SiteWidgetFormat(columns, rows) } }

        fun valueOf(name: String): SiteWidgetFormat = when (name) {
            "FourByThree" -> FourByThree
            "FourByFour" -> FourByFour
            else -> entries.firstOrNull { it.name == name }
                ?: throw IllegalArgumentException("Unknown website widget format: $name")
        }
    }
}

internal data class SiteWidgetCrop(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

internal data class SiteWidgetPixelBounds(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

internal object SiteWidgetCropRules {
    const val MINIMUM_SIZE_FRACTION = 0.1f
    const val MAXIMUM_ZOOM = 10f

    fun initialCrop(
        format: SiteWidgetFormat,
        viewportWidth: Int,
        viewportHeight: Int,
    ): SiteWidgetCrop? {
        if (!format.isValid || viewportWidth <= 0 || viewportHeight <= 0) return null
        val normalizedAspect = normalizedAspect(format, viewportWidth, viewportHeight)
        val width = minOf(1.0, normalizedAspect).toFloat()
        val height = minOf(1.0, 1.0 / normalizedAspect).toFloat()
        return SiteWidgetCrop(
            left = (1f - width) / 2f,
            top = (1f - height) / 2f,
            width = width,
            height = height,
        )
    }

    fun sanitize(crop: SiteWidgetCrop): SiteWidgetCrop? {
        if (
            !crop.left.isFinite() || !crop.top.isFinite() ||
            !crop.width.isFinite() || !crop.height.isFinite() ||
            crop.left < 0f || crop.top < 0f ||
            crop.width <= 0f || crop.height <= 0f ||
            crop.width > 1f || crop.height > 1f ||
            crop.left > 1f - crop.width || crop.top > 1f - crop.height
        ) return null
        return crop
    }

    fun moveCrop(crop: SiteWidgetCrop, deltaX: Float, deltaY: Float): SiteWidgetCrop? {
        if (sanitize(crop) == null || !deltaX.isFinite() || !deltaY.isFinite()) return null
        return crop.copy(
            left = (crop.left.toDouble() + deltaX).coerceIn(0.0, (1f - crop.width).toDouble()).toFloat(),
            top = (crop.top.toDouble() + deltaY).coerceIn(0.0, (1f - crop.height).toDouble()).toFloat(),
        )
    }

    fun resizeCrop(
        crop: SiteWidgetCrop,
        scale: Float,
        format: SiteWidgetFormat,
        viewportWidth: Int,
        viewportHeight: Int,
    ): SiteWidgetCrop? {
        if (
            sanitize(crop) == null || !format.isValid || !scale.isFinite() || scale <= 0f ||
            viewportWidth <= 0 || viewportHeight <= 0
        ) return null
        val normalizedAspect = normalizedAspect(format, viewportWidth, viewportHeight)
        val maximumWidth = minOf(1.0, normalizedAspect)
        val width = (crop.width.toDouble() * scale)
            .coerceIn(maximumWidth * MINIMUM_SIZE_FRACTION, maximumWidth).toFloat()
        val height = (width.toDouble() / normalizedAspect).coerceAtMost(1.0).toFloat()
        val resized = SiteWidgetCrop(
            left = (crop.left + (crop.width - width) / 2f).coerceIn(0f, 1f - width),
            top = (crop.top + (crop.height - height) / 2f).coerceIn(0f, 1f - height),
            width = width,
            height = height,
        )
        return sanitize(resized)
    }

    fun reformatCrop(
        crop: SiteWidgetCrop,
        previousFormat: SiteWidgetFormat,
        format: SiteWidgetFormat,
        viewportWidth: Int,
        viewportHeight: Int,
    ): SiteWidgetCrop? {
        val previousMaximum = initialCrop(previousFormat, viewportWidth, viewportHeight) ?: return null
        val maximum = initialCrop(format, viewportWidth, viewportHeight) ?: return null
        return resizeCrop(crop, maximum.width / previousMaximum.width, format, viewportWidth, viewportHeight)
    }

    fun zoomCrop(
        crop: SiteWidgetCrop,
        zoom: Float,
        format: SiteWidgetFormat,
        viewportWidth: Int,
        viewportHeight: Int,
    ): SiteWidgetCrop? {
        if (!zoom.isFinite() || zoom <= 0f || sanitize(crop) == null) return null
        val maximum = initialCrop(format, viewportWidth, viewportHeight) ?: return null
        return resizeCrop(
            crop,
            maximum.width / zoom.coerceIn(1f, MAXIMUM_ZOOM) / crop.width,
            format,
            viewportWidth,
            viewportHeight,
        )
    }

    fun pixelBounds(crop: SiteWidgetCrop, bitmapWidth: Int, bitmapHeight: Int): SiteWidgetPixelBounds? {
        if (sanitize(crop) == null || bitmapWidth <= 0 || bitmapHeight <= 0) return null
        val left = floor(crop.left.toDouble() * bitmapWidth).toInt().coerceIn(0, bitmapWidth - 1)
        val top = floor(crop.top.toDouble() * bitmapHeight).toInt().coerceIn(0, bitmapHeight - 1)
        val right = ceil((crop.left.toDouble() + crop.width) * bitmapWidth)
            .toInt().coerceIn(left + 1, bitmapWidth)
        val bottom = ceil((crop.top.toDouble() + crop.height) * bitmapHeight)
            .toInt().coerceIn(top + 1, bitmapHeight)
        return SiteWidgetPixelBounds(left = left, top = top, width = right - left, height = bottom - top)
    }

    private fun normalizedAspect(format: SiteWidgetFormat, viewportWidth: Int, viewportHeight: Int): Double =
        format.columns.toDouble() / format.rows * viewportHeight / viewportWidth
}
