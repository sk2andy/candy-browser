package dev.sk2andy.materialbrowser.reader.rss

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import dev.sk2andy.materialbrowser.data.RssFeedStore
import dev.sk2andy.materialbrowser.RssFeedWidgetProvider
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import java.util.concurrent.TimeUnit

internal class RssRefreshWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val store = RssFeedStore(applicationContext)
        val notifier = RssFeedNotifier(applicationContext)
        var hadFailure = false
        store.feeds().forEach { feed ->
            if (isStopped) return Result.retry()
            val parsed = RssFeedNetwork.fetch(feed.feedUrl)
            if (parsed == null) {
                hadFailure = true
                return@forEach
            }
            val newArticles = store.update(feed.id, parsed.title, parsed.articles)
            RssFeedWidgetProvider.updateAll(applicationContext)
            if (feed.notify && newArticles.isNotEmpty()) {
                notifier.notifyFeed(feed.copy(title = parsed.title), newArticles.first())
            }
        }
        return if (hadFailure) Result.retry() else Result.success()
    }

}

internal object RssFeedNetwork {
    private const val MAX_REDIRECTS = 4
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val USER_AGENT = "CandyBrowser RSS Reader"

    fun fetch(initialUrl: String): ParsedRssFeed? {
        var currentUrl = RssFeedRules.safeHttpUrl(initialUrl) ?: return null
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val connection = java.net.URL(currentUrl).openConnection() as? java.net.HttpURLConnection
                ?: return null
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.instanceFollowRedirects = false
                connection.setRequestProperty(
                    "Accept",
                    "application/atom+xml, application/rss+xml, application/xml, text/xml",
                )
                connection.setRequestProperty("User-Agent", USER_AGENT)
                val code = connection.responseCode
                if (code in 300..399) {
                    if (redirectCount == MAX_REDIRECTS) return null
                    val location = connection.getHeaderField("Location") ?: return null
                    currentUrl = RssFeedRules.safeHttpUrl(
                        java.net.URI(currentUrl).resolve(location).toString(),
                    ) ?: return null
                    return@repeat
                }
                if (code !in 200..299) return null
                val xml = connection.inputStream.use { input ->
                    val bytes = input.readBounded(RssFeedRules.MAX_FEED_BYTES)
                        ?: return null
                    bytes.toString(Charsets.UTF_8)
                }
                return RssFeedParser.parse(xml, currentUrl)
            } catch (_: Exception) {
                return null
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

    fun discover(pageUrl: String): List<Pair<String, String>> {
        val safeUrl = RssFeedRules.safeHttpUrl(pageUrl) ?: return emptyList()
        val connection = runCatching {
            java.net.URL(safeUrl).openConnection() as java.net.HttpURLConnection
        }.getOrNull() ?: return emptyList()
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (connection.responseCode !in 200..299) return emptyList()
            val html = connection.inputStream.use { input ->
                input.readBounded(RssFeedRules.MAX_FEED_BYTES)?.toString(Charsets.UTF_8)
            } ?: return emptyList()
            val pattern = Regex("<link\\b(?=[^>]*\\brel\\s*=\\s*['\\\"][^'\\\"]*alternate[^'\\\"]*['\\\"])(?=[^>]*\\btype\\s*=\\s*['\\\"](?:application/(?:rss|atom)\\+xml|application/xml|text/xml)['\\\"])[^>]*>", RegexOption.IGNORE_CASE)
            pattern.findAll(html).mapNotNull { match ->
                val tag = match.value
                val href = Regex("\\bhref\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"]", RegexOption.IGNORE_CASE)
                    .find(tag)?.groupValues?.get(1) ?: return@mapNotNull null
                val type = Regex("\\btype\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"]", RegexOption.IGNORE_CASE)
                    .find(tag)?.groupValues?.get(1).orEmpty()
                val url = RssFeedRules.safeHttpUrl(java.net.URI(safeUrl).resolve(href).toString())
                    ?: return@mapNotNull null
                url to type
            }.distinctBy { it.first }.take(8).toList()
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private fun java.io.InputStream.readBounded(maxBytes: Int): ByteArray? {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            if (output.size() + count > maxBytes) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}

internal object RssRefreshScheduler {
    private const val WORK_NAME = "rss_feed_refresh"
    const val DEFAULT_INTERVAL_MINUTES = 15L
    val INTERVAL_OPTIONS_MINUTES = listOf(15L, 30L, 60L, 180L, 360L, 720L, 1_440L)

    fun schedule(
        context: Context,
        intervalMinutes: Long = dev.sk2andy.materialbrowser.data.RssSettingsStore(context)
            .refreshIntervalMinutes(),
    ) {
        val feeds = RssFeedStore(context).feeds()
        val manager = WorkManager.getInstance(context.applicationContext)
        if (feeds.isEmpty()) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val interval = intervalMinutes.coerceIn(15L, 1_440L)
        val request = PeriodicWorkRequestBuilder<RssRefreshWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun refreshNow(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "$WORK_NAME-now",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RssRefreshWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build(),
        )
    }
}
