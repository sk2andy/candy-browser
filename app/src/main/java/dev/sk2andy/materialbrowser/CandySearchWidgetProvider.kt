package dev.sk2andy.materialbrowser

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import dev.sk2andy.materialbrowser.browser.integration.CandySearchWidgetLayout
import dev.sk2andy.materialbrowser.browser.integration.CandySearchWidgetProfile
import dev.sk2andy.materialbrowser.browser.integration.CandySearchWidgetRules
import dev.sk2andy.materialbrowser.browser.integration.CandySearchWidgetState
import dev.sk2andy.materialbrowser.browser.integration.LauncherShortcutPublisher
import dev.sk2andy.materialbrowser.browser.integration.LauncherShortcutTarget
import dev.sk2andy.materialbrowser.data.BrowserSessionStore

class CandySearchWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val state = loadState(context)
        appWidgetIds.forEach { appWidgetId ->
            update(
                context = context,
                appWidgetManager = appWidgetManager,
                appWidgetId = appWidgetId,
                state = state,
            )
        }
    }

    companion object {
        internal fun updateAll(
            context: Context,
            state: CandySearchWidgetState,
        ) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            manager.getAppWidgetIds(ComponentName(appContext, CandySearchWidgetProvider::class.java))
                .forEach { appWidgetId ->
                    update(
                        context = appContext,
                        appWidgetManager = manager,
                        appWidgetId = appWidgetId,
                        state = state,
                    )
                }
        }

        private fun loadState(context: Context): CandySearchWidgetState {
            val store = BrowserSessionStore(context.applicationContext)
            return CandySearchWidgetRules.state(
                profiles = store.loadProfiles().first,
                profilesEnabled = store.loadProfilesEnabled(),
            )
        }

        private fun update(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            state: CandySearchWidgetState,
        ) {
            val views = CandySearchWidgetRules.responsiveSizes.associate { size ->
                SizeF(size.minWidthDp.toFloat(), size.minHeightDp.toFloat()) to
                    createViews(context, state, size.layout)
            }
            appWidgetManager.updateAppWidget(appWidgetId, RemoteViews(views))
        }

        private fun createViews(
            context: Context,
            state: CandySearchWidgetState,
            layout: CandySearchWidgetLayout,
        ): RemoteViews = RemoteViews(
            context.packageName,
            when (layout) {
                CandySearchWidgetLayout.Compact -> R.layout.candy_search_widget_compact
                CandySearchWidgetLayout.Medium -> R.layout.candy_search_widget_medium
                CandySearchWidgetLayout.Large -> R.layout.candy_search_widget
            },
        ).apply {
            setOnClickPendingIntent(
                R.id.candy_widget_logo,
                actionPendingIntent(
                    context = context,
                    requestCode = OPEN_APP_REQUEST_CODE,
                    target = LauncherShortcutTarget.OpenApp,
                ),
            )
            if (layout != CandySearchWidgetLayout.Compact) {
                setOnClickPendingIntent(
                    R.id.candy_widget_search,
                    actionPendingIntent(
                        context = context,
                        requestCode = NEW_TAB_REQUEST_CODE,
                        target = LauncherShortcutTarget.NewTab,
                    ),
                )
            }
            setOnClickPendingIntent(
                R.id.candy_widget_private,
                actionPendingIntent(
                    context = context,
                    requestCode = PRIVATE_TAB_REQUEST_CODE,
                    target = LauncherShortcutTarget.NewPrivateTabInCurrentProfile,
                ),
            )
            bindProfile(
                context = context,
                views = this,
                viewId = R.id.candy_widget_profile_one,
                profile = state.profiles.getOrNull(0),
                requestCode = FIRST_PROFILE_REQUEST_CODE,
            )
            bindProfile(
                context = context,
                views = this,
                viewId = R.id.candy_widget_profile_two,
                profile = state.profiles.getOrNull(1),
                requestCode = SECOND_PROFILE_REQUEST_CODE,
            )
        }

        private fun bindProfile(
            context: Context,
            views: RemoteViews,
            viewId: Int,
            profile: CandySearchWidgetProfile?,
            requestCode: Int,
        ) {
            views.setViewVisibility(viewId, if (profile == null) View.GONE else View.VISIBLE)
            if (profile == null) return
            views.setTextViewText(viewId, profile.emoji)
            views.setContentDescription(
                viewId,
                context.getString(R.string.widget_open_profile_tab, profile.emoji),
            )
            views.setOnClickPendingIntent(
                viewId,
                actionPendingIntent(
                    context = context,
                    requestCode = requestCode,
                    target = LauncherShortcutTarget.NewTabInProfile(profile.profileId),
                ),
            )
        }

        private fun actionPendingIntent(
            context: Context,
            requestCode: Int,
            target: LauncherShortcutTarget,
        ): PendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            LauncherShortcutPublisher(context).dispatcherIntent(target),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private const val OPEN_APP_REQUEST_CODE = 1
        private const val NEW_TAB_REQUEST_CODE = 2
        private const val PRIVATE_TAB_REQUEST_CODE = 3
        private const val FIRST_PROFILE_REQUEST_CODE = 4
        private const val SECOND_PROFILE_REQUEST_CODE = 5
    }
}
