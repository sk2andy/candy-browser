package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The test APK owns the same HTML that the host runner serves to a minified Release APK.
 * This suite checks geometry and actual UserTiming coverage, not a performance threshold.
 */
@RunWith(AndroidJUnit4::class)
class GeckoSafeAreaScalingInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences by lazy {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        assumeTrue(BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS)
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        BrowserSessionStore(context).saveStartupAnimationEnabled(false)
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        instrumentation.runOnMainSync { GeckoPerformanceDiagnostics.discard(context) }
    }

    @After
    fun tearDown() {
        if (BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS) {
            instrumentation.runOnMainSync { GeckoPerformanceDiagnostics.discard(context) }
            preferences.edit().clear().commit()
        }
    }

    @Test
    fun retainedShadowFeedWith256ElementsKeepsAnchorsSafeDuringLongScroll() = runScale(256)

    @Test
    fun retainedShadowFeedWith1024ElementsKeepsAnchorsSafeDuringLongScroll() = runScale(1024)

    @Test
    fun retainedShadowFeedWith4096ElementsKeepsAnchorsSafeDuringLongScroll() = runScale(4096)

    private fun runScale(scale: Int) {
        val html = instrumentation.context.assets.open(FIXTURE_ASSET).bufferedReader().use { it.readText() }
        val report = AtomicReference<JSONObject?>(null)
        val reportFailure = AtomicReference<String?>(null)
        EdgeToEdgeSiteFixtureServer { requestTarget ->
            val request = Uri.parse("http://127.0.0.1$requestTarget")
            when (request.path) {
                "/safe-area-scaling" -> html
                "/safe-area-scaling/report" -> {
                    val data = request.getQueryParameter("data")
                    val parsed = if (data != null && data.length <= MAX_REPORT_CHARACTERS) {
                        runCatching { JSONObject(data) }.getOrNull()
                    } else {
                        null
                    }
                    if (parsed?.optInt("scale") == scale && request.getQueryParameter("scale") == scale.toString()) {
                        report.compareAndSet(null, parsed)
                    } else {
                        reportFailure.set("invalid_fixture_report")
                    }
                    "<!doctype html><title>Candy scaling report stored</title>"
                }
                else -> "<!doctype html><title>Candy scaling fixture</title>"
            }
        }.use { server ->
            val path = "/safe-area-scaling?scale=$scale&steps=$SCROLL_STEPS&stepMs=$STEP_MILLISECONDS"
            val readyUrl = server.fixtureUrl(path)
            val runUrl = server.fixtureUrl("$path&run=1")
            val tab = BrowserTab(
                id = "gecko-safe-area-scaling-$scale",
                lastAccessedAt = System.currentTimeMillis(),
                url = readyUrl,
            )
            assertTrue(BrowserSessionStore(context).saveTabsImmediately(listOf(tab), tab.id))
            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
            ).use { scenario ->
                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().submitAddress(readyUrl)
                }
                awaitReady(scenario, scale)
                scenario.onActivity { GeckoPerformanceDiagnostics.start(context) }
                awaitRecording()
                // Reload after recording starts, so the document policy enables real Candy measures.
                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().submitAddress(runUrl)
                }
                val deadline = SystemClock.uptimeMillis() + REPORT_TIMEOUT_MILLISECONDS
                while (report.get() == null && reportFailure.get() == null && SystemClock.uptimeMillis() < deadline) {
                    SystemClock.sleep(100)
                }
                assertEquals("Fixture must publish a bounded numeric report", null, reportFailure.get())
                val result = requireNotNull(report.get()) { "Scaling fixture did not report before timeout" }
                Log.i(LOG_TAG, result.toString())
                assertTrue("Capture must cover the full workload", GeckoPerformanceDiagnostics.isRecording)
                assertReport(result, scale)
                scenario.onActivity { activity ->
                    val view = requireNotNull(activity.browserControllerForTesting().selectedGeckoViewForTesting())
                    assertEdgeToEdge(view, activity.window.decorView.height)
                    assertEdgeToEdge((view as ViewGroup).getChildAt(0), activity.window.decorView.height)
                }
            }
        }
    }

    private fun assertReport(report: JSONObject, scale: Int) {
        assertEquals(scale, report.getInt("initialRetainedFeedElements"))
        assertEquals(SCROLL_STEPS, report.getInt("requestedScrollSteps"))
        assertEquals(SCROLL_STEPS, report.getInt("completedScrollSteps"))
        assertEquals(STEP_MILLISECONDS, report.getInt("stepMs"))
        assertEquals(SCROLL_STEPS / 4, report.getInt("nonleafAdditions"))
        assertEquals(SCROLL_STEPS / 4, report.getInt("virtualizedRemovals"))
        assertEquals(0, report.getInt("protectedInsetMissing"))
        assertEquals(0, report.getInt("fixedHeaderFailures"))
        assertTrue(report.getInt("activeStickySamples") > 0)
        assertEquals(0, report.getInt("stickyHeaderFailures"))
        assertTrue(report.getInt("tinyControlSamples") > 0)
        assertEquals(0, report.getInt("tinyControlFailures"))
        assertTrue("Page observer must see actual content-bridge UserTiming", report.getBoolean("diagnosticsCovered"))
        assertTrue(report.getBoolean("passed"))
        val phases = report.getJSONObject("phases")
        assertTrue(phases.getJSONObject("Candy.SafeArea.Reconcile").getInt("count") > 0)
        assertTrue(phases.getJSONObject("Candy.SafeArea.PointDiscovery").getInt("count") > 0)
        // Phase durations are inclusive/nested; never add them into a wall-time assertion.
    }

    private fun awaitReady(scenario: ActivityScenario<MainActivity>, scale: Int) {
        val deadline = SystemClock.uptimeMillis() + 45_000
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                ready = controller.selectedGeckoViewForTesting() != null &&
                    controller.selectedTabForTesting().title == "Candy scaling ready: $scale"
            }
            if (!ready) SystemClock.sleep(100)
        }
        assertTrue("Gecko scaling fixture must receive its protected inset", ready)
    }

    private fun awaitRecording() {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (GeckoPerformanceDiagnostics.status != "recording" && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(100)
        }
        assertEquals("recording", GeckoPerformanceDiagnostics.status)
    }

    private fun assertEdgeToEdge(view: View, windowHeight: Int) {
        val margins = view.layoutParams as ViewGroup.MarginLayoutParams
        assertEquals(0, margins.leftMargin)
        assertEquals(0, margins.topMargin)
        assertEquals(0, margins.rightMargin)
        assertEquals(0, margins.bottomMargin)
        val location = IntArray(2)
        view.getLocationInWindow(location)
        assertEquals(0, location[1])
        assertEquals(windowHeight, location[1] + view.height)
    }

    private companion object {
        const val FIXTURE_ASSET = "gecko_safe_area_scaling_fixture.html"
        const val TEST_ACTIVITY_ACTION = "dev.sk2andy.materialbrowser.test.GECKO_SAFE_AREA_SCALING"
        const val LOG_TAG = "CandySafeAreaScaling"
        const val SCROLL_STEPS = 96
        const val STEP_MILLISECONDS = 550
        const val REPORT_TIMEOUT_MILLISECONDS = 110_000L
        const val MAX_REPORT_CHARACTERS = 16_384
    }
}
