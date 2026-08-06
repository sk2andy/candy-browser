package dev.sk2andy.materialbrowser.data

internal object TabPreviewCaptureRules {
    fun sourceBottomPx(
        viewTopPx: Int,
        viewHeightPx: Int,
        decorHeightPx: Int,
        contentBottomPx: Int?,
    ): Int = minOf(
        viewTopPx + viewHeightPx,
        decorHeightPx,
        contentBottomPx?.takeIf { it > 0 } ?: decorHeightPx,
    )
}
