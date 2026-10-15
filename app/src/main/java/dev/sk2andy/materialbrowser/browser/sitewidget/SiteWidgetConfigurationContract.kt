package dev.sk2andy.materialbrowser.browser.sitewidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.SiteWidgetProvider

internal object SiteWidgetConfigurationContract {
    fun editIntent(context: Context, widgetId: Int, provider: ComponentName?): Intent? {
        if (
            widgetId <= 0 ||
            SiteWidgetFormat.entries.none { SiteWidgetProvider.component(context, it) == provider }
        ) return null
        return Intent(context, MainActivity::class.java)
            .setAction(SiteWidgetProvider.ACTION_EDIT)
            .setData(Uri.parse("candy-site-widget://widget/$widgetId/EDIT"))
            .putExtra(SiteWidgetProvider.EXTRA_WIDGET_ID, widgetId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    fun result(widgetId: Int): Intent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}
