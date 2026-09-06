package dev.sk2andy.materialbrowser.shared.browser

data class BrowserTabHeroCardLayout(
    val width: Float,
    val aspectRatio: Float,
)

data class BrowserTabPreviewCropLayout(
    val sourceTop: Float,
    val sourceHeight: Float,
)

data class BrowserTabGridLayout(
    val columnCount: Int,
    val previewAspectRatio: Float,
    val cardWidth: Float,
    val columnPitch: Float,
    val rowPitch: Float,
    val contentPadding: Float,
    val itemSpacing: Float,
)

data class BrowserTabListLayout(
    val rowHeight: Float,
    val horizontalPadding: Float,
    val itemSpacing: Float,
    val cornerRadius: Float,
)

/** Exact cross-platform geometry for Candy's Hero, Grid and List tab switchers. */
object BrowserTabOverviewLayoutRules {
    fun heroCard(
        viewportWidth: Float,
        viewportHeight: Float,
    ): BrowserTabHeroCardLayout {
        val safeWidth = viewportWidth.takeIf { it.isFinite() && it > 0f } ?: 0f
        val safeHeight = viewportHeight.takeIf { it.isFinite() && it > 0f } ?: 0f
        if (safeWidth <= safeHeight) {
            return BrowserTabHeroCardLayout(
                width = (safeWidth * PORTRAIT_HERO_WIDTH_FRACTION)
                    .coerceIn(PORTRAIT_HERO_MIN_WIDTH, PORTRAIT_HERO_MAX_WIDTH)
                    .coerceAtMost(safeWidth),
                aspectRatio = PORTRAIT_HERO_ASPECT_RATIO,
            )
        }

        val widthFromViewport = (safeWidth * LANDSCAPE_HERO_WIDTH_FRACTION)
            .coerceIn(LANDSCAPE_HERO_MIN_WIDTH, LANDSCAPE_HERO_MAX_WIDTH)
        val widthFromHeight = safeHeight * LANDSCAPE_HERO_HEIGHT_FRACTION *
            LANDSCAPE_PREVIEW_ASPECT_RATIO
        return BrowserTabHeroCardLayout(
            width = minOf(widthFromViewport, widthFromHeight, safeWidth),
            aspectRatio = LANDSCAPE_PREVIEW_ASPECT_RATIO,
        )
    }

    fun previewCrop(
        rootWidth: Float,
        rootHeight: Float,
        targetWidth: Float,
        targetHeight: Float,
        cropTopFraction: Float,
    ): BrowserTabPreviewCropLayout {
        val targetScale = (targetWidth / rootWidth).coerceAtLeast(MINIMUM_SCALE)
        val sourceHeight = targetHeight / targetScale
        return BrowserTabPreviewCropLayout(
            sourceTop = (rootHeight - sourceHeight) * cropTopFraction,
            sourceHeight = sourceHeight,
        )
    }

    fun interpolatePreviewCrop(
        startTop: Float,
        startHeight: Float,
        target: BrowserTabPreviewCropLayout,
        progress: Float,
    ): BrowserTabPreviewCropLayout {
        val fraction = progress.coerceIn(0f, 1f)
        return BrowserTabPreviewCropLayout(
            sourceTop = startTop + (target.sourceTop - startTop) * fraction,
            sourceHeight = startHeight + (target.sourceHeight - startHeight) * fraction,
        )
    }

    fun grid(
        viewportWidth: Float,
        viewportHeight: Float,
    ): BrowserTabGridLayout {
        val safeWidth = viewportWidth.takeIf { it.isFinite() && it > 0f } ?: 0f
        val safeHeight = viewportHeight.takeIf { it.isFinite() && it > 0f } ?: 0f
        val isLandscape = safeWidth > safeHeight
        val columnCount = if (isLandscape && safeWidth >= TABLET_WIDTH) {
            TABLET_LANDSCAPE_COLUMNS
        } else {
            DEFAULT_COLUMNS
        }
        val previewAspectRatio = if (isLandscape) {
            LANDSCAPE_PREVIEW_ASPECT_RATIO
        } else {
            PORTRAIT_GRID_PREVIEW_ASPECT_RATIO
        }
        val cardWidth = (
            safeWidth - GRID_CONTENT_PADDING * 2f - GRID_ITEM_SPACING * (columnCount - 1)
        ).coerceAtLeast(0f) / columnCount
        return BrowserTabGridLayout(
            columnCount = columnCount,
            previewAspectRatio = previewAspectRatio,
            cardWidth = cardWidth,
            columnPitch = cardWidth + GRID_ITEM_SPACING,
            rowPitch = cardWidth / previewAspectRatio + GRID_ITEM_SPACING,
            contentPadding = GRID_CONTENT_PADDING,
            itemSpacing = GRID_ITEM_SPACING,
        )
    }

    fun list(): BrowserTabListLayout = BrowserTabListLayout(
        rowHeight = LIST_ROW_HEIGHT,
        horizontalPadding = LIST_HORIZONTAL_PADDING,
        itemSpacing = LIST_ITEM_SPACING,
        cornerRadius = LIST_CORNER_RADIUS,
    )

    private const val MINIMUM_SCALE = 0.01f
    private const val PORTRAIT_HERO_WIDTH_FRACTION = 0.74f
    private const val PORTRAIT_HERO_MIN_WIDTH = 244f
    private const val PORTRAIT_HERO_MAX_WIDTH = 360f
    private const val PORTRAIT_HERO_ASPECT_RATIO = 0.45f
    private const val LANDSCAPE_HERO_WIDTH_FRACTION = 0.68f
    private const val LANDSCAPE_HERO_HEIGHT_FRACTION = 0.66f
    private const val LANDSCAPE_HERO_MIN_WIDTH = 360f
    private const val LANDSCAPE_HERO_MAX_WIDTH = 720f
    private const val LANDSCAPE_PREVIEW_ASPECT_RATIO = 1.6f
    private const val PORTRAIT_GRID_PREVIEW_ASPECT_RATIO = 0.72f
    private const val TABLET_WIDTH = 900f
    private const val TABLET_LANDSCAPE_COLUMNS = 3
    private const val DEFAULT_COLUMNS = 2
    private const val GRID_CONTENT_PADDING = 16f
    private const val GRID_ITEM_SPACING = 12f
    private const val LIST_ROW_HEIGHT = 64f
    private const val LIST_HORIZONTAL_PADDING = 16f
    private const val LIST_ITEM_SPACING = 8f
    private const val LIST_CORNER_RADIUS = 18f
}
