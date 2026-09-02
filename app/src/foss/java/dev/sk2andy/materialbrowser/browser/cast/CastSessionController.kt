package dev.sk2andy.materialbrowser.browser.cast

import android.content.Context

internal class CastSessionController(
    @Suppress("UNUSED_PARAMETER") context: Context,
    @Suppress("UNUSED_PARAMETER") onMediaLoaded: (CastMediaCandidate) -> Unit,
) {
    val state = CastUiState()

    fun updateCandidate(@Suppress("UNUSED_PARAMETER") candidate: CastMediaCandidate?) = Unit

    fun togglePlayback() = Unit

    fun disconnect() = Unit

    fun seekTo(@Suppress("UNUSED_PARAMETER") positionMillis: Long) = Unit

    fun setDeviceVolume(@Suppress("UNUSED_PARAMETER") volume: Float) = Unit

    fun release() = Unit
}
