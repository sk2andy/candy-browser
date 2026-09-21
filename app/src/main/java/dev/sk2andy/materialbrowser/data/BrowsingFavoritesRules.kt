package dev.sk2andy.materialbrowser.data

import java.net.URI
import java.util.Locale

internal object BrowsingFavoritesRules {
    const val MAX_QUERY_CHARS = 256
    const val MAX_FOLDER_TITLE_CHARS = 120
    const val MAX_FOLDER_ID_CHARS = 128
    const val MAX_FOLDER_EMOJI_CHARS = 32

    fun normalizeLibrary(library: FavoriteLibrary): FavoriteLibrary {
        val acceptedFolders = linkedMapOf<String, FavoriteFolder>()
        val acceptedFavoriteIds = hashSetOf<String>()
        val acceptedUrlKeys = hashSetOf<String>()
        val candidates = library.entries.mapNotNull { entry ->
            when (entry) {
                is FavoriteFolder -> entry.takeIf(::isValidFolder)
                is FavoriteEntry -> normalizedFavorite(entry)
            }
        }
        candidates.filterIsInstance<FavoriteFolder>().forEach { folder ->
            if (folder.id !in acceptedFolders) acceptedFolders[folder.id] = folder
        }
        val validFolderParents = acceptedFolders.keys.associateWith { id ->
            validFolderParent(id, acceptedFolders)
        }
        return FavoriteLibrary(
            entries = buildList {
                candidates.forEach { entry ->
                    when (entry) {
                        is FavoriteFolder -> {
                            val folder = acceptedFolders[entry.id] ?: return@forEach
                            if (folder !== entry) return@forEach
                            add(folder.copy(parentFolderId = validFolderParents.getValue(folder.id)))
                        }
                        is FavoriteEntry -> {
                            val key = favoriteKey(entry.url) ?: return@forEach
                            if (
                                entry.id in acceptedFolders ||
                                !acceptedFavoriteIds.add(entry.id) ||
                                !acceptedUrlKeys.add(key)
                            ) {
                                return@forEach
                            }
                            if (acceptedFavoriteIds.size > BrowsingLibraryRules.MAX_FAVORITES) {
                                return@forEach
                            }
                            val parentFolderId = entry.parentFolderId
                                ?.takeIf(acceptedFolders::containsKey)
                            add(entry.copy(parentFolderId = parentFolderId))
                        }
                    }
                }
            },
        )
    }

    fun children(
        library: FavoriteLibrary,
        parentFolderId: String?,
    ): List<FavoriteLibraryEntry> {
        val normalized = normalizeLibrary(library)
        val parent = parentFolderId?.takeIf { id -> normalized.folders.any { it.id == id } }
        return normalized.entries.filter { it.parentFolderId == parent }
    }

    fun folder(library: FavoriteLibrary, folderId: String): FavoriteFolder? =
        normalizeLibrary(library).folders.firstOrNull { it.id == folderId }

    fun addFolder(
        library: FavoriteLibrary,
        folder: FavoriteFolder,
    ): FavoriteLibrary {
        val normalized = normalizeLibrary(library)
        if (!isValidFolder(folder) || normalized.entries.any { it.id == folder.id }) return normalized
        val parent = folder.parentFolderId
        if (parent != null && normalized.folders.none { it.id == parent }) return normalized
        return normalizeLibrary(normalized.copy(entries = normalized.entries + folder))
    }

    fun rename(
        library: FavoriteLibrary,
        entryId: String,
        title: String,
    ): FavoriteLibrary {
        val normalized = normalizeLibrary(library)
        val safeTitle = title.trim().take(MAX_FOLDER_TITLE_CHARS)
        if (safeTitle.isEmpty()) return normalized
        return normalized.copy(
            entries = normalized.entries.map { entry ->
                if (entry.id != entryId) entry else when (entry) {
                    is FavoriteEntry -> entry.copy(title = safeTitle)
                    is FavoriteFolder -> entry.copy(title = safeTitle)
                }
            },
        )
    }

    fun move(
        library: FavoriteLibrary,
        entryId: String,
        destinationParentFolderId: String?,
    ): FavoriteLibrary {
        val normalized = normalizeLibrary(library)
        val entry = normalized.entries.firstOrNull { it.id == entryId } ?: return normalized
        if (entry.parentFolderId == destinationParentFolderId) return normalized
        if (
            destinationParentFolderId != null &&
            normalized.folders.none { it.id == destinationParentFolderId }
        ) {
            return normalized
        }
        if (
            entry is FavoriteFolder &&
            (destinationParentFolderId == entry.id ||
                isFolderDescendant(normalized, destinationParentFolderId, entry.id))
        ) {
            return normalized
        }
        return normalized.copy(
            entries = normalized.entries.map { candidate ->
                if (candidate.id != entryId) candidate else candidate.withParent(destinationParentFolderId)
            },
        )
    }

