package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.shared.browser.BrowserTabOverviewLayoutRules
import dev.sk2andy.materialbrowser.shared.browser.BrowserTabPreviewCropLayout

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
    ): CoverflowCardLayout = BrowserTabOverviewLayoutRules.heroCard(
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
    ).let { layout ->
        CoverflowCardLayout(width = layout.width, aspectRatio = layout.aspectRatio)
    }

    fun cardPreviewLayout(
        rootWidthPx: Float,
        rootHeightPx: Float,
        targetWidthPx: Float,
        targetHeightPx: Float,
        cropTopFraction: Float,
    ): CardPreviewLayout = BrowserTabOverviewLayoutRules.previewCrop(
        rootWidth = rootWidthPx,
        rootHeight = rootHeightPx,
        targetWidth = targetWidthPx,
        targetHeight = targetHeightPx,
        cropTopFraction = cropTopFraction,
    ).let { layout ->
        CardPreviewLayout(
            sourceTopPx = layout.sourceTop,
            sourceHeightPx = layout.sourceHeight,
        )
    }

    fun cardPreviewFrame(
        startTopPx: Float,
        startHeightPx: Float,
        targetLayout: CardPreviewLayout,
        targetFraction: Float,
    ): CardPreviewLayout = BrowserTabOverviewLayoutRules.interpolatePreviewCrop(
        startTop = startTopPx,
        startHeight = startHeightPx,
        target = BrowserTabPreviewCropLayout(
            sourceTop = targetLayout.sourceTopPx,
            sourceHeight = targetLayout.sourceHeightPx,
        ),
        progress = targetFraction,
    ).let { layout ->
        CardPreviewLayout(
            sourceTopPx = layout.sourceTop,
            sourceHeightPx = layout.sourceHeight,
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
