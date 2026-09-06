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
    ): Layout = BrowserTabOverviewLayoutRules.grid(
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
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
}
