package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class ForcePageZoomInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun geckoAllowsPinchOnLockedViewportWhenEnabled() = verifyZoom(AndroidBrowserEngineKind.GeckoView)

    @Test
    fun systemWebViewAllowsPinchOnLockedViewportWhenEnabled() = verifyZoom(AndroidBrowserEngineKind.SystemWebView)

    private fun verifyZoom(engine: AndroidBrowserEngineKind) {
        val preferences = context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val store = BrowserSessionStore(context)
        GestureOnboardingStore(context).markCompleted()
        store.saveStartupAnimationEnabled(false)
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        assertTrue(store.saveAndroidBrowserEngineKind(engine))
        try {
            val loads = AtomicInteger()
            EdgeToEdgeSiteFixtureServer { path ->
                if (path.startsWith("/zoom")) loads.incrementAndGet()
                HTML
            }.use { server ->
                val url = server.fixtureUrl("/zoom")
                val tab = BrowserTab(id = "zoom-test", url = url, lastAccessedAt = System.currentTimeMillis())
                assertTrue(store.saveTabsImmediately(listOf(tab), tab.id))
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                    awaitTitle(scenario) { it.contains("maximum-scale=1") }
                    pinch(scenario)
                    SystemClock.sleep(1_000)
                    awaitTitle(scenario) {
                        it.substringBefore('|').toFloatOrNull()?.let { scale -> scale <= 1.01f } == true
                    }
                    scenario.onActivity { activity ->
                        assertTrue(activity.browserControllerForTesting().setForcePageZooming(tab.id, true))
                    }
                    awaitTitle(scenario) { loads.get() >= 2 && it.contains("maximum-scale=10") }
                    pinch(scenario)
                    awaitTitle(scenario) { it.substringBefore('|').toFloatOrNull()?.let { scale -> scale > 1.1f } == true }
                    scenario.onActivity { activity ->
                        assertTrue(activity.browserControllerForTesting().setForcePageZooming(tab.id, false))
                    }
                    awaitTitle(scenario) { loads.get() >= 3 && it.contains("maximum-scale=1") && !it.contains("maximum-scale=10") }
                }
            }
        } finally {
            preferences.edit().clear().commit()
        }
    }

    private fun awaitTitle(scenario: ActivityScenario<MainActivity>, matches: (String) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        var title = ""
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { title = it.browserControllerForTesting().selectedTabForTesting().title }
            if (matches(title)) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Unexpected page title: $title")
    }

    private fun pinch(scenario: ActivityScenario<MainActivity>) {
        val location = IntArray(2)
        var width = 0
        var height = 0
        scenario.onActivity { activity ->
            val view = requireNotNull(activity.browserControllerForTesting().selectedBrowserEngineViewForTesting())
            view.getLocationOnScreen(location)
            width = view.width
            height = view.height
        }
        val x = location[0] + width / 2f
        val y = location[1] + height / 2f
        val downTime = SystemClock.uptimeMillis()
        val properties = Array(2) { index -> MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        fun send(action: Int, count: Int, distance: Float) {
            val coords = Array(count) { index -> MotionEvent.PointerCoords().apply {
                this.x = x + if (index == 0) -distance else distance
                this.y = y
                pressure = 1f
                size = 1f
            } }
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, count, properties, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
            SystemClock.sleep(40)
        }
        send(MotionEvent.ACTION_DOWN, 1, 30f)
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, 30f)
        for (distance in 40..200 step 20) send(MotionEvent.ACTION_MOVE, 2, distance.toFloat())
        send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, 200f)
        send(MotionEvent.ACTION_UP, 1, 200f)
    }

    private companion object {
        const val HTML = """
            <!doctype html><meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
            <body style="height:2000px">Zoom fixture<script>
            setInterval(() => { document.title = visualViewport.scale.toFixed(2) + '|' +
              document.querySelector('meta[name=viewport]').content; }, 100);
            </script></body>
        """
    }
}
