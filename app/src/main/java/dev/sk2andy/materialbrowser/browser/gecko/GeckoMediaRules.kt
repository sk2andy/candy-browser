package dev.sk2andy.materialbrowser.browser.gecko

internal enum class GeckoAutoplayPermission {
    Audible,
    Inaudible,
    Other,
}

internal enum class GeckoAutoplayDecision {
    Allow,
    Deny,
    Defer,
}

internal object GeckoAutoplayRules {
    fun resolve(
        permission: GeckoAutoplayPermission,
        blocked: Boolean,
    ): GeckoAutoplayDecision = when (permission) {
        GeckoAutoplayPermission.Audible,
        GeckoAutoplayPermission.Inaudible,
        -> if (blocked) GeckoAutoplayDecision.Deny else GeckoAutoplayDecision.Allow
        GeckoAutoplayPermission.Other -> GeckoAutoplayDecision.Defer
    }
}

internal enum class GeckoAutoplayPermissionSyncAction {
    Reload,
    Retry,
    KeepCurrentDocument,
}

internal object GeckoAutoplayPermissionSyncRules {
    const val MAX_ATTEMPTS = 20
    const val RETRY_DELAY_MILLIS = 100L
    const val PROPAGATION_DELAY_MILLIS = 100L

    fun resolve(
        storedValues: Map<GeckoAutoplayPermission, GeckoAutoplayDecision>,
        desiredValue: GeckoAutoplayDecision,
        attempt: Int,
    ): GeckoAutoplayPermissionSyncAction {
        val isConfirmed = storedValues[GeckoAutoplayPermission.Audible] == desiredValue &&
            storedValues[GeckoAutoplayPermission.Inaudible] == desiredValue
        return when {
            isConfirmed -> GeckoAutoplayPermissionSyncAction.Reload
            attempt >= MAX_ATTEMPTS -> GeckoAutoplayPermissionSyncAction.KeepCurrentDocument
            else -> GeckoAutoplayPermissionSyncAction.Retry
        }
    }
}

internal object GeckoPictureInPictureRules {
    fun isFullscreenVideo(state: GeckoMediaSessionState?): Boolean = state?.let { media ->
        media.isActive &&
            media.isFullscreen &&
            media.videoTrackCount > 0 &&
            media.videoWidth > 0 &&
            media.videoHeight > 0
    } == true

    fun isEligible(
        state: GeckoMediaSessionState?,
        isPrivate: Boolean,
        isSelectedTab: Boolean,
    ): Boolean = state?.let { media ->
        !isPrivate &&
            isSelectedTab &&
            media.isPlaying &&
            isFullscreenVideo(media)
    } == true

    fun playbackExpectedDuringTransition(
        currentExpected: Boolean,
        transitionPending: Boolean,
        inPictureInPicture: Boolean,
        mediaIsPlaying: Boolean,
    ): Boolean = if (transitionPending || inPictureInPicture) {
        currentExpected
    } else {
        mediaIsPlaying
    }
}

internal object GeckoMediaSessionRules {
    fun activatedState(): GeckoMediaSessionState = GeckoMediaSessionState(isActive = true)

    fun stoppedState(): GeckoMediaSessionState = GeckoMediaSessionState()
}
