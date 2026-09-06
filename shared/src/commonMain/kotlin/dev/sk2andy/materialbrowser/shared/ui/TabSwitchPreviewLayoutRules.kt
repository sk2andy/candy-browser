package dev.sk2andy.materialbrowser.shared.ui

data class TabSwitchPreviewLayout(
    val topInsetPx: Float,
    val visibleHeightPx: Float,
)

object TabSwitchPreviewLayoutRules {
    fun resolve(
        rootHeightPx: Float,
        previewTopInsetPx: Int,
        bottomBarTopPx: Float,
        capturedHeightPx: Float? = null,
    ): TabSwitchPreviewLayout {
        val rootHeight = rootHeightPx.takeIf { it.isFinite() && it > 0f } ?: 0f
        val topInset = previewTopInsetPx.toFloat().coerceIn(0f, rootHeight)
        val availableHeight = (rootHeight - topInset).coerceAtLeast(0f)
        val visibleHeight = capturedHeightPx
            ?.takeIf { it.isFinite() && it > 0f }
            ?.coerceAtMost(availableHeight)
            ?: run {
                val bottom = bottomBarTopPx
                    .takeIf(Float::isFinite)
                    ?.coerceIn(topInset, rootHeight)
                    ?: rootHeight
                bottom - topInset
            }
        return TabSwitchPreviewLayout(
            topInsetPx = topInset,
            visibleHeightPx = visibleHeight,
        )
    }
}
