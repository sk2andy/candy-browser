package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.HistoryRecordingMode
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoClosedSessionOwnershipInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun finalCloseReleasesCandySessionAndDestroyedController() {
        // The helper returns after the scenario and all session-owning stack frames close.
        // A native GeckoSession may remain rooted; this checks only Candy's ownership.
        val references = loadAndCloseController()
        repeat(30) { SystemClock.sleep(1_000L) }
        repeat(10) {
            instrumentation.waitForIdleSync()
            Runtime.getRuntime().gc()
            Runtime.getRuntime().runFinalization()
            Runtime.getRuntime().gc()
            SystemClock.sleep(500L)
        }

        Log.i(TAG, "After final close: candySessionAlive=${references.session.get() != null}, " +
            "controllerAlive=${references.controller.get() != null}")
        assertNull("Closed native session still retains the Candy wrapper", references.session.get())
        assertNull("Closed session still retains the destroyed controller", references.controller.get())
    }

    private fun loadAndCloseController(): ClosedReferences {
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val previousPreferences = preferences.all
        try {
            assertTrue(preferences.edit().clear().commit())
            val store = BrowserSessionStore(context)
            assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
            assertTrue(store.saveHistoryRecordingMode(HistoryRecordingMode.Disabled))
            EdgeToEdgeSiteFixtureServer { "<!doctype html><title>ownership-ready</title><p>Ownership fixture</p>" }.use { server ->
                return ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                    var controller: BrowserController? = null
                    try {
                        scenario.onActivity { activity ->
                            controller = BrowserController(activity)
                            val container = FrameLayout(activity)
                            activity.setContentView(container)
                            val browser = requireNotNull(controller)
                            browser.attachSelectedBrowserEngineView(container)
                            browser.submitAddress(server.url)
                        }
                        awaitLoaded(scenario) { requireNotNull(controller) }
                        var references: ClosedReferences? = null
                        scenario.onActivity {
                            val browser = requireNotNull(controller)
                            val sessions = field(browser, "browserEngineSessions") as Map<*, *>
                            val adapter = requireNotNull(sessions[browser.selectedTabId])
                            val session = field(adapter, "session") as GeckoBrowserSession
                            references = ClosedReferences(WeakReference(session), WeakReference(browser))
                            browser.destroy()
                            controller = null
                        }
                        requireNotNull(references)
                    } finally {
                        scenario.onActivity {
                            controller?.destroy()
                            controller = null
                        }
                    }
                }
            }
        } finally {
            val restored = preferences.edit().clear()
            previousPreferences.forEach { (key, value) ->
                when (value) {
                    is String -> restored.putString(key, value)
                    is Boolean -> restored.putBoolean(key, value)
                    is Int -> restored.putInt(key, value)
                    is Long -> restored.putLong(key, value)
                    is Float -> restored.putFloat(key, value)
                    is Set<*> -> restored.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            assertTrue(restored.commit())
        }
    }

    private fun awaitLoaded(
        scenario: ActivityScenario<GeckoScrollTestActivity>,
        controller: () -> BrowserController,
    ) {
        val loaded = AtomicBoolean(false)
        val deadline = SystemClock.elapsedRealtime() + 30_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity {
                val browser = controller()
                val view = browser.selectedGeckoViewForTesting()
                loaded.set(browser.selectedTab.title == "ownership-ready" && !browser.selectedTab.isLoading &&
                    view?.isAttachedToWindow == true && view.width > 0 && view.height > 0)
            }
            if (loaded.get()) return
            SystemClock.sleep(25L)
        }
        assertTrue("Attached ownership fixture did not load", loaded.get())
    }

    private fun field(owner: Any, name: String): Any? =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)

    private data class ClosedReferences(
        val session: WeakReference<GeckoBrowserSession>,
        val controller: WeakReference<BrowserController>,
    )

    private companion object {
        const val TAG = "CandyOwnershipTest"
    }
}
