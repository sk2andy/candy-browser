package dev.sk2andy.materialbrowser

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import dev.sk2andy.materialbrowser.data.RssFeedStore
import dev.sk2andy.materialbrowser.reader.rss.RssArticle
import dev.sk2andy.materialbrowser.reader.rss.RssFeed
import dev.sk2andy.materialbrowser.reader.rss.RssFeedNotifier
import dev.sk2andy.materialbrowser.reader.rss.RssRefreshScheduler
import dev.sk2andy.materialbrowser.reader.rss.RssReaderNavigation

class RssFeedWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
        RssRefreshScheduler.schedule(context)
    }

    companion object {
        internal fun updateAll(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            manager.getAppWidgetIds(ComponentName(appContext, RssFeedWidgetProvider::class.java))
                .forEach { update(appContext, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.rss_feed_widget)
            val feeds = RssFeedStore(context).feeds()
            val recent = feeds.flatMap { feed -> feed.articles.map { feed to it } }
                .sortedByDescending { (_, article) -> article.publishedAt }
                .take(MAX_ARTICLES)
            views.setTextViewText(R.id.rss_widget_title, context.getString(R.string.rss_widget_title))
            views.setTextViewText(R.id.rss_widget_count, context.getString(R.string.rss_widget_count, recent.size))
            val rows = listOf(
                Triple(R.id.rss_widget_row_one, R.id.rss_widget_feed_one, R.id.rss_widget_article_one),
                Triple(R.id.rss_widget_row_two, R.id.rss_widget_feed_two, R.id.rss_widget_article_two),
                Triple(R.id.rss_widget_row_three, R.id.rss_widget_feed_three, R.id.rss_widget_article_three),
            )
            rows.forEachIndexed { index, (rowId, feedViewId, articleViewId) ->
                val entry = recent.getOrNull(index)
                views.setViewVisibility(
                    rowId,
                    if (entry == null) android.view.View.GONE else android.view.View.VISIBLE,
                )
                if (entry != null) {
                    views.setTextViewText(feedViewId, entry.first.title)
                    views.setTextViewText(articleViewId, entry.second.title)
                    views.setOnClickPendingIntent(
                        rowId,
                        articleIntent(context, entry.first, entry.second),
                    )
                }
            }
            views.setViewVisibility(R.id.rss_widget_empty, if (recent.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE)
            views.setViewVisibility(
                R.id.rss_widget_divider_one,
                if (recent.size > 1) android.view.View.VISIBLE else android.view.View.GONE,
            )
            views.setViewVisibility(
                R.id.rss_widget_divider_two,
                if (recent.size > 2) android.view.View.VISIBLE else android.view.View.GONE,
            )
            views.setOnClickPendingIntent(
                R.id.rss_widget_all_feeds,
                PendingIntent.getActivity(
                    context,
                    OPEN_FEEDS_REQUEST_CODE,
                    RssReaderNavigation.feedsIntent(context),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            manager.updateAppWidget(widgetId, views)
        }

        private fun articleIntent(context: Context, feed: RssFeed, article: RssArticle): PendingIntent =
            PendingIntent.getActivity(
                context,
                RssFeedNotifier.notificationId(feed.id + article.id),
                Intent(context, MainActivity::class.java)
                    .setAction(RssFeedNotifier.ACTION_OPEN_ARTICLE)
                    .setData(Uri.parse(article.url))
                    .putExtra(RssFeedNotifier.EXTRA_ARTICLE_URL, article.url)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private const val OPEN_FEEDS_REQUEST_CODE = 501
        private const val MAX_ARTICLES = 3
    }
}
