package dev.sk2andy.materialbrowser.shared.ui

import dev.sk2andy.materialbrowser.shared.browser.BrowserTabOverviewLayoutRules

object TabOverviewGridRules {
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
        preferredColumns: Int = BrowserTabOverviewLayoutRules.AUTO_GRID_COLUMNS,
    ): Layout = BrowserTabOverviewLayoutRules.grid(
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        preferredColumns = preferredColumns,
    ).let { layout ->
        Layout(
            columnCount = layout.columnCount,
            previewAspectRatio = layout.previewAspectRatio,
            cardWidth = layout.cardWidth,
            columnPitch = layout.columnPitch,
            rowPitch = layout.rowPitch,
            contentPadding = layout.contentPadding,
            itemSpacing = layout.itemSpacing,
        )
    }

    fun usesDenseTitle(cardWidth: Float): Boolean = cardWidth < 140f

    fun leadingEmptyCellCount(
        tabCount: Int,
        columnCount: Int,
        startsAtBottom: Boolean,
    ): Int {
        if (!startsAtBottom || tabCount <= 0 || columnCount <= 0) return 0
        val remainder = tabCount % columnCount
        return if (remainder == 0) 0 else columnCount - remainder
    }
}
