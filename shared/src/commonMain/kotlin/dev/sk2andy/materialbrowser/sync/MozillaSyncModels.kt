package dev.sk2andy.materialbrowser.sync

enum class MozillaSyncStatus { Unavailable, SignedOut, Connecting, Connected, Error }

data class MozillaSyncTab(val title: String, val url: String, val lastUsedMillis: Long)

data class MozillaSyncDevice(
    val id: String,
    val displayName: String,
    val tabs: List<MozillaSyncTab>,
    val canReceiveTabs: Boolean = false,
)

data class MozillaSyncState(
    val status: MozillaSyncStatus = MozillaSyncStatus.SignedOut,
    val devices: List<MozillaSyncDevice> = emptyList(),
    val localProfileId: String? = null,
    val currentDeviceId: String? = null,
    val deviceName: String = "",
)
