package dev.sk2andy.materialbrowser.browser.guest

import android.appwidget.AppWidgetManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.sk2andy.materialbrowser.CandySearchWidgetProvider
import dev.sk2andy.materialbrowser.RssFeedWidgetProvider
import dev.sk2andy.materialbrowser.SiteWidgetProvider
import dev.sk2andy.materialbrowser.browser.integration.CandySearchWidgetRules
import dev.sk2andy.materialbrowser.browser.integration.devicesearch.DeviceSearchIndexer
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetFormat
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

internal object GuestBubblePublicSurfaces {
    suspend fun hide(context: Context): Boolean {
        val widgetsHidden = withContext(Dispatchers.IO) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                SiteWidgetFormat.entries.forEach { format ->
                    manager.getAppWidgetIds(SiteWidgetProvider.component(context, format))
                        .forEach { id -> SiteWidgetProvider.update(context, manager, id) }
                }
                RssFeedWidgetProvider.updateAllNow(context)
                CandySearchWidgetProvider.updateAll(
                    context,
                    CandySearchWidgetRules.state(profiles = emptyList(), profilesEnabled = false),
                )
            }.onFailure { error -> Log.w("GuestBubble", "Could not hide website widgets", error) }
                .isSuccess
        }
        if (!widgetsHidden) return false
        return suspendCancellableCoroutine { continuation ->
            DeviceSearchIndexer.refresh(context) { success ->
                if (continuation.isActive) continuation.resume(success)
            }
        }
    }

    fun restore(context: Context) {
        if (Application.getProcessName() != context.packageName) {
            context.sendBroadcast(Intent(context, GuestBubbleRestoreReceiver::class.java))
            return
        }
        if (GuestBubbleSessionStore(context).isLocked()) return
        SiteWidgetProvider.refreshAll(context)
        RssFeedWidgetProvider.updateAll(context)
        val store = BrowserSessionStore(context)
        CandySearchWidgetProvider.updateAll(
            context,
            CandySearchWidgetRules.state(
                profiles = store.loadProfiles().first,
                profilesEnabled = store.loadProfilesEnabled(),
            ),
        )
        DeviceSearchIndexer.refresh(context)
    }
}
