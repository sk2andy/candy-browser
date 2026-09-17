package dev.sk2andy.materialbrowser.browser

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.SnoozeWakeNotifier
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivateTabsNotifierInstrumentedTest {
    @Test
    fun privateTabsUseDedicatedOngoingNotificationThatClosesEveryPrivateTab() {
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
        val manager = context.getSystemService(NotificationManager::class.java)
        val notifier = PrivateTabsNotifier(context)
        val closed = CountDownLatch(1)
        val closeCallback: () -> Int = {
            closed.countDown()
            2
        }
        manager.cancel(PrivateTabsNotifier.NOTIFICATION_ID)
        PrivateTabsRuntimeRegistry.register(closeCallback)

        try {
            assertTrue(notifier.update(privateTabCount = 2))

            val posted = awaitNotification(manager)
            assertNotNull(posted)
            val notification = requireNotNull(posted)
            val channel = requireNotNull(
                manager.getNotificationChannel(PrivateTabsNotifier.CHANNEL_ID),
            )
            assertEquals(PrivateTabsNotifier.CHANNEL_ID, notification.channelId)
            assertNotEquals(SnoozeWakeNotifier.CHANNEL_ID, notification.channelId)
            assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
            assertFalse(channel.canShowBadge())
            assertEquals(
                context.resources.getQuantityString(
                    R.plurals.private_tabs_notification_title,
                    2,
                    2,
                ),
                notification.extras.getString(Notification.EXTRA_TITLE),
            )
            assertEquals(
                context.getString(R.string.private_tabs_notification_close),
                notification.extras.getString(Notification.EXTRA_TEXT),
            )
            assertEquals(Notification.VISIBILITY_SECRET, notification.visibility)
            assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertTrue(notification.flags and Notification.FLAG_LOCAL_ONLY != 0)
            assertNotNull(notification.contentIntent)

            notification.contentIntent.send()

            assertTrue(closed.await(2, TimeUnit.SECONDS))
            assertTrue(awaitNotificationRemoval(manager))
        } finally {
            PrivateTabsRuntimeRegistry.unregister(closeCallback)
            notifier.cancel()
        }
    }

    private fun awaitNotification(manager: NotificationManager): Notification? {
        val deadline = SystemClock.uptimeMillis() + 2_000L
        var notification: Notification? = null
        while (notification == null && SystemClock.uptimeMillis() < deadline) {
            notification = manager.activeNotifications
                .firstOrNull { it.id == PrivateTabsNotifier.NOTIFICATION_ID }
                ?.notification
            if (notification == null) SystemClock.sleep(50L)
        }
        return notification
    }

    private fun awaitNotificationRemoval(manager: NotificationManager): Boolean {
        val deadline = SystemClock.uptimeMillis() + 2_000L
        while (SystemClock.uptimeMillis() < deadline) {
            if (manager.activeNotifications.none {
                    it.id == PrivateTabsNotifier.NOTIFICATION_ID
                }
            ) {
                return true
            }
            SystemClock.sleep(50L)
        }
        return false
    }
}
