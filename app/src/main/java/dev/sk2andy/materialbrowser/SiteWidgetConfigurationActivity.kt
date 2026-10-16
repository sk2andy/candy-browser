package dev.sk2andy.materialbrowser

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.os.Bundle
import dev.sk2andy.materialbrowser.browser.guest.GuestBubbleGate
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetConfigurationContract

/** Routes the launcher's standard Reconfigure action into Candy's existing crop editor. */
class SiteWidgetConfigurationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (GuestBubbleGate.redirectIfLocked(this)) return
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val result = SiteWidgetConfigurationContract.result(widgetId)
        setResult(RESULT_CANCELED, result)
        if (widgetId > 0 && intent.action == AppWidgetManager.ACTION_APPWIDGET_CONFIGURE) {
            val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider
            val target = SiteWidgetConfigurationContract.editIntent(this, widgetId, provider)
            if (target != null) {
                try {
                    startActivity(target)
                    // Configuration is optional: the existing widget stays valid during editing.
                    setResult(RESULT_OK, result)
                } catch (_: ActivityNotFoundException) {
                    // Keep the launcher's canceled result when Candy cannot accept the request.
                } catch (_: SecurityException) {
                    // A stale launcher request must not claim configuration succeeded.
                }
            }
        }
        finish()
    }
}
