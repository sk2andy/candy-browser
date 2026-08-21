package dev.sk2andy.materialbrowser.ui

internal object TabOverviewGridRules {
    data class Layout(
        val columnCount: Int,
        val previewAspectRatio: Float,
        val cardWidth: Float,
        val columnPitch: Float,
        val rowPitch: Float,
        val contentPadding: Float,
        val itemSpacing: Float,
    )

    fun layout(
        viewportWidth: Float,
        viewportHeight: Float,
    ): Layout {
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
            PORTRAIT_PREVIEW_ASPECT_RATIO
        }
        val cardWidth = (
            safeWidth - CONTENT_PADDING * 2f - ITEM_SPACING * (columnCount - 1)
        ).coerceAtLeast(0f) / columnCount
        return Layout(
            columnCount = columnCount,
            previewAspectRatio = previewAspectRatio,
            cardWidth = cardWidth,
            columnPitch = cardWidth + ITEM_SPACING,
            rowPitch = CARD_HEADER_HEIGHT + cardWidth / previewAspectRatio + ITEM_SPACING,
            contentPadding = CONTENT_PADDING,
            itemSpacing = ITEM_SPACING,
        )
    }

    private const val TABLET_WIDTH = 900f
    private const val TABLET_LANDSCAPE_COLUMNS = 3
    private const val DEFAULT_COLUMNS = 2
    private const val LANDSCAPE_PREVIEW_ASPECT_RATIO = 1.6f
    private const val PORTRAIT_PREVIEW_ASPECT_RATIO = 0.72f
    private const val CONTENT_PADDING = 16f
    private const val ITEM_SPACING = 12f
    private const val CARD_HEADER_HEIGHT = 48f
}
