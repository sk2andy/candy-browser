package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.StatusBarScrollAccessibilityContract
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatusBarSmoothScrollInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = BrowserSessionStore(context)

    @After
    fun tearDown() {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun geckoTapAnimatesToTop() = assertTapAnimatesToTop(AndroidBrowserEngineKind.GeckoView)

    @Test
    fun systemWebViewTapAnimatesToTop() = assertTapAnimatesToTop(AndroidBrowserEngineKind.SystemWebView)

    private fun assertTapAnimatesToTop(engine: AndroidBrowserEngineKind) {
        EdgeToEdgeSiteFixtureServer { _ ->
            """<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Scroll fixture</title><div style="height:10000px">Scroll fixture</div>"""
        }.use { server ->
            GestureOnboardingStore(context).markCompleted()
            ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
            store.saveAndroidBrowserEngineKind(engine)
            store.saveStartupAnimationEnabled(false)
            store.saveOpenHomeOnStartupEnabled(false)
            val tab = BrowserTab(
                id = "scroll-test",
                url = server.fixtureUrl("/scroll"),
                lastAccessedAt = System.currentTimeMillis(),
            )
            assertTrue(store.saveTabsImmediately(listOf(tab), tab.id))
            val intent = Intent(context, MainActivity::class.java)
                .setAction("dev.sk2andy.materialbrowser.ISSUE_302_TEST")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                await(scenario) { it.selectedTab.title == "Scroll fixture" && !it.selectedTab.isLoading }
                scenario.onActivity {
                    assertTrue(it.browserControllerForTesting().scrollSelectedBrowserEngineToVerticalOffset(3000))
                }
                await(scenario) { (it.selectedBrowserEngineScrollMetrics()?.offsetPx ?: 0) > 2000 }
                var initial = 0
                scenario.onActivity {
                    val controller = it.browserControllerForTesting()
                    initial = controller.selectedBrowserEngineScrollMetrics()!!.offsetPx
                    if (engine == AndroidBrowserEngineKind.SystemWebView) {
                        (controller.selectedBrowserEngineViewForTesting() as WebView)
                            .settings.javaScriptEnabled = false
                    }
                }
                context.sendBroadcast(
                    Intent(StatusBarScrollAccessibilityContract.ACTION_SCROLL_TO_TOP).setPackage(context.packageName),
                    StatusBarScrollAccessibilityContract.STATUS_BAR_SCROLL_CONTROL_PERMISSION,
                )
                var sawIntermediate = false
                val deadline = SystemClock.elapsedRealtime() + 10_000
                do {
                    var offset = initial
                    scenario.onActivity {
                        offset = it.browserControllerForTesting().selectedBrowserEngineScrollMetrics()?.offsetPx ?: initial
                    }
                    if (offset in 1 until initial) sawIntermediate = true
                    if (offset == 0) break
                    SystemClock.sleep(16)
                } while (SystemClock.elapsedRealtime() < deadline)
                assertTrue("$engine must show an intermediate scroll position", sawIntermediate)
                await(scenario) { it.selectedBrowserEngineScrollMetrics()?.offsetPx == 0 }
            }
        }
    }

    private fun await(scenario: ActivityScenario<MainActivity>, condition: (BrowserController) -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        do {
            var satisfied = false
            scenario.onActivity { satisfied = condition(it.browserControllerForTesting()) }
            if (satisfied) return
            SystemClock.sleep(50)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("Timed out waiting for scroll state")
    }
}
