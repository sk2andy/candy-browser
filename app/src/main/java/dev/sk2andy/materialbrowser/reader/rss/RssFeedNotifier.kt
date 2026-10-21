package dev.sk2andy.materialbrowser.reader.rss

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R

internal class RssFeedNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NotificationManager::class.java)

    fun cancelFeed(feedId: String) {
        manager.cancel(TAG_PREFIX + feedId, notificationId(feedId))
    }

    fun notifyFeed(feed: RssFeed, article: RssArticle) {
        if (appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED || !manager.areNotificationsEnabled()
        ) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.rss_notifications_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = appContext.getString(R.string.rss_notifications_channel_description) },
        )
        val id = notificationId(feed.id)
        val intent = Intent(appContext, MainActivity::class.java)
            .setAction(ACTION_OPEN_ARTICLE)
            .setData(Uri.parse(article.url))
            .putExtra(EXTRA_ARTICLE_URL, article.url)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_history)
            .setContentTitle(feed.title)
            .setContentText(article.title)
            .setStyle(Notification.BigTextStyle().bigText(article.title))
            .setContentIntent(pendingIntent)
            .setCategory(Notification.CATEGORY_RECOMMENDATION)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        manager.notify(TAG_PREFIX + feed.id, id, notification)
    }

    companion object {
        const val CHANNEL_ID = "rss_feed_updates"
        const val ACTION_OPEN_ARTICLE = "dev.sk2andy.materialbrowser.action.OPEN_RSS_ARTICLE"
        const val EXTRA_ARTICLE_URL = "rss_article_url"
        private const val TAG_PREFIX = "rss-feed-"

        fun notificationId(feedId: String): Int = feedId.hashCode() and Int.MAX_VALUE
    }
}
