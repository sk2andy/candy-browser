package dev.sk2andy.materialbrowser.browser

data class BrowserProfile(
    val id: String,
    val emoji: String,
    val selectedTabId: String? = null,
    val isolationEnabled: Boolean = false,
)

const val DEFAULT_PROFILE_ID = "candy"
const val DEFAULT_PROFILE_EMOJI = "🍬"
const val MAX_PROFILES = 12

val DEFAULT_BROWSER_PROFILE = BrowserProfile(
    id = DEFAULT_PROFILE_ID,
    emoji = DEFAULT_PROFILE_EMOJI,
)
