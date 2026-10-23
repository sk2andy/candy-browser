package dev.sk2andy.materialbrowser.feeds

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dev.sk2andy.materialbrowser.reader.ReaderDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Fetches inert HTML, never creates a browser tab or executes publisher scripts. */
class FeedArticleLoader(
    context: Context,
    private val repository: FeedRepository,
    private val fetchHtml: suspend (String) -> String = FeedNetwork::fetch,
) {
    private val connectivity =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    suspend fun read(article: FeedArticle): ReaderDocument {
        check(!repository.isPrivate) { "Private sessions have no saved feed collection" }
        repository.requireAccess()
        val stored =
            article.offlineDocument
                ?: if (
                    article.offlineDocumentId != null &&
                        article.offlineStatus == FeedOfflineStatus.Ready
                )
                    repository.loadOfflineDocument(article.id).getOrThrow()
                else null
        val legacyPreview =
            stored != null && FeedArticleContentRules.isLegacyPreview(article, stored)
        if (stored != null && !legacyPreview) return stored
        // Opening an article and listening both request the publisher's full Reader document.
        // A source's feed-text preference must not turn the listening queue into a teaser queue.
        val document =
            try {
                val html = fetchHtml(article.url)
                repository.requireAccess()
                withContext(Dispatchers.Default) { extract(article, html) }
                    ?: fullFeedDocument(article)
                    ?: error("Full article unavailable")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                repository.requireAccess()
                fullFeedDocument(article) ?: throw error
            }
        repository.requireAccess()
        if (legacyPreview) {
            // Repair an already-saved legacy preview. A failed replacement keeps the old file;
            // the successfully fetched full text can still be read in this session.
            repository.updateOffline(article.id, FeedOfflineStatus.Ready, document)
        }
        repository.requireAccess()
        return document
    }

    private fun fullFeedDocument(article: FeedArticle): ReaderDocument? =
        FeedRules.document(article).takeIf { FeedArticleContentRules.hasFeedBody(article) }

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
        internal fun extract(article: FeedArticle, html: String): ReaderDocument? =
            FeedArticleExtraction.extract(article, html)
    }
}
