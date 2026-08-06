package dev.sk2andy.materialbrowser.ui

internal object AddressEditorLayoutRules {
    const val SUGGESTION_GAP_DP = 16f
    const val FALLBACK_BOTTOM_PADDING_DP = 108f

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
}
