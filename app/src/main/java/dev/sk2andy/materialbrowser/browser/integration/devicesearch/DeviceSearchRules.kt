package dev.sk2andy.materialbrowser.browser.integration.devicesearch

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.DEFAULT_PROFILE_ID
import dev.sk2andy.materialbrowser.browser.isSynced
import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import dev.sk2andy.materialbrowser.reader.ReaderSnapshot

data class DeviceSearchSettings(
    val enabled: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val bookmarkProfileId: String = DEFAULT_PROFILE_ID,
)

enum class DeviceSearchKind { Bookmark, Article }

data class DeviceSearchItem(
    val id: String,
    val title: String,
    val sourceUrl: String,
    val profileId: String,
    val kind: DeviceSearchKind,
    val snapshotId: String? = null,
)

object DeviceSearchRules {
    const val MAX_ITEMS = 256
    const val MAX_TITLE_LENGTH = 256

    fun validId(value: String?): Boolean = value != null && value.length in 3..128 &&
        (value.startsWith("f:") || value.startsWith("r:")) &&
        value.all { it.isLetterOrDigit() || it in ":_-" }

    fun eligibleProfiles(profiles: List<BrowserProfile>, profilesEnabled: Boolean): List<BrowserProfile> =
        (if (profilesEnabled) profiles else profiles.take(1)).filter {
            it.protection == null && !it.isSynced
        }

    fun candidates(
        settings: DeviceSearchSettings,
        favorites: List<FavoriteEntry>,
        snapshots: List<ReaderSnapshot>,
        profiles: List<BrowserProfile>,
        profilesEnabled: Boolean,
    ): List<DeviceSearchItem> {
        val profileIds = eligibleProfiles(profiles, profilesEnabled).mapTo(hashSetOf(), BrowserProfile::id)
        val bookmarks = if (settings.bookmarkProfileId in profileIds) {
            favorites.mapNotNull { favorite ->
                val url = BrowserUriPolicy.normalizeHttpUrl(favorite.url) ?: return@mapNotNull null
                val id = "f:${favorite.id}".takeIf(::validId) ?: return@mapNotNull null
                DeviceSearchItem(
                    id = id,
                    title = favorite.title.trim().take(MAX_TITLE_LENGTH).ifEmpty { url },
                    sourceUrl = url,
                    profileId = settings.bookmarkProfileId,
                    kind = DeviceSearchKind.Bookmark,
                )
            }
        } else emptyList()
        val articles = snapshots.mapNotNull { snapshot ->
            val profileId = snapshot.sourceProfileId?.takeIf { it in profileIds } ?: return@mapNotNull null
            if (snapshot.isPrivate) return@mapNotNull null
            val url = BrowserUriPolicy.normalizeHttpUrl(snapshot.document.sourceUrl) ?: return@mapNotNull null
            val id = "r:${snapshot.id}".takeIf(::validId) ?: return@mapNotNull null
            DeviceSearchItem(
                id = id,
                title = snapshot.document.title.trim().take(MAX_TITLE_LENGTH).ifEmpty { url },
                sourceUrl = url,
                profileId = profileId,
                kind = DeviceSearchKind.Article,
                snapshotId = snapshot.id,
            )
        }
        return (bookmarks + articles).distinctBy(DeviceSearchItem::id).take(MAX_ITEMS)
    }

    fun published(settings: DeviceSearchSettings, candidates: List<DeviceSearchItem>): List<DeviceSearchItem> =
        if (settings.enabled) candidates.filter { it.id in settings.selectedIds } else emptyList()
}
