package dev.sk2andy.materialbrowser.browser.gecko

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoDownloadForegroundServiceInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun serviceRemainsForegroundUntilTheLastTransferEnds() {
        val first = -301
        val second = -302
        val failed = AtomicBoolean(false)
        try {
            assertTrue(GeckoDownloadForegroundService.begin(context, first) { failed.set(true) })
            assertTrue("service did not enter foreground", waitForService(foreground = true))
            assertTrue(GeckoDownloadForegroundService.begin(context, second) { failed.set(true) })
            GeckoDownloadForegroundService.end(first)
            assertTrue("service stopped while another transfer was active", serviceIsForeground())
            GeckoDownloadForegroundService.end(second)
            assertTrue("service did not stop after the final transfer", waitForService(foreground = false))
            assertFalse(failed.get())
        } finally {
            GeckoDownloadForegroundService.end(first)
            GeckoDownloadForegroundService.end(second)
        }
    }

    @Test
    fun rejectedServiceStartDoesNotRetainTheTransfer() {
        val rejected = object : ContextWrapper(context) {
            override fun startForegroundService(service: Intent): ComponentName? {
                throw SecurityException("test rejection")
            }
        }
        val failed = AtomicBoolean(false)
        val unavailable = object : ContextWrapper(context) {
            override fun startForegroundService(service: Intent): ComponentName? = null
        }
        try {
            assertFalse(GeckoDownloadForegroundService.begin(rejected, -303) { failed.set(true) })
            assertFalse(GeckoDownloadForegroundService.begin(unavailable, -305) { failed.set(true) })
            assertTrue(GeckoDownloadForegroundService.begin(context, -304) { failed.set(true) })
            assertTrue("a later transfer could not start the service", waitForService(foreground = true))
        } finally {
            GeckoDownloadForegroundService.end(-303)
            GeckoDownloadForegroundService.end(-304)
            GeckoDownloadForegroundService.end(-305)
        }
        assertTrue("service retained the rejected transfer", waitForService(foreground = false))
        assertFalse(failed.get())
    }

    @Test
    fun immediatelyFinishedTransfersStillAllowTheServiceToStart() {
        val failed = AtomicBoolean(false)
        repeat(40) { index ->
            val id = -1_000 - index
            assertTrue(GeckoDownloadForegroundService.begin(context, id) { failed.set(true) })
            GeckoDownloadForegroundService.end(id)
        }
        // Android enforces the foreground-start deadline asynchronously.
        SystemClock.sleep(8_000)
        assertFalse(failed.get())
        assertTrue("service did not stop after rapid transfers", waitForService(foreground = false))
    }

    private fun waitForService(foreground: Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (serviceIsForeground() == foreground) return true
            SystemClock.sleep(50)
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun serviceIsForeground(): Boolean = context.getSystemService(ActivityManager::class.java)
        .getRunningServices(Int.MAX_VALUE)
        .any { it.service.className == GeckoDownloadForegroundService::class.java.name && it.foreground }
}
