package dev.sk2andy.materialbrowser.feeds

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Looper
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommands
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserMediaControllerAuthRules

/** Owns Android's speech session, foreground notification, audio focus and background lifetime. */
@OptIn(markerClass = [UnstableApi::class])
class FeedMediaPlaybackService : MediaSessionService() {
    private lateinit var manager: FeedPlaybackManager
    private lateinit var player: FeedMedia3Player
    private lateinit var mediaSession: MediaSession
    private lateinit var audioManager: AudioManager
    private lateinit var focusRequest: AudioFocusRequest
    private lateinit var wakeLock: PowerManager.WakeLock
    private var hasFocus = false
    private var destroyed = false
    private var currentProfileId: String? = null

    internal val speechAudioAttributes: AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

    private val noisyReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) manager.pause()
            }
        }

    override fun onCreate() {
        super.onCreate()
        manager = FeedPlaybackManager.get(this)
        audioManager = getSystemService(AudioManager::class.java)
        wakeLock =
            getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:FeedSpeech")
                .also { it.setReferenceCounted(false) }
        focusRequest =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(speechAudioAttributes)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener { change ->
                    if (change != AudioManager.AUDIOFOCUS_GAIN) {
                        hasFocus = false
                        manager.pause()
                    }
                }
                .build()
        ContextCompat.registerReceiver(
            this,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        player = FeedMedia3Player(Looper.getMainLooper(), manager)
        mediaSession =
            MediaSession.Builder(this, player)
                .setId(SESSION_ID)
                .setCallback(SessionCallback())
                .build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                    this,
                    DefaultMediaNotificationProvider.NotificationIdProvider { NOTIFICATION_ID },
                    CHANNEL_ID,
                    R.string.feed_playback_channel_name,
                )
                .also { it.setSmallIcon(R.drawable.ic_media_playback) }
        )
        addSession(mediaSession)
        if (manager.hasPendingPlayback()) manager.attach(this)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession =
        mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // No boot restore or persisted auto-play: a dead process has no user playback session.
        if (manager.state.value.isActive) {
            if (!manager.isAttached(this)) manager.attach(this)
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onUpdateNotificationAsync(
        session: MediaSession,
        startInForegroundRequired: Boolean,
    ): ListenableFuture<Void?> {
        val update = super.onUpdateNotificationAsync(session, startInForegroundRequired)
        Futures.addCallback(
            update,
            object : FutureCallback<Void?> {
                override fun onSuccess(result: Void?) {
                    // Android 15 permits audio focus only after this foreground promotion (or
                    // while top). Never start TTS first and hope that promotion wins the race.
                    if (!destroyed && isPlaybackOngoing) manager.foregroundReady()
                }

                override fun onFailure(throwable: Throwable) {
                    if (!destroyed) manager.pause()
                }
            },
            ContextCompat.getMainExecutor(this),
        )
        return update
    }

    internal fun acquireAudioFocus(): Boolean {
        if (hasFocus) return true
        if (destroyed || !isPlaybackOngoing) return false
        hasFocus =
            audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!hasFocus) manager.pause()
        return hasFocus
    }

    internal fun abandonAudioFocus() {
        audioManager.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
        if (wakeLock.isHeld) wakeLock.release()
    }

    internal fun publish(snapshot: FeedMediaPlaybackSnapshot?) {
        if (destroyed) return
        if (currentProfileId != snapshot?.profileId) {
            currentProfileId = snapshot?.profileId
            snapshot?.let { mediaSession.setSessionActivity(openQueuePendingIntent(it.profileId)) }
        }
        player.publish(snapshot)
        if (snapshot?.playWhenReady == true) {
            // Renewed on real TTS progress publications; bounded if the engine stalls forever.
            wakeLock.acquire(WAKE_LOCK_TIMEOUT_MILLIS)
        } else {
            abandonAudioFocus()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Media3 retains active playback and its notification; idle/paused sessions may stop.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        destroyed = true
        manager.detach(this)
        abandonAudioFocus()
        unregisterReceiver(noisyReceiver)
        removeSession(mediaSession)
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    private fun openQueuePendingIntent(profileId: String): PendingIntent =
        PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            Intent(this, MainActivity::class.java)
                .setAction(FeedLaunchContract.ACTION_OPEN)
                .putExtra(FeedLaunchContract.EXTRA_PROFILE, profileId)
                .putExtra(FeedLaunchContract.EXTRA_QUEUE, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val trustedSystem =
                runCatching {
                        packageManager.getApplicationInfo(controller.packageName, 0).flags and
                            ApplicationInfo.FLAG_SYSTEM != 0
                    }
                    .getOrDefault(false)
            if (
                !BrowserMediaControllerAuthRules.accepts(
                    ownPackageName = packageName,
                    controllerPackageName = controller.packageName,
                    controllerUid = controller.uid,
                    isTrustedSystemController = trustedSystem,
                    isMediaNotificationController =
                        session.isMediaNotificationController(controller),
                )
            ) {
                return MediaSession.ConnectionResult.reject()
            }
            return MediaSession.ConnectionResult.accept(
                SessionCommands.EMPTY,
                FeedMedia3Player.controllerCommands(),
            )
        }
    }

    @VisibleForTesting internal fun playerForTesting(): FeedMedia3Player = player

    internal companion object {
        const val SESSION_ID = "CandyFeedSpeech"
        const val NOTIFICATION_ID = 0x43414644
        const val CHANNEL_ID = "feed_speech"
        const val WAKE_LOCK_TIMEOUT_MILLIS = 10 * 60 * 1_000L
    }
}
