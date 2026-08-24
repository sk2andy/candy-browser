package dev.sk2andy.materialbrowser.ui

internal object TabOverviewHeroRules {
    const val ENTRY_DURATION_MILLIS = 160

    data class CoverflowCardLayout(
        val width: Float,
        val aspectRatio: Float,
    )

    data class CardPreviewLayout(
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

    fun isGridPreviewVisible(
        isInitialCard: Boolean,
        isCardVisible: Boolean,
        isHeroVisible: Boolean,
    ): Boolean = isCardVisible && (!isInitialCard || !isHeroVisible)

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

    fun coverflowCardLayout(
        viewportWidth: Float,
        viewportHeight: Float,
    ): CoverflowCardLayout {
        val safeWidth = viewportWidth.takeIf { it.isFinite() && it > 0f } ?: 0f
        val safeHeight = viewportHeight.takeIf { it.isFinite() && it > 0f } ?: 0f
        if (safeWidth <= safeHeight) {
            return CoverflowCardLayout(
                width = (safeWidth * PORTRAIT_CARD_WIDTH_FRACTION)
                    .coerceIn(PORTRAIT_CARD_MIN_WIDTH, PORTRAIT_CARD_MAX_WIDTH)
                    .coerceAtMost(safeWidth),
                aspectRatio = PORTRAIT_CARD_ASPECT_RATIO,
            )
        }

        val widthFromViewport = (safeWidth * LANDSCAPE_CARD_WIDTH_FRACTION)
            .coerceIn(LANDSCAPE_CARD_MIN_WIDTH, LANDSCAPE_CARD_MAX_WIDTH)
        val widthFromHeight = safeHeight * LANDSCAPE_CARD_HEIGHT_FRACTION *
            LANDSCAPE_CARD_ASPECT_RATIO
        return CoverflowCardLayout(
            width = minOf(widthFromViewport, widthFromHeight, safeWidth),
            aspectRatio = LANDSCAPE_CARD_ASPECT_RATIO,
        )
    }

    fun cardPreviewLayout(
        rootWidthPx: Float,
        rootHeightPx: Float,
        targetWidthPx: Float,
        targetHeightPx: Float,
        cropTopFraction: Float,
    ): CardPreviewLayout {
        val targetScale = (targetWidthPx / rootWidthPx).coerceAtLeast(0.01f)
        val sourceHeightPx = targetHeightPx / targetScale
        return CardPreviewLayout(
            sourceTopPx = (rootHeightPx - sourceHeightPx) * cropTopFraction,
            sourceHeightPx = sourceHeightPx,
        )
    }

    fun cardPreviewFrame(
        startTopPx: Float,
        startHeightPx: Float,
        targetLayout: CardPreviewLayout,
        targetFraction: Float,
    ): CardPreviewLayout {
        val fraction = targetFraction.coerceIn(0f, 1f)
        return CardPreviewLayout(
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
    private const val PORTRAIT_CARD_WIDTH_FRACTION = 0.74f
    private const val PORTRAIT_CARD_MIN_WIDTH = 244f
    private const val PORTRAIT_CARD_MAX_WIDTH = 360f
    private const val PORTRAIT_CARD_ASPECT_RATIO = 0.45f
    private const val LANDSCAPE_CARD_WIDTH_FRACTION = 0.68f
    private const val LANDSCAPE_CARD_HEIGHT_FRACTION = 0.66f
    private const val LANDSCAPE_CARD_MIN_WIDTH = 360f
    private const val LANDSCAPE_CARD_MAX_WIDTH = 720f
    private const val LANDSCAPE_CARD_ASPECT_RATIO = 1.6f
}
