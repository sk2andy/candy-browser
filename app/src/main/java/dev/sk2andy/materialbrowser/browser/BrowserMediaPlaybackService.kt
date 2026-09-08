package dev.sk2andy.materialbrowser.browser

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.session.MediaController
import android.media.session.MediaSession
import android.os.IBinder
import androidx.core.content.ContextCompat
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.data.AppDataTransferLock

class BrowserMediaPlaybackService : Service() {
    private var sessionToken: MediaSession.Token? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureBrowserMediaNotificationChannel(this)
        startForeground(
            BrowserMediaSystemSession.FOREGROUND_NOTIFICATION_ID,
            buildBrowserMediaStartingNotification(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (AppDataTransferLock.isActive(this)) {
            stopPlayback(startId)
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) {
            stopPlayback(startId)
            return START_NOT_STICKY
        }
        val token = intent.getParcelableExtra(EXTRA_SESSION_TOKEN, MediaSession.Token::class.java)
        val state = intent.toBrowserMediaState()
        if (token == null || state == null || state.kind != BrowserMediaKind.Audio || !state.isPlaying) {
            stopPlayback(startId)
            return START_NOT_STICKY
        }
        sessionToken = token
        val contentIntent = PendingIntent.getActivity(
            this,
            BrowserMediaSystemSession.FOREGROUND_NOTIFICATION_ID,
            Intent(this, MainActivity::class.java)
                .setAction(BrowserMediaSystemSession.ACTION_OPEN_BROWSER_MEDIA)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        startForeground(
            BrowserMediaSystemSession.FOREGROUND_NOTIFICATION_ID,
            buildBrowserMediaNotification(this, state, contentIntent, token),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        sessionToken?.let { token ->
            runCatching { MediaController(this, token).transportControls.stop() }
        }
        stopPlayback()
        super.onTaskRemoved(rootIntent)
    }

    private fun stopPlayback() {
        sessionToken = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopPlayback(startId: Int) {
        if (!stopSelfResult(startId)) return
        sessionToken = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun Intent.toBrowserMediaState(): BrowserMediaState? {
        val kind = runCatching {
            BrowserMediaKind.valueOf(getStringExtra(EXTRA_KIND).orEmpty())
        }.getOrNull() ?: return null
        return BrowserMediaState(
            tabId = "",
            title = getStringExtra(EXTRA_TITLE).orEmpty(),
            origin = getStringExtra(EXTRA_ORIGIN).orEmpty(),
            kind = kind,
            isPlaying = getBooleanExtra(EXTRA_PLAYING, false),
            currentPositionMillis = getLongExtra(EXTRA_POSITION, 0L),
            durationMillis = getLongExtra(EXTRA_DURATION, -1L).takeIf { it >= 0L },
            playbackRate = getFloatExtra(EXTRA_RATE, 1f),
            sourceUrl = null,
            contentType = null,
            posterUrl = null,
        )
    }

    companion object {
        private const val ACTION_START =
            "dev.sk2andy.materialbrowser.action.START_BROWSER_MEDIA_PLAYBACK"
        private const val EXTRA_SESSION_TOKEN = "session_token"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ORIGIN = "origin"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_PLAYING = "playing"
        private const val EXTRA_POSITION = "position"
        private const val EXTRA_DURATION = "duration"
        private const val EXTRA_RATE = "rate"

        internal fun start(
            context: Context,
            state: BrowserMediaState,
            sessionToken: MediaSession.Token,
        ) {
            val intent = Intent(context, BrowserMediaPlaybackService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SESSION_TOKEN, sessionToken)
                .putExtra(EXTRA_TITLE, state.title)
                .putExtra(EXTRA_ORIGIN, state.origin)
                .putExtra(EXTRA_KIND, state.kind.name)
                .putExtra(EXTRA_PLAYING, state.isPlaying)
                .putExtra(EXTRA_POSITION, state.currentPositionMillis)
                .putExtra(EXTRA_DURATION, state.durationMillis ?: -1L)
                .putExtra(EXTRA_RATE, state.playbackRate)
            ContextCompat.startForegroundService(context, intent)
        }

        internal fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, BrowserMediaPlaybackService::class.java)
                        .setAction(ACTION_STOP),
                )
            }
        }

        private const val ACTION_STOP =
            "dev.sk2andy.materialbrowser.action.STOP_BROWSER_MEDIA_PLAYBACK"
    }
}
