package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoKeyboardInsetsInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store = BrowserSessionStore(context)
    private val preferences = context.getSharedPreferences(
        BrowserSessionStore.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun forwardedKeyboardInsetsResizeVisualViewportAndRespectChromeOwnership() {
        withFixture { scenario ->
            val initial = awaitReport(scenario) { true }
            val expectedHeight = initial.getDouble("visualHeight") -
                KEYBOARD_HEIGHT_PX / initial.getDouble("density")
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, KEYBOARD_HEIGHT_PX))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()

            scenario.onActivity { it.browserControllerForTesting().onWindowInsetsChanged(insets) }
            awaitReport(scenario) { kotlin.math.abs(it.getDouble("visualHeight") - expectedHeight) < 1 }

            scenario.onActivity { assertTrue(it.browserControllerForTesting().openFindInPage()) }
            awaitImeVisibility(scenario, true)
            awaitReport(scenario) {
                kotlin.math.abs(it.getDouble("visualHeight") - initial.getDouble("visualHeight")) < 1
            }

            scenario.onActivity { activity ->
                activity.browserControllerForTesting().closeFindInPage()
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .hide(WindowInsetsCompat.Type.ime())
            }
            awaitImeVisibility(scenario, false)
            awaitReport(scenario) {
                kotlin.math.abs(it.getDouble("visualHeight") - initial.getDouble("visualHeight")) < 1
            }
        }
    }

    @Test
    fun lowerWebsiteInputStaysVisibleWithKeyboardAndRestoresViewport() {
        assertLowerInputVisible(immersive = false)
    }

    @Test
    fun immersiveLowerWebsiteInputStaysVisibleWithoutShrinkingNativeHost() {
        assertLowerInputVisible(immersive = true)
    }

    private fun assertLowerInputVisible(immersive: Boolean) {
        withFixture(immersive) { scenario ->
            val initial = awaitReport(scenario) { true }
            val coordinates = FloatArray(2)
            var initialHostHeight = 0
            scenario.onActivity { activity ->
                val host = requireNotNull(activity.browserControllerForTesting().selectedGeckoViewForTesting())
                initialHostHeight = host.height
                val view = (host as ViewGroup).getChildAt(0)
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                val density = initial.getDouble("density").toFloat()
                coordinates[0] = location[0] + 100f * density
                coordinates[1] = location[1] +
                    (initial.getDouble("inputTop").toFloat() + 20f) * density
                // Gecko installs one root listener per view. Losing that listener when another
                // engine view detaches must not break Candy's explicit per-session forwarding.
                ViewCompat.setOnApplyWindowInsetsListener(activity.window.decorView.rootView, null)
            }
            tap(coordinates[0], coordinates[1])
            awaitImeVisibility(scenario, true)
            val focused = awaitReport(scenario) {
                it.getBoolean("focused") &&
                    it.getDouble("visualHeight") < initial.getDouble("visualHeight") - 100 &&
                    it.getDouble("inputTop") >= it.getDouble("visualTop") - 1 &&
                    it.getDouble("inputBottom") <=
                    it.getDouble("visualTop") + it.getDouble("visualHeight") + 1
            }
            assertTrue(focused.getBoolean("focused"))
            instrumentation.sendStringSync("candy")
            awaitReport(scenario) { it.getString("value") == "candy" && it.getBoolean("focused") }
            scenario.onActivity { activity ->
                val host = requireNotNull(activity.browserControllerForTesting().selectedGeckoViewForTesting())
                if (immersive) assertEquals(initialHostHeight, host.height)
                val location = IntArray(2)
                host.getLocationInWindow(location)
                assertEquals(0, location[1])
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .hide(WindowInsetsCompat.Type.ime())
            }
            awaitImeVisibility(scenario, false)
            awaitReport(scenario) {
                kotlin.math.abs(it.getDouble("visualHeight") - initial.getDouble("visualHeight")) < 1
            }
        }
    }

    private fun withFixture(
        immersive: Boolean = false,
        block: (ActivityScenario<MainActivity>) -> Unit,
    ) {
        EdgeToEdgeSiteFixtureServer { HTML }.use { server ->
            preferences.edit().clear().commit()
            GestureOnboardingStore(context).markCompleted()
            store.saveStartupAnimationEnabled(false)
            store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView)
            store.saveFullImmersiveModeEnabled(immersive)
            ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
            val tab = BrowserTab(
                id = "gecko-keyboard-fixture",
                lastAccessedAt = System.currentTimeMillis(),
                url = server.url,
            )
            assertTrue(store.saveTabsImmediately(listOf(tab), tab.id))
            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
            ).use { scenario ->
                awaitReport(scenario) { true }
                scenario.onActivity { activity ->
                    WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                        .hide(WindowInsetsCompat.Type.ime())
                }
                awaitImeVisibility(scenario, false)
                awaitReport(scenario) { report ->
                    var engineHeight = 0
                    scenario.onActivity { activity ->
                        val host = requireNotNull(
                            activity.browserControllerForTesting().selectedGeckoViewForTesting(),
                        )
                        engineHeight = (host as ViewGroup).getChildAt(0).height
                    }
                    kotlin.math.abs(report.getDouble("visualHeight") *
                        report.getDouble("density") - engineHeight) < 2
                }
                block(scenario)
            }
        }
    }

    private fun awaitReport(
        scenario: ActivityScenario<MainActivity>,
        matches: (JSONObject) -> Boolean,
    ): JSONObject {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        var lastTitle = ""
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { lastTitle = it.browserControllerForTesting().selectedTabForTesting().title }
            if (lastTitle.startsWith(REPORT_PREFIX)) {
                val report = JSONObject(lastTitle.removePrefix(REPORT_PREFIX))
                if (matches(report)) return report
            }
            SystemClock.sleep(50)
        }
        throw AssertionError("Gecko keyboard viewport did not settle; last report=$lastTitle")
    }

    private fun awaitImeVisibility(scenario: ActivityScenario<MainActivity>, visible: Boolean) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            var matches = false
            scenario.onActivity {
                matches = ViewCompat.getRootWindowInsets(it.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == visible
            }
            if (matches) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Expected keyboard visible=$visible")
    }

    private fun tap(x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }
    }

    private companion object {
        const val TEST_ACTIVITY_ACTION = "dev.sk2andy.materialbrowser.test.GECKO_KEYBOARD_INSETS"
        const val KEYBOARD_HEIGHT_PX = 600
        const val TIMEOUT_MILLIS = 20_000L
        const val REPORT_PREFIX = "Candy keyboard: "
        val HTML = """
            <!doctype html>
            <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
            <style>
              html, body { margin: 0; min-height: 2000px; }
              input { position: absolute; top: 70vh; left: 20px; width: 250px;
                      height: 40px; font-size: 18px; }
            </style>
            <input id="target" aria-label="Keyboard regression input" placeholder="Type here">
            <script>
              const target = document.querySelector('#target');
              let lastReport = '';
              function sample() {
                const rect = target.getBoundingClientRect();
                const report = JSON.stringify({
                  density: devicePixelRatio,
                  visualHeight: visualViewport.height,
                  visualTop: visualViewport.offsetTop,
                  inputTop: rect.top,
                  inputBottom: rect.bottom,
                  focused: document.activeElement === target,
                  value: target.value,
                });
                if (report !== lastReport) {
                  lastReport = report;
                  document.title = '$REPORT_PREFIX' + report;
                }
                requestAnimationFrame(sample);
              }
              requestAnimationFrame(sample);
            </script>
        """.trimIndent()
    }
}
