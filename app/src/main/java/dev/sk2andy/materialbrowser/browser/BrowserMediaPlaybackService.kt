package dev.sk2andy.materialbrowser.browser

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Looper
import android.os.Process
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommands
import com.google.common.collect.ImmutableList
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R

/** Process-local authorization rule for Android media controllers. */
internal object BrowserMediaControllerAuthRules {
    fun accepts(
        ownPackageName: String,
        controllerPackageName: String,
        controllerUid: Int,
        isTrustedSystemController: Boolean,
        isMediaNotificationController: Boolean,
    ): Boolean = controllerPackageName == ownPackageName ||
        controllerUid == Process.SYSTEM_UID ||
        isTrustedSystemController ||
        isMediaNotificationController
}

/**
 * The only Android system-media owner. Gecko remains the decoder: [GeckoMedia3Player] exposes
 * its state and transports commands back through the current, memory-only owner identity.
 */
@OptIn(markerClass = [UnstableApi::class])
class BrowserMediaPlaybackService : MediaSessionService() {
    private lateinit var player: GeckoMedia3Player
    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()
        player = GeckoMedia3Player(Looper.getMainLooper(), BrowserMediaServiceRegistry::dispatch)
        mediaSession = MediaSession.Builder(this, player)
            .setId(SESSION_ID)
            .setSessionActivity(openBrowserPendingIntent())
            .setCallback(MediaSessionCallback())
            .build()
        setMediaNotificationProvider(mediaNotificationProvider())
        addSession(mediaSession)
        BrowserMediaServiceRegistry.attach(this)
        trace("lifecycle:on-create")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        trace("lifecycle:on-start-command")
        BrowserMediaServiceRegistry.applyPending(this)
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        trace("lifecycle:on-task-removed")
        BrowserMediaServiceRegistry.stopAndClear()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        trace("lifecycle:on-destroy")
        BrowserMediaServiceRegistry.detachAndClear(this)
        removeSession(mediaSession)
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    internal fun publish(publication: GeckoMediaPlaybackPublication?) {
        player.publish(publication)
        trace("publish", publication)
        stopIfIdle()
    }

    internal fun invalidate(owner: GeckoMediaPlaybackOwner) {
        player.invalidate(owner)
        trace("invalidate", owner = owner)
        stopIfIdle()
    }

    internal fun restoreAfterPictureInPicture(publication: GeckoMediaPlaybackPublication?) {
        replacePublication(publication)
    }

    internal fun replacePublication(publication: GeckoMediaPlaybackPublication?) {
        player.replacePublication(publication)
        trace("replace-publication", publication)
        stopIfIdle()
    }

    internal fun clearForPrivateContext() = clearWith(
        action = "clear-private-context",
        clear = player::clearForPrivateContext,
    )

    internal fun clearForProfileLock() = clearWith(
        action = "clear-profile-lock",
        clear = player::clearForProfileLock,
    )

    internal fun clearForAppDataTransfer() = clearWith(
        action = "clear-app-data-transfer",
        clear = player::clearForAppDataTransfer,
    )

    internal fun clearPlayback() = clearWith(
        action = "clear-playback",
        clear = { player.publish(null) },
    )

    private fun clearWith(action: String, clear: () -> Unit) {
        clear()
        trace(action)
        stopIfIdle()
    }

    private fun stopIfIdle() {
        // SimpleBasePlayer delivers invalidateState asynchronously. Checking playbackState here
        // can still observe the initial IDLE immediately after a valid Gecko publication and
        // tear down the service before Media3 posts the playing session notification.
        if (!player.hasPublishedMedia()) {
            trace("stop-if-idle:stop-self")
            stopSelf()
        } else {
            trace("stop-if-idle:retain")
        }
    }

    private fun trace(
        action: String,
        publication: GeckoMediaPlaybackPublication? = null,
        owner: GeckoMediaPlaybackOwner? = null,
    ) {
        BrowserMediaLifecycleTrace.record(
            source = "BrowserMediaPlaybackService",
            action = action,
            publication = publication,
            owner = owner,
            serviceInstanceId = System.identityHashCode(this),
            hasPublishedMedia = if (::player.isInitialized) player.hasPublishedMedia() else null,
        )
    }

