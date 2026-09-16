package dev.sk2andy.materialbrowser.browser.downloads

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.text.format.Formatter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadTransferStart
import dev.sk2andy.materialbrowser.data.DownloadEntry
import dev.sk2andy.materialbrowser.data.DownloadRuntimeRegistry
import dev.sk2andy.materialbrowser.data.DownloadStatus
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CandyDownloadNotifierInstrumentedTest {
    @Test
    fun notificationPermissionRequestHasSingleBoundedOwner() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val notifier = CandyDownloadNotifier(context)

        assertTrue(notifier.beginPermissionRequest())
        assertFalse(notifier.beginPermissionRequest())
        notifier.onPermissionResult(granted = false)
        assertTrue(notifier.beginPermissionRequest())
        notifier.onPermissionResult(granted = false)
    }

    @Test
    fun activeDownloadPostsExpressiveProgressAndWorkingControls() {
        val context = notificationContext()
        val manager = context.getSystemService(NotificationManager::class.java)
        val notifier = CandyDownloadNotifier(context)
        val transferId = DownloadRuntimeRegistry.nextTransferId()
        val notificationId = CandyDownloadNotifier.notificationId(transferId)
        val cancelled = AtomicBoolean(false)
        val start = GeckoDownloadTransferStart(
            id = transferId,
            fileName = "expressive-download.bin",
            mimeType = "application/octet-stream",
            sourceUrl = "https://example.com/expressive-download.bin",
            referrer = null,
            startedAtMillis = 10L,
            totalBytes = 200L,
        )
        manager.cancel(notificationId)
        DownloadRuntimeRegistry.started(
            id = transferId,
            name = start.fileName,
            source = start.sourceUrl,
            mime = start.mimeType,
            total = start.totalBytes,
            startedAt = start.startedAtMillis,
            mediaStoreId = 91_001L,
            cancel = {
                cancelled.set(true)
                DownloadRuntimeRegistry.failed(
                    id = transferId,
                    cancelled = true,
                    updatedAt = 30L,
                )
                notifier.cancel(transferId)
            },
            pause = {
                DownloadRuntimeRegistry.paused(transferId, paused = true, updatedAt = 20L)
                notifier.paused(start.id)
                true
            },
            resume = {
                DownloadRuntimeRegistry.paused(transferId, paused = false, updatedAt = 25L)
                notifier.paused(start.id)
                true
            },
        )

        try {
            notifier.started(start.id)
            DownloadRuntimeRegistry.progress(transferId, bytes = 100L, total = 200L, updatedAt = 15L)
            notifier.progress(start.id)

            val running = awaitNotification(manager, notificationId)
            assertEquals(CandyDownloadNotifier.CHANNEL_ID, running.channelId)
            assertEquals("expressive-download.bin", running.extras.getString(Notification.EXTRA_TITLE))
            assertEquals(100, running.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
            assertEquals(50, running.extras.getInt(Notification.EXTRA_PROGRESS))
            if (Build.VERSION.SDK_INT >= 36) {
                assertEquals(
                    Notification.ProgressStyle::class.java.name,
                    running.extras.getString(Notification.EXTRA_TEMPLATE),
                )
            }
            assertTrue(running.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertNotNull(running.contentIntent)
            assertEquals(
                listOf(
                    context.getString(R.string.downloads_pause),
                    context.getString(R.string.action_cancel),
                ),
                requireNotNull(running.actions).map { action -> action.title.toString() },
            )

            requireNotNull(running.actions).first().actionIntent.send()
            awaitStatus(transferId, DownloadStatus.Paused)
            val paused = awaitNotification(manager, notificationId) { notification ->
                notification.actions?.firstOrNull()?.title == context.getString(R.string.downloads_resume)
            }
            assertEquals(
                context.getString(
                    R.string.download_notification_paused,
                    Formatter.formatShortFileSize(context, 100L),
                    Formatter.formatShortFileSize(context, 200L),
                ),
                paused.extras.getString(Notification.EXTRA_TEXT),
            )

            requireNotNull(paused.actions).last().actionIntent.send()
            awaitCondition { cancelled.get() }
            assertTrue(cancelled.get())
        } finally {
            DownloadRuntimeRegistry.completed(transferId)
            notifier.cancel(transferId)
        }
    }

    @Test
    fun fastCompletionWhilePermissionIsPendingPublishesOnlyCompletion() {
        val context = notificationContext()
        val manager = context.getSystemService(NotificationManager::class.java)
        val canNotify = AtomicBoolean(false)
        val notifier = CandyDownloadNotifier(context, canNotify::get)
        val transferId = DownloadRuntimeRegistry.nextTransferId()
        val notificationId = CandyDownloadNotifier.notificationId(transferId)
        val start = GeckoDownloadTransferStart(
            id = transferId,
            fileName = "fast-download.bin",
            mimeType = "application/octet-stream",
            sourceUrl = "https://example.com/fast-download.bin",
            referrer = null,
            startedAtMillis = 10L,
            totalBytes = 100L,
        )
        val entry = DownloadEntry(
            id = -91_002L,
            name = start.fileName,
            source = start.sourceUrl,
            status = DownloadStatus.Running,
            bytes = 10L,
            total = start.totalBytes,
            lastModified = 15L,
            mime = start.mimeType,
            supportsCancel = true,
        )
        manager.cancel(notificationId)

        try {
            assertTrue(notifier.beginPermissionRequest())
            notifier.active(transferId, entry)
            notifier.complete(
                transferId = start.id,
                fileName = start.fileName,
                mimeType = start.mimeType,
                uri = Uri.parse("content://media/external/downloads/91002"),
            )
            canNotify.set(true)
            notifier.onPermissionResult(granted = true)

            val completed = awaitNotification(manager, notificationId) { notification ->
                notification.extras.getString(Notification.EXTRA_TEXT) ==
                    context.getString(R.string.download_notification_complete)
            }
            assertEquals(0, completed.flags and Notification.FLAG_ONGOING_EVENT)
            assertTrue(completed.flags and Notification.FLAG_AUTO_CANCEL != 0)
        } finally {
            notifier.onPermissionResult(granted = false)
            notifier.cancel(transferId)
        }
    }

    @Test
    fun staleProgressAndStartupReconciliationRemoveOrphanedOngoingNotifications() {
        val context = notificationContext()
        val manager = context.getSystemService(NotificationManager::class.java)
        val notifier = CandyDownloadNotifier(context)
        val transferId = DownloadRuntimeRegistry.nextTransferId()
        val notificationId = CandyDownloadNotifier.notificationId(transferId)
        val start = GeckoDownloadTransferStart(
            id = transferId,
            fileName = "orphaned-download.bin",
            mimeType = "application/octet-stream",
            sourceUrl = "https://example.com/orphaned-download.bin",
            referrer = null,
            startedAtMillis = 10L,
            totalBytes = 100L,
        )
        val entry = DownloadEntry(
            id = -91_003L,
            name = start.fileName,
            source = start.sourceUrl,
            status = DownloadStatus.Running,
            bytes = 10L,
            total = start.totalBytes,
            lastModified = 15L,
            mime = start.mimeType,
            supportsCancel = true,
        )
        manager.cancel(notificationId)

        try {
            notifier.active(transferId, entry)
            awaitNotification(manager, notificationId)
            notifier.reconcileOrphanedActiveNotifications()
            awaitCondition {
                manager.activeNotifications.none { active -> active.id == notificationId }
            }

            notifier.progress(start.id)
            SystemClock.sleep(100L)
            assertTrue(manager.activeNotifications.none { active -> active.id == notificationId })
        } finally {
            notifier.cancel(transferId)
        }
    }

    private fun awaitStatus(transferId: Int, status: DownloadStatus) {
        awaitCondition { DownloadRuntimeRegistry.entryForTransfer(transferId)?.status == status }
    }

    private fun notificationContext(): Context {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        runCatching {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        assumeTrue(
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
        return context
    }

    private fun awaitNotification(
        manager: NotificationManager,
        id: Int,
        predicate: (Notification) -> Boolean = { true },
    ): Notification {
        var notification: Notification? = null
        awaitCondition {
            notification = manager.activeNotifications
                .firstOrNull { active -> active.id == id }
                ?.notification
                ?.takeIf(predicate)
            notification != null
        }
        return requireNotNull(notification)
    }

    private fun awaitCondition(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 2_000L
        while (!predicate() && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(25L)
        }
        assertTrue(predicate())
    }
}
