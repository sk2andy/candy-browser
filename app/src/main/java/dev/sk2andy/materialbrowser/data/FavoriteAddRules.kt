package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy

internal object FavoriteAddRules {
    const val MAX_URL_CHARS = 8_192

    fun entry(url: String, title: String, addedAt: Long): FavoriteEntry? {
        if (url.length > MAX_URL_CHARS) return null
        val safeUrl = BrowserUriPolicy.normalizeHttpUrl(url) ?: return null
        return BrowsingFavoritesRules.normalizeLibrary(
            FavoriteLibrary(
                listOf(
                    FavoriteEntry(
                        url = safeUrl,
                        title = title.take(BrowsingFavoritesRules.MAX_FOLDER_TITLE_CHARS),
                        addedAt = addedAt,
                    ),
                ),
            ),
        ).favorites.singleOrNull()
    }

    fun add(library: FavoriteLibrary, favorite: FavoriteEntry): FavoriteLibrary? {
        val current = BrowsingFavoritesRules.normalizeLibrary(library)
        if (current.favorites.size >= BrowsingLibraryRules.MAX_FAVORITES) return null
        if (BrowsingLibraryRules.isFavorite(current.favorites, favorite.url)) return null
        val safeEntry = entry(favorite.url, favorite.title, favorite.addedAt) ?: return null
        return current.copy(entries = listOf(safeEntry) + current.entries)
    }
}
