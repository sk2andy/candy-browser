package dev.sk2andy.materialbrowser.browser

internal enum class FullscreenVideoPlacement {
    Expanded,
    MiniPlayer,
}

internal data class FullscreenVideoOffset(
    val x: Float,
    val y: Float,
)

internal data class FullscreenVideoBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

internal data class FullscreenVideoAspectRatio(
    val width: Int,
    val height: Int,
)

internal object FullscreenVideoRules {
    fun supportsPreparedAutoEnter(sdkInt: Int): Boolean = sdkInt >= 35

    fun pictureInPictureAspectRatio(
        videoWidth: Int,
        videoHeight: Int,
    ): FullscreenVideoAspectRatio {
        if (videoWidth <= 0 || videoHeight <= 0) return DEFAULT_VIDEO_ASPECT_RATIO
        val ratio = videoWidth.toDouble() / videoHeight
        if (!ratio.isFinite()) return DEFAULT_VIDEO_ASPECT_RATIO
        if (ratio > MAX_PICTURE_IN_PICTURE_ASPECT_RATIO) {
            return FullscreenVideoAspectRatio(width = 239, height = 100)
        }
        if (ratio < MIN_PICTURE_IN_PICTURE_ASPECT_RATIO) {
            return FullscreenVideoAspectRatio(width = 100, height = 239)
        }
        val divisor = greatestCommonDivisor(videoWidth, videoHeight)
        return FullscreenVideoAspectRatio(
            width = videoWidth / divisor,
            height = videoHeight / divisor,
        )
    }

    fun hostsSourceInOverlay(host: FullscreenVideoHost): Boolean =
        host == FullscreenVideoHost.Overlay

    fun placement(
        sessionTabId: String?,
        selectedTabId: String,
        minimizedByUser: Boolean,
        videoOnlyPresentation: Boolean,
    ): FullscreenVideoPlacement? {
        if (sessionTabId == null) return null
        return if (
            videoOnlyPresentation ||
            (sessionTabId == selectedTabId && !minimizedByUser)
        ) {
            FullscreenVideoPlacement.Expanded
        } else {
            FullscreenVideoPlacement.MiniPlayer
        }
    }

    fun pictureInPictureSourceBounds(
        windowBounds: FullscreenVideoBounds,
        aspectWidth: Int,
        aspectHeight: Int,
    ): FullscreenVideoBounds? {
        val windowWidth = windowBounds.right - windowBounds.left
        val windowHeight = windowBounds.bottom - windowBounds.top
        if (windowWidth <= 0 || windowHeight <= 0 || aspectWidth <= 0 || aspectHeight <= 0) {
            return null
        }
        val fitsByWidth = windowWidth.toLong() * aspectHeight <=
            windowHeight.toLong() * aspectWidth
        val sourceWidth: Int
        val sourceHeight: Int
        if (fitsByWidth) {
            sourceWidth = windowWidth
            sourceHeight = (windowWidth.toLong() * aspectHeight / aspectWidth)
                .toInt()
                .coerceAtLeast(1)
        } else {
            sourceHeight = windowHeight
            sourceWidth = (windowHeight.toLong() * aspectWidth / aspectHeight)
                .toInt()
                .coerceAtLeast(1)
        }
        val left = windowBounds.left + (windowWidth - sourceWidth) / 2
        val top = if (fitsByWidth) {
            windowBounds.top
        } else {
            windowBounds.top + (windowHeight - sourceHeight) / 2
        }
        return FullscreenVideoBounds(
            left = left,
            top = top,
            right = left + sourceWidth,
            bottom = top + sourceHeight,
        )
    }

    fun isPictureInPictureReturnLayoutReady(
        width: Int,
        height: Int,
        targetWidth: Int,
        targetHeight: Int,
        tolerance: Int,
    ): Boolean {
        if (width <= 0 || height <= 0 || targetWidth <= 0 || targetHeight <= 0) return false
        val safeTolerance = tolerance.coerceAtLeast(0)
        return width >= targetWidth - safeTolerance &&
            height >= targetHeight - safeTolerance
    }

    fun clampMiniPlayerOffset(
        proposedX: Float,
        proposedY: Float,
        maxLeftTravel: Float,
        maxUpTravel: Float,
    ): FullscreenVideoOffset = FullscreenVideoOffset(
        x = proposedX.coerceIn(-maxLeftTravel.coerceAtLeast(0f), 0f),
        y = proposedY.coerceIn(-maxUpTravel.coerceAtLeast(0f), 0f),
    )

    fun nextMiniPlayerAnchor(
        current: FullscreenVideoOffset,
        maxLeftTravel: Float,
        maxUpTravel: Float,
    ): FullscreenVideoOffset {
        val left = maxLeftTravel.coerceAtLeast(0f)
        val up = maxUpTravel.coerceAtLeast(0f)
        val isRight = current.x > -left / 2f
        val isBottom = current.y > -up / 2f
        return when {
            isRight && isBottom -> FullscreenVideoOffset(x = -left, y = 0f)
            !isRight && isBottom -> FullscreenVideoOffset(x = -left, y = -up)
            !isRight && !isBottom -> FullscreenVideoOffset(x = 0f, y = -up)
            else -> FullscreenVideoOffset(x = 0f, y = 0f)
        }
    }

    private fun greatestCommonDivisor(first: Int, second: Int): Int {
        var left = first
        var right = second
        while (right != 0) {
            val remainder = left % right
            left = right
            right = remainder
        }
        return left.coerceAtLeast(1)
    }

    private val DEFAULT_VIDEO_ASPECT_RATIO = FullscreenVideoAspectRatio(width = 16, height = 9)
    private const val MAX_PICTURE_IN_PICTURE_ASPECT_RATIO = 2.39
    private const val MIN_PICTURE_IN_PICTURE_ASPECT_RATIO = 1.0 / 2.39
}
