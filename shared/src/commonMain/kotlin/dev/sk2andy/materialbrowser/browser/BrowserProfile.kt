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
    val linkedSyncDeviceId: String? = null,
    val newTabWallpaper: ProfileWallpaper? = null,
    val tabSwitcherWallpaper: ProfileWallpaper? = null,
)

data class ProfileWallpaper(
    val zoom: Float = 1f,
    val normalizedPanX: Float = 0f,
    val normalizedPanY: Float = 0f,
)

enum class ProfileWallpaperTarget(val wireValue: String) {
    NewTab("new_tab"),
    TabSwitcher("tab_switcher"),
    ;

    companion object {
        fun fromWireValue(value: String?): ProfileWallpaperTarget? =
            entries.firstOrNull { it.wireValue == value }
    }
}

fun BrowserProfile.wallpaperFor(target: ProfileWallpaperTarget): ProfileWallpaper? = when (target) {
    ProfileWallpaperTarget.NewTab -> newTabWallpaper
    ProfileWallpaperTarget.TabSwitcher -> tabSwitcherWallpaper
}

fun BrowserProfile.withWallpaper(
    target: ProfileWallpaperTarget,
    wallpaper: ProfileWallpaper?,
): BrowserProfile = when (target) {
    ProfileWallpaperTarget.NewTab -> copy(newTabWallpaper = wallpaper)
    ProfileWallpaperTarget.TabSwitcher -> copy(tabSwitcherWallpaper = wallpaper)
}

val BrowserProfile.isSynced: Boolean
    get() = syncedDeviceId != null

val BrowserProfile.isSyncLinked: Boolean
    get() = syncedDeviceId != null || linkedSyncDeviceId != null

const val DEFAULT_PROFILE_ID = "candy"
const val DEFAULT_PROFILE_EMOJI = "🍬"
const val MAX_PROFILES = 12

val DEFAULT_BROWSER_PROFILE = BrowserProfile(
    id = DEFAULT_PROFILE_ID,
    emoji = DEFAULT_PROFILE_EMOJI,
)
