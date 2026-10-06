package dev.sk2andy.materialbrowser.browser

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.lang.ref.WeakReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserControllerPopupOwnershipInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        activityRule.scenario.onActivity { controller?.destroy() }
    }

    @Test
    fun closingPopupReleasesQueuedTimeoutBeforeItsThirtySecondDeadline() {
        val timeout = createAndReleasePopup(destroyController = false)

        awaitCollection(timeout)

        assertNull("Closed popup timeout remains rooted in the main message queue", timeout.get())
    }

    @Test
    fun destroyingControllerReleasesPendingPopupTimeoutBeforeItsDeadline() {
        val timeout = createAndReleasePopup(destroyController = true)

        awaitCollection(timeout)

        assertNull("Destroyed controller still has a queued popup timeout", timeout.get())
    }

    private fun createAndReleasePopup(destroyController: Boolean): WeakReference<Runnable> {
        var timeout: WeakReference<Runnable>? = null
        activityRule.scenario.onActivity { activity ->
            val browser = BrowserController(activity).also { controller = it }
            val popupId = requireNotNull(browser.createPendingGeckoPopupForTesting())
            val callback = requireNotNull(browser.pendingPopupTimeoutForTesting(popupId))
            timeout = WeakReference(callback)
            assertEquals(1, browser.pendingPopupCountForTesting)
            if (destroyController) {
                browser.destroy()
                controller = null
            } else {
                browser.closeTab(popupId)
                assertTrue(browser.tabs.none { it.id == popupId })
            }
            assertEquals(0, browser.pendingPopupCountForTesting)
        }
        return requireNotNull(timeout)
    }

    private fun awaitCollection(reference: WeakReference<Runnable>) {
        repeat(10) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Runtime.getRuntime().gc()
            Runtime.getRuntime().runFinalization()
            Runtime.getRuntime().gc()
            SystemClock.sleep(100L)
            if (reference.get() == null) return
        }
    }
}
