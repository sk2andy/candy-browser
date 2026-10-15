package dev.sk2andy.materialbrowser.browser

import android.content.Context

/**
 * Activity-side bridge to the process-owned [BrowserMediaPlaybackService].
 *
 * This deliberately owns no Android session. The service is the sole owner of the Media3
 * session and notification, while this bridge supplies current Gecko state and command routing.
 */
internal class BrowserMediaSystemSession(
    context: Context,
    private val onCommand: (GeckoMediaPlaybackOwner, GeckoMediaPlaybackCommand) -> Unit,
    private val mayStartService: () -> Boolean,
) {
    private val appContext = context.applicationContext

    fun publish(publication: GeckoMediaPlaybackPublication?) {
        if (shouldKeepOtherSession(publication)) return
        BrowserMediaLifecycleTrace.record(
            source = "BrowserMediaSystemSession",
            action = "publish",
            publication = publication,
        )
        BrowserMediaPlaybackService.publish(
            context = appContext,
            publication = publication,
            onCommand = onCommand,
            mayStartService = mayStartService(),
        )
    }

    fun invalidate(owner: GeckoMediaPlaybackOwner) {
        if (isOwnedByAnotherSession()) return
        BrowserMediaLifecycleTrace.record(
            source = "BrowserMediaSystemSession",
            action = "invalidate",
            owner = owner,
        )
        BrowserMediaPlaybackService.invalidate(appContext, owner)
    }

    fun restoreAfterPictureInPicture(publication: GeckoMediaPlaybackPublication?) {
        if (shouldKeepOtherSession(publication)) return
        BrowserMediaLifecycleTrace.record(
            source = "BrowserMediaSystemSession",
            action = "restore-picture-in-picture",
            publication = publication,
        )
        BrowserMediaPlaybackService.restoreAfterPictureInPicture(
            context = appContext,
            publication = publication,
            onCommand = onCommand,
            mayStartService = mayStartService(),
        )
    }

    fun replacePublication(publication: GeckoMediaPlaybackPublication?) {
        if (shouldKeepOtherSession(publication)) return
        BrowserMediaLifecycleTrace.record(
            source = "BrowserMediaSystemSession",
            action = "replace-publication",
            publication = publication,
        )
        BrowserMediaPlaybackService.replacePublication(
            context = appContext,
            publication = publication,
            onCommand = onCommand,
            mayStartService = mayStartService(),
        )
    }

    fun clearForPrivateContext() {
        if (isOwnedByAnotherSession()) return
        BrowserMediaLifecycleTrace.record(
            source = "BrowserMediaSystemSession",
            action = "clear-private-context",
        )
        BrowserMediaPlaybackService.clearForPrivateContext(appContext)
    }

    fun clearForProfileLock() {
        if (isOwnedByAnotherSession()) return
        BrowserMediaLifecycleTrace.record(
            source = "BrowserMediaSystemSession",
            action = "clear-profile-lock",
        )
        BrowserMediaPlaybackService.clearForProfileLock(appContext)
    }

    fun clearForAppDataTransfer() {
        BrowserMediaLifecycleTrace.record(
            source = "BrowserMediaSystemSession",
            action = "clear-app-data-transfer",
        )
        BrowserMediaPlaybackService.clearForAppDataTransfer(appContext)
    }

    fun stopAndClear() {
        if (isOwnedByAnotherSession()) return
        BrowserMediaLifecycleTrace.record(
            source = "BrowserMediaSystemSession",
            action = "stop-and-clear",
        )
        BrowserMediaPlaybackService.stopAndClear(appContext)
    }

    private fun isOwnedByAnotherSession(): Boolean =
        BrowserMediaPlaybackService.isOwnedByAnotherSession(onCommand)

    private fun shouldKeepOtherSession(publication: GeckoMediaPlaybackPublication?): Boolean {
        if (!isOwnedByAnotherSession()) return false
        val safePublication = publication ?: return true
        val snapshot = GeckoMediaPlaybackRules.publicationOrNull(safePublication) ?: return true
        return !snapshot.isPlaying && !safePublication.isPictureInPictureOwner
    }

    fun release() {
        if (isOwnedByAnotherSession()) return
        BrowserMediaLifecycleTrace.record(
            source = "BrowserMediaSystemSession",
            action = "release",
        )
        BrowserMediaPlaybackService.release(appContext)
    }
}
