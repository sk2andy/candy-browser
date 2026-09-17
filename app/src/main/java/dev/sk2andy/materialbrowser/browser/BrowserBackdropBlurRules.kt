package dev.sk2andy.materialbrowser.browser

internal enum class BrowserBackdropBlurMode {
    ViewHierarchyCapture,
    NativeSurfaceRegion,
    Unavailable,
}

internal data class BrowserBackdropBlurRegion(
    val leftInWindowPx: Float,
    val topInWindowPx: Float,
    val rightInWindowPx: Float,
    val bottomInWindowPx: Float,
    val cornerRadiusPx: Float,
    val blurRadiusPx: Float,
)

internal data class BrowserSurfaceBackdropBlurRegion(
    val leftPx: Float,
    val topPx: Float,
    val rightPx: Float,
    val bottomPx: Float,
    val cornerRadiusPx: Float,
    val blurRadiusPx: Float,
)

internal object BrowserBackdropBlurRules {
    fun mode(
        engineKind: AndroidBrowserEngineKind,
        sdkInt: Int,
    ): BrowserBackdropBlurMode = when (engineKind) {
        AndroidBrowserEngineKind.SystemWebView -> BrowserBackdropBlurMode.ViewHierarchyCapture
        AndroidBrowserEngineKind.GeckoView -> if (sdkInt >= NATIVE_SURFACE_BLUR_MIN_SDK) {
            BrowserBackdropBlurMode.NativeSurfaceRegion
        } else {
            BrowserBackdropBlurMode.Unavailable
        }
    }

    fun regionInWindow(
        leftPx: Float,
        topPx: Float,
        rightPx: Float,
        bottomPx: Float,
        cornerRadiusPx: Float,
        blurRadiusPx: Float,
    ): BrowserBackdropBlurRegion? {
        if (
            !leftPx.isFinite() || !topPx.isFinite() ||
            !rightPx.isFinite() || !bottomPx.isFinite() ||
            !cornerRadiusPx.isFinite() || !blurRadiusPx.isFinite() ||
            rightPx <= leftPx || bottomPx <= topPx ||
            cornerRadiusPx < 0f || blurRadiusPx <= 0f
        ) {
            return null
        }
        return BrowserBackdropBlurRegion(
            leftInWindowPx = leftPx,
            topInWindowPx = topPx,
            rightInWindowPx = rightPx,
            bottomInWindowPx = bottomPx,
            cornerRadiusPx = cornerRadiusPx,
            blurRadiusPx = blurRadiusPx,
        )
    }

    fun regionInSurface(
        region: BrowserBackdropBlurRegion,
        surfaceLeftInWindowPx: Int,
        surfaceTopInWindowPx: Int,
        surfaceWidthPx: Int,
        surfaceHeightPx: Int,
    ): BrowserSurfaceBackdropBlurRegion? {
        if (surfaceWidthPx <= 0 || surfaceHeightPx <= 0) return null
        val left = (region.leftInWindowPx - surfaceLeftInWindowPx).coerceIn(
            0f,
            surfaceWidthPx.toFloat(),
        )
        val top = (region.topInWindowPx - surfaceTopInWindowPx).coerceIn(
            0f,
            surfaceHeightPx.toFloat(),
        )
        val right = (region.rightInWindowPx - surfaceLeftInWindowPx).coerceIn(
            0f,
            surfaceWidthPx.toFloat(),
        )
        val bottom = (region.bottomInWindowPx - surfaceTopInWindowPx).coerceIn(
            0f,
            surfaceHeightPx.toFloat(),
        )
        if (right <= left || bottom <= top) return null
        return BrowserSurfaceBackdropBlurRegion(
            leftPx = left,
            topPx = top,
            rightPx = right,
            bottomPx = bottom,
            cornerRadiusPx = region.cornerRadiusPx.coerceAtMost(
                minOf(right - left, bottom - top) / 2f,
            ),
            blurRadiusPx = region.blurRadiusPx,
        )
    }

    const val NATIVE_SURFACE_BLUR_MIN_SDK = 37
}
