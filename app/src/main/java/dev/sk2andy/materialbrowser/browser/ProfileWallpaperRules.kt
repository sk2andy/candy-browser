package dev.sk2andy.materialbrowser.browser

import kotlin.math.max

internal data class ProfileWallpaperLayout(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val maxPanX: Float,
    val maxPanY: Float,
)

internal object ProfileWallpaperRules {
    const val MIN_ZOOM = 1f
    const val MAX_ZOOM = 4f

    fun isSupportedLocalProfileId(profileId: String): Boolean =
        profileId.isNotBlank() &&
            profileId.length <= MAX_PROFILE_ID_LENGTH &&
            profileId.none(Char::isISOControl)

    fun sanitize(wallpaper: ProfileWallpaper): ProfileWallpaper = ProfileWallpaper(
        zoom = wallpaper.zoom.finiteOr(MIN_ZOOM).coerceIn(MIN_ZOOM, MAX_ZOOM),
        normalizedPanX = wallpaper.normalizedPanX.finiteOr(0f).coerceIn(-1f, 1f),
        normalizedPanY = wallpaper.normalizedPanY.finiteOr(0f).coerceIn(-1f, 1f),
    )

    fun layout(
        imageWidth: Float,
        imageHeight: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        wallpaper: ProfileWallpaper,
    ): ProfileWallpaperLayout? {
        if (
            !imageWidth.isFinite() ||
            !imageHeight.isFinite() ||
            !viewportWidth.isFinite() ||
            !viewportHeight.isFinite() ||
            imageWidth <= 0f ||
            imageHeight <= 0f ||
            viewportWidth <= 0f ||
            viewportHeight <= 0f
        ) return null

        val safe = sanitize(wallpaper)
        val coverScale = max(viewportWidth / imageWidth, viewportHeight / imageHeight)
        val scaledWidth = imageWidth * coverScale * safe.zoom
        val scaledHeight = imageHeight * coverScale * safe.zoom
        val maxPanX = ((scaledWidth - viewportWidth) / 2f).coerceAtLeast(0f)
        val maxPanY = ((scaledHeight - viewportHeight) / 2f).coerceAtLeast(0f)
        return ProfileWallpaperLayout(
            left = (viewportWidth - scaledWidth) / 2f + maxPanX * safe.normalizedPanX,
            top = (viewportHeight - scaledHeight) / 2f + maxPanY * safe.normalizedPanY,
            width = scaledWidth,
            height = scaledHeight,
            maxPanX = maxPanX,
            maxPanY = maxPanY,
        )
    }

    fun transformed(
        wallpaper: ProfileWallpaper,
        zoomChange: Float,
        panX: Float,
        panY: Float,
        centroidX: Float,
        centroidY: Float,
        imageWidth: Float,
        imageHeight: Float,
        viewportWidth: Float,
        viewportHeight: Float,
    ): ProfileWallpaper {
        val safe = sanitize(wallpaper)
        val oldLayout = layout(
            imageWidth,
            imageHeight,
            viewportWidth,
            viewportHeight,
            safe,
        ) ?: return safe
        val safeZoomChange = zoomChange.finiteOr(1f).coerceAtLeast(0f)
        val zoomed = safe.copy(zoom = (safe.zoom * safeZoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM))
        val newLayout = checkNotNull(
            layout(imageWidth, imageHeight, viewportWidth, viewportHeight, zoomed),
        )
        val scaleChange = zoomed.zoom / safe.zoom
        val focalX = centroidX.finiteOr(viewportWidth / 2f)
        val focalY = centroidY.finiteOr(viewportHeight / 2f)
        val zoomedLeft = focalX - (focalX - oldLayout.left) * scaleChange
        val zoomedTop = focalY - (focalY - oldLayout.top) * scaleChange
        val centeredLeft = (viewportWidth - newLayout.width) / 2f
        val centeredTop = (viewportHeight - newLayout.height) / 2f
        val nextPanX = zoomedLeft - centeredLeft + panX.finiteOr(0f)
        val nextPanY = zoomedTop - centeredTop + panY.finiteOr(0f)
        return zoomed.copy(
            normalizedPanX = nextPanX.normalizedBy(newLayout.maxPanX),
            normalizedPanY = nextPanY.normalizedBy(newLayout.maxPanY),
        )
    }

    private fun Float.normalizedBy(maximum: Float): Float =
        if (maximum <= 0f) 0f else (this / maximum).coerceIn(-1f, 1f)

    private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback

    private const val MAX_PROFILE_ID_LENGTH = 128
}
