package dev.sk2andy.materialbrowser.browser.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.sk2andy.materialbrowser.DownloadsActivity
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.hasNotificationPermission
import dev.sk2andy.materialbrowser.data.DownloadEntry
import dev.sk2andy.materialbrowser.data.DownloadRuntimeRegistry
import dev.sk2andy.materialbrowser.data.DownloadStatus
import java.util.concurrent.ConcurrentHashMap

/** Material Expressive progress notifications for Candy-owned download transfers. */
internal class CandyDownloadNotifier(
    context: Context,
    private val canNotifyOverride: (() -> Boolean)? = null,
    private val groupWithForegroundService: Boolean = false,
) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NotificationManager::class.java)
    private val accentColor = ContextCompat.getColor(appContext, R.color.candy_notification_accent)

    fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.download_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = appContext.getString(R.string.download_notification_channel_description)
            },
        )
    }

    fun started(transferId: Int) {
        DownloadRuntimeRegistry.entryForTransfer(transferId)?.let { entry ->
            active(transferId, entry)
        }
    }

    fun progress(transferId: Int) {
        val entry = DownloadRuntimeRegistry.entryForTransfer(transferId) ?: return
        synchronized(notificationLock) {
            if (DownloadRuntimeRegistry.entryForTransfer(transferId)?.status != DownloadStatus.Running) return
            active(transferId, entry, priority = false)
        }
    }

    fun paused(transferId: Int) {
        DownloadRuntimeRegistry.entryForTransfer(transferId)?.let { entry ->
            active(transferId, entry)
        }
    }

    fun complete(
        transferId: Int,
        fileName: String,
        mimeType: String,
        uri: Uri,
    ) {
        DownloadCompletionEvents.completed(DownloadCompletion(fileName, mimeType, uri))
        val openIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val contentIntent = PendingIntent.getActivity(
            appContext,
            notificationId(transferId),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        post(
            transferId = transferId,
            notification = baseBuilder(fileName)
                .setContentText(appContext.getString(R.string.download_notification_complete))
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setProgress(0, 0, false)
                .build(),
        )
    }

    fun failed(transferId: Int) {
        val entry = DownloadRuntimeRegistry.entryForTransfer(transferId) ?: return
        post(
            transferId,
            baseBuilder(entry.name)
                .setContentText(appContext.getString(R.string.download_notification_failed))
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setContentIntent(openDownloadsIntent(transferId))
                .setAutoCancel(true)
                .build(),
        )
    }

    fun cancel(transferId: Int) {
        synchronized(notificationLock) {
            notificationQueue.remove(notificationId(transferId))
            pendingNotifications.remove(notificationId(transferId))
            manager.cancel(notificationId(transferId))
        }
    }

    fun beginPermissionRequest(): Boolean = synchronized(notificationLock) {
        if (permissionRequestPending) {
            false
        } else {
            permissionRequestPending = true
            true
        }
    }

    fun onPermissionResult(granted: Boolean) {
        synchronized(notificationLock) {
            permissionRequestPending = false
            if (!granted || !canNotify()) {
                pendingNotifications.clear()
                return
            }
            val pending = pendingNotifications.toMap()
            pendingNotifications.clear()
            pending.forEach { (id, notification) -> enqueue(id, notification, priority = true) }
        }
    }

    fun reconcileOrphanedActiveNotifications() {
        synchronized(notificationLock) {
            val expectedIds = DownloadRuntimeRegistry.activeTransferIds()
                .mapTo(mutableSetOf(), ::notificationId)
            pendingNotifications.entries.removeAll { (id, notification) ->
                notification.flags and Notification.FLAG_ONGOING_EVENT != 0 && id !in expectedIds
            }
            notificationQueue.removeIf { id, update ->
                update.notification.flags and Notification.FLAG_ONGOING_EVENT != 0 && id !in expectedIds
            }
            manager.activeNotifications
                .filter { active ->
                    active.notification.channelId == CHANNEL_ID &&
                        active.notification.flags and Notification.FLAG_ONGOING_EVENT != 0 &&
                        active.id !in expectedIds
                }
                .forEach { active -> manager.cancel(active.id) }
        }
    }

    internal fun active(transferId: Int, entry: DownloadEntry, priority: Boolean = true) {
        if (!entry.status.isActive) return
        val progress = if (entry.status == DownloadStatus.Pending) null else progressPercent(entry.bytes, entry.total)
        val running = entry.status == DownloadStatus.Running
        val progressStyle = NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressIndeterminate(progress == null)
            .addProgressSegment(
                NotificationCompat.ProgressStyle.Segment(PROGRESS_MAX).setColor(accentColor),
            )
        if (progress != null) {
            progressStyle.setProgress(progress)
        }
        val builder = baseBuilder(entry.name)
            .setContentText(statusText(entry, progress))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openDownloadsIntent(transferId))
            .setOngoing(true)
            .setAutoCancel(false)
            .setRequestPromotedOngoing(running)
            .setShortCriticalText(progress?.takeIf { running }?.let { "$it%" })
            .setGroup(GECKO_GROUP.takeIf { groupWithForegroundService && entry.status != DownloadStatus.Paused })
            .setStyle(progressStyle)
        if (progress == null) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(PROGRESS_MAX, progress, false)
        }
        if (entry.supportsPause) {
            val paused = entry.status == DownloadStatus.Paused
            builder.addAction(
                if (paused) R.drawable.ic_media_playback else R.drawable.ic_reader_pause,
                appContext.getString(if (paused) R.string.downloads_resume else R.string.downloads_pause),
                controlIntent(transferId, ACTION_TOGGLE_PAUSE),
            )
        }
        if (entry.supportsCancel) {
            builder.addAction(
                R.drawable.ic_reader_stop,
                appContext.getString(R.string.action_cancel),
                controlIntent(transferId, ACTION_CANCEL),
            )
        }
        post(transferId, builder.build(), priority)
    }

    private fun baseBuilder(fileName: String): NotificationCompat.Builder {
        ensureChannel()
        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reader_download)
            .setContentTitle(fileName)
            .setColor(accentColor)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
    }

    private fun statusText(entry: DownloadEntry, progress: Int?): String {
        val received = Formatter.formatShortFileSize(appContext, entry.bytes.coerceAtLeast(0L))
        val total = entry.total.takeIf { it > 0L }?.let { Formatter.formatShortFileSize(appContext, it) }
        return when {
            entry.status == DownloadStatus.Pending -> appContext.getString(R.string.download_notification_pending)
            entry.status == DownloadStatus.Paused && total != null -> appContext.getString(
                R.string.download_notification_paused,
                received,
                total,
            )
            entry.status == DownloadStatus.Paused -> appContext.getString(
                R.string.download_notification_paused_unknown,
                received,
            )
            total != null && progress != null -> appContext.getString(
                R.string.download_notification_running,
                received,
                total,
                progress,
            )
            else -> appContext.getString(R.string.download_notification_running_unknown, received)
        }
    }

    private fun openDownloadsIntent(transferId: Int): PendingIntent = PendingIntent.getActivity(
        appContext,
        notificationId(transferId),
        Intent(appContext, DownloadsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun controlIntent(transferId: Int, action: String): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        notificationId(transferId) xor action.hashCode(),
        Intent(appContext, CandyDownloadNotificationReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_TRANSFER_ID, transferId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun post(transferId: Int, notification: Notification, priority: Boolean = true) {
        val id = notificationId(transferId)
        synchronized(notificationLock) {
            if (!canNotify()) {
                if (permissionRequestPending) {
                    pendingNotifications[id] = notification
                } else {
                    pendingNotifications.remove(id)
                }
                return
            }
            enqueue(id, notification, priority)
        }
    }

    private fun enqueue(id: Int, notification: Notification, priority: Boolean) {
        notificationQueue.enqueue(id, QueuedNotification(this, id, notification), priority)
    }

    private fun deliver(id: Int, notification: Notification) {
        if (!canNotify()) {
            if (permissionRequestPending) pendingNotifications[id] = notification
            else pendingNotifications.remove(id)
        } else if (runCatching { manager.notify(id, notification) }.isSuccess) {
            pendingNotifications.remove(id)
        } else if (permissionRequestPending) {
            pendingNotifications[id] = notification
        }
    }

    private fun canNotify(): Boolean {
        canNotifyOverride?.let { return it() }
        return appContext.hasNotificationPermission() && manager.areNotificationsEnabled()
    }

    internal companion object {
        const val CHANNEL_ID = "candy_downloads"
        const val GECKO_GROUP = "candy_gecko_downloads"
        const val ACTION_CANCEL = "dev.sk2andy.materialbrowser.action.CANCEL_DOWNLOAD"
        const val ACTION_TOGGLE_PAUSE = "dev.sk2andy.materialbrowser.action.TOGGLE_DOWNLOAD_PAUSE"
        const val EXTRA_TRANSFER_ID = "download_transfer_id"
        private const val PROGRESS_MAX = 100
        private const val NOTIFICATION_ID_NAMESPACE = 0x40000000
        private val notificationLock = Any()
        private data class QueuedNotification(
            val notifier: CandyDownloadNotifier,
            val id: Int,
            val notification: Notification,
        )
        private val notificationQueue by lazy {
            val handler = Handler(Looper.getMainLooper())
            DownloadNotificationQueue<QueuedNotification>(
                lock = notificationLock,
                now = android.os.SystemClock::elapsedRealtime,
                schedule = { delay, action -> handler.postDelayed(action, delay) },
                send = { update -> update.notifier.deliver(update.id, update.notification) },
            )
        }
        private val pendingNotifications = ConcurrentHashMap<Int, Notification>()
        private var permissionRequestPending = false

        internal fun notificationId(transferId: Int): Int =
            NOTIFICATION_ID_NAMESPACE or (transferId and 0x3fffffff)

        internal fun progressPercent(received: Long, total: Long): Int? {
            if (total <= 0L) return null
            return ((received.coerceIn(0L, total).toDouble() / total.toDouble()) * PROGRESS_MAX)
                .toInt()
                .coerceIn(0, PROGRESS_MAX)
        }
    }
}

internal class CandyDownloadNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val transferId = intent.getIntExtra(CandyDownloadNotifier.EXTRA_TRANSFER_ID, 0)
            .takeIf { it > 0 }
            ?: return
        when (intent.action) {
            CandyDownloadNotifier.ACTION_CANCEL -> DownloadRuntimeRegistry.cancelTransfer(transferId)
            CandyDownloadNotifier.ACTION_TOGGLE_PAUSE ->
                DownloadRuntimeRegistry.togglePauseTransfer(transferId)
        }
    }
}
