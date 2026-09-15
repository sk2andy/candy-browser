package dev.sk2andy.materialbrowser.browser

internal data class BrowserViewportRect(
    val leftFraction: Float,
    val topFraction: Float,
    val rightFraction: Float,
    val bottomFraction: Float,
)

internal enum class TextInputOcclusionProbeMode {
    AllEditors,
    FocusedTextInput,
}

internal enum class TextInputOcclusionProbeResult(val wireValue: Int) {
    NoFocusedTextInput(0),
    FocusedTextInputClear(1),
    Occluded(2),
    ;

    companion object {
        fun fromWireValue(value: Int): TextInputOcclusionProbeResult =
            entries.firstOrNull { result -> result.wireValue == value } ?: NoFocusedTextInput
    }
}

internal object AddressBarAutoDockRules {
    private val focusedProbeRetryDelaysMillis = listOf(150L, 200L, 350L, 500L)

    fun shouldProbeForImeState(
        isImeVisible: Boolean,
        browserChromeOwnsIme: Boolean,
    ): Boolean = isImeVisible && !browserChromeOwnsIme

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

    fun isProbeContextCurrent(
        dockingEnabled: Boolean,
        addressBarDocked: Boolean,
        selectedTabMatches: Boolean,
        sessionMatches: Boolean,
        navigationMatches: Boolean,
        urlMatches: Boolean,
        viewportRectMatches: Boolean,
        isPrivatePage: Boolean,
    ): Boolean =
        dockingEnabled &&
        !addressBarDocked &&
        selectedTabMatches &&
        sessionMatches &&
        navigationMatches &&
        urlMatches &&
        viewportRectMatches &&
        !isPrivatePage

    fun focusedProbeRetryDelayMillis(completedRetryCount: Int): Long? =
        focusedProbeRetryDelaysMillis.getOrNull(completedRetryCount)

    fun shouldRetryFocusedProbe(result: TextInputOcclusionProbeResult): Boolean =
        result == TextInputOcclusionProbeResult.FocusedTextInputClear
}
