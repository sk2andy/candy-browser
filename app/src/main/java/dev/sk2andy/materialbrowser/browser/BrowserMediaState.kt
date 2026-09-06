package dev.sk2andy.materialbrowser.browser

/**
 * Engine-neutral snapshot used by Android's system media controls.
 *
 * Android currently receives this state from Gecko's native media-session delegate. It must never
 * contain private-tab data.
 */
internal data class BrowserMediaState(
    val tabId: String,
    val title: String,
    val origin: String,
    val kind: BrowserMediaKind,
    val isPlaying: Boolean,
    val currentPositionMillis: Long,
    val durationMillis: Long?,
    val playbackRate: Float,
    val sourceUrl: String?,
    val contentType: String?,
    val posterUrl: String?,
)

internal enum class BrowserMediaKind {
    Audio,
    Video,
}
