package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.DeveloperSettings
import dev.sk2andy.materialbrowser.data.GeckoSafeAreaSettings
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import kotlin.math.abs
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoFullHeightSafeAreaInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store by lazy { BrowserSessionStore(context) }
    private val preferences by lazy {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        store.saveStartupAnimationEnabled(false)
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        store.saveDeveloperSettings(
            DeveloperSettings(geckoSafeAreaSettings = GeckoSafeAreaSettings(enabled = false)),
        )
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun clippedFullHeightPageUsesNativeInsetAndKeepsFooterVisibleDuringNestedScroll() {
        EdgeToEdgeSiteFixtureServer { FIXTURE_HTML }.use { server ->
            val tab = BrowserTab(
                id = "gecko-full-height-safe-area",
                lastAccessedAt = System.currentTimeMillis(),
                url = server.fixtureUrl("/site-matrix/full-height"),
            )
            assertTrue(store.saveTabsImmediately(listOf(tab), tab.id))
            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java)
                    .setAction("dev.sk2andy.materialbrowser.test.GECKO_FULL_HEIGHT_SAFE_AREA"),
            ).use { scenario ->
                val original = awaitReport(scenario) { it.getDouble("height") > 0 }
                assertEquals(original.getDouble("height"), original.getDouble("footerBottom"), 0.5)
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    controller.updateDeveloperSettings(
                        controller.developerSettings.copy(geckoSafeAreaSettings = GeckoSafeAreaSettings()),
                    )
                }
                val protected = awaitReport(scenario) {
                    it.getDouble("height") < original.getDouble("height") - 1 &&
                        abs(it.getDouble("padding")) < 0.5 &&
                        abs(it.getDouble("footerBottom") - it.getDouble("height")) < 0.5
                }
                val location = IntArray(2)
                var topMargin = 0
                scenario.onActivity { activity ->
                    val host = requireNotNull(activity.browserControllerForTesting().selectedGeckoViewForTesting())
                    val engine = (host as ViewGroup).getChildAt(0)
                    topMargin = (engine.layoutParams as ViewGroup.MarginLayoutParams).topMargin
                    engine.getLocationOnScreen(location)
                }
                assertTrue("Automatic native top inset", topMargin > 0)
                assertEquals(
                    topMargin.toDouble(),
                    (original.getDouble("height") - protected.getDouble("height")) * protected.getDouble("density"),
                    2.0,
                )
                val x = location[0] + protected.getDouble("buttonX").toFloat() * protected.getDouble("density").toFloat()
                val y = location[1] + protected.getDouble("buttonY").toFloat() * protected.getDouble("density").toFloat()
                val downTime = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0).also { event ->
                        event.source = InputDevice.SOURCE_TOUCHSCREEN
                        try {
                            assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
                        } finally {
                            event.recycle()
                        }
                    }
                }
                val scrolled = awaitReport(scenario) { it.getDouble("nestedScroll") >= 600 }
                assertEquals(0.0, scrolled.getDouble("documentScroll"), 0.5)
                assertEquals(scrolled.getDouble("height"), scrolled.getDouble("footerBottom"), 0.5)
                assertEquals(protected.getDouble("footerTop"), scrolled.getDouble("footerTop"), 0.5)
            }
        }
    }

    private fun awaitReport(
        scenario: ActivityScenario<MainActivity>,
        predicate: (JSONObject) -> Boolean,
    ): JSONObject {
        val deadline = SystemClock.elapsedRealtime() + 30_000L
        var latest = ""
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity -> latest = activity.browserControllerForTesting().selectedTab.title }
            val report = runCatching { JSONObject(latest) }.getOrNull()
            if (report != null && report.optBoolean("loaded") && predicate(report)) return report
            SystemClock.sleep(50)
        }
        throw AssertionError("Full-height page did not settle: $latest")
    }

    private companion object {
        val FIXTURE_HTML = """
            <!doctype html>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
              * { box-sizing: border-box; }
              html, body, #root { margin: 0; height: 100%; }
              #root { overflow: hidden; }
              #scene { height: 100dvh; overflow: hidden; background: white; display: flex; flex-direction: column; }
              header { height: 64px; flex: none; }
              button { width: 100%; height: 64px; }
              main { flex: 1; min-height: 0; overflow: auto; }
              article { height: 2400px; background: linear-gradient(white, steelblue); }
              footer { height: 180px; flex: none; background: white; }
            </style>
            <div id="root"><div id="scene">
              <header><button id="scroll">Scroll configuration</button></header>
              <main id="content"><article>Configuration options</article></main>
              <footer id="footer">Price and next step</footer>
            </div></div>
            <script>
              const content = document.getElementById('content');
              const footer = document.getElementById('footer');
              const button = document.getElementById('scroll');
              button.onclick = () => { content.scrollTop = 600; report(); };
              function report() {
                const rect = footer.getBoundingClientRect();
                const control = button.getBoundingClientRect();
                document.title = JSON.stringify({loaded: document.readyState === 'complete',
                  height: innerHeight, density: devicePixelRatio, padding: parseFloat(getComputedStyle(document.body).paddingTop),
                  footerTop: rect.top, footerBottom: rect.bottom, nestedScroll: content.scrollTop,
                  documentScroll: scrollY, buttonX: control.x + control.width / 2, buttonY: control.y + control.height / 2});
              }
              const timer = setInterval(report, 100);
              setTimeout(() => clearInterval(timer), 90000);
              addEventListener('resize', report);
              report();
            </script>
        """.trimIndent()
    }
}
