package dev.sk2andy.materialbrowser.browser

import org.json.JSONException
import org.json.JSONObject

internal enum class WebMediaKind {
    Audio,
    Video,
}

internal enum class WebMediaEvent {
    State,
    DocumentGone,
}

internal data class WebMediaPayload(
    val event: WebMediaEvent,
    val bridgeToken: String,
    val documentId: String,
    val mediaId: String?,
    val kind: WebMediaKind?,
    val paused: Boolean,
    val ended: Boolean,
    val currentPositionMillis: Long,
    val durationMillis: Long?,
    val playbackRate: Float,
    val muted: Boolean,
    val volume: Float,
    val videoWidth: Int,
    val videoHeight: Int,
    val clientWidth: Int,
    val clientHeight: Int,
    val visibleRatio: Float,
) {
    val isPlaying: Boolean
        get() = !paused && !ended
}

internal data class WebMediaState(
    val tabId: String,
    val title: String,
    val origin: String,
    val kind: WebMediaKind,
    val isPlaying: Boolean,
    val currentPositionMillis: Long,
    val durationMillis: Long?,
    val playbackRate: Float,
    val muted: Boolean,
    val volume: Float,
    val videoWidth: Int,
    val videoHeight: Int,
    val clientWidth: Int,
    val clientHeight: Int,
    val visibleRatio: Float,
)

internal enum class WebMediaCommand {
    Play,
    Pause,
    Stop,
    KeepPlaying,
    AllowPause,
    EnterPresentation,
    ExitPresentation,
}

internal object WebMediaContract {
    const val BRIDGE_NAME = "CandyWebMediaBridge"
    const val MAX_MESSAGE_BYTES = 4_096
    private const val MAX_ID_LENGTH = 80
    private const val MAX_MEDIA_TIME_SECONDS = 604_800.0
    private const val MAX_MEDIA_TIME_MILLIS = 604_800_000L
    private val safeId = Regex("[A-Za-z0-9_-]{1,$MAX_ID_LENGTH}")

