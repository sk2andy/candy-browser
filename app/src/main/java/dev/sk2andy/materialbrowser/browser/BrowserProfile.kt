package dev.sk2andy.materialbrowser.browser

data class BrowserProfile(
    val id: String,
    val emoji: String,
    val selectedTabId: String? = null,
    val isolationEnabled: Boolean = false,
    val syncedDeviceId: String? = null,
    val syncedDisplayName: String? = null,
    val syncedIconCatalogId: String? = null,
    val syncedIconEmoji: String? = null,
    val syncedIconAccentHue: Int? = null,
)

val BrowserProfile.isSynced: Boolean
    get() = syncedDeviceId != null

const val DEFAULT_PROFILE_ID = "candy"
const val DEFAULT_PROFILE_EMOJI = "🍬"
const val MAX_PROFILES = 12

val DEFAULT_BROWSER_PROFILE = BrowserProfile(
    id = DEFAULT_PROFILE_ID,
    emoji = DEFAULT_PROFILE_EMOJI,
)
