package dev.sk2andy.materialbrowser.browser.gecko

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.DeveloperSettings
import dev.sk2andy.materialbrowser.data.HistoryRecordingMode
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoBackgroundRestoreInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun evictedTabRestoresHistoryOnlyWhenSelectedAfterForegroundReturn() {
        val store = BrowserSessionStore(instrumentation.targetContext)
        val originalTabs = store.loadTabs()
        val originalEngine = store.loadAndroidBrowserEngineKind()
        val originalHistory = store.loadHistoryRecordingMode()
        val originalLifetime = store.loadInactiveTabLifetime()
        val originalSettings = store.loadDeveloperSettings()
        EdgeToEdgeSiteFixtureServer { target ->
            val page = target.substringBefore('?').removePrefix("/")
            "<!doctype html><title>$page</title><body style='background:#e7f2fe'><p>$page</p></body>"
        }.use { server ->
            ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                var ownedController: BrowserController? = null
                lateinit var controller: BrowserController
                lateinit var host: FrameLayout
                var historyTabId = ""
                var selectedTabId = ""
                var coldTabId = ""
                lateinit var oldHistorySession: GeckoSession
                lateinit var selectedSession: GeckoSession
                try {
                    scenario.onActivity { activity ->
                        assertTrue(store.saveTabsImmediately(emptyList(), ""))
                        assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
                        assertTrue(store.saveHistoryRecordingMode(HistoryRecordingMode.Disabled))
                        store.saveInactiveTabLifetime(InactiveTabLifetime.Never)
                        store.saveDeveloperSettings(DeveloperSettings())
                        controller = BrowserController(activity)
                        ownedController = controller
                        host = FrameLayout(activity)
                        activity.setContentView(host)
                        controller.onStart()
                        controller.onResume()
                        historyTabId = controller.selectedTabId
                        controller.submitAddress(server.fixtureUrl("/root"))
                        assertTrue(controller.attachSelectedBrowserEngineView(host) != null)
                    }
                    awaitCondition("Root page did not load") {
                        controller.selectedTab.title == "root" && !controller.selectedTab.isLoading
                    }
                    scenario.onActivity { controller.submitAddress(server.fixtureUrl("/second")) }
                    awaitCondition("Second page did not retain history") {
                        controller.selectedTab.title == "second" && !controller.selectedTab.isLoading &&
                            controller.selectedTab.canGoBack
                    }
                    scenario.onActivity {
                        oldHistorySession = requireNotNull(host.findGeckoView().session)
                        selectedTabId = controller.createTab(server.fixtureUrl("/selected"), isIncognito = false)
                        assertTrue(controller.attachSelectedBrowserEngineView(host) != null)
                        coldTabId = requireNotNull(controller.createBackgroundTab(
                            initialUrl = server.fixtureUrl("/cold"),
                            isIncognito = false,
                        ))
                    }
                    awaitCondition("Selected page did not load") {
                        controller.selectedTab.title == "selected" && !controller.selectedTab.isLoading
                    }
                    scenario.onActivity {
                        selectedSession = requireNotNull(host.findGeckoView().session)
                        assertFalse(coldTabId in controller.residentTabIdsForTesting())
                        controller.onAppBackgrounded()
                    }
                    awaitCondition("Unselected real Gecko session was not unloaded") {
                        historyTabId !in controller.residentTabIdsForTesting()
                    }
                    scenario.onActivity {
                        assertFalse(oldHistorySession.isOpen)
                        assertTrue(selectedSession.isOpen)
                        controller.onAppForegrounded()
                        controller.onStart()
                        controller.onResume()
                        assertEquals(setOf(selectedTabId), controller.residentTabIdsForTesting())
                        assertTrue(controller.attachSelectedBrowserEngineView(host) != null)
                        assertSame(selectedSession, host.findGeckoView().session)
                        controller.selectTab(historyTabId)
                        assertTrue(controller.attachSelectedBrowserEngineView(host) != null)
                    }
                    awaitCondition("Selected unloaded tab did not restore its native history") {
                        val restored = engineSession(controller, historyTabId)
                        restored?.isContentPresented == true &&
                            restored.historyUrlAtOffset(0) == server.fixtureUrl("/second") &&
                            restored.historyUrlAtOffset(-1) == server.fixtureUrl("/root") &&
                            !controller.selectedTab.isLoading && controller.selectedTab.canGoBack
                    }
                    scenario.onActivity {
                        assertNotSame(oldHistorySession, host.findGeckoView().session)
                        assertEquals(setOf(selectedTabId, historyTabId), controller.residentTabIdsForTesting())
                        assertFalse(coldTabId in controller.residentTabIdsForTesting())
                        controller.goBack()
                    }
                    awaitCondition("Restored history did not navigate back to root") {
                        controller.selectedTab.url == server.fixtureUrl("/root") &&
                            controller.selectedTab.title == "root" && !controller.selectedTab.isLoading
                    }
                } finally {
                    scenario.onActivity {
                        ownedController?.destroy()
                        assertTrue(store.saveAndroidBrowserEngineKind(originalEngine))
                        assertTrue(store.saveHistoryRecordingMode(originalHistory))
                        store.saveInactiveTabLifetime(originalLifetime)
                        store.saveDeveloperSettings(originalSettings)
                        assertTrue(store.saveTabsImmediately(originalTabs.first, originalTabs.second.orEmpty()))
                    }
                }
            }
        }
    }

    private fun engineSession(controller: BrowserController, tabId: String): AndroidBrowserEngineSessionPort? =
        (controller.javaClass.getDeclaredField("browserEngineSessions").apply { isAccessible = true }
            .get(controller) as Map<*, *>)[tabId] as? AndroidBrowserEngineSessionPort

    private fun View.findGeckoView(): GeckoView {
        if (this is GeckoView) return this
        if (this !is ViewGroup) error("GeckoView descendant is missing")
        for (index in 0 until childCount) {
            runCatching { getChildAt(index).findGeckoView() }.getOrNull()?.let { return it }
        }
        error("GeckoView descendant is missing")
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000L
        val observed = AtomicBoolean(false)
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync { observed.set(condition()) }
            if (observed.get()) return
            SystemClock.sleep(25L)
        }
        assertTrue(message, observed.get())
    }
}
