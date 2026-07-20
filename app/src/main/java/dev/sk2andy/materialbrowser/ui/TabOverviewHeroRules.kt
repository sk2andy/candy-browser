package dev.sk2andy.materialbrowser.ui

internal object TabOverviewHeroRules {
    fun canStart(hasTargetBounds: Boolean): Boolean = hasTargetBounds

    fun isHeroVisible(hasTargetBounds: Boolean, progress: Float): Boolean =
        hasTargetBounds && progress < COMPLETION_THRESHOLD

    fun isCardVisible(
        isInitialCard: Boolean,
        progress: Float,
        isExitTarget: Boolean = false,
    ): Boolean = !isExitTarget && (!isInitialCard || progress >= COMPLETION_THRESHOLD)

    fun backgroundAlpha(entryProgress: Float, isExiting: Boolean): Float =
        if (isExiting) 1f else entryProgress

    fun contentAlpha(exitProgress: Float, isExiting: Boolean): Float =
        if (isExiting) {
            (1f - exitProgress * EXIT_CONTENT_FADE_MULTIPLIER).coerceIn(0f, 1f)
        } else {
            1f
        }

    fun neighborAlpha(entryProgress: Float): Float =
        ((entryProgress - NEIGHBOR_ENTRY_START) / (1f - NEIGHBOR_ENTRY_START))
            .coerceIn(0f, 1f)

    private const val COMPLETION_THRESHOLD = 0.995f
    private const val EXIT_CONTENT_FADE_MULTIPLIER = 4f
    private const val NEIGHBOR_ENTRY_START = 0.55f
}