    private fun openBrowserPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        FOREGROUND_NOTIFICATION_ID,
        Intent(this, MainActivity::class.java)
            .setAction(ACTION_OPEN_BROWSER_MEDIA)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Delegates to Media3's provider while retaining only non-content creation diagnostics. */
    private fun mediaNotificationProvider(): MediaNotification.Provider {
        val delegate = DefaultMediaNotificationProvider(
            this,
            DefaultMediaNotificationProvider.NotificationIdProvider {
                FOREGROUND_NOTIFICATION_ID
            },
            CHANNEL_ID,
            R.string.media_notification_channel_name,
        ).also { provider ->
            provider.setSmallIcon(R.drawable.ic_media_playback)
        }
        return object : MediaNotification.Provider {
            override fun createNotification(
                session: MediaSession,
                customLayout: ImmutableList<CommandButton>,
                actionFactory: MediaNotification.ActionFactory,
                callback: MediaNotification.Provider.Callback,
            ): MediaNotification = delegate.createNotification(
                session,
                customLayout,
                actionFactory,
                callback,
            ).also { notification ->
                trace(
                    "notification-provider:created:id=${notification.notificationId}:" +
                        "playing=${player.isPlaying}",
                )
            }

            override fun handleCustomCommand(
                session: MediaSession,
                action: String,
                extras: Bundle,
            ): Boolean = delegate.handleCustomCommand(session, action, extras)

            override fun getNotificationChannelInfo():
                MediaNotification.Provider.NotificationChannelInfo = delegate.notificationChannelInfo
        }
    }

    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val isMediaNotificationController = session.isMediaNotificationController(controller)
            val isTrustedSystemController = isSystemController(controller.packageName)
            val accepted = BrowserMediaControllerAuthRules.accepts(
                ownPackageName = packageName,
                controllerPackageName = controller.packageName,
                controllerUid = controller.uid,
                isTrustedSystemController = isTrustedSystemController,
                isMediaNotificationController = isMediaNotificationController,
            )
            trace(
                "controller-connect:accepted=$accepted:notification=$isMediaNotificationController:" +
                    "trusted-system=$isTrustedSystemController:package=${controller.packageName}:" +
                    "timeline=${!player.currentTimeline.isEmpty}:" +
                    "timeline-command=${player.availableCommands.contains(Player.COMMAND_GET_TIMELINE)}",
            )
            if (!accepted) {
                return MediaSession.ConnectionResult.reject()
            }
            return MediaSession.ConnectionResult.accept(
                SessionCommands.EMPTY,
                GeckoMedia3PlayerRules.controllerCommands(),
            )
        }
    }

    private fun isSystemController(packageName: String): Boolean = runCatching {
        packageManager.getApplicationInfo(packageName, 0).flags and
            ApplicationInfo.FLAG_SYSTEM != 0
    }.getOrDefault(false)

    @VisibleForTesting
    internal fun playerForTesting(): GeckoMedia3Player = player

    @VisibleForTesting
    internal fun sessionCountForTesting(): Int = sessions.size

    internal companion object {
        const val ACTION_OPEN_BROWSER_MEDIA = "dev.sk2andy.materialbrowser.action.OPEN_BROWSER_MEDIA"
        const val CHANNEL_ID = "web_media"
        const val FOREGROUND_NOTIFICATION_ID = 0x43414E44
        const val SESSION_ID = "CandyBrowserMedia"

        internal fun publish(
            context: Context,
            publication: GeckoMediaPlaybackPublication?,
            onCommand: (GeckoMediaPlaybackOwner, GeckoMediaPlaybackCommand) -> Unit,
            mayStartService: Boolean,
        ) = BrowserMediaServiceRegistry.publish(context, publication, onCommand, mayStartService)

        internal fun invalidate(context: Context, owner: GeckoMediaPlaybackOwner) =
            BrowserMediaServiceRegistry.invalidate(context, owner)

        internal fun restoreAfterPictureInPicture(
            context: Context,
            publication: GeckoMediaPlaybackPublication?,
            onCommand: (GeckoMediaPlaybackOwner, GeckoMediaPlaybackCommand) -> Unit,
            mayStartService: Boolean,
        ) = BrowserMediaServiceRegistry.restoreAfterPictureInPicture(
            context,
            publication,
            onCommand,
            mayStartService,
        )

        internal fun replacePublication(
            context: Context,
            publication: GeckoMediaPlaybackPublication?,
            onCommand: (GeckoMediaPlaybackOwner, GeckoMediaPlaybackCommand) -> Unit,
            mayStartService: Boolean,
        ) = BrowserMediaServiceRegistry.replacePublication(
            context,
            publication,
            onCommand,
            mayStartService,
        )

        internal fun clearForPrivateContext(context: Context) =
            BrowserMediaServiceRegistry.clear(context, ClearReason.Private)

        internal fun clearForProfileLock(context: Context) =
            BrowserMediaServiceRegistry.clear(context, ClearReason.ProfileLock)

        internal fun clearForAppDataTransfer(context: Context) =
            BrowserMediaServiceRegistry.clear(context, ClearReason.AppDataTransfer)

        internal fun clear(context: Context) = BrowserMediaServiceRegistry.clear(context, ClearReason.All)

        internal fun release(context: Context) = BrowserMediaServiceRegistry.release(context)

        internal fun stopAndClear(context: Context) = BrowserMediaServiceRegistry.stopAndClear()

        @VisibleForTesting
        internal fun serviceStateForTesting(): BrowserMediaServiceTestState =
            BrowserMediaServiceRegistry.stateForTesting()

        @VisibleForTesting
        internal fun dispatchForTesting(
            owner: GeckoMediaPlaybackOwner,
            command: GeckoMediaPlaybackCommand,
        ) = BrowserMediaServiceRegistry.dispatch(owner, command)

        @VisibleForTesting
        internal fun taskRemovedForTesting() = BrowserMediaServiceRegistry.taskRemovedForTesting()

        @VisibleForTesting
        internal fun unexpectedServiceDetachForTesting() =
            BrowserMediaServiceRegistry.unexpectedServiceDetachForTesting()

        @VisibleForTesting
        internal fun addPlayerListenerForTesting(listener: Player.Listener): Boolean =
            BrowserMediaServiceRegistry.addPlayerListenerForTesting(listener)

        @VisibleForTesting
        internal fun removePlayerListenerForTesting(listener: Player.Listener) =
            BrowserMediaServiceRegistry.removePlayerListenerForTesting(listener)

        @VisibleForTesting
        internal fun lifecycleTraceForTesting(): List<BrowserMediaLifecycleTraceEvent> =
            BrowserMediaLifecycleTrace.eventsForTesting()

        @VisibleForTesting
        internal fun resetLifecycleTraceForTesting() = BrowserMediaLifecycleTrace.resetForTesting()
    }
}

