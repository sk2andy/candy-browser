package dev.sk2andy.materialbrowser.data

import java.net.URI
import java.util.Locale

internal object BrowsingFavoritesRules {
    const val MAX_QUERY_CHARS = 256

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
