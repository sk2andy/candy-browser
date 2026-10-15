package dev.sk2andy.materialbrowser.browser.sitewidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.sk2andy.materialbrowser.SiteWidgetProvider
import dev.sk2andy.materialbrowser.data.SiteWidgetStore
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object SiteWidgetRefreshScheduler {
    const val WIDGET_ID = "widget_id"

    fun schedule(context: Context, widgetId: Int, snapshot: SiteWidgetSnapshot?) {
        val manager = WorkManager.getInstance(context.applicationContext)
        if (snapshot == null || !SiteWidgetRefreshRules.hasGeometry(snapshot.source)) {
            cancel(context, widgetId)
            return
        }
        val interval = snapshot.refreshIntervalMinutes
        if (interval == 0) {
            manager.cancelUniqueWork(periodicName(widgetId))
            return
        }
        val request = PeriodicWorkRequestBuilder<SiteWidgetRefreshWorker>(interval.toLong(), TimeUnit.MINUTES)
            .setInputData(workDataOf(WIDGET_ID to widgetId))
            .setConstraints(networkConstraints())
            .build()
        manager.enqueueUniquePeriodicWork(periodicName(widgetId), ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun refresh(context: Context, widgetId: Int) {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val request = OneTimeWorkRequestBuilder<SiteWidgetRefreshWorker>()
            .setInputData(workDataOf(WIDGET_ID to widgetId))
            .setConstraints(networkConstraints())
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(manualName(widgetId), ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context, widgetId: Int) {
        WorkManager.getInstance(context.applicationContext).apply {
            cancelUniqueWork(periodicName(widgetId))
            cancelUniqueWork(manualName(widgetId))
        }
    }

    private fun periodicName(id: Int) = "site_widget_periodic_$id"
    private fun manualName(id: Int) = "site_widget_manual_$id"
    private fun networkConstraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
}

internal class SiteWidgetRefreshWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = renderMutex.withLock {
        val widgetId = inputData.getInt(SiteWidgetRefreshScheduler.WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return@withLock Result.success()
        val store = SiteWidgetStore(applicationContext)
        val manager = AppWidgetManager.getInstance(applicationContext)
        val snapshot = store.snapshotForWidget(widgetId)
        if (snapshot == null) {
            SiteWidgetProvider.execute { SiteWidgetProvider.update(applicationContext, manager, widgetId) }
            return@withLock Result.success()
        }
        if (manager.getAppWidgetInfo(widgetId)?.provider != SiteWidgetProvider.component(applicationContext, snapshot.format)) {
            return@withLock Result.success()
        }
        val bitmap = SiteWidgetPageRenderer(applicationContext).render(snapshot) ?: return@withLock Result.success()
        try {
            val bounds = SiteWidgetCropRules.pixelBounds(snapshot.crop, bitmap.width, bitmap.height)
                ?: return@withLock Result.success()
            val selected = Bitmap.createBitmap(bitmap, bounds.left, bounds.top, bounds.width, bounds.height)
            val scale = minOf(1f, SiteWidgetSnapshotRules.MAX_BITMAP_DIMENSION.toFloat() / maxOf(selected.width, selected.height))
            val bounded = if (scale < 1f) Bitmap.createScaledBitmap(
                selected,
                (selected.width * scale).toInt().coerceAtLeast(1),
                (selected.height * scale).toInt().coerceAtLeast(1),
                true,
            ) else selected
            try {
                // The same queue publishes profile invalidations. Recheck the binding and profile there.
                suspendCoroutine { continuation ->
                    SiteWidgetProvider.execute {
                        try {
                            if (!isStopped && manager.getAppWidgetInfo(widgetId)?.provider ==
                                SiteWidgetProvider.component(applicationContext, snapshot.format)
                            ) {
                                store.replaceIfCurrent(
                                    widgetId,
                                    snapshot.id,
                                    snapshot.copy(id = UUID.randomUUID().toString(), createdAtMillis = System.currentTimeMillis()),
                                    bounded,
                                )
                                SiteWidgetProvider.update(applicationContext, manager, widgetId)
                            }
                        } finally {
                            continuation.resume(Unit)
                        }
                    }
                }
            } finally {
                if (bounded !== selected) bounded.recycle()
                if (selected !== bitmap) selected.recycle()
            }
            Result.success()
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        val renderMutex = Mutex()
    }
}
