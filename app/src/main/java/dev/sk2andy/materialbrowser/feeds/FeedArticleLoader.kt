package dev.sk2andy.materialbrowser.feeds

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dev.sk2andy.materialbrowser.reader.ReaderDocument
import dev.sk2andy.materialbrowser.reader.ReaderExtractionBlock
import dev.sk2andy.materialbrowser.reader.ReaderExtractionContract
import dev.sk2andy.materialbrowser.reader.ReaderExtractionPayload
import dev.sk2andy.materialbrowser.reader.ReaderExtractionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Fetches inert HTML, never creates a browser tab or executes publisher scripts. */
class FeedArticleLoader(context: Context, private val repository: FeedRepository) {
    private val connectivity =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    suspend fun read(article: FeedArticle): ReaderDocument {
        repository.requireAccess()
        article.offlineDocument?.let {
            return it
        }
        if (article.offlineDocumentId != null && article.offlineStatus == FeedOfflineStatus.Ready) {
            return repository.loadOfflineDocument(article.id).getOrThrow()
                ?: error("Offline article unavailable")
        }
        val source = repository.state.value.sources.firstOrNull { it.id in article.sourceIds }
        if (source?.readingMode != FeedReadingMode.Reader) return FeedRules.document(article)
        return try {
            val html = FeedNetwork.fetch(article.url)
            repository.requireAccess()
            withContext(Dispatchers.Default) { extract(article, html) }
                ?: FeedRules.document(article)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            FeedRules.document(article)
        }
    }

    suspend fun download(article: FeedArticle): Result<Unit> {
        if (repository.state.value.settings.wifiOnly && !onWifi()) {
            return repository.updateOffline(article.id, FeedOfflineStatus.Waiting)
        }
        repository.updateOffline(article.id, FeedOfflineStatus.Downloading).getOrElse {
            return Result.failure(it)
        }
        return try {
            val document = read(article)
            require(document.blocks.any { it.text.isNotBlank() }) {
                "Article has no downloadable text"
            }
            val result = repository.updateOffline(article.id, FeedOfflineStatus.Ready, document)
            if (result.isFailure) repository.updateOffline(article.id, FeedOfflineStatus.Failed)
            result
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                repository.updateOffline(article.id, FeedOfflineStatus.Waiting)
            }
            throw cancelled
        } catch (error: Exception) {
            repository.updateOffline(article.id, FeedOfflineStatus.Failed)
            Result.failure(error)
        }
    }

    private fun onWifi(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        return connectivity
            .getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    companion object {
        internal fun extract(article: FeedArticle, html: String): ReaderDocument? {
            val inert =
                html
                    .take(FeedRules.MAX_FEED_BYTES)
                    .replace(
                        Regex(
                            "<(script|style|noscript|nav|aside|footer)\\b[^>]*>.*?</\\1\\s*>",
                            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                        ),
                        "",
                    )
            val body =
                Regex(
                        "<(article|main)\\b[^>]*>(.*?)</\\1\\s*>",
                        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                    )
                    .find(inert)
                    ?.groupValues
                    ?.get(2) ?: return null
            val blocks =
                Regex(
                        "<(h[1-6]|p|blockquote|li)\\b[^>]*>(.*?)</\\1\\s*>",
                        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                    )
                    .findAll(body)
                    .take(ReaderExtractionContract.MAX_BLOCKS)
                    .map { match ->
                        val tag = match.groupValues[1].lowercase()
                        ReaderExtractionBlock(
                            kind =
                                when {
                                    tag.startsWith("h") -> "heading"
                                    tag == "li" -> "listitem"
                                    tag == "blockquote" -> "quote"
                                    else -> "paragraph"
                                },
                            text = FeedParser.plain(match.groupValues[2]),
                            level = tag.removePrefix("h").toIntOrNull() ?: 0,
                        )
                    }
                    .toList()
            val result =
                ReaderExtractionContract.sanitize(
                    ReaderExtractionPayload(
                        title = article.title,
                        sourceUrl = article.url,
                        siteName = java.net.URI(article.url).host,
                        blocks = blocks,
                    )
                )
            return (result as? ReaderExtractionResult.Success)?.document
        }
    }
}
