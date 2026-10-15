package dev.sk2andy.materialbrowser.browser.integration.devicesearch

import android.content.Context
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.data.AppDataTransferLock
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.reader.ReaderLibraryStore
import dev.sk2andy.materialbrowser.reader.ReaderSnapshot

internal data class DeviceSearchSources(
    val settings: DeviceSearchSettings,
    val items: List<DeviceSearchItem>,
    val profiles: List<BrowserProfile>,
    val snapshots: List<ReaderSnapshot>,
)

internal fun loadDeviceSearchSources(context: Context): DeviceSearchSources {
    if (AppDataTransferLock.isActive(context)) {
        return DeviceSearchSources(DeviceSearchSettings(), emptyList(), emptyList(), emptyList())
    }
    val settings = DeviceSearchStore(context).load()
    val browserStore = BrowserSessionStore(context)
    val profiles = browserStore.loadProfiles().first
    val profilesEnabled = browserStore.loadProfilesEnabled()
    val snapshots = ReaderLibraryStore(context).load(isPrivate = false).snapshots
    return DeviceSearchSources(
        settings = settings,
        items = DeviceSearchRules.candidates(
            settings = settings,
            favorites = browserStore.loadFavoriteLibrary().favorites,
            snapshots = snapshots,
            profiles = profiles,
            profilesEnabled = profilesEnabled,
        ),
        profiles = DeviceSearchRules.eligibleProfiles(profiles, profilesEnabled),
        snapshots = snapshots,
    )
}
