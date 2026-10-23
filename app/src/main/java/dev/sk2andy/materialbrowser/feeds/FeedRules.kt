package dev.sk2andy.materialbrowser.feeds

import dev.sk2andy.materialbrowser.reader.ReaderBlock
import dev.sk2andy.materialbrowser.reader.ReaderBlockKind
import dev.sk2andy.materialbrowser.reader.ReaderDocument
import dev.sk2andy.materialbrowser.reader.ReaderExtractionContract
import dev.sk2andy.materialbrowser.shared.browser.BrowserUrlRules
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

object FeedRules {
    const val MAX_SOURCES = 500
    const val MAX_ARTICLES = 5_000
    const val MAX_TEXT_CHARS = 100_000
    const val MAX_FEED_BYTES = 2_097_152
    const val MAX_ITEMS_PER_FEED = 500

    fun safeUrl(value: String): String? = ReaderExtractionContract.safeHttpUrl(value)

    fun identity(url: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest((BrowserUrlRules.httpUrlKey(url) ?: url).toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun filter(
        state: FeedState,
        filter: FeedInboxFilter = FeedInboxFilter.Unread,
        query: String = "",
        sourceId: String? = null,
        folderId: String? = null,
        tag: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<FeedArticle> {
        val today =
            Instant.ofEpochMilli(nowMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        val search = FeedSearch("", "", query, sourceId, folderId, tag = tag)
        return state.articles
            .filter { article ->
                (article.origin == FeedArticleOrigin.Feed || article.sourceIds.isNotEmpty()) &&
                    matches(article, search, state) &&
                    when (filter) {
                        FeedInboxFilter.Unread -> !article.isHidden && !article.isRead
                        FeedInboxFilter.Today ->
                            !article.isHidden && article.publishedAtMillis >= today
                        FeedInboxFilter.Starred -> !article.isHidden && article.isStarred
                        FeedInboxFilter.All -> !article.isHidden
                        FeedInboxFilter.Hidden -> article.isHidden
                    }
            }
            .sortedWith(compareByDescending<FeedArticle> { it.publishedAtMillis }.thenBy { it.id })
    }

    fun matches(article: FeedArticle, search: FeedSearch, state: FeedState): Boolean =
        (!search.unreadOnly || !article.isRead) &&
            (search.tag == null || search.tag in article.tags) &&
            scopeMatches(article, search.sourceId, search.folderId, state) &&
            wordsMatch(article, search.query)

    fun ruleMatches(article: FeedArticle, rule: FeedRule, state: FeedState): Boolean =
        article.origin == FeedArticleOrigin.Feed &&
            scopeMatches(article, rule.sourceId, rule.folderId, state) &&
            wordsMatch(article, rule.query)

    fun document(article: FeedArticle): ReaderDocument =
        article.offlineDocument
            ?: ReaderDocument(
                title = article.title,
                sourceUrl = article.url,
                siteName =
                    runCatching { java.net.URI(article.url).host.orEmpty() }.getOrDefault(""),
                blocks =
                    article.content
                        .ifBlank { article.summary }
                        .chunked(12_000)
                        .map { ReaderBlock(ReaderBlockKind.Paragraph, it) },
            )

    internal fun merge(
        state: FeedState,
        source: FeedSource,
        incoming: List<FeedArticle>,
        now: Long,
    ): FeedState {
        val articles = state.articles.associateByTo(linkedMapOf()) { it.id }
        incoming.forEach { fetched ->
            val old = articles[fetched.id]
            var article =
                if (old == null) {
                    fetched.copy(
                        sourceIds = setOf(source.id),
                        receivedAtMillis = now,
                        publishedAtMillis = fetched.publishedAtMillis.takeIf { it > 0 } ?: now,
                    )
                } else {
                    val changed = old.content != fetched.content || old.title != fetched.title
                    fetched.copy(
                        sourceIds = old.sourceIds + source.id,
                        origin = old.origin,
                        websiteSnapshotId = old.websiteSnapshotId,
                        receivedAtMillis = old.receivedAtMillis,
                        publishedAtMillis =
                            fetched.publishedAtMillis.takeIf { it > 0 } ?: old.publishedAtMillis,
                        updatedAtMillis = if (changed) now else old.updatedAtMillis,
                        previousContent = if (changed) old.content else old.previousContent,
                        isRead = old.isRead,
                        readingProgress = old.readingProgress,
                        isStarred = old.isStarred,
                        isHidden = old.isHidden,
                        tags = old.tags,
                        offlineStatus = old.offlineStatus,
                        offlineDocument = old.offlineDocument,
                        offlineDocumentId = old.offlineDocumentId,
                    )
                }
            // Rules apply to new items only. Refresh never resets an explicit reader decision.
            if (old == null)
                state.rules
                    .filter { it.enabled && ruleMatches(article, it, state) }
                    .forEach { rule ->
                        article =
                            when (rule.action) {
                                FeedRuleAction.MarkRead -> article.copy(isRead = true)
                                FeedRuleAction.Star -> article.copy(isStarred = true)
                                FeedRuleAction.Hide -> article.copy(isHidden = true)
                                FeedRuleAction.Tag -> article.copy(tags = article.tags + rule.tag)
                                FeedRuleAction.Notify -> article
                            }
                    }
            articles[article.id] = article
        }
        val all =
            articles.values.sortedWith(
                compareByDescending<FeedArticle> { isProtected(it) }
                    .thenByDescending { it.publishedAtMillis }
                    .thenBy { it.id }
            )
        val protectedCount = all.count(::isProtected)
        return state.copy(articles = all.take(maxOf(MAX_ARTICLES, protectedCount)))
    }

    private fun isProtected(article: FeedArticle): Boolean =
        article.origin == FeedArticleOrigin.Website ||
            article.isStarred ||
            article.offlineDocument != null ||
            article.offlineDocumentId != null

    private fun wordsMatch(article: FeedArticle, query: String): Boolean {
        val haystack = "${article.title}\n${article.summary}\n${article.content}\n${article.author}"
        return query.trim().split(Regex("\\s+")).all { haystack.contains(it, ignoreCase = true) }
    }

    private fun scopeMatches(
        article: FeedArticle,
        sourceId: String?,
        folderId: String?,
        state: FeedState,
    ): Boolean =
        (sourceId == null || sourceId in article.sourceIds) &&
            (folderId == null ||
                state.sources.any { it.id in article.sourceIds && it.folderId == folderId })
}
