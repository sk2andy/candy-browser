package dev.sk2andy.materialbrowser

import android.app.PendingIntent
import android.app.Application
import dev.sk2andy.materialbrowser.browser.guest.GuestBubbleSessionStore
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetFormat
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetGridSizingRules
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetSnapshot
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetRefreshScheduler
import dev.sk2andy.materialbrowser.data.SiteWidgetStore
import dev.sk2andy.materialbrowser.data.SiteWidgetGridSizingStore
import java.util.concurrent.Executors

open class SiteWidgetProvider : AppWidgetProvider() {
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        execute {
            try {
                appWidgetIds.forEach { update(context, manager, it) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val pending = goAsync()
        execute {
            try {
                val store = SiteWidgetStore(context.applicationContext)
                appWidgetIds.forEach {
                    SiteWidgetRefreshScheduler.cancel(context, it)
                    store.deleteWidget(it)
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                SiteWidgetRefreshScheduler.refresh(context, widgetId)
            }
            return
        }
        if (intent.action != ACTION_PINNED) {
            super.onReceive(context, intent)
            return
        }
        val id = intent.getStringExtra(EXTRA_SNAPSHOT_ID) ?: return
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val pending = goAsync()
        execute {
            try {
                val store = SiteWidgetStore(context.applicationContext)
                val snapshot = store.load(id) ?: return@execute
                val manager = AppWidgetManager.getInstance(context)
                if (manager.getAppWidgetInfo(widgetId)?.provider != component(context, snapshot.format)) {
                    return@execute
                }
                if (store.bind(widgetId, id)) {
                    update(context, manager, widgetId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        internal const val ACTION_OPEN = "dev.sk2andy.materialbrowser.site_widget.OPEN"
        internal const val ACTION_EDIT = "dev.sk2andy.materialbrowser.site_widget.EDIT"
        internal const val ACTION_REFRESH = "dev.sk2andy.materialbrowser.site_widget.REFRESH"
        internal const val EXTRA_WIDGET_ID = "site_widget.widget_id"
        internal const val EXTRA_SNAPSHOT_ID = "site_widget.snapshot_id"
        private const val ACTION_PINNED = "dev.sk2andy.materialbrowser.site_widget.PINNED"
        private val executor = Executors.newSingleThreadExecutor()

        internal fun execute(block: () -> Unit) {
            executor.execute(block)
        }

        internal fun component(context: Context, format: SiteWidgetFormat): ComponentName {
            require(format.isValid)
            val className = when (format) {
                SiteWidgetFormat.FourByThree -> SiteWidgetProvider::class.java.name
                SiteWidgetFormat.FourByFour -> SquareSiteWidgetProvider::class.java.name
                else -> "${SiteWidgetProvider::class.java.packageName}.SiteWidget${format.columns}x${format.rows}Provider"
            }
            return ComponentName(context.packageName, className)
        }

        internal fun pinCallback(context: Context, snapshot: SiteWidgetSnapshot): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_PINNED)
                .setComponent(component(context, snapshot.format))
                .setData(Uri.parse("candy-site-widget://pin/${snapshot.id}"))
                .putExtra(EXTRA_SNAPSHOT_ID, snapshot.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        internal fun refreshAll(context: Context) {
            val appContext = context.applicationContext
            execute {
                val manager = AppWidgetManager.getInstance(appContext)
                SiteWidgetFormat.entries.forEach { format ->
                    manager.getAppWidgetIds(component(appContext, format)).forEach { id ->
                        update(appContext, manager, id)
                    }
                }
            }
        }

        internal fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val store = SiteWidgetStore(context.applicationContext)
            val provider = manager.getAppWidgetInfo(widgetId)?.provider
            SiteWidgetFormat.entries.firstOrNull { component(context, it) == provider }?.let { format ->
                rememberLauncherCellAspect(context, manager, widgetId, format, store)
            }
            val snapshot = store.snapshotForWidget(widgetId)
                .takeUnless { GuestBubbleSessionStore(context).isLocked() }
            if (Application.getProcessName() == context.packageName) {
                SiteWidgetRefreshScheduler.schedule(context, widgetId, snapshot)
            }
            val bitmap = snapshot?.let { store.loadBitmap(it.id) }
            try {
                manager.updateAppWidget(widgetId, views(context, snapshot, bitmap, widgetId))
            } finally {
                bitmap?.recycle()
            }
        }

        private fun rememberLauncherCellAspect(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            format: SiteWidgetFormat,
            store: SiteWidgetStore,
        ) {
            val options = manager.getAppWidgetOptions(widgetId)
            val ratio = SiteWidgetGridSizingRules.cellAspectRatio(
                minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
                minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
                maxWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
                maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
                isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
                format = format,
            ) ?: return
            val launcherPackage = SiteWidgetGridSizingStore(context).currentLauncherPackage() ?: return
            store.saveLauncherCellAspectRatio(launcherPackage, ratio)
        }

        internal fun views(
            context: Context,
            snapshot: SiteWidgetSnapshot?,
            bitmap: Bitmap?,
            widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
        ): RemoteViews = RemoteViews(context.packageName, R.layout.site_widget).apply {
            val locked = GuestBubbleSessionStore(context).isLocked()
            val hasImage = !locked && snapshot != null && bitmap != null
            setViewVisibility(R.id.site_widget_image, if (hasImage) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.site_widget_empty, if (hasImage) View.GONE else View.VISIBLE)
            setViewVisibility(R.id.site_widget_refresh, if (hasImage) View.VISIBLE else View.GONE)
            setImageViewResource(R.id.site_widget_image, android.R.color.transparent)
            setContentDescription(R.id.site_widget_image, context.getString(R.string.site_widget_open))
            val setup = if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) action(context, widgetId, ACTION_EDIT) else null
            setOnClickPendingIntent(R.id.site_widget_empty, setup)
            setOnClickPendingIntent(R.id.site_widget_image, null)
            setOnClickPendingIntent(R.id.site_widget_refresh, null)
            if (locked) setTextViewText(R.id.site_widget_empty, context.getString(R.string.guest_bubble_widget_locked))
            if (hasImage && snapshot != null && bitmap != null) {
                setImageViewBitmap(R.id.site_widget_image, bitmap)
                setContentDescription(R.id.site_widget_image, snapshot.source.title)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val open = action(context, widgetId, ACTION_OPEN)
                    setOnClickPendingIntent(R.id.site_widget_image, open)
                    setOnClickPendingIntent(
                        R.id.site_widget_refresh,
                        PendingIntent.getBroadcast(
                            context,
                            widgetId,
                            Intent(context, SiteWidgetProvider::class.java)
                                .setAction(ACTION_REFRESH)
                                .setData(Uri.parse("candy-site-widget://refresh/$widgetId"))
                                .putExtra(EXTRA_WIDGET_ID, widgetId),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    )
                }
            }
        }

        private fun action(context: Context, widgetId: Int, action: String): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setAction(action)
                .setData(Uri.parse("candy-site-widget://widget/$widgetId/${action.substringAfterLast('.')}"))
                .putExtra(EXTRA_WIDGET_ID, widgetId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class SquareSiteWidgetProvider : SiteWidgetProvider()
