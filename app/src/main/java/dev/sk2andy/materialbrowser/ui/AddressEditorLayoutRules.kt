package dev.sk2andy.materialbrowser.ui

internal object AddressEditorLayoutRules {
    const val SUGGESTION_GAP_DP = 16f
    const val SUGGESTION_TOP_GAP_DP = 12f
    const val FALLBACK_BOTTOM_PADDING_DP = 108f
    const val FALLBACK_MAX_HEIGHT_DP = 320f

    fun suggestionBottomPaddingDp(
        rootHeightPx: Float,
        bottomBarTopPx: Float,
        density: Float,
    ): Float {
        if (
            !rootHeightPx.isFinite() ||
            !bottomBarTopPx.isFinite() ||
            density <= 0f ||
            bottomBarTopPx !in 0f..rootHeightPx
        ) {
            return FALLBACK_BOTTOM_PADDING_DP
        }
        return (rootHeightPx - bottomBarTopPx) / density + SUGGESTION_GAP_DP
    }

    fun suggestionMaxHeightDp(
        bottomBarTopPx: Float,
        topInsetPx: Float,
        density: Float,
    ): Float {
        if (
            !bottomBarTopPx.isFinite() ||
            !topInsetPx.isFinite() ||
            density <= 0f ||
            bottomBarTopPx <= topInsetPx
        ) {
            return FALLBACK_MAX_HEIGHT_DP
        }
        return (
            (bottomBarTopPx - topInsetPx) / density -
                SUGGESTION_GAP_DP -
                SUGGESTION_TOP_GAP_DP
            ).coerceAtLeast(0f)
    }
}
