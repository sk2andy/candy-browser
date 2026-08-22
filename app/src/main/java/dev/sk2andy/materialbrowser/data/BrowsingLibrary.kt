package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserTab
import java.net.URI
import java.util.Locale

data class HistoryEntry(
    val url: String,
    val title: String,
    val lastVisitedAt: Long,
)

data class FavoriteEntry(
    val url: String,
    val title: String,
    val addedAt: Long,
)

data class AddressSuggestion(
    val url: String,
    val title: String,
    val openTabId: String? = null,
)

internal object BrowsingLibraryRules {
    const val MAX_HISTORY_ENTRIES = 250
    const val MAX_FAVORITES = 100

    fun addHistory(
        current: List<HistoryEntry>,
        entry: HistoryEntry,
        limit: Int = MAX_HISTORY_ENTRIES,
    ): List<HistoryEntry> {
        val key = urlKey(entry.url) ?: return current
        val safeTitle = entry.title.trim().ifEmpty { displayHost(entry.url) }
        return buildList {
            add(entry.copy(title = safeTitle))
            current.forEach { existing ->
                if (urlKey(existing.url) != key) add(existing)
            }
        }.sortedByDescending(HistoryEntry::lastVisitedAt).take(limit.coerceAtLeast(0))
    }

    fun suggestions(
        history: List<HistoryEntry>,
        query: String,
        limit: Int,
    ): List<HistoryEntry> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        return history.asSequence()
            .filter { urlKey(it.url) != null }
            .mapNotNull { entry ->
                val title = entry.title.lowercase(Locale.ROOT)
                val url = entry.url.lowercase(Locale.ROOT)
                val host = displayHost(entry.url).lowercase(Locale.ROOT)
                val score = when {
                    normalizedQuery.isEmpty() -> 0
                    host.startsWith(normalizedQuery) -> 4
                    title.startsWith(normalizedQuery) -> 3
                    url.startsWith(normalizedQuery) -> 2
                    host.contains(normalizedQuery) ||
                        title.contains(normalizedQuery) ||
                        url.contains(normalizedQuery) -> 1
                    else -> return@mapNotNull null
                }
                ScoredHistory(entry, score)
            }
            .sortedWith(
                compareByDescending<ScoredHistory> { it.score }
                    .thenByDescending { it.entry.lastVisitedAt },
            )
            .distinctBy { scored -> urlKey(scored.entry.url) }
            .map(ScoredHistory::entry)
            .take(limit.coerceAtLeast(0))
            .toList()
    }

    fun addressSuggestions(
        history: List<HistoryEntry>,
        tabs: List<BrowserTab>,
        selectedTabId: String,
        isIncognito: Boolean,
        query: String,
        limit: Int,
    ): List<AddressSuggestion> {
        val openTabsByUrl = tabs.asSequence()
            .filter { tab ->
                tab.id != selectedTabId &&
                    tab.isIncognito == isIncognito &&
                    tab.url != BLANK_URL
            }
            .sortedByDescending(BrowserTab::lastAccessedAt)
            .mapNotNull { tab -> urlKey(tab.url)?.let { key -> key to tab } }
            .distinctBy { (key, _) -> key }
            .toMap()
        val candidates = buildList {
            openTabsByUrl.values.forEach { tab ->
                add(
                    HistoryEntry(
                        url = tab.url,
                        title = tab.title.trim().ifEmpty { displayHost(tab.url) },
                        lastVisitedAt = tab.lastAccessedAt,
                    ),
                )
            }
            if (!isIncognito) {
                history.forEach { entry ->
                    val key = urlKey(entry.url)
                    if (key != null && key !in openTabsByUrl) add(entry)
                }
            }
        }

        return suggestions(candidates, query, candidates.size)
            .map { entry ->
                AddressSuggestion(
                    url = entry.url,
                    title = entry.title,
                    openTabId = urlKey(entry.url)?.let(openTabsByUrl::get)?.id,
                )
            }
            .sortedByDescending { suggestion -> suggestion.openTabId != null }
            .take(limit.coerceAtLeast(0))
    }

    fun domainCompletion(
        history: List<HistoryEntry>,
        favorites: List<FavoriteEntry>,
        tabs: List<BrowserTab>,
        selectedTabId: String,
        isIncognito: Boolean,
        query: String,
    ): String? {
        val value = query.trim()
        val prefix = value.lowercase(Locale.ROOT)
        if (
            prefix.isEmpty() ||
            value != query ||
            prefix.any(Char::isWhitespace) ||
            prefix.any { it == '/' || it == ':' || it == '@' }
        ) {
            return null
        }
        val candidateUrls = buildList {
            tabs.asSequence()
                .filter { it.id != selectedTabId && it.isIncognito == isIncognito }
                .sortedByDescending(BrowserTab::lastAccessedAt)
                .map(BrowserTab::url)
                .forEach(::add)
            if (!isIncognito) {
                favorites.asSequence().map(FavoriteEntry::url).forEach(::add)
                history.asSequence()
                    .sortedByDescending(HistoryEntry::lastVisitedAt)
                    .map(HistoryEntry::url)
                    .forEach(::add)
            }
        }
        return candidateUrls.asSequence()
            .mapNotNull(::completionHost)
            .distinctBy { it.lowercase(Locale.ROOT) }
            .firstOrNull { host ->
                host.length > prefix.length && host.lowercase(Locale.ROOT).startsWith(prefix)
            }
    }

    fun toggleFavorite(
        current: List<FavoriteEntry>,
        entry: FavoriteEntry,
        limit: Int = MAX_FAVORITES,
    ): List<FavoriteEntry> {
        val key = urlKey(entry.url) ?: return current
        val existing = current.any { urlKey(it.url) == key }
        if (existing) return current.filterNot { urlKey(it.url) == key }
        val safeTitle = entry.title.trim().ifEmpty { displayHost(entry.url) }
        return (listOf(entry.copy(title = safeTitle)) + current)
            .distinctBy { urlKey(it.url) }
            .take(limit.coerceAtLeast(0))
    }

    fun isFavorite(favorites: List<FavoriteEntry>, url: String): Boolean {
        val key = urlKey(url) ?: return false
        return favorites.any { urlKey(it.url) == key }
    }

    private fun urlKey(url: String): String? = CanonicalWebUrl.key(url)

    private fun displayHost(url: String): String = runCatching {
        URI(url).host?.removePrefix("www.")
    }.getOrNull().orEmpty().ifEmpty { url }

    private fun completionHost(url: String): String? = runCatching {
        URI(url).host?.removePrefix("www.")?.takeIf(String::isNotBlank)
    }.getOrNull()

    private data class ScoredHistory(
        val entry: HistoryEntry,
        val score: Int,
    )
}
