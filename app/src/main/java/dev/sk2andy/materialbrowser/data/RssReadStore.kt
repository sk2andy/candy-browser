package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.util.AtomicFile
import dev.sk2andy.materialbrowser.reader.rss.RssFeed
import dev.sk2andy.materialbrowser.reader.rss.RssInboxRules
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal class RssReadStore(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.filesDir, FILE_NAME))

    fun readKeys(feeds: List<RssFeed>): Set<String> = synchronized(LOCK) {
        RssInboxRules.retainedReadKeys(feeds, load())
    }

    fun markRead(feeds: List<RssFeed>, feedId: String, articleId: String): Boolean =
        markRead(feeds, setOf(RssInboxRules.articleKey(feedId, articleId)))

    fun markRead(feeds: List<RssFeed>, articleKeys: Set<String>): Boolean = synchronized(LOCK) {
        val previous = load()
        val keys = RssInboxRules.retainedReadKeys(feeds, previous + articleKeys)
        if (keys == previous) return true
        val root = JSONObject().put("read", JSONArray(keys.toList()))
        runCatching {
            val stream = file.startWrite()
            try {
                stream.write(root.toString().toByteArray(Charsets.UTF_8))
                file.finishWrite(stream)
            } catch (error: Throwable) {
                file.failWrite(stream)
                throw error
            }
            true
        }.getOrDefault(false)
    }

    private fun load(): Set<String> = runCatching {
        val encoded = file.openRead().use { input ->
            if (input.channel.size() > MAX_FILE_BYTES) return emptySet()
            input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        val items = JSONObject(encoded).optJSONArray("read") ?: return emptySet()
        buildSet {
            for (index in 0 until minOf(items.length(), RssInboxRules.MAX_READ_ARTICLES)) {
                items.optString(index).takeIf { key ->
                    key.length == 64 && key.all { it in '0'..'9' || it in 'a'..'f' }
                }?.let(::add)
            }
        }
    }.getOrDefault(emptySet())

    companion object {
        private const val FILE_NAME = "rss-read-articles.json"
        private const val MAX_FILE_BYTES = 256 * 1024
        private val LOCK = Any()
    }
}
