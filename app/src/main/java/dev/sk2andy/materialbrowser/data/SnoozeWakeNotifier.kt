package dev.sk2andy.materialbrowser.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserTab

class SnoozeWakeNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.snooze_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = appContext.getString(R.string.snooze_notification_channel_description)
            },
        )
    }

    fun hasPostNotificationPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun canNotify(): Boolean =
        hasPostNotificationPermission() && notificationManager.areNotificationsEnabled()

    fun notifyRestored(tabs: List<BrowserTab>): Boolean {
        if (tabs.isEmpty() || !canNotify()) return false
        ensureChannel()
        val firstTab = tabs.first()
        val title = if (tabs.size == 1) {
            appContext.getString(R.string.snooze_notification_title)
        } else {
            appContext.resources.getQuantityString(
                R.plurals.snooze_notification_title_count,
                tabs.size,
                tabs.size,
            )
        }
        val body = if (tabs.size == 1) {
            firstTab.title.ifBlank {
                firstTab.url.takeUnless { it == BLANK_URL }
                    ?: appContext.getString(R.string.new_tab_title)
            }
        } else {
            appContext.getString(R.string.snooze_notification_open_app)
        }
        val contentIntent = PendingIntent.getActivity(
            appContext,
            notificationId(firstTab.id),
            Intent(appContext, MainActivity::class.java)
                .setAction(ACTION_OPEN_RESTORED_TAB)
                .putExtra(EXTRA_TAB_ID, firstTab.id)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_snooze)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .build()
        return runCatching {
            notificationManager.notify(notificationId(firstTab.id), notification)
        }.onFailure { error ->
            Log.w(SnoozeScheduler.LOG_TAG, "Wake notification failed", error)
        }.isSuccess
    }

    companion object {
        internal const val ACTION_OPEN_RESTORED_TAB =
            "dev.sk2andy.materialbrowser.action.OPEN_RESTORED_SNOOZED_TAB"
        internal const val EXTRA_TAB_ID = "snoozed_tab_id"
        internal const val CHANNEL_ID = "snoozed_tabs"

        internal fun notificationId(tabId: String): Int = tabId.hashCode() and Int.MAX_VALUE
    }
}