private enum class ClearReason { Private, ProfileLock, AppDataTransfer, All }

@VisibleForTesting
internal data class BrowserMediaServiceTestState(
    val publication: GeckoMediaPlaybackPublication?,
    val hasCommandSink: Boolean,
    val playbackState: Int?,
    val sessionCount: Int?,
    val serviceInstanceId: Int?,
)

/** Static only for the lifetime of this process; no media URL, artwork or resume state is stored. */
@OptIn(markerClass = [UnstableApi::class])
private object BrowserMediaServiceRegistry {
    private var service: BrowserMediaPlaybackService? = null
    private var publication: GeckoMediaPlaybackPublication? = null
    private var invalidatedOwner: GeckoMediaPlaybackOwner? = null
    private var clearReason: ClearReason? = null
    private var commandSink: ((GeckoMediaPlaybackOwner, GeckoMediaPlaybackCommand) -> Unit)? = null
    private var stopAndClearInProgress = false

    @Synchronized
    fun publish(
        context: Context,
        nextPublication: GeckoMediaPlaybackPublication?,
        nextCommandSink: (GeckoMediaPlaybackOwner, GeckoMediaPlaybackCommand) -> Unit,
        mayStartService: Boolean,
    ) {
        publication = nextPublication?.takeIf { candidate ->
            GeckoMediaPlaybackRules.publicationOrNull(candidate) != null
        }
        invalidatedOwner = null
        clearReason = null
        commandSink = publication?.let { nextCommandSink }
        trace(
            action = if (publication != null) "publish:accepted" else "publish:rejected",
            publication = publication,
        )
        dispatchOrStart(context, mayStartService)
    }

