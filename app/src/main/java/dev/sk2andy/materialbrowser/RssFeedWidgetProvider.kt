package dev.sk2andy.materialbrowser

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import dev.sk2andy.materialbrowser.browser.guest.GuestBubbleSessionStore
import dev.sk2andy.materialbrowser.data.AppDataTransferLock
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.feeds.FeedLaunchContract
import dev.sk2andy.materialbrowser.feeds.FeedRepository
import dev.sk2andy.materialbrowser.feeds.FeedState
import dev.sk2andy.materialbrowser.feeds.FeedWidgetRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RssFeedWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateWidgets(context, manager, ids)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        updateWidgets(context, manager, intArrayOf(appWidgetId))
    }

    private fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                updateMutex.withLock { ids.forEach { update(appContext, manager, it) } }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val updateMutex = Mutex()

        internal fun updateAll(context: Context) {
            val appContext = context.applicationContext
            scope.launch { updateAllNow(appContext) }
        }

        internal suspend fun updateAllNow(context: Context) = withContext(Dispatchers.IO) {
            updateMutex.withLock {
                val manager = AppWidgetManager.getInstance(context)
                manager.getAppWidgetIds(ComponentName(context, RssFeedWidgetProvider::class.java))
                    .forEach { update(context, manager, it) }
            }
        }

        private suspend fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = loadViews(context, widgetId)
            try {
                manager.updateAppWidget(widgetId, views)
            } catch (error: IllegalArgumentException) {
                Log.w("CandyFeeds", "Feed widget is no longer available", error)
            }
        }

        internal suspend fun loadViews(context: Context, widgetId: Int): RemoteViews {
            val guestSession = GuestBubbleSessionStore(context)
            if (guestSession.isLocked()) return createViews(context, FeedState(), widgetId)
            val profiles = BrowserSessionStore(context)
            val (available, activeId) = profiles.loadProfiles()
            // Launcher RemoteViews outlive unlock sessions, so protected profiles never publish titles.
            val profile = available.firstOrNull { it.id == activeId && it.protection == null }
            if (profile == null || AppDataTransferLock.isActive(context)) {
                return createViews(context, FeedState(), widgetId)
            }
            val repository = FeedRepository.get(context, profile.id)
            val loaded = repository.load().isSuccess
            val (currentProfiles, currentId) = profiles.loadProfiles()
            val stillAvailable = currentId == profile.id && currentProfiles.any {
                it.id == profile.id && it.protection == null
            }
            val state = if (loaded && stillAvailable && !guestSession.isLocked() &&
                !AppDataTransferLock.isActive(context)
            ) {
                repository.state.value
            } else {
                FeedState()
            }
            return createViews(context, state, widgetId, profile.id)
        }

        internal fun createViews(
            context: Context,
            state: FeedState,
            widgetId: Int,
            profileId: String? = null,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.rss_feed_widget)
            val recent = FeedWidgetRules.articles(state)
            views.setTextViewText(R.id.rss_widget_title, context.getString(R.string.rss_widget_title))
            views.setTextViewText(R.id.rss_widget_count, context.getString(R.string.rss_widget_count, recent.size))
            val items = RemoteViews.RemoteCollectionItems.Builder().setViewTypeCount(1)
            recent.forEachIndexed { index, article ->
                val row = RemoteViews(context.packageName, R.layout.rss_feed_widget_article)
                row.setTextViewText(R.id.rss_widget_feed, article.sourceTitle)
                row.setTextViewText(R.id.rss_widget_article, article.title)
                row.setOnClickFillInIntent(
                    R.id.rss_widget_row,
                    Intent()
                        .putExtra(FeedLaunchContract.EXTRA_ARTICLE, article.id),
                )
                items.addItem(index.toLong(), row)
            }
            views.setPendingIntentTemplate(R.id.rss_widget_articles, articleIntent(context, widgetId, profileId))
            views.setRemoteAdapter(R.id.rss_widget_articles, items.build())
            views.setEmptyView(R.id.rss_widget_articles, R.id.rss_widget_empty)
            views.setOnClickPendingIntent(
                R.id.rss_widget_all_feeds,
                PendingIntent.getActivity(
                    context,
                    OPEN_FEEDS_REQUEST_CODE,
                    FeedLaunchContract.discoverIntent(context)
                        .setData(Uri.parse("candy://feeds/widget/discover"))
                        .putExtra(FeedLaunchContract.EXTRA_PROFILE, profileId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            return views
        }

        private fun articleIntent(context: Context, widgetId: Int, profileId: String?): PendingIntent =
            PendingIntent.getActivity(
                context,
                widgetId,
                Intent(context, MainActivity::class.java)
                    .setAction(FeedLaunchContract.ACTION_OPEN)
                    .setData(Uri.parse("candy://feeds/widget/$widgetId"))
                    .putExtra(FeedLaunchContract.EXTRA_PROFILE, profileId)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                // Collection rows supply their article identity through a fill-in intent.
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )

        private const val OPEN_FEEDS_REQUEST_CODE = 501
    }
}
