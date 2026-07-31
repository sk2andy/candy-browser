package dev.sk2andy.materialbrowser.ui

internal data class TabSwitchPreviewLayout(
    val topInsetPx: Float,
    val visibleHeightPx: Float,
)

internal object TabSwitchPreviewLayoutRules {
    fun resolve(
        rootHeightPx: Float,
        previewTopInsetPx: Int,
        bottomBarTopPx: Float,
    ): TabSwitchPreviewLayout {
        val rootHeight = rootHeightPx.coerceAtLeast(0f)
        val topInset = previewTopInsetPx.toFloat().coerceIn(0f, rootHeight)
        val bottom = bottomBarTopPx
            .takeIf(Float::isFinite)
            ?.coerceIn(topInset, rootHeight)
            ?: rootHeight
        return TabSwitchPreviewLayout(
            topInsetPx = topInset,
            visibleHeightPx = bottom - topInset,
        )
    }
}
