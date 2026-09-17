package dev.sk2andy.materialbrowser.browser

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dev.sk2andy.materialbrowser.R

internal class PrivateTabsNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.private_tabs_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = appContext.getString(
                    R.string.private_tabs_notification_channel_description,
                )
                setShowBadge(false)
            },
        )
    }

    fun hasPostNotificationPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun update(privateTabCount: Int): Boolean {
        if (privateTabCount <= 0) {
            cancel()
            return true
        }
        if (!hasPostNotificationPermission() || !notificationManager.areNotificationsEnabled()) {
            return false
        }
        ensureChannel()
        val closeIntent = PendingIntent.getBroadcast(
            appContext,
            NOTIFICATION_ID,
            Intent(appContext, PrivateTabsNotificationReceiver::class.java)
                .setAction(ACTION_CLOSE_PRIVATE_TABS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_incognito_filled)
            .setContentTitle(
                appContext.resources.getQuantityString(
                    R.plurals.private_tabs_notification_title,
                    privateTabCount,
                    privateTabCount,
                ),
            )
            .setContentText(appContext.getString(R.string.private_tabs_notification_close))
            .setContentIntent(closeIntent)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setShowWhen(false)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
        return runCatching {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }.isSuccess
    }

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    internal companion object {
        const val CHANNEL_ID = "private_tabs"
        const val ACTION_CLOSE_PRIVATE_TABS =
            "dev.sk2andy.materialbrowser.action.CLOSE_PRIVATE_TABS"
        const val NOTIFICATION_ID = 0x50000001
    }
}

internal object PrivateTabsRuntimeRegistry {
    @Volatile
    private var closeAllCallback: (() -> Int)? = null

    fun register(callback: () -> Int) {
        closeAllCallback = callback
    }

    fun unregister(callback: () -> Int) {
        if (closeAllCallback === callback) closeAllCallback = null
    }

    fun closeAll(): Int = closeAllCallback?.invoke() ?: 0
}

internal class PrivateTabsNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PrivateTabsNotifier.ACTION_CLOSE_PRIVATE_TABS) return
        PrivateTabsRuntimeRegistry.closeAll()
        PrivateTabsNotifier(context).cancel()
    }
}
