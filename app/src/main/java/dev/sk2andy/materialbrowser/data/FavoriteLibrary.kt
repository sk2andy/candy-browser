package dev.sk2andy.materialbrowser.data

import java.security.MessageDigest

sealed interface FavoriteLibraryEntry {
    val id: String
    val parentFolderId: String?
}

data class FavoriteEntry(
    val url: String,
    val title: String,
    val addedAt: Long,
    override val id: String = favoriteEntryId(url),
    override val parentFolderId: String? = null,
) : FavoriteLibraryEntry

data class FavoriteFolder(
    override val id: String,
    val title: String,
    override val parentFolderId: String? = null,
    val icon: FavoriteFolderIcon? = null,
) : FavoriteLibraryEntry

sealed interface FavoriteFolderIcon {
    data class Emoji(val value: String) : FavoriteFolderIcon

    data object Custom : FavoriteFolderIcon
}

data class FavoriteLibrary(
    val entries: List<FavoriteLibraryEntry> = emptyList(),
) {
    val favorites: List<FavoriteEntry>
        get() = entries.filterIsInstance<FavoriteEntry>()

    val folders: List<FavoriteFolder>
        get() = entries.filterIsInstance<FavoriteFolder>()
}

internal fun favoriteEntryId(url: String): String {
    val value = CanonicalWebUrl.key(url) ?: url.trim()
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    return "favorite-$digest"
}
