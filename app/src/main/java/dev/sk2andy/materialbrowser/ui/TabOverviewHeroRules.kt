package dev.sk2andy.materialbrowser.ui

internal object TabOverviewHeroRules {
    const val ENTRY_DURATION_MILLIS = 160

    data class CoverflowPreviewLayout(
        val sourceTopPx: Float,
        val sourceHeightPx: Float,
    )

    fun canStart(hasTargetBounds: Boolean): Boolean = hasTargetBounds

    fun isHeroVisible(hasTargetBounds: Boolean, progress: Float): Boolean =
        hasTargetBounds && progress < COMPLETION_THRESHOLD

    fun isCardVisible(
        isInitialCard: Boolean,
        progress: Float,
        isExitTarget: Boolean = false,
    ): Boolean = !isExitTarget && (!isInitialCard || progress >= COMPLETION_THRESHOLD)

    fun backgroundAlpha(entryProgress: Float, isExiting: Boolean): Float =
        if (isExiting) 1f else entryProgress

    fun contentAlpha(exitProgress: Float, isExiting: Boolean): Float =
        if (isExiting) {
            (1f - exitProgress * EXIT_CONTENT_FADE_MULTIPLIER).coerceIn(0f, 1f)
        } else {
            1f
        }

    fun neighborAlpha(entryProgress: Float): Float =
        ((entryProgress - NEIGHBOR_ENTRY_START) / (1f - NEIGHBOR_ENTRY_START))
            .coerceIn(0f, 1f)

    fun compactChromeAlpha(targetFraction: Float): Float =
        ((targetFraction - COMPACT_CHROME_START) / (1f - COMPACT_CHROME_START))
            .coerceIn(0f, 1f)

    fun blankFavoritesAlpha(targetFraction: Float): Float =
        (1f - (targetFraction - BLANK_FAVORITES_FADE_START) /
            (BLANK_FAVORITES_FADE_END - BLANK_FAVORITES_FADE_START))
            .coerceIn(0f, 1f)

    fun blankPreviewSourceExtentPx(
        rootViewExtentPx: Int,
        configurationExtentPx: Float,
    ): Float = rootViewExtentPx.takeIf { it > 0 }?.toFloat() ?: configurationExtentPx

    fun incognitoVeilAlpha(entryProgress: Float): Float =
        (entryProgress.coerceIn(0f, 1f) / INCOGNITO_VEIL_END).coerceIn(0f, 1f)

    fun coverflowPreviewLayout(
        rootWidthPx: Float,
        rootHeightPx: Float,
        targetWidthPx: Float,
        targetHeightPx: Float,
        cropTopFraction: Float,
    ): CoverflowPreviewLayout {
        val targetScale = (targetWidthPx / rootWidthPx).coerceAtLeast(0.01f)
        val sourceHeightPx = targetHeightPx / targetScale
        return CoverflowPreviewLayout(
            sourceTopPx = (rootHeightPx - sourceHeightPx) * cropTopFraction,
            sourceHeightPx = sourceHeightPx,
        )
    }

    fun coverflowPreviewFrame(
        startTopPx: Float,
        startHeightPx: Float,
        targetLayout: CoverflowPreviewLayout,
        targetFraction: Float,
    ): CoverflowPreviewLayout {
        val fraction = targetFraction.coerceIn(0f, 1f)
        return CoverflowPreviewLayout(
            sourceTopPx = startTopPx +
                (targetLayout.sourceTopPx - startTopPx) * fraction,
            sourceHeightPx = startHeightPx +
                (targetLayout.sourceHeightPx - startHeightPx) * fraction,
        )
    }

    private const val COMPLETION_THRESHOLD = 0.995f
    private const val EXIT_CONTENT_FADE_MULTIPLIER = 4f
    private const val NEIGHBOR_ENTRY_START = 0.55f
    private const val COMPACT_CHROME_START = 0.62f
    private const val BLANK_FAVORITES_FADE_START = 0.35f
    private const val BLANK_FAVORITES_FADE_END = 0.78f
    private const val INCOGNITO_VEIL_END = 0.24f
}
