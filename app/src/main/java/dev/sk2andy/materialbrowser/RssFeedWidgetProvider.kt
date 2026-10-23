package dev.sk2andy.materialbrowser

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import dev.sk2andy.materialbrowser.data.RssFeedStore
import dev.sk2andy.materialbrowser.reader.rss.RssFeed
import dev.sk2andy.materialbrowser.reader.rss.RssFeedNotifier
import dev.sk2andy.materialbrowser.reader.rss.RssRefreshScheduler
import dev.sk2andy.materialbrowser.reader.rss.RssReaderNavigation

class RssFeedWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
        RssRefreshScheduler.schedule(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        update(context, manager, appWidgetId)
    }

    companion object {
        internal fun updateAll(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            manager.getAppWidgetIds(ComponentName(appContext, RssFeedWidgetProvider::class.java))
                .forEach { update(appContext, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            manager.updateAppWidget(widgetId, createViews(context, RssFeedStore(context).feeds(), widgetId))
        }

        internal fun createViews(context: Context, feeds: List<RssFeed>, widgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.rss_feed_widget)
            val recent = feeds.flatMap { feed -> feed.articles.map { feed to it } }
                .sortedByDescending { (_, article) -> article.publishedAt }
                .take(MAX_ARTICLES)
            views.setTextViewText(R.id.rss_widget_title, context.getString(R.string.rss_widget_title))
            views.setTextViewText(R.id.rss_widget_count, context.getString(R.string.rss_widget_count, recent.size))
            val items = RemoteViews.RemoteCollectionItems.Builder().setViewTypeCount(1)
            recent.forEachIndexed { index, (feed, article) ->
                val row = RemoteViews(context.packageName, R.layout.rss_feed_widget_article)
                row.setTextViewText(R.id.rss_widget_feed, feed.title)
                row.setTextViewText(R.id.rss_widget_article, article.title)
                row.setOnClickFillInIntent(
                    R.id.rss_widget_row,
                    Intent()
                        .setData(Uri.parse(article.url))
                        .putExtra(RssFeedNotifier.EXTRA_ARTICLE_URL, article.url),
                )
                items.addItem(index.toLong(), row)
            }
            views.setPendingIntentTemplate(R.id.rss_widget_articles, articleIntent(context, widgetId))
            views.setRemoteAdapter(R.id.rss_widget_articles, items.build())
            views.setEmptyView(R.id.rss_widget_articles, R.id.rss_widget_empty)
            views.setOnClickPendingIntent(
                R.id.rss_widget_all_feeds,
                PendingIntent.getActivity(
                    context,
                    OPEN_FEEDS_REQUEST_CODE,
                    RssReaderNavigation.feedsIntent(context),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            return views
        }

        private fun articleIntent(context: Context, widgetId: Int): PendingIntent =
            PendingIntent.getActivity(
                context,
                widgetId,
                Intent(context, MainActivity::class.java)
                    .setAction(RssFeedNotifier.ACTION_OPEN_ARTICLE)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                // Collection rows supply their article URL through a fill-in intent.
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )

        private const val OPEN_FEEDS_REQUEST_CODE = 501
        // Keep the RemoteViews payload bounded when many feeds are subscribed.
        private const val MAX_ARTICLES = 50
    }
}
