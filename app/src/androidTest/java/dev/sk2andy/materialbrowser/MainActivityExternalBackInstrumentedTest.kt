package dev.sk2andy.materialbrowser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityExternalBackInstrumentedTest {
    @After
    fun tearDown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        clearSession(context)
        context.getSharedPreferences(
            GestureOnboardingStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }

    @Test
    fun rootBackFromViewIntentReturnsToCallerAndKeepsExternalTab() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        clearSession(context)
        GestureOnboardingStore(context).markCompleted()
        val launchIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(EXTERNAL_URL),
            context,
            MainActivity::class.java,
        )

        ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
            lateinit var browserController: BrowserController
            var externalTabId = ""
            scenario.onActivity { activity ->
                browserController = activity.browserControllerForTesting()
                externalTabId = browserController.selectedTabId
                assertEquals(EXTERNAL_URL, browserController.selectedTab.url)
                activity.onBackPressedDispatcher.onBackPressed()
            }

            waitUntil {
                scenario.state == Lifecycle.State.CREATED
            }
            instrumentation.runOnMainSync {
                assertTrue(browserController.tabs.any { it.id == externalTabId })
            }
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        fail("Condition not met within $TIMEOUT_MILLIS ms")
    }

    private fun clearSession(context: Context) {
        context.getSharedPreferences(
            BrowserSessionStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }

    private companion object {
        const val EXTERNAL_URL = "https://example.com/from-another-app"
        const val TIMEOUT_MILLIS = 5_000L
        const val POLL_INTERVAL_MILLIS = 50L
    }
}
