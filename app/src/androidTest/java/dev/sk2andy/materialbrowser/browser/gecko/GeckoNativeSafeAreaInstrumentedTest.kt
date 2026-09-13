package dev.sk2andy.materialbrowser.browser.gecko

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Native CSS contract only; no scroll performance or checkerboard-absence assertion. */
@RunWith(AndroidJUnit4::class)
class GeckoNativeSafeAreaInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun nativeTopInsetUpdatesCssConsumersWithoutMovingUnawareContentOrShrinkingSurface() {
        val title = AtomicReference<String?>(null)
        EdgeToEdgeSiteFixtureServer { target ->
            if (target.startsWith("/site-matrix/native-safe-area")) HTML else "<!doctype html><title>Fixture resource</title>"
        }.use { server ->
            ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                lateinit var session: GeckoBrowserSession
                lateinit var view: View
                scenario.onActivity { activity ->
                    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                    session = GeckoRuntimeOwner.getOrCreate(activity).createSession(
                        profileId = "native-safe-area-${UUID.randomUUID()}",
                        isPrivate = false,
                        privacyPolicy = GeckoPrivacyPolicy.Disabled,
                    )
                    session.setStateListener { state -> title.set(state.title) }
                    view = session.createView(activity)
                    activity.setContentView(view)
                    session.setActive(true)
                    updateNativeTop(view, 0)
                    assertTrue(session.loadUrl(server.fixtureUrl("/site-matrix/native-safe-area")))
                }
                try {
                    var lastSequence = 0
                    var initialViewportHeight: Double? = null
                    for (topPx in listOf(0, 144, 216, 0)) {
                        scenario.onActivity { updateNativeTop(view, topPx) }
                        val report = awaitNativeInset(title, topPx, lastSequence)
                        lastSequence = report.getInt("sequence")
                        val viewportHeight = report.getDouble("viewportHeight")
                        if (initialViewportHeight == null) initialViewportHeight = viewportHeight
                        assertEquals(initialViewportHeight, viewportHeight, 0.5)
                        val cssInset = topPx / report.getDouble("density")
                        assertEquals(cssInset, report.getDouble("envInset"), 0.5)
                        assertEquals(cssInset, report.getDouble("awareFixedTop"), 0.5)
                        assertEquals(0.0, report.getDouble("unawareFixedTop"), 0.5)
                        assertEquals(0.0, report.getDouble("unawareFlowTop"), 0.5)
                        assertEquals(cssInset, report.getDouble("awareFlowContentTop"), 0.5)
                        assertEquals(0.0, report.getDouble("candyInset"), 0.5)
                        assertEquals(0, report.getInt("candyOwnedElements"))
                        scenario.onActivity { activity ->
                            assertEdgeToEdge(view, activity.window.decorView.height)
                            assertEdgeToEdge((view as ViewGroup).getChildAt(0), activity.window.decorView.height)
                        }
                    }
                    assertEquals("Native inset changes must not reload the document", 1, server.documentRequestCount.get())
                } finally {
                    scenario.onActivity {
                        session.releaseView(view)
                        session.setActive(false)
                        session.close()
                    }
                }
            }
        }
    }

    private fun updateNativeTop(view: View, topPx: Int) {
        (view as GeckoViewInsetHost).updateInsets(
            GeckoViewInsetRules.resolve(
                safeArea = GeckoViewInsets(left = 0, top = topPx, right = 0, bottom = 0),
                forceNativeSafeArea = false,
                forceNativeTopSafeArea = false,
                isFullscreenContent = false,
                isInsideSafeDrawingHost = false,
            ),
            WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, topPx, 0, 0))
                .build(),
        )
    }

    private fun awaitNativeInset(title: AtomicReference<String?>, topPx: Int, afterSequence: Int): JSONObject {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val current = title.get().orEmpty()
            val report = if (current.startsWith(REPORT_PREFIX) && current.length < 2_048) {
                runCatching { JSONObject(current.removePrefix(REPORT_PREFIX)) }.getOrNull()
            } else {
                null
            }
            if (report != null && report.getInt("sequence") > afterSequence && kotlin.math.abs(
                    report.getDouble("envInset") * report.getDouble("density") - topPx,
                ) <= 0.5
            ) {
                return report
            }
            SystemClock.sleep(50)
        }
        throw AssertionError("Native CSS safe-area did not reach $topPx screen pixels; last title=${title.get()}")
    }

    private fun assertEdgeToEdge(view: View, windowHeight: Int) {
        val margins = view.layoutParams as ViewGroup.MarginLayoutParams
        assertEquals(0, margins.topMargin)
        assertEquals(0, margins.bottomMargin)
        val location = IntArray(2)
        view.getLocationInWindow(location)
        assertEquals(0, location[1])
        assertEquals(windowHeight, location[1] + view.height)
    }

    private companion object {
        const val REPORT_PREFIX = "Candy native safe-area: "
        val HTML = """
            <!doctype html>
            <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
            <title>Preparing native safe-area</title>
            <style>
              html, body { margin: 0; min-height: 2000px; }
              #probe { position: absolute; padding-top: env(safe-area-inset-top); }
              #aware-fixed { position: fixed; top: env(safe-area-inset-top); left: 80px; }
              #unaware-fixed { position: fixed; top: 0; left: 160px; }
              #aware-flow { position: absolute; top: 0; padding-top: env(safe-area-inset-top); }
            </style>
            <body>
              <div id="probe"></div>
              <button id="aware-fixed">Aware</button>
              <button id="unaware-fixed">Unaware</button>
              <div id="unaware-flow">Unaware flow</div>
              <main id="aware-flow"><div id="flow-content">Aware flow</div></main>
            </body>
            <script>
              let lastReport = '';
              let sequence = 0;
              function sample() {
                const data = {
                  density: devicePixelRatio,
                  viewportHeight: innerHeight,
                  envInset: parseFloat(getComputedStyle(document.querySelector('#probe')).paddingTop),
                  awareFixedTop: document.querySelector('#aware-fixed').getBoundingClientRect().top,
                  unawareFixedTop: document.querySelector('#unaware-fixed').getBoundingClientRect().top,
                  unawareFlowTop: document.querySelector('#unaware-flow').getBoundingClientRect().top,
                  awareFlowContentTop: document.querySelector('#flow-content').getBoundingClientRect().top,
                  candyInset: parseFloat(document.documentElement.style.getPropertyValue(
                    '--candy-browser-content-top-inset')) || 0,
                  candyOwnedElements: document.querySelectorAll(
                    '[data-candy-browser-top-inset-sticky],[data-candy-browser-top-inset-offset]').length,
                };
                const report = JSON.stringify(data);
                if (report !== lastReport) {
                  lastReport = report;
                  document.title = '$REPORT_PREFIX' + JSON.stringify({ ...data, sequence: ++sequence });
                }
                requestAnimationFrame(sample);
              }
              requestAnimationFrame(sample);
            </script>
        """.trimIndent()
    }
}
