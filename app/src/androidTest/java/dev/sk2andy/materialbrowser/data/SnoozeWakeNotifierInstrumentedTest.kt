package dev.sk2andy.materialbrowser.data

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnoozeWakeNotifierInstrumentedTest {
    @Test
    fun restoredTabPostsTappableNotificationOnSnoozeChannel() {
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
        val notifier = SnoozeWakeNotifier(context)
        val tab = BrowserTab(
            id = "notification-test",
            lastAccessedAt = 1L,
            title = "Saved page",
            url = "https://example.com",
        )
        val notificationId = SnoozeWakeNotifier.notificationId(tab.id)
        manager.cancel(notificationId)

        assertTrue(notifier.notifyRestored(listOf(tab)))

        val deadline = SystemClock.uptimeMillis() + 2_000L
        var notification: Notification? = null
        while (notification == null && SystemClock.uptimeMillis() < deadline) {
            notification = manager.activeNotifications
                .firstOrNull { it.id == notificationId }
                ?.notification
            if (notification == null) SystemClock.sleep(50L)
        }
        assertNotNull(notification)
        val posted = requireNotNull(notification)
        assertEquals(SnoozeWakeNotifier.CHANNEL_ID, posted.channelId)
        assertEquals(
            context.getString(R.string.snooze_notification_title),
            posted.extras.getString(Notification.EXTRA_TITLE),
        )
        assertEquals("Saved page", posted.extras.getString(Notification.EXTRA_TEXT))
        assertNotNull(posted.contentIntent)
        assertTrue(posted.flags and Notification.FLAG_AUTO_CANCEL != 0)

        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        posted.contentIntent.send()
        val activity = instrumentation.waitForMonitorWithTimeout(monitor, 3_000L)
        assertNotNull(activity)
        assertEquals(SnoozeWakeNotifier.ACTION_OPEN_RESTORED_TAB, activity.intent.action)
        assertEquals(tab.id, activity.intent.getStringExtra(SnoozeWakeNotifier.EXTRA_TAB_ID))
        activity.finish()
        instrumentation.removeMonitor(monitor)

        manager.cancel(notificationId)
    }
}
