package dev.sk2andy.materialbrowser.browser.gecko

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.permissions.PermissionOrigin
import org.mozilla.geckoview.WebNotification
import org.mozilla.geckoview.WebNotificationDelegate

/** Presents Gecko's page notifications while their process-owned callbacks are still available. */
internal class GeckoWebNotificationPresenter(context: Context) : WebNotificationDelegate {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NotificationManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val active = linkedMapOf<Int, WebNotification>()
    private var nextId = runCatching {
        manager.activeNotifications.filter { it.tag == TAG }.maxOfOrNull { it.id } ?: 0
    }.getOrDefault(0)

    init {
        current = this
    }

    override fun onShowNotification(notification: WebNotification) {
        mainHandler.post {
            val siteOrigin = PermissionOrigin.normalize(notification.source)
            if (
                notification.privateBrowsing ||
                siteOrigin == null ||
                !PermissionOrigin.isPotentiallyTrustworthy(siteOrigin) ||
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED ||
                !manager.areNotificationsEnabled()
            ) {
                notification.dismiss()
                return@post
            }
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    appContext.getString(R.string.web_notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
            if (manager.getNotificationChannel(CHANNEL_ID)?.importance ==
                NotificationManager.IMPORTANCE_NONE
            ) {
                notification.dismiss()
                return@post
            }
            val id = ++nextId
            if (notification.tag.isNotEmpty()) {
                active.entries.firstOrNull { (_, shown) ->
                    shown.tag == notification.tag && shown.source == notification.source
                }?.let { (replacedId, replaced) ->
                    active.remove(replacedId)
                    manager.cancel(TAG, replacedId)
                    replaced.dismiss()
                }
            }
            if (active.size >= MAX_ACTIVE_NOTIFICATIONS) {
                val oldestId = active.keys.first()
                active.remove(oldestId)?.dismiss()
                manager.cancel(TAG, oldestId)
            }
            val title = notification.title?.take(MAX_TEXT_LENGTH)
                ?.takeIf(String::isNotBlank) ?: siteOrigin
            val body = notification.text?.take(MAX_TEXT_LENGTH).orEmpty()
            val nativeNotification = Notification.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_snooze)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setContentIntent(actionIntent(id, ACTION_CLICK))
                .setDeleteIntent(actionIntent(id, ACTION_DISMISS))
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .build()
            val shown = runCatching {
                manager.notify(TAG, id, nativeNotification)
            }.isSuccess
            if (shown) {
                active[id] = notification
                notification.show()
            } else {
                notification.dismiss()
            }
        }
    }

    override fun onCloseNotification(notification: WebNotification) {
        mainHandler.post {
            val id = active.entries.firstOrNull { (_, shown) -> shown === notification }?.key
            if (id != null) {
                active.remove(id)
                manager.cancel(TAG, id)
            }
            notification.dismiss()
        }
    }

    private fun actionIntent(id: Int, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            id,
            Intent(appContext, GeckoWebNotificationReceiver::class.java)
                .setAction(action)
                .setData(Uri.parse("candy-web-notification://$id/$action"))
                .putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun onAction(id: Int, clicked: Boolean): Boolean {
        val notification = active.remove(id) ?: return false
        if (clicked) notification.click()
        notification.dismiss()
        manager.cancel(TAG, id)
        return true
    }

    internal companion object {
        private const val CHANNEL_ID = "web_notifications"
        private const val TAG = "gecko_web_notification"
        private const val MAX_TEXT_LENGTH = 512
        private const val MAX_ACTIVE_NOTIFICATIONS = 64
        private const val ACTION_CLICK = "dev.sk2andy.materialbrowser.WEB_NOTIFICATION_CLICK"
        private const val ACTION_DISMISS = "dev.sk2andy.materialbrowser.WEB_NOTIFICATION_DISMISS"
        private const val EXTRA_ID = "web_notification_id"

        private var current: GeckoWebNotificationPresenter? = null

        fun receive(context: Context, intent: Intent) {
            val id = intent.getIntExtra(EXTRA_ID, -1)
            if (id < 0) return
            val clicked = intent.action == ACTION_CLICK
            if (current?.onAction(id, clicked) == true) return
            context.getSystemService(NotificationManager::class.java).cancel(TAG, id)
            if (clicked) {
                val openApp = Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                runCatching { context.startActivity(openApp) }
            }
        }
    }
}

internal class GeckoWebNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GeckoWebNotificationPresenter.receive(context, intent)
    }
}
