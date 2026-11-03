package dev.sk2andy.materialbrowser.reader.rss

import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(maxSdkVersion = 32)
class RssFeedNotifierInstrumentedTest {
    @Test
    fun postsNotificationWithoutAndroid13Permission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val notifier = RssFeedNotifier(context)
        val feed = RssFeed("test-feed", "Feed", "https://example.com/feed", "https://example.com")
        val article = RssArticle("test-article", "Article", "https://example.com/article", "", 0L)

        try {
            notifier.notifyFeed(feed, article)
            assertTrue(manager.activeNotifications.any { it.id == RssFeedNotifier.notificationId(feed.id) })
        } finally {
            notifier.cancelFeed(feed.id)
        }
    }
}
