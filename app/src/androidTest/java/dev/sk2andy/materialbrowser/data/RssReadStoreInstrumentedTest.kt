package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.reader.rss.RssArticle
import dev.sk2andy.materialbrowser.reader.rss.RssFeed
import dev.sk2andy.materialbrowser.reader.rss.RssInboxRules
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RssReadStoreInstrumentedTest {
    private val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var directory: File
    private lateinit var context: Context

    @Before
    fun setUp() {
        directory = File(baseContext.cacheDir, "rss-read-${UUID.randomUUID()}")
        assertTrue(directory.mkdirs())
        context = IsolatedFilesContext(baseContext, directory)
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun readStatePersistsAcrossStoreInstancesAndDropsRemovedFeedKeys() {
        val article = RssArticle(
            id = "artikel-über-📰",
            title = "Unicode bleibt erhalten",
            url = "https://example.com/article",
            summary = "",
            publishedAt = 1L,
        )
        val feed = RssFeed(
            id = "feed-über",
            title = "Unicode Feed",
            feedUrl = "https://example.com/rss",
            siteUrl = "https://example.com",
            articles = listOf(article),
        )
        val store = RssReadStore(context)

        assertTrue(store.markRead(listOf(feed), feed.id, article.id))
        assertEquals(
            setOf(RssInboxRules.articleKey(feed.id, article.id)),
            RssReadStore(context).readKeys(listOf(feed)),
        )
        assertEquals(emptySet<String>(), RssReadStore(context).readKeys(emptyList()))
    }

    private class IsolatedFilesContext(
        base: Context,
        private val isolatedFiles: File,
    ) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File = isolatedFiles
    }
}
