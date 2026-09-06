package dev.sk2andy.materialbrowser.ui

internal object TabStackFolderLayoutRules {
    const val CONTENT_PADDING = 18f

    data class Layout(
        val dialogMaxWidth: Float,
        val gridColumnCount: Int,
        val coverflowPageWidth: Float,
        val coverflowContentPadding: Float,
    )

    fun layout(availableWidth: Float): Layout {
        val safeWidth = availableWidth.takeIf { it.isFinite() && it > 0f } ?: 0f
        val dialogMaxWidth = safeWidth.coerceAtMost(MAX_DIALOG_WIDTH)
        val contentWidth = (dialogMaxWidth - CONTENT_PADDING * 2f).coerceAtLeast(0f)
        val coverflowContentPadding = when {
            contentWidth <= 0f -> 0f
            contentWidth < COMPACT_PADDING_THRESHOLD -> COMPACT_CONTENT_PADDING
            else -> DEFAULT_CONTENT_PADDING
        }
        return Layout(
            dialogMaxWidth = dialogMaxWidth,
            gridColumnCount = if (contentWidth >= TABLET_CONTENT_WIDTH) {
                TABLET_GRID_COLUMNS
            } else {
                COMPACT_GRID_COLUMNS
            },
            coverflowPageWidth = (contentWidth - coverflowContentPadding * 2f)
                .coerceIn(0f, MAX_COVERFLOW_PAGE_WIDTH),
            coverflowContentPadding = coverflowContentPadding,
        )
    }

    private const val MAX_DIALOG_WIDTH = 720f
    private const val TABLET_CONTENT_WIDTH = 600f
    private const val TABLET_GRID_COLUMNS = 3
    private const val COMPACT_GRID_COLUMNS = 2
    private const val MAX_COVERFLOW_PAGE_WIDTH = 232f
    private const val COMPACT_PADDING_THRESHOLD = 320f
    private const val COMPACT_CONTENT_PADDING = 16f
    private const val DEFAULT_CONTENT_PADDING = 44f
}