    fun parse(raw: String, expectedBridgeToken: String): WebMediaPayload? {
        if (raw.length > MAX_MESSAGE_BYTES) return null
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_MESSAGE_BYTES) return null
        return try {
            val json = JSONObject(raw)
            if (json.optInt("v", -1) != 1) return null
            val bridgeToken = json.optString("bridgeToken")
            if (bridgeToken != expectedBridgeToken || !isSafeId(bridgeToken)) return null
            val event = when (json.optString("event")) {
                "state" -> WebMediaEvent.State
                "document-gone" -> WebMediaEvent.DocumentGone
                else -> return null
            }
            val documentId = json.optString("documentId").takeIf(::isSafeId) ?: return null
            if (event == WebMediaEvent.DocumentGone) {
                return WebMediaPayload(
                    event = event,
                    bridgeToken = bridgeToken,
                    documentId = documentId,
                    mediaId = null,
                    kind = null,
                    paused = true,
                    ended = true,
                    currentPositionMillis = 0,
                    durationMillis = null,
                    playbackRate = 1f,
                    muted = true,
                    volume = 0f,
                    videoWidth = 0,
                    videoHeight = 0,
                    clientWidth = 0,
                    clientHeight = 0,
                    visibleRatio = 0f,
                )
            }
            val mediaId = json.optString("mediaId").takeIf(::isSafeId) ?: return null
            val kind = when (json.optString("kind")) {
                "audio" -> WebMediaKind.Audio
                "video" -> WebMediaKind.Video
                else -> return null
            }
            WebMediaPayload(
                event = event,
                bridgeToken = bridgeToken,
                documentId = documentId,
                mediaId = mediaId,
                kind = kind,
                paused = json.optBoolean("paused", true),
                ended = json.optBoolean("ended", false),
                currentPositionMillis = json.finiteSeconds("currentTime")
                    ?.times(1_000)
                    ?.toLong()
                    ?: 0,
                durationMillis = json.finiteSeconds("duration")
                    ?.times(1_000)
                    ?.toLong(),
                playbackRate = json.finiteDouble("playbackRate")
                    ?.coerceIn(0.1, 16.0)
                    ?.toFloat()
                    ?: 1f,
                muted = json.optBoolean("muted", false),
                volume = json.finiteDouble("volume")
                    ?.coerceIn(0.0, 1.0)
                    ?.toFloat()
                    ?: 1f,
                videoWidth = json.boundedDimension("videoWidth"),
                videoHeight = json.boundedDimension("videoHeight"),
                clientWidth = json.boundedDimension("clientWidth"),
                clientHeight = json.boundedDimension("clientHeight"),
                visibleRatio = json.finiteDouble("visibleRatio")
                    ?.coerceIn(0.0, 1.0)
                    ?.toFloat()
                    ?: 0f,
            )
        } catch (_: JSONException) {
            null
        }
    }

    fun command(
        command: WebMediaCommand,
        documentId: String,
        mediaId: String,
        positionMillis: Long? = null,
    ): String = JSONObject().apply {
        put("v", 1)
        put("command", when (command) {
            WebMediaCommand.Play -> "play"
            WebMediaCommand.Pause -> "pause"
            WebMediaCommand.Stop -> "stop"
            WebMediaCommand.KeepPlaying -> "keep-playing"
            WebMediaCommand.AllowPause -> "allow-pause"
            WebMediaCommand.EnterPresentation -> "enter-presentation"
            WebMediaCommand.ExitPresentation -> "exit-presentation"
        })
        put("documentId", documentId)
        put("mediaId", mediaId)
        positionMillis?.let {
            put("position", it.coerceIn(0L, MAX_MEDIA_TIME_MILLIS) / 1_000.0)
        }
    }.toString()

    fun seekCommand(documentId: String, mediaId: String, positionMillis: Long): String =
        JSONObject().apply {
            put("v", 1)
            put("command", "seek-to")
            put("documentId", documentId)
            put("mediaId", mediaId)
            put("position", positionMillis.coerceIn(0L, MAX_MEDIA_TIME_MILLIS) / 1_000.0)
        }.toString()

    private fun isSafeId(value: String): Boolean = safeId.matches(value)

    private fun JSONObject.finiteSeconds(name: String): Double? =
        finiteDouble(name)?.takeIf { it in 0.0..MAX_MEDIA_TIME_SECONDS }

    private fun JSONObject.finiteDouble(name: String): Double? =
        if (!has(name) || isNull(name)) {
            null
        } else {
            optDouble(name, Double.NaN).takeIf(Double::isFinite)
        }

    private fun JSONObject.boundedDimension(name: String): Int =
        optInt(name, 0).coerceIn(0, 16_384)
}

internal object WebMediaRules {
    fun isExternalPresentationEligible(state: WebMediaState?, isPrivate: Boolean): Boolean {
        if (state == null || isPrivate || state.kind != WebMediaKind.Video) return false
        val visibleArea = state.clientWidth.toLong() * state.clientHeight
        return state.videoWidth > 0 &&
            state.videoHeight > 0 &&
            state.visibleRatio >= MIN_VISIBLE_RATIO &&
            visibleArea >= MIN_VISIBLE_VIDEO_AREA &&
            (state.isPlaying || state.currentPositionMillis > 0)
    }

    fun isSystemSessionEligible(state: WebMediaState?, isPrivate: Boolean): Boolean =
        state != null &&
            !isPrivate &&
            (
                isExternalPresentationEligible(state, isPrivate = false) ||
                    (!state.muted && state.volume > 0f)
                )

    fun score(
        payload: WebMediaPayload,
        isSelectedTab: Boolean,
        isPresented: Boolean,
    ): Long {
        val visibleArea = payload.clientWidth.toLong() * payload.clientHeight
        return (if (isPresented) 1_000_000_000_000L else 0L) +
            (if (payload.isPlaying) 100_000_000_000L else 0L) +
            (if (isSelectedTab) 10_000_000_000L else 0L) +
            (if (payload.kind == WebMediaKind.Video) 1_000_000_000L else 0L) +
            (if (!payload.muted && payload.volume > 0f) 100_000_000L else 0L) +
            visibleArea.coerceAtMost(99_999_999L)
    }

    private const val MIN_VISIBLE_RATIO = 0.1f
    private const val MIN_VISIBLE_VIDEO_AREA = 40_000L
}
