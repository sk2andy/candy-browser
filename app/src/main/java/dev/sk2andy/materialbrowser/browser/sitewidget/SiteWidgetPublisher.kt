package dev.sk2andy.materialbrowser.browser.sitewidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import dev.sk2andy.materialbrowser.SiteWidgetProvider
import dev.sk2andy.materialbrowser.data.SiteWidgetStore
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class SiteWidgetPublisher(context: Context) {
    private val appContext = context.applicationContext
    private val store = SiteWidgetStore(appContext)

    suspend fun prepare(
        capture: SiteWidgetCapture,
        format: SiteWidgetFormat,
        crop: SiteWidgetCrop,
        refreshIntervalMinutes: Int = 0,
    ): SiteWidgetSnapshot? = withContext(Dispatchers.IO) {
        val bitmap = capture.bitmap
        if (bitmap.isRecycled) return@withContext null
        val bounds = SiteWidgetCropRules.pixelBounds(crop, bitmap.width, bitmap.height)
            ?: return@withContext null
        val selected = Bitmap.createBitmap(bitmap, bounds.left, bounds.top, bounds.width, bounds.height)
        val scale = minOf(
            1f,
            SiteWidgetSnapshotRules.MAX_BITMAP_DIMENSION.toFloat() / maxOf(selected.width, selected.height),
        )
        val bounded = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                selected,
                (selected.width * scale).toInt().coerceAtLeast(1),
                (selected.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            selected
        }
        try {
            val snapshot = SiteWidgetSnapshotRules.sanitize(
                SiteWidgetSnapshot(
                    id = UUID.randomUUID().toString(),
                    source = capture.source,
                    format = format,
                    crop = crop,
                    createdAtMillis = System.currentTimeMillis(),
                    refreshIntervalMinutes = refreshIntervalMinutes,
                ),
            ) ?: return@withContext null
            snapshot.takeIf { store.save(it, bounded) }
        } finally {
            if (bounded !== selected) bounded.recycle()
            if (selected !== bitmap) selected.recycle()
        }
    }

    fun canPin(): Boolean = AppWidgetManager.getInstance(appContext).isRequestPinAppWidgetSupported

    fun requestPin(snapshot: SiteWidgetSnapshot, bitmap: Bitmap): Boolean {
        val manager = AppWidgetManager.getInstance(appContext)
        if (!manager.isRequestPinAppWidgetSupported) return false
        return runCatching {
            manager.requestPinAppWidget(
                SiteWidgetProvider.component(appContext, snapshot.format),
                Bundle().apply {
                    putParcelable(
                        AppWidgetManager.EXTRA_APPWIDGET_PREVIEW,
                        SiteWidgetProvider.views(appContext, snapshot, bitmap),
                    )
                },
                SiteWidgetProvider.pinCallback(appContext, snapshot),
            )
        }.getOrDefault(false)
    }

    suspend fun replace(widgetId: Int, snapshot: SiteWidgetSnapshot): Boolean = withContext(Dispatchers.IO) {
        val manager = AppWidgetManager.getInstance(appContext)
        if (manager.getAppWidgetInfo(widgetId)?.provider != SiteWidgetProvider.component(appContext, snapshot.format)) {
            return@withContext false
        }
        val bound = store.bind(widgetId, snapshot.id)
        if (bound) SiteWidgetProvider.refreshAll(appContext)
        bound
    }

    suspend fun image(snapshot: SiteWidgetSnapshot): Bitmap? = withContext(Dispatchers.IO) {
        store.loadBitmap(snapshot.id)
    }

    suspend fun discard(snapshot: SiteWidgetSnapshot) = withContext(Dispatchers.IO) { store.delete(snapshot.id) }
}
