package dev.sk2andy.materialbrowser

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.PrivateTabsNotifier
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivateTabsNotificationFlowInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    @Test
    fun openingPrivateTabPostsNotificationWhoseTapClosesIt() {
        clearState()
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
        GestureOnboardingStore(context).markCompleted()
        BrowserSessionStore(context).saveStartupAnimationEnabled(false)
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())

        try {
            ActivityScenario.launch<MainActivity>(launcherIntent()).use { scenario ->
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assumeTrue(controller.isProfileIsolationSupported)
                    controller.createTab(
                        initialUrl = "https://private.example",
                        isIncognito = true,
                    )
                }

                val notification = awaitNotification()
                assertNotNull(notification)
                requireNotNull(notification).contentIntent.send()

                assertTrue(awaitPrivateTabsClosed(scenario))
                assertTrue(awaitNotificationRemoval())
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assertEquals(1, controller.tabs.size)
                    assertTrue(controller.tabs.none { it.isIncognito })
                }
            }
        } finally {
            clearState()
        }
    }

    private fun launcherIntent(): Intent = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    private fun awaitNotification(): Notification? {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        var notification: Notification? = null
        while (notification == null && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            notification = notificationManager.activeNotifications
                .firstOrNull { it.id == PrivateTabsNotifier.NOTIFICATION_ID }
                ?.notification
            if (notification == null) SystemClock.sleep(50L)
        }
        return notification
    }

    private fun awaitPrivateTabsClosed(scenario: ActivityScenario<MainActivity>): Boolean {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            var closed = false
            scenario.onActivity { activity ->
                closed = activity.browserControllerForTesting().tabs.none { it.isIncognito }
            }
            if (closed) return true
            SystemClock.sleep(50L)
        }
        return false
    }

    private fun awaitNotificationRemoval(): Boolean {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            if (notificationManager.activeNotifications.none {
                    it.id == PrivateTabsNotifier.NOTIFICATION_ID
                }
            ) {
                return true
            }
            SystemClock.sleep(50L)
        }
        return false
    }

    private fun clearState() {
        notificationManager.cancel(PrivateTabsNotifier.NOTIFICATION_ID)
        listOf(
            BrowserSessionStore.PREFERENCES_NAME,
            GestureOnboardingStore.PREFERENCES_NAME,
            ReleaseNotesStore.PREFERENCES_NAME,
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
