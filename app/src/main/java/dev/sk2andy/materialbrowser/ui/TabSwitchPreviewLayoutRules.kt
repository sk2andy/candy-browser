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
        capturedHeightPx: Float? = null,
    ): TabSwitchPreviewLayout {
        val rootHeight = rootHeightPx.coerceAtLeast(0f)
        val topInset = previewTopInsetPx.toFloat().coerceIn(0f, rootHeight)
        val visibleHeight = capturedHeightPx
            ?.takeIf { it.isFinite() && it > 0f }
            ?.coerceAtMost(rootHeight - topInset)
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