    @Synchronized
    fun invalidate(context: Context, owner: GeckoMediaPlaybackOwner) {
        invalidatedOwner = owner
        publication = null
        commandSink = null
        trace(action = "invalidate", owner = owner)
        dispatchOrStart(context, mayStartService = false)
    }

    @Synchronized
    fun restoreAfterPictureInPicture(
        context: Context,
        nextPublication: GeckoMediaPlaybackPublication?,
        nextCommandSink: (GeckoMediaPlaybackOwner, GeckoMediaPlaybackCommand) -> Unit,
        mayStartService: Boolean,
    ) {
        publication = nextPublication?.takeIf { candidate ->
            GeckoMediaPlaybackRules.publicationOrNull(candidate) != null
        }
        invalidatedOwner = null
        clearReason = null
        commandSink = publication?.let { nextCommandSink }
        trace(
            action = if (publication != null) "restore-picture-in-picture:accepted" else {
                "restore-picture-in-picture:rejected"
            },
            publication = publication,
        )
        service?.restoreAfterPictureInPicture(publication) ?: dispatchOrStart(context, mayStartService)
    }

    @Synchronized
    fun replacePublication(
        context: Context,
        nextPublication: GeckoMediaPlaybackPublication?,
        nextCommandSink: (GeckoMediaPlaybackOwner, GeckoMediaPlaybackCommand) -> Unit,
        mayStartService: Boolean,
    ) {
        publication = nextPublication?.takeIf { candidate ->
            GeckoMediaPlaybackRules.publicationOrNull(candidate) != null
        }
        invalidatedOwner = null
        clearReason = null
        commandSink = publication?.let { nextCommandSink }
        trace(
            action = if (publication != null) "replace-publication:accepted" else {
                "replace-publication:rejected"
            },
            publication = publication,
        )
        service?.replacePublication(publication) ?: dispatchOrStart(context, mayStartService)
    }

    @Synchronized
    fun clear(context: Context, reason: ClearReason) {
        clearReason = reason
        publication = null
        invalidatedOwner = null
        commandSink = null
        trace(action = "clear:${reason.name}")
        dispatchOrStart(context, mayStartService = false)
    }

    @Synchronized
    fun release(context: Context) {
        clear(context, ClearReason.All)
    }

    @Synchronized
    fun attach(service: BrowserMediaPlaybackService) {
        this.service = service
        trace(action = "attach")
        applyPending(service)
    }

    fun detachAndClear(service: BrowserMediaPlaybackService) {
        val pendingStop = synchronized(this) {
            if (this.service !== service) return@synchronized null
            val owner = publication?.snapshot?.owner
            val sink = commandSink
            this.service = null
            publication = null
            invalidatedOwner = null
            clearReason = null
            commandSink = null
            trace(action = "detach-and-clear", owner = owner)
            owner?.let { currentOwner -> sink?.let { currentSink -> currentSink to currentOwner } }
        }
        pendingStop?.let { (sink, owner) -> sink(owner, GeckoMediaPlaybackCommand.Stop) }
    }

    @Synchronized
    fun applyPending(service: BrowserMediaPlaybackService) {
        trace(action = "apply-pending")
        clearReason?.let { reason ->
            when (reason) {
                ClearReason.Private -> service.clearForPrivateContext()
                ClearReason.ProfileLock -> service.clearForProfileLock()
                ClearReason.AppDataTransfer -> service.clearForAppDataTransfer()
                ClearReason.All -> service.clearPlayback()
            }
            clearReason = null
            return
        }
        invalidatedOwner?.let { owner ->
            service.invalidate(owner)
            invalidatedOwner = null
            return
        }
        service.publish(publication)
    }

