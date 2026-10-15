package dev.sk2andy.materialbrowser.ui

internal object BrowserMenuBottomSheetMotionRules {
    private const val HEIGHT_FRACTION = 0.85f
    private const val COMPACT_HEIGHT_DP = 480f

    fun isCompactHeight(availableHeightDp: Float): Boolean = availableHeightDp <= COMPACT_HEIGHT_DP

    fun heightFraction(availableHeightDp: Float): Float =
        if (isCompactHeight(availableHeightDp)) 1f else HEIGHT_FRACTION
    private const val HANDLE_HEIGHT_DP = 24f
    private const val COLLAPSED_HEIGHT_DP = 92f
    private const val FADE_START_PROGRESS = 0.35f

    fun translationY(
        progress: Float,
        sheetHeightPx: Float,
        anchorTopPx: Float?,
        sheetTopPx: Float,
        density: Float,
    ): Float {
        val offset = if (anchorTopPx != null) {
            anchorTopPx - sheetTopPx - HANDLE_HEIGHT_DP * density
        } else {
            (sheetHeightPx - COLLAPSED_HEIGHT_DP * density).coerceAtLeast(0f)
        }
        return offset.coerceAtLeast(0f) * (1f - progress.coerceIn(0f, 1f))
    }

    fun dragProgress(progress: Float, deltaPx: Float, collapsedOffsetPx: Float): Float =
        if (collapsedOffsetPx > 0f) {
            (progress - deltaPx / collapsedOffsetPx).coerceIn(0f, 1f)
        } else {
            progress.coerceIn(0f, 1f)
        }

    fun surfaceAlpha(progress: Float): Float {
        val fraction = (progress / FADE_START_PROGRESS).coerceIn(0f, 1f)
        return fraction * fraction * (3f - 2f * fraction)
    }

    fun shouldDismiss(progress: Float, velocityDpPerSecond: Float): Boolean =
        progress <= FADE_START_PROGRESS || (progress < 1f && velocityDpPerSecond > 1_000f)
}
