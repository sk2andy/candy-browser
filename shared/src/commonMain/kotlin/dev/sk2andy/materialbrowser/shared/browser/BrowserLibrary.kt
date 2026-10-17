package dev.sk2andy.materialbrowser.shared.browser

import dev.sk2andy.materialbrowser.browser.DEFAULT_PROFILE_ID

/** Small, platform-neutral records; native stores own only encoding and atomic file I/O. */
data class BrowserLibraryEntry(
    val address: String,
    val title: String,
    val updatedAtMillis: Long,
    val profileId: String = DEFAULT_PROFILE_ID,
)

data class BrowserLibraryState(
    val favorites: List<BrowserLibraryEntry> = emptyList(),
    val history: List<BrowserLibraryEntry> = emptyList(),
)

object BrowserLibraryRules {
    const val MAX_FAVORITES = 100
    const val MAX_HISTORY_ENTRIES = 250
    private const val MAX_TITLE_LENGTH = 4_096
    private const val MAX_PROFILE_ID_LENGTH = 256

    fun sanitize(entry: BrowserLibraryEntry): BrowserLibraryEntry? {
        val address = BrowserUrlRules.normalizeHttpUrl(entry.address)?.value ?: return null
        val profileId = entry.profileId.takeIf {
            it.isNotBlank() && it.length <= MAX_PROFILE_ID_LENGTH &&
                it.none { character -> character.code < 32 || character.code == 127 }
        } ?: return null
        if (entry.updatedAtMillis < 0) return null
        return entry.copy(
            address = address,
            title = entry.title.trim().take(MAX_TITLE_LENGTH).ifEmpty {
                address.substringAfter("://").substringBefore('/').substringBefore('?')
                    .substringBefore('#').removePrefix("www.")
            },
            profileId = profileId,
        )
    }

    fun favorites(entries: List<BrowserLibraryEntry>): List<BrowserLibraryEntry> = entries
        .asSequence()
        .mapNotNull(::sanitize)
        .distinctBy { BrowserUrlRules.httpUrlKey(it.address) }
        .take(MAX_FAVORITES)
        .toList()

    fun history(entries: List<BrowserLibraryEntry>): List<BrowserLibraryEntry> = newestHistory(
        entries = entries.mapNotNull(::sanitize),
        limit = MAX_HISTORY_ENTRIES,
        visitedAt = BrowserLibraryEntry::updatedAtMillis,
    )

    /** Android keeps its existing rich records and canonicalizer while sharing mutation mechanics. */
    fun <T> newestHistory(
        entries: List<T>,
        limit: Int,
        visitedAt: (T) -> Long,
    ): List<T> = entries.sortedByDescending(visitedAt).take(limit.coerceAtLeast(0))

    fun <T> prependFavorite(
        current: List<T>,
        entry: T,
        limit: Int,
        key: (T) -> String?,
    ): List<T> = (listOf(entry) + current).distinctBy(key).take(limit.coerceAtLeast(0))

    fun <T> toggleFavorite(
        current: List<T>,
        entry: T,
        limit: Int,
        key: (T) -> String?,
    ): List<T> {
        val entryKey = key(entry) ?: return current
        return if (current.any { key(it) == entryKey }) {
            current.filterNot { key(it) == entryKey }
        } else {
            prependFavorite(current, entry, limit, key)
        }
    }

    fun matchesQuery(normalizedQuery: String, values: List<String>): Boolean =
        normalizedQuery.isEmpty() || values.any { it.contains(normalizedQuery) }

    fun matchingEntries(
        entries: List<BrowserLibraryEntry>,
        query: String,
    ): List<BrowserLibraryEntry> {
        val normalized = query.trim().lowercase()
        return entries.filter { entry ->
            matchesQuery(normalized, listOf(entry.title.lowercase(), entry.address.lowercase()))
        }
    }
}

class BrowserLibraryController {
    var state: BrowserLibraryState = BrowserLibraryState()
        private set

    fun restore(
        favorites: List<BrowserLibraryEntry>,
        history: List<BrowserLibraryEntry>,
    ): BrowserLibraryState = update(
        BrowserLibraryState(
            favorites = BrowserLibraryRules.favorites(favorites),
            history = BrowserLibraryRules.history(history),
        ),
    )

    fun isFavorite(address: String): Boolean {
        val key = BrowserUrlRules.httpUrlKey(address) ?: return false
        return state.favorites.any { BrowserUrlRules.httpUrlKey(it.address) == key }
    }

    fun toggleFavorite(tab: BrowserTabState, nowMillis: Long): BrowserLibraryState {
        if (tab.isPrivate) return state
        val entry = entry(tab, nowMillis) ?: return state
        return update(
            state.copy(
                favorites = BrowserLibraryRules.toggleFavorite(
                    current = state.favorites,
                    entry = entry,
                    limit = BrowserLibraryRules.MAX_FAVORITES,
                    key = { BrowserUrlRules.httpUrlKey(it.address) },
                ),
            ),
        )
    }

    fun addFavorite(entry: BrowserLibraryEntry): BrowserLibraryState {
        val safeEntry = BrowserLibraryRules.sanitize(entry) ?: return state
        return update(
            state.copy(
                favorites = BrowserLibraryRules.prependFavorite(
                    current = state.favorites,
                    entry = safeEntry,
                    limit = BrowserLibraryRules.MAX_FAVORITES,
                    key = { BrowserUrlRules.httpUrlKey(it.address) },
                ),
            ),
        )
    }

    fun removeFavorite(address: String): BrowserLibraryState {
        val key = BrowserUrlRules.httpUrlKey(address) ?: return state
        return update(
            state.copy(
                favorites = state.favorites.filterNot {
                    BrowserUrlRules.httpUrlKey(it.address) == key
                },
            ),
        )
    }

    fun recordVisit(tab: BrowserTabState, nowMillis: Long): BrowserLibraryState {
        if (tab.isPrivate) return state
        val entry = entry(tab, nowMillis) ?: return state
        return update(
            state.copy(history = BrowserLibraryRules.history(listOf(entry) + state.history)),
        )
    }

    fun removeHistory(address: String, profileId: String): BrowserLibraryState {
        val key = BrowserUrlRules.httpUrlKey(address) ?: return state
        return update(
            state.copy(
                history = state.history.filterNot {
                    it.profileId == profileId && BrowserUrlRules.httpUrlKey(it.address) == key
                },
            ),
        )
    }

    fun clearHistory(profileId: String?): BrowserLibraryState = update(
        state.copy(
            history = if (profileId == null) emptyList() else {
                state.history.filterNot { it.profileId == profileId }
            },
        ),
    )

    private fun entry(tab: BrowserTabState, nowMillis: Long): BrowserLibraryEntry? =
        BrowserLibraryRules.sanitize(
            BrowserLibraryEntry(tab.address, tab.title, nowMillis, tab.profileId),
        )

    private fun update(value: BrowserLibraryState): BrowserLibraryState {
        state = value
        return value
    }
}
