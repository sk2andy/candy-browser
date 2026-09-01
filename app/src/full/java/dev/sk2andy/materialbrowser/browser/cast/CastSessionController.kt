package dev.sk2andy.materialbrowser.browser.cast

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage

internal class CastSessionController(
    context: Context,
    private val onMediaLoaded: (CastMediaCandidate) -> Unit,
) {
    var state by mutableStateOf(CastUiState())
        private set

    private var candidate: CastMediaCandidate? = null
    private val castContext = runCatching { CastContext.getSharedInstance(context) }.getOrNull()
    private val sessionManager: SessionManager? = runCatching {
        castContext?.sessionManager
    }.getOrNull()
    private var remoteMediaClient: RemoteMediaClient? = null
    private var attachedSession: CastSession? = null
    private var loadGeneration = 0L
    private val progressListener = RemoteMediaClient.ProgressListener { position, duration ->
        state = state.copy(
            positionMillis = position.coerceAtLeast(0L),
            durationMillis = duration.takeIf { it > 0L },
        )
    }
    private val remoteCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = publishRemoteState()

        override fun onMetadataUpdated() = publishRemoteState()
    }
    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            attach(session)
            candidate?.let { load(session, it) }
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            attach(session)
        }

        override fun onSessionEnded(session: CastSession, error: Int) = detach()

        override fun onSessionSuspended(session: CastSession, reason: Int) = detach()

        override fun onSessionStartFailed(session: CastSession, error: Int) = detach()

        override fun onSessionResumeFailed(session: CastSession, error: Int) = detach()

        override fun onSessionStarting(session: CastSession) = Unit

        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit

        override fun onSessionEnding(session: CastSession) = Unit
    }

    init {
        sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
        sessionManager?.currentCastSession?.let(::attach)
    }

    fun updateCandidate(candidate: CastMediaCandidate?) {
        this.candidate = candidate
    }

    fun togglePlayback() {
        remoteMediaClient?.let { client ->
            if (client.isPlaying) client.pause() else client.play()
        }
    }

    fun disconnect() {
        sessionManager?.endCurrentSession(true)
    }

    fun seekTo(positionMillis: Long) {
        remoteMediaClient?.seek(
            MediaSeekOptions.Builder()
                .setPosition(positionMillis.coerceAtLeast(0L))
                .build(),
        )
    }

    fun setDeviceVolume(volume: Float) {
        val bounded = volume.coerceIn(0f, 1f)
        runCatching { attachedSession?.volume = bounded.toDouble() }
        state = state.copy(deviceVolume = bounded)
    }

    fun release() {
        sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
        detach()
        candidate = null
    }

    private fun attach(session: CastSession) {
        val client = session.remoteMediaClient
        attachedSession = session
        if (remoteMediaClient !== client) {
            remoteMediaClient?.unregisterCallback(remoteCallback)
            remoteMediaClient?.removeProgressListener(progressListener)
            remoteMediaClient = client
            client?.registerCallback(remoteCallback)
            client?.addProgressListener(progressListener, PROGRESS_UPDATE_INTERVAL_MILLIS)
        }
        state = state.copy(
            isConnected = true,
            deviceName = session.castDevice?.friendlyName.orEmpty(),
            deviceVolume = runCatching { session.volume.toFloat() }.getOrDefault(0f),
        )
        publishRemoteState()
    }

    private fun detach() {
        loadGeneration++
        remoteMediaClient?.unregisterCallback(remoteCallback)
        remoteMediaClient?.removeProgressListener(progressListener)
        remoteMediaClient = null
        attachedSession = null
        state = CastUiState()
    }

    private fun load(session: CastSession, candidate: CastMediaCandidate) {
        val client = session.remoteMediaClient ?: return
        val generation = ++loadGeneration
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, candidate.source.title.ifBlank {
                candidate.source.origin
            })
            putString(MediaMetadata.KEY_SUBTITLE, candidate.source.origin)
            candidate.source.posterUrl?.let { addImage(WebImage(Uri.parse(it))) }
        }
        val mediaInfo = MediaInfo.Builder(candidate.source.url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(candidate.source.contentType)
            .setMetadata(metadata)
            .build()
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .setCurrentTime(candidate.source.startPositionMillis)
            .build()
        client.load(request).setResultCallback { result ->
            if (CastSessionRules.shouldPauseLocalAfterLoad(
                    succeeded = result.status.isSuccess,
                    expectedGeneration = generation,
                    currentGeneration = loadGeneration,
                    sameSession = attachedSession === session,
                    sameClient = remoteMediaClient === client,
                    loadedIdentity = candidate.identity,
                    currentCandidateIdentity = this.candidate?.identity,
                    loadedSourceUrl = candidate.source.url,
                    currentCandidateSourceUrl = this.candidate?.source?.url,
                )
            ) {
                onMediaLoaded(candidate)
                publishRemoteState()
            }
        }
    }

    private fun publishRemoteState() {
        val client = remoteMediaClient ?: return
        val metadata = client.mediaInfo?.metadata
        state = state.copy(
            hasMedia = client.hasMediaSession(),
            isPlaying = client.isPlaying,
            title = metadata?.getString(MediaMetadata.KEY_TITLE).orEmpty(),
            positionMillis = client.approximateStreamPosition.coerceAtLeast(0L),
            durationMillis = client.streamDuration.takeIf { it > 0L },
            deviceVolume = runCatching { attachedSession?.volume?.toFloat() }
                .getOrNull()
                ?: state.deviceVolume,
        )
    }

    private companion object {
        const val PROGRESS_UPDATE_INTERVAL_MILLIS = 1_000L
    }
}
