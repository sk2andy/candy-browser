package dev.sk2andy.materialbrowser.browser.sitewidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import dev.sk2andy.materialbrowser.SiteWidgetProvider
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.data.SiteWidgetStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class SiteWidgetIntentHandler(
    context: Context,
    private val controller: BrowserController,
    private val scope: CoroutineScope,
    private val onNavigate: () -> Unit,
) {
    private val appContext = context.applicationContext
    private var generation = 0

    fun open(intent: Intent): Boolean {
        val requestGeneration = ++generation
        if (intent.action != SiteWidgetProvider.ACTION_OPEN && intent.action != SiteWidgetProvider.ACTION_EDIT) return false
        val widgetId = intent.getIntExtra(SiteWidgetProvider.EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val manager = AppWidgetManager.getInstance(appContext)
        val provider = manager.getAppWidgetInfo(widgetId)?.provider
        if (SiteWidgetFormat.entries.none { SiteWidgetProvider.component(appContext, it) == provider }) return true
        val edit = intent.action == SiteWidgetProvider.ACTION_EDIT
        scope.launch {
            val snapshot = withContext(Dispatchers.IO) { SiteWidgetStore(appContext).snapshotForWidget(widgetId) }
            if (generation != requestGeneration) return@launch
            controller.dismissExternalLinkPreview()
            controller.leaveSiteCapsule()
            if (snapshot != null) {
                val profile = controller.localBrowserProfiles.firstOrNull { it.id == snapshot.source.profileId }
                if (profile == null || profile.protection != null) return@launch
                if (
                    controller.activeProfileId != snapshot.source.profileId &&
                    !controller.selectProfile(snapshot.source.profileId)
                ) return@launch
                val tab = controller.tabs.firstOrNull {
                    it.id == snapshot.source.tabId && it.profileId == snapshot.source.profileId &&
                        !it.isIncognito && it.url == snapshot.source.url
                }
                if (tab != null) {
                    controller.selectTab(tab.id)
                } else if (!controller.openUrl(snapshot.source.url, inNewTab = true)) {
                    return@launch
                }
            }
            onNavigate()
            if (edit) controller.requestSiteWidgetEditor(widgetId)
        }
        return true
    }
}
