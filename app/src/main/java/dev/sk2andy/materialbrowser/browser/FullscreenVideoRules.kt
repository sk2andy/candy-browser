package dev.sk2andy.materialbrowser.browser

internal enum class FullscreenVideoPlacement {
    Expanded,
    MiniPlayer,
}

internal data class FullscreenVideoOffset(
    val x: Float,
    val y: Float,
)

internal object FullscreenVideoRules {
    fun hostsSourceInOverlay(
        host: FullscreenVideoHost,
        videoOnlyPresentation: Boolean,
    ): Boolean = !videoOnlyPresentation || host == FullscreenVideoHost.Overlay

    fun placement(
        sessionTabId: String?,
        selectedTabId: String,
        minimizedByUser: Boolean,
        videoOnlyPresentation: Boolean,
    ): FullscreenVideoPlacement? {
        if (sessionTabId == null) return null
        return if (
            videoOnlyPresentation ||
            (sessionTabId == selectedTabId && !minimizedByUser)
        ) {
            FullscreenVideoPlacement.Expanded
        } else {
            FullscreenVideoPlacement.MiniPlayer
        }
    }

    fun keepsWebViewResumed(
        sessionTabId: String?,
        tabId: String,
        isPrivate: Boolean,
    ): Boolean = sessionTabId == tabId && !isPrivate

    fun isPictureInPictureEligible(
        sessionTabId: String?,
        isPrivate: Boolean?,
    ): Boolean = sessionTabId != null && isPrivate == false

    fun clampMiniPlayerOffset(
        proposedX: Float,
        proposedY: Float,
        maxLeftTravel: Float,
        maxUpTravel: Float,
    ): FullscreenVideoOffset = FullscreenVideoOffset(
        x = proposedX.coerceIn(-maxLeftTravel.coerceAtLeast(0f), 0f),
        y = proposedY.coerceIn(-maxUpTravel.coerceAtLeast(0f), 0f),
    )

    fun nextMiniPlayerAnchor(
        current: FullscreenVideoOffset,
        maxLeftTravel: Float,
        maxUpTravel: Float,
    ): FullscreenVideoOffset {
        val left = maxLeftTravel.coerceAtLeast(0f)
        val up = maxUpTravel.coerceAtLeast(0f)
        val isRight = current.x > -left / 2f
        val isBottom = current.y > -up / 2f
        return when {
            isRight && isBottom -> FullscreenVideoOffset(x = -left, y = 0f)
            !isRight && isBottom -> FullscreenVideoOffset(x = -left, y = -up)
            !isRight && !isBottom -> FullscreenVideoOffset(x = 0f, y = -up)
            else -> FullscreenVideoOffset(x = 0f, y = 0f)
        }
    }
}
