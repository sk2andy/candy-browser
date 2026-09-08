package dev.sk2andy.materialbrowser.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R

internal class BrowserMediaSystemSession(
    context: Context,
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onStop: () -> Unit,
    private val onSeekTo: (Long) -> Unit,
) {
    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)
    private val contentIntent = PendingIntent.getActivity(
        appContext,
        NOTIFICATION_ID,
        Intent(appContext, MainActivity::class.java)
            .setAction(ACTION_OPEN_BROWSER_MEDIA)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    private val mediaSession = MediaSession(appContext, SESSION_TAG).apply {
        setSessionActivity(contentIntent)
        setCallback(
            object : MediaSession.Callback() {
                override fun onPlay() = onPlay.invoke()

                override fun onPause() = onPause.invoke()

                override fun onStop() = onStop.invoke()

                override fun onSeekTo(pos: Long) = onSeekTo.invoke(pos.coerceAtLeast(0L))
            },
        )
    }
    private var audioForeground = false

    init {
        ensureBrowserMediaNotificationChannel(appContext)
    }

    fun publish(state: BrowserMediaState?) {
        if (state == null) {
            stopAudioForeground()
            mediaSession.isActive = false
            mediaSession.setPlaybackState(null)
            mediaSession.setMetadata(null)
            notificationManager.cancel(NOTIFICATION_ID)
            notificationManager.cancel(FOREGROUND_NOTIFICATION_ID)
            return
        }
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(
                    MediaMetadata.METADATA_KEY_TITLE,
                    state.title.ifBlank { state.origin },
                )
                .putString(MediaMetadata.METADATA_KEY_ARTIST, state.origin)
                .apply {
                    state.durationMillis?.let { duration ->
                        putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
                    }
                }
                .build(),
        )
        val playbackActions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_SEEK_TO
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(playbackActions)
                .setState(
                    if (state.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    state.currentPositionMillis,
                    if (state.isPlaying) state.playbackRate else 0f,
                    SystemClock.elapsedRealtime(),
                )
                .build(),
        )
        mediaSession.isActive = true
        val notification = buildBrowserMediaNotification(
            context = appContext,
            state = state,
            contentIntent = contentIntent,
            sessionToken = mediaSession.sessionToken,
        )
        if (state.kind == BrowserMediaKind.Audio && state.isPlaying) {
            notificationManager.cancel(NOTIFICATION_ID)
            if (!audioForeground) {
                audioForeground = runCatching {
                    BrowserMediaPlaybackService.start(
                        context = appContext,
                        state = state,
                        sessionToken = mediaSession.sessionToken,
                    )
                }.isSuccess
                if (!audioForeground) {
                    runCatching { notificationManager.notify(NOTIFICATION_ID, notification) }
                }
            } else {
                runCatching {
                    notificationManager.notify(FOREGROUND_NOTIFICATION_ID, notification)
                }
            }
        } else {
            stopAudioForeground()
            runCatching { notificationManager.notify(NOTIFICATION_ID, notification) }
        }
    }

    fun release() {
        stopAudioForeground()
        notificationManager.cancel(NOTIFICATION_ID)
        notificationManager.cancel(FOREGROUND_NOTIFICATION_ID)
        mediaSession.isActive = false
        mediaSession.release()
    }

    private fun stopAudioForeground() {
        if (!audioForeground) return
        BrowserMediaPlaybackService.stop(appContext)
        audioForeground = false
    }

    internal companion object {
        const val ACTION_OPEN_BROWSER_MEDIA = "dev.sk2andy.materialbrowser.action.OPEN_BROWSER_MEDIA"
        const val CHANNEL_ID = "web_media"
        const val NOTIFICATION_ID = 0x43414E44
        const val FOREGROUND_NOTIFICATION_ID = NOTIFICATION_ID + 1
        const val SESSION_TAG = "CandyBrowserMedia"
    }
}

internal fun ensureBrowserMediaNotificationChannel(context: Context) {
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(
            BrowserMediaSystemSession.CHANNEL_ID,
            context.getString(R.string.media_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.media_notification_channel_description)
            setSound(null, null)
            enableVibration(false)
        },
    )
}

internal fun buildBrowserMediaNotification(
    context: Context,
    state: BrowserMediaState,
    contentIntent: PendingIntent,
    sessionToken: MediaSession.Token,
): Notification = Notification.Builder(context, BrowserMediaSystemSession.CHANNEL_ID)
    .setSmallIcon(R.drawable.ic_media_playback)
    .setContentTitle(state.title.ifBlank { state.origin })
    .setContentText(state.origin)
    .setContentIntent(contentIntent)
    .setCategory(Notification.CATEGORY_TRANSPORT)
    .setVisibility(Notification.VISIBILITY_PRIVATE)
    .setOngoing(state.isPlaying)
    .setOnlyAlertOnce(true)
    .setShowWhen(false)
    .setStyle(Notification.MediaStyle().setMediaSession(sessionToken))
    .build()

internal fun buildBrowserMediaStartingNotification(context: Context): Notification {
    val contentIntent = PendingIntent.getActivity(
        context,
        BrowserMediaSystemSession.FOREGROUND_NOTIFICATION_ID,
        Intent(context, MainActivity::class.java)
            .setAction(BrowserMediaSystemSession.ACTION_OPEN_BROWSER_MEDIA)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return Notification.Builder(context, BrowserMediaSystemSession.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_media_playback)
        .setContentTitle(context.getString(R.string.media_notification_channel_name))
        .setContentText(context.getString(R.string.media_notification_channel_description))
        .setContentIntent(contentIntent)
        .setCategory(Notification.CATEGORY_TRANSPORT)
        .setVisibility(Notification.VISIBILITY_PRIVATE)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .build()
}
