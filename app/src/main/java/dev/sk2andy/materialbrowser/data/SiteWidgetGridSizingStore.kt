package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Reads the active home app identity and persists its learned widget-cell proportions locally. */
internal class SiteWidgetGridSizingStore(context: Context) {
    private val appContext = context.applicationContext
    private val widgetStore = SiteWidgetStore(appContext)

    fun currentLauncherPackage(): String? = appContext.packageManager.resolveActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
        PackageManager.MATCH_DEFAULT_ONLY,
    )?.activityInfo?.packageName

    fun cellAspectRatio(launcherPackage: String?): Float? = launcherPackage
        ?.let(widgetStore::launcherCellAspectRatio)
}
