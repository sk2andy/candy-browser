package dev.sk2andy.materialbrowser.browser.cast

internal data class CastUiState(
    val isConnected: Boolean = false,
    val hasMedia: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val deviceName: String = "",
    val positionMillis: Long = 0L,
    val durationMillis: Long? = null,
    val deviceVolume: Float = 0f,
)
