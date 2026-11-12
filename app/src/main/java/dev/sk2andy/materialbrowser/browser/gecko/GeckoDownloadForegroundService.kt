package dev.sk2andy.materialbrowser.browser.gecko

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import dev.sk2andy.materialbrowser.DownloadsActivity
import dev.sk2andy.materialbrowser.R

/** Keeps the process in the foreground only while Gecko owns active download streams. */
internal class GeckoDownloadForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        synchronized(lock) { service = this }
        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.download_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
            val openDownloads = PendingIntent.getActivity(
                this,
                0,
                Intent(this, DownloadsActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_reader_download)
                .setContentTitle(getString(R.string.download_notification_channel_name))
                .setContentText(getString(R.string.download_notification_description))
                .setContentIntent(openDownloads)
                .setOngoing(true)
                .setLocalOnly(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }.onFailure {
            failAndStop()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        synchronized(lock) {
            if (transfers.isEmpty()) stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        failAndStop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        synchronized(lock) {
            if (service === this) {
                service = null
                if (transfers.isEmpty()) accepting = true
            }
        }
        super.onDestroy()
    }

    private fun failAndStop() {
        val callbacks = synchronized(lock) {
            accepting = false
            transfers.values.toList()
        }
        stopSelf()
        Thread({ callbacks.forEach { callback -> runCatching(callback) } }, "candy-download-stop").start()
    }

    companion object {
        private const val CHANNEL_ID = "candy_gecko_download_service"
        private const val NOTIFICATION_ID = 0x30000153
        private val lock = Any()
        private val mainHandler = Handler(Looper.getMainLooper())
        private val transfers = mutableMapOf<Int, () -> Unit>()
        private var service: GeckoDownloadForegroundService? = null
        private var accepting = true

        fun begin(context: Context, id: Int, onFailure: () -> Unit): Boolean = synchronized(lock) {
            if (!accepting) return@synchronized false
            transfers[id] = onFailure
            if (transfers.size != 1) return@synchronized true
            runCatching {
                context.startForegroundService(Intent(context, GeckoDownloadForegroundService::class.java)) != null
            }.getOrDefault(false).also { started ->
                if (!started) transfers.remove(id)
            }
        }

        fun end(id: Int) {
            synchronized(lock) {
                if (transfers.remove(id) == null) return
                if (transfers.isEmpty() && service == null) accepting = true
            }
            mainHandler.post {
                synchronized(lock) {
                    if (transfers.isEmpty()) service?.stopSelf()
                }
            }
        }
    }
}
