package dev.sk2andy.materialbrowser.ui

internal object TabOverviewHeroRules {
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

    fun coverflowPreviewAlpha(targetFraction: Float): Float =
        ((targetFraction - COVERFLOW_PREVIEW_START) / (1f - COVERFLOW_PREVIEW_START))
            .coerceIn(0f, 1f)

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

    private const val COMPLETION_THRESHOLD = 0.995f
    private const val EXIT_CONTENT_FADE_MULTIPLIER = 4f
    private const val NEIGHBOR_ENTRY_START = 0.55f
    private const val COMPACT_CHROME_START = 0.62f
    private const val COVERFLOW_PREVIEW_START = 0.82f
    private const val INCOGNITO_VEIL_END = 0.24f
}
