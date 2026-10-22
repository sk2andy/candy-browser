package dev.sk2andy.materialbrowser.reader.rss

import android.content.Context
import android.util.Log
import dev.sk2andy.materialbrowser.data.RssFeedStore
import dev.sk2andy.materialbrowser.data.RssReadStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class RssInboxRepository private constructor(context: Context) {
    private val feeds = RssFeedStore(context.applicationContext)
    private val read = RssReadStore(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operations = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (operation in operations) {
                try {
                    operation()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    Log.e(TAG, "Feed inbox operation failed", error)
                }
            }
        }
    }

    fun load(onLoaded: (RssInboxState) -> Unit) {
        check(operations.trySend {
            val currentFeeds = feeds.feeds()
            val state = RssInboxState(currentFeeds, read.readKeys(currentFeeds))
            withContext(Dispatchers.Main.immediate) { onLoaded(state) }
        }.isSuccess)
    }

    fun markRead(feedId: String, articleId: String) {
        check(operations.trySend {
            if (!read.markRead(feeds.feeds(), feedId, articleId)) {
                Log.w(TAG, "Could not save the feed article read state")
            }
        }.isSuccess)
    }

    fun markReadUrl(url: String) {
        check(operations.trySend {
            val currentFeeds = feeds.feeds()
            val keys = currentFeeds.flatMap { feed ->
                feed.articles.filter { it.url == url }.map { article ->
                    RssInboxRules.articleKey(feed.id, article.id)
                }
            }.toSet()
            if (keys.isNotEmpty() && !read.markRead(currentFeeds, keys)) {
                Log.w(TAG, "Could not save the feed article read state")
            }
        }.isSuccess)
    }

    companion object {
        private const val TAG = "RssInbox"
        @Volatile private var instance: RssInboxRepository? = null

        fun get(context: Context): RssInboxRepository = instance ?: synchronized(this) {
            instance ?: RssInboxRepository(context.applicationContext).also { instance = it }
        }
    }
}
