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

class WebMediaPlaybackService : Service() {
    private var sessionToken: MediaSession.Token? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (AppDataTransferLock.isActive(this)) {
            stopPlayback()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) {
            stopPlayback()
            return START_NOT_STICKY
        }
        val token = intent.getParcelableExtra(EXTRA_SESSION_TOKEN, MediaSession.Token::class.java)
        val state = intent.toWebMediaState()
        if (token == null || state == null || state.kind != WebMediaKind.Audio || !state.isPlaying) {
            stopPlayback()
            return START_NOT_STICKY
        }
        sessionToken = token
        ensureWebMediaNotificationChannel(this)
        val contentIntent = PendingIntent.getActivity(
            this,
            WebMediaSystemSession.FOREGROUND_NOTIFICATION_ID,
            Intent(this, MainActivity::class.java)
                .setAction(WebMediaSystemSession.ACTION_OPEN_MEDIA)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        startForeground(
            WebMediaSystemSession.FOREGROUND_NOTIFICATION_ID,
            buildWebMediaNotification(this, state, contentIntent, token),
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

    private fun Intent.toWebMediaState(): WebMediaState? {
        val kind = runCatching {
            WebMediaKind.valueOf(getStringExtra(EXTRA_KIND).orEmpty())
        }.getOrNull() ?: return null
        return WebMediaState(
            tabId = "",
            title = getStringExtra(EXTRA_TITLE).orEmpty(),
            origin = getStringExtra(EXTRA_ORIGIN).orEmpty(),
            kind = kind,
            isPlaying = getBooleanExtra(EXTRA_PLAYING, false),
            currentPositionMillis = getLongExtra(EXTRA_POSITION, 0L),
            durationMillis = getLongExtra(EXTRA_DURATION, -1L).takeIf { it >= 0L },
            playbackRate = getFloatExtra(EXTRA_RATE, 1f),
            muted = false,
            volume = 1f,
            videoWidth = 0,
            videoHeight = 0,
            clientWidth = 0,
            clientHeight = 0,
            visibleRatio = 0f,
            sourceUrl = null,
            contentType = null,
            posterUrl = null,
        )
    }

    companion object {
        private const val ACTION_START =
            "dev.sk2andy.materialbrowser.action.START_WEB_MEDIA_PLAYBACK"
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
            state: WebMediaState,
            sessionToken: MediaSession.Token,
        ) {
            val intent = Intent(context, WebMediaPlaybackService::class.java)
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
            context.stopService(Intent(context, WebMediaPlaybackService::class.java))
        }
    }
}
