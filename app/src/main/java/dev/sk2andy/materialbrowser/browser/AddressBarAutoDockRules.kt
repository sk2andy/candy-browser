package dev.sk2andy.materialbrowser.browser

internal data class BrowserViewportRect(
    val leftFraction: Float,
    val topFraction: Float,
    val rightFraction: Float,
    val bottomFraction: Float,
)

internal object AddressBarAutoDockRules {
    fun shouldProbeAfterImeChange(
        wasImeVisible: Boolean,
        isImeVisible: Boolean,
        browserChromeOwnsIme: Boolean,
    ): Boolean = !browserChromeOwnsIme && !wasImeVisible && isImeVisible

    fun viewportRect(
        leftPx: Float,
        topPx: Float,
        rightPx: Float,
        bottomPx: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
    ): BrowserViewportRect? {
        if (
            !leftPx.isFinite() || !topPx.isFinite() ||
            !rightPx.isFinite() || !bottomPx.isFinite() ||
            !viewportWidthPx.isFinite() || !viewportHeightPx.isFinite() ||
            viewportWidthPx <= 0f || viewportHeightPx <= 0f
        ) {
            return null
        }
        val left = (leftPx / viewportWidthPx).coerceIn(0f, 1f)
        val top = (topPx / viewportHeightPx).coerceIn(0f, 1f)
        val right = (rightPx / viewportWidthPx).coerceIn(0f, 1f)
        val bottom = (bottomPx / viewportHeightPx).coerceIn(0f, 1f)
        if (right <= left || bottom <= top) return null
        return BrowserViewportRect(
            leftFraction = left,
            topFraction = top,
            rightFraction = right,
            bottomFraction = bottom,
        )
    }

    fun shouldProbe(
        dockingEnabled: Boolean,
        addressBarDocked: Boolean,
        selectedTabMatches: Boolean,
        isHttpPage: Boolean,
        isPrivatePage: Boolean,
        hasViewportRect: Boolean,
    ): Boolean = dockingEnabled &&
        !addressBarDocked &&
        selectedTabMatches &&
        isHttpPage &&
        !isPrivatePage &&
        hasViewportRect

    fun shouldApplyResult(
        occluded: Boolean,
        dockingEnabled: Boolean,
        addressBarDocked: Boolean,
        selectedTabMatches: Boolean,
        sessionMatches: Boolean,
        navigationMatches: Boolean,
        urlMatches: Boolean,
        viewportRectMatches: Boolean,
        isPrivatePage: Boolean,
    ): Boolean = occluded &&
        dockingEnabled &&
        !addressBarDocked &&
        selectedTabMatches &&
        sessionMatches &&
        navigationMatches &&
        urlMatches &&
        viewportRectMatches &&
        !isPrivatePage
}