    fun reorder(
        library: FavoriteLibrary,
        entryId: String,
        destinationIndex: Int,
    ): FavoriteLibrary {
        val normalized = normalizeLibrary(library)
        val entry = normalized.entries.firstOrNull { it.id == entryId } ?: return normalized
        val siblings = normalized.entries.filter { it.parentFolderId == entry.parentFolderId }
        val sourceIndex = siblings.indexOfFirst { it.id == entryId }
        if (sourceIndex < 0 || siblings.size < 2) return normalized
        val targetIndex = destinationIndex.coerceIn(0, siblings.lastIndex)
        if (sourceIndex == targetIndex) return normalized
        val reordered = siblings.toMutableList().apply {
            add(targetIndex, removeAt(sourceIndex))
        }
        val iterator = reordered.iterator()
        return normalized.copy(
            entries = normalized.entries.map { candidate ->
                if (candidate.parentFolderId == entry.parentFolderId) iterator.next() else candidate
            },
        )
    }

    fun visibleEntries(
        favorites: List<FavoriteEntry>,
        query: String,
    ): List<FavoriteEntry> {
        val normalizedQuery = query.trim().take(MAX_QUERY_CHARS).lowercase(Locale.ROOT)
        return favorites.asSequence()
            .filter { entry -> favoriteKey(entry.url) != null }
            .distinctBy { entry -> favoriteKey(entry.url) }
            .take(BrowsingLibraryRules.MAX_FAVORITES)
            .filter { entry ->
                normalizedQuery.isEmpty() ||
                    entry.title.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                    entry.url.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                    displayHost(entry.url).lowercase(Locale.ROOT).contains(normalizedQuery)
            }
            .toList()
    }

    fun remove(
        favorites: List<FavoriteEntry>,
        entry: FavoriteEntry,
    ): List<FavoriteEntry> {
        val key = CanonicalWebUrl.key(entry.url) ?: return favorites
        return favorites.filterNot { favorite -> CanonicalWebUrl.key(favorite.url) == key }
    }

    private fun normalizedFavorite(entry: FavoriteEntry): FavoriteEntry? {
        val key = favoriteKey(entry.url) ?: return null
        val id = entry.id.trim().take(MAX_FOLDER_ID_CHARS).ifEmpty { favoriteEntryId(key) }
        if (!isValidId(id)) return null
        return entry.copy(
            id = id,
            title = entry.title.trim().ifEmpty { displayHost(entry.url) },
            parentFolderId = entry.parentFolderId?.trim()?.takeIf(String::isNotEmpty),
        )
    }

    private fun isValidFolder(folder: FavoriteFolder): Boolean =
        isValidId(folder.id) &&
            folder.title.trim().isNotEmpty() &&
            folder.title.trim().length <= MAX_FOLDER_TITLE_CHARS &&
            folder.icon.isValid()

    private fun isValidId(id: String): Boolean =
        id.isNotBlank() && id.length <= MAX_FOLDER_ID_CHARS && id.none(Char::isWhitespace)

    private fun FavoriteFolderIcon?.isValid(): Boolean = when (this) {
        null,
        FavoriteFolderIcon.Custom,
        -> true
        is FavoriteFolderIcon.Emoji -> value.trim().isNotEmpty() && value.length <= MAX_FOLDER_EMOJI_CHARS
    }

    private fun validFolderParent(
        folderId: String,
        folders: Map<String, FavoriteFolder>,
    ): String? {
        val parent = folders.getValue(folderId).parentFolderId?.trim()?.takeIf(String::isNotEmpty)
            ?: return null
        var current = parent
        val visited = hashSetOf(folderId)
        while (true) {
            if (!visited.add(current)) return null
            val folder = folders[current] ?: return null
            current = folder.parentFolderId?.trim()?.takeIf(String::isNotEmpty) ?: return parent
        }
    }

    private fun isFolderDescendant(
        library: FavoriteLibrary,
        folderId: String?,
        possibleAncestorId: String,
    ): Boolean {
        var current = folderId ?: return false
        val folders = library.folders.associateBy(FavoriteFolder::id)
        while (true) {
            if (current == possibleAncestorId) return true
            current = folders[current]?.parentFolderId ?: return false
        }
    }

    private fun FavoriteLibraryEntry.withParent(parentFolderId: String?): FavoriteLibraryEntry = when (this) {
        is FavoriteEntry -> copy(parentFolderId = parentFolderId)
        is FavoriteFolder -> copy(parentFolderId = parentFolderId)
    }

    private fun displayHost(url: String): String = runCatching {
        URI(url).host?.removePrefix("www.")
    }.getOrNull().orEmpty()

    private fun favoriteKey(url: String): String? {
        val candidate = url.trim()
        if (candidate.any { character -> character.code <= 0x20 || character.code == 0x7f }) {
            return null
        }
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        if (uri.rawUserInfo != null) return null
        return CanonicalWebUrl.key(candidate)
    }
}