    fun dispatch(owner: GeckoMediaPlaybackOwner, command: GeckoMediaPlaybackCommand) {
        trace(action = "dispatch:${command.javaClass.simpleName}", owner = owner)
        val sink = synchronized(this) { commandSink }
        sink?.invoke(owner, command)
    }

    fun stopAndClear() {
        var reentrant = false
        val pendingStop = synchronized(this) {
            if (stopAndClearInProgress) {
                reentrant = true
                publication = null
                invalidatedOwner = null
                clearReason = null
                commandSink = null
                trace(action = "stop-and-clear:reentrant")
                null
            } else {
                stopAndClearInProgress = true
                val owner = publication?.snapshot?.owner
                val sink = commandSink
                publication = null
                invalidatedOwner = null
                clearReason = null
                commandSink = null
                trace(action = "stop-and-clear", owner = owner)
                owner?.let { currentOwner ->
                    sink?.let { currentSink -> currentSink to currentOwner }
                }
            }
        }
        if (reentrant) return
        try {
            pendingStop?.let { (sink, owner) ->
                trace(action = "dispatch:Stop", owner = owner)
                sink(owner, GeckoMediaPlaybackCommand.Stop)
            }
        } finally {
            val attachedService = synchronized(this) {
                publication = null
                invalidatedOwner = null
                clearReason = null
                commandSink = null
                stopAndClearInProgress = false
                service
            }
            attachedService?.clearPlayback()
        }
    }

    @VisibleForTesting
    fun stateForTesting(): BrowserMediaServiceTestState = synchronized(this) {
        BrowserMediaServiceTestState(
            publication = publication,
            hasCommandSink = commandSink != null,
            playbackState = service?.playerForTesting()?.playbackState,
            sessionCount = service?.sessionCountForTesting(),
            serviceInstanceId = service?.let(System::identityHashCode),
        )
    }

    @VisibleForTesting
    fun taskRemovedForTesting() {
        service?.onTaskRemoved(null)
    }

    @VisibleForTesting
    fun unexpectedServiceDetachForTesting() {
        service?.let(::detachAndClear)
    }

    @VisibleForTesting
    fun addPlayerListenerForTesting(listener: Player.Listener): Boolean = synchronized(this) {
        val player = service?.playerForTesting() ?: return@synchronized false
        player.addListener(listener)
        true
    }

    @VisibleForTesting
    fun removePlayerListenerForTesting(listener: Player.Listener) {
        synchronized(this) { service?.playerForTesting()?.removeListener(listener) }
    }

    private fun dispatchOrStart(context: Context, mayStartService: Boolean) {
        service?.let {
            trace(action = "dispatch-or-start:apply-existing")
            applyPending(it)
        } ?: publication?.let { candidate ->
            if (BrowserMediaServiceStartRules.defersPausedPublication(candidate.snapshot.isPlaying)) {
                trace(action = "dispatch-or-start:defer-paused-publication", publication = candidate)
                return
            }
            if (!BrowserMediaServiceStartRules.mayStartNewService(
                    isPlaying = candidate.snapshot.isPlaying,
                    isForegroundActivity = mayStartService,
                )
            ) {
                trace(action = "dispatch-or-start:clear-start-not-allowed", publication = candidate)
                stopAndClear()
                return
            }
            runCatching {
                // Do not call startForegroundService while Media3 is still constructing its
                // initial player state; MediaSessionService promotes itself after playback starts.
                context.startService(Intent(context, BrowserMediaPlaybackService::class.java))
            }.onSuccess {
                trace(action = "dispatch-or-start:start-service", publication = candidate)
            }.onFailure {
                trace(action = "dispatch-or-start:start-service-failed", publication = candidate)
                stopAndClear()
            }
        }
    }

    private fun trace(
        action: String,
        publication: GeckoMediaPlaybackPublication? = this.publication,
        owner: GeckoMediaPlaybackOwner? = null,
    ) {
        BrowserMediaLifecycleTrace.record(
            source = "BrowserMediaServiceRegistry",
            action = action,
            publication = publication,
            owner = owner,
            serviceInstanceId = service?.let(System::identityHashCode),
        )
    }
}
