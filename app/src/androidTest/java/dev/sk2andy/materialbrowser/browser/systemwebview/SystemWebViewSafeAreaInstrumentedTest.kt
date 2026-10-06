package dev.sk2andy.materialbrowser.browser.systemwebview

import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.graphics.Insets
import androidx.core.view.DisplayCutoutCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.browser.engine.BrowserEngineContentKind
import dev.sk2andy.materialbrowser.browser.gecko.AndroidBrowserEngineSessionPort
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEngineEventSink
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPrivacyPolicy
import dev.sk2andy.materialbrowser.browser.gecko.GeckoScrollTestActivity
import dev.sk2andy.materialbrowser.browser.gecko.GeckoViewInsetHost
import dev.sk2andy.materialbrowser.browser.gecko.GeckoViewInsetRules
import dev.sk2andy.materialbrowser.browser.gecko.GeckoViewInsets
import dev.sk2andy.materialbrowser.data.GeckoSafeAreaSettings
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommands
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class SystemWebViewSafeAreaInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun returningToTopKeepsPageColorDuringOverscroll() {
        val html = fixtureHtml(cover = false, authorEnv = false)
            .replace("html, body { margin: 0; }", "html, body { margin: 0; background: #f6f5ed; }")
        withLoadedFixture(html = html) { fixture ->
            evaluate(fixture, "scrollTo(0, 400)")
            awaitReport(fixture, "Page did not scroll down") { it.getDouble("scrollY") >= 399 }
            evaluate(fixture, "scrollTo(0, 0)")
            awaitReport(fixture, "Page did not return to the top") { it.getDouble("scrollY") == 0.0 }
            val location = IntArray(2)
            fixture.scenario.onActivity { fixture.webView.getLocationOnScreen(location) }
            val sampleX = location[0] + fixture.webView.width / 2
            val sampleY = maxOf(location[1], NATIVE_TOP_PX) + 48
            val expectedColor = Color.rgb(246, 245, 237)
            val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
            var color = Color.TRANSPARENT
            while (color != expectedColor && SystemClock.elapsedRealtime() < deadline) {
                val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                color = screenshot.getPixel(sampleX, sampleY)
                screenshot.recycle()
                if (color != expectedColor) SystemClock.sleep(POLL_MILLIS)
            }
            assertEquals("Loaded page must paint before the gesture", expectedColor, color)

            val downTime = SystemClock.uptimeMillis()
            try {
                listOf(
                    MotionEvent.ACTION_DOWN to 0.3f,
                    MotionEvent.ACTION_MOVE to 0.4f,
                    MotionEvent.ACTION_MOVE to 0.6f,
                    MotionEvent.ACTION_MOVE to 0.8f,
                ).forEach { (action, heightFraction) ->
                    fixture.scenario.onActivity {
                        MotionEvent.obtain(
                            downTime,
                            SystemClock.uptimeMillis(),
                            action,
                            fixture.webView.width / 2f,
                            fixture.webView.height * heightFraction,
                            0,
                        ).also { event ->
                            event.source = InputDevice.SOURCE_TOUCHSCREEN
                            fixture.webView.dispatchTouchEvent(event)
                            event.recycle()
                        }
                    }
                    SystemClock.sleep(32)
                }
                val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                try {
                    assertEquals(
                        "Top edge must retain the page color",
                        expectedColor,
                        screenshot.getPixel(sampleX, sampleY),
                    )
                } finally {
                    screenshot.recycle()
                }
                fixture.scenario.onActivity { assertEquals(0, fixture.webView.scrollY) }
            } finally {
                fixture.scenario.onActivity {
                    MotionEvent.obtain(
                        downTime,
                        SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_CANCEL,
                        0f,
                        0f,
                        0,
                    ).also { event ->
                        event.source = InputDevice.SOURCE_TOUCHSCREEN
                        fixture.webView.dispatchTouchEvent(event)
                        event.recycle()
                    }
                }
            }
        }
    }

    @Test
    fun coverAndNonCoverPagesReceiveOneInsetForFlowFixedAndStickyContent() {
        listOf(false, true).forEach { cover ->
            withLoadedFixture(cover = cover) { fixture ->
                val report = awaitProtectedReport(fixture)
                assertProtectedGeometry(report)
                assertEquals(-8.0, report.getDouble("negativeTop"), CSS_TOLERANCE)
                assertEdgeToEdge(fixture)

                evaluate(fixture, "scrollTo(0, 400)")
                val scrolled = awaitReport(fixture, "Sticky content did not settle after scrolling") {
                    it.getDouble("scrollY") >= 399 &&
                        abs(it.getDouble("stickyTop") - cssInset(it)) <= CSS_TOLERANCE
                }
                assertEquals(cssInset(scrolled), scrolled.getDouble("stickyTop"), CSS_TOLERANCE)
                assertEquals(cssInset(scrolled) + 3, scrolled.getDouble("fixedTop"), CSS_TOLERANCE)
                assertEquals(1, fixture.server.documentRequestCount.get())
            }
        }
    }

    @Test
    fun authoredCoverEnvPaddingAndFixedTopReceiveProtectionExactlyOnce() {
        withLoadedFixture(cover = true, authorEnv = true) { fixture ->
            val report = awaitReport(fixture, "Author env protection did not settle") {
                abs(it.getDouble("bodyPadding") - maxOf(cssInset(it), it.getDouble("envInset"))) <= CSS_TOLERANCE &&
                    abs(it.getDouble("fixedTop") - maxOf(cssInset(it), it.getDouble("envInset"))) <= CSS_TOLERANCE
            }
            // Older WebView providers expose zero env(); Candy still supplies one fallback inset.
            assertTrue(report.getDouble("envInset") >= 0)
            val expectedInset = maxOf(cssInset(report), report.getDouble("envInset"))
            assertEquals(expectedInset, report.getDouble("bodyPadding"), CSS_TOLERANCE)
            assertEquals(expectedInset, report.getDouble("flowTop"), CSS_TOLERANCE)
            assertEquals(expectedInset, report.getDouble("fixedTop"), CSS_TOLERANCE)
            assertEquals("env(safe-area-inset-top)", report.getString("bodyInlinePadding"))
            assertEquals("env(safe-area-inset-top)", report.getString("fixedInlineTop"))
            assertEdgeToEdge(fixture)
            assertEquals(1, fixture.server.documentRequestCount.get())
        }
    }

    @Test
    fun disablingAndReenablingPolicyRestoresAuthorStylesWithoutReload() {
        withLoadedFixture { fixture ->
            assertProtectedGeometry(awaitProtectedReport(fixture))
            updatePolicy(fixture, cssPolicy().copy(geckoSafeAreaSettings = GeckoSafeAreaSettings(enabled = false)))
            val restored = awaitAuthorReport(fixture)
            assertEquals("7px", restored.getString("bodyInlinePadding"))
            assertEquals("3px", restored.getString("fixedInlineTop"))
            assertEquals("0px", restored.getString("stickyInlineTop"))
            assertEquals(-8.0, restored.getDouble("negativeTop"), CSS_TOLERANCE)
            assertEdgeToEdge(fixture)

            updatePolicy(fixture, cssPolicy())
            assertProtectedGeometry(awaitProtectedReport(fixture))
            assertEquals(1, fixture.server.documentRequestCount.get())
        }
    }

    @Test
    fun nativeSafeAreaAndSafeDrawingHostClearCssOwnershipAndRestoreOnReturn() {
        withLoadedFixture { fixture ->
            assertProtectedGeometry(awaitProtectedReport(fixture))
            updateInsets(fixture, forceNativeSafeArea = true)
            awaitAuthorReport(fixture)
            fixture.scenario.onActivity {
                val margins = fixture.webView.layoutParams as ViewGroup.MarginLayoutParams
                assertEquals(NATIVE_TOP_PX, margins.topMargin)
            }

            updateInsets(fixture)
            assertProtectedGeometry(awaitProtectedReport(fixture))
            assertEdgeToEdge(fixture)

            updateInsets(fixture, isInsideSafeDrawingHost = true)
            awaitAuthorReport(fixture)
            assertEdgeToEdge(fixture)

            updateInsets(fixture)
            assertProtectedGeometry(awaitProtectedReport(fixture))
            assertEquals(1, fixture.server.documentRequestCount.get())
        }
    }

    @Test
    fun laterFrameworkCutoutDeliveryKeepsNativeAndSafeDrawingOwnersFreeOfCssInsets() {
        withLoadedFixture(cover = true, authorEnv = true) { fixture ->
            val providerMajorVersion = WebView.getCurrentWebViewPackage()?.versionName
                ?.substringBefore('.')?.toIntOrNull()
            val nativeCssSafeAreaSupported = providerMajorVersion != null && providerMajorVersion >= 144
            val rawInsets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, NATIVE_TOP_PX, 0, 0))
                .setDisplayCutout(
                    DisplayCutoutCompat(
                        Insets.of(0, NATIVE_TOP_PX, 0, 0),
                        null,
                        Rect(0, 0, 100, NATIVE_TOP_PX),
                        null,
                        null,
                        Insets.NONE,
                    ),
                )
                .build()
            listOf(false, true).forEach { insideSafeDrawingHost ->
                updateInsets(
                    fixture,
                    forceNativeSafeArea = !insideSafeDrawingHost,
                    isInsideSafeDrawingHost = insideSafeDrawingHost,
                    windowInsets = rawInsets,
                )
                fixture.scenario.onActivity {
                    // Framework delivery can happen after Candy's explicit override dispatch.
                    ViewCompat.dispatchApplyWindowInsets(fixture.webView, rawInsets)
                }
                awaitInsetFrames(fixture)
                val cleared = awaitReport(fixture, "Later framework cutout delivery restored a CSS inset") {
                    abs(it.getDouble("envInset")) <= CSS_TOLERANCE &&
                        abs(it.getDouble("bodyPadding")) <= CSS_TOLERANCE &&
                        abs(it.getDouble("fixedTop")) <= CSS_TOLERANCE
                }
                assertEquals("env(safe-area-inset-top)", cleared.getString("bodyInlinePadding"))
                assertEquals("env(safe-area-inset-top)", cleared.getString("fixedInlineTop"))
                assertEquals(0.0, cleared.getDouble("stickyDeclaredTop"), CSS_TOLERANCE)
                assertEquals(-8.0, cleared.getDouble("negativeTop"), CSS_TOLERANCE)

                updateInsets(fixture, windowInsets = rawInsets)
                val restored = awaitReport(fixture, "Returning to CSS ownership did not restore one safe-area inset") {
                    val expectedEnvInset = if (nativeCssSafeAreaSupported) cssInset(it) else 0.0
                    abs(it.getDouble("envInset") - expectedEnvInset) <= CSS_TOLERANCE &&
                        abs(it.getDouble("bodyPadding") - cssInset(it)) <= CSS_TOLERANCE &&
                        abs(it.getDouble("fixedTop") - cssInset(it)) <= CSS_TOLERANCE
                }
                assertEquals(cssInset(restored), restored.getDouble("flowTop"), CSS_TOLERANCE)
                assertEdgeToEdge(fixture)
            }
            assertEquals(1, fixture.server.documentRequestCount.get())
        }
    }

    @Test
    fun privateSessionUsesSameCssGeometryAndRestoration() {
        withLoadedFixture(isPrivate = true) { fixture ->
            assertProtectedGeometry(awaitProtectedReport(fixture))
            assertEdgeToEdge(fixture)
            updatePolicy(fixture, cssPolicy().copy(geckoSafeAreaSettings = GeckoSafeAreaSettings(enabled = false)))
            awaitAuthorReport(fixture)
            assertEquals(1, fixture.server.documentRequestCount.get())
        }
    }

    @Test
    fun amazonOffscreenStickyToolbarStaysHiddenAndShownToolbarReceivesOneInset() {
        withLoadedFixture(baseUrl = "https://www.amazon.in/", html = amazonToolbarHtml()) { fixture ->
            val documentMarker = evaluate(fixture, "globalThis.__candyAmazonDocumentMarker = 'same-document'")
            evaluate(fixture, "scrollTo(0, 400)")
            val hidden = awaitReport(fixture, "Amazon's hidden toolbar remained partly visible") {
                it.getDouble("scrollY") >= 399 &&
                    it.getDouble("toolbarBottom") <= CSS_TOLERANCE &&
                    abs(it.getDouble("stickyDeclaredTop")) <= CSS_TOLERANCE
            }
            assertEquals(0.0, hidden.getDouble("toolbarBottom"), CSS_TOLERANCE)
            assertEquals("0px", hidden.getString("stickyInlineTop"))

            evaluate(fixture, "document.querySelector('#sticky').classList.remove('s-mobile-toolbar-offscreen')")
            val shown = awaitReport(fixture, "Amazon's shown toolbar did not receive one inset") {
                abs(it.getDouble("stickyTop") - cssInset(it)) <= CSS_TOLERANCE
            }
            assertEquals(cssInset(shown), shown.getDouble("stickyDeclaredTop"), CSS_TOLERANCE)

            evaluate(
                fixture,
                "scrollTo(0, 600); document.querySelector('#sticky').classList.add('s-mobile-toolbar-offscreen')",
            )
            val hiddenAfterScroll = awaitReport(fixture, "Amazon's toolbar became partly visible after hiding") {
                it.getDouble("scrollY") >= 599 &&
                    it.getDouble("toolbarBottom") <= CSS_TOLERANCE &&
                    abs(it.getDouble("stickyDeclaredTop")) <= CSS_TOLERANCE
            }
            assertEquals(0.0, hiddenAfterScroll.getDouble("toolbarBottom"), CSS_TOLERANCE)

            updatePolicy(fixture, cssPolicy().copy(geckoSafeAreaSettings = GeckoSafeAreaSettings(enabled = false)))
            evaluate(fixture, "document.querySelector('#sticky').classList.remove('s-mobile-toolbar-offscreen')")
            val restored = awaitReport(fixture, "Disabling policy did not restore Amazon's author top") {
                abs(it.getDouble("stickyDeclaredTop")) <= CSS_TOLERANCE &&
                    abs(it.getDouble("stickyTop")) <= CSS_TOLERANCE
            }
            assertEquals("0px", restored.getString("stickyInlineTop"))
            assertEquals(documentMarker, evaluate(fixture, "globalThis.__candyAmazonDocumentMarker"))
            assertEdgeToEdge(fixture)
        }
    }

    private fun withLoadedFixture(
        cover: Boolean = false,
        authorEnv: Boolean = false,
        isPrivate: Boolean = false,
        baseUrl: String? = null,
        html: String = fixtureHtml(cover, authorEnv),
        action: (SafeAreaFixture) -> Unit,
    ) {
        EdgeToEdgeSiteFixtureServer { html }.use { server ->
            ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                lateinit var factory: SystemWebViewBrowserEngineFactory
                lateinit var session: AndroidBrowserEngineSessionPort
                lateinit var webView: WebView
                scenario.onActivity { activity ->
                    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                    factory = SystemWebViewBrowserEngineFactory(activity)
                    session = factory.create(
                        tabId = "safe-area-${UUID.randomUUID()}",
                        profileId = "default",
                        isPrivate = isPrivate,
                        contentKind = BrowserEngineContentKind.RegularTab,
                        privacyPolicy = cssPolicy(),
                        eventSink = BrowserEngineEventSink {},
                    )
                    webView = session.createView(activity) as WebView
                    activity.setContentView(webView)
                    session.setActive(true)
                    if (baseUrl == null) {
                        session.execute(BrowserEngineCommands.load(server.fixtureUrl("/site-matrix/css-safe-area")))
                    } else {
                        webView.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
                    }
                }
                val fixture = SafeAreaFixture(session, webView, scenario, server)
                try {
                    awaitReport(fixture, "System WebView safe-area fixture did not load") { it.getBoolean("loaded") }
                    updateInsets(fixture)
                    action(fixture)
                } finally {
                    scenario.onActivity {
                        session.releaseView(webView)
                        session.setActive(false)
                        session.execute(BrowserEngineCommands.close())
                        factory.shutdown()
                    }
                }
            }
        }
    }

    private fun cssPolicy(): GeckoPrivacyPolicy = GeckoPrivacyPolicy.Disabled.copy(
        cssSafeAreaTopInsetPx = NATIVE_TOP_PX,
        geckoSafeAreaSettings = GeckoSafeAreaSettings(),
    )

    private fun updatePolicy(fixture: SafeAreaFixture, policy: GeckoPrivacyPolicy) {
        val ready = CountDownLatch(1)
        fixture.scenario.onActivity { fixture.session.updatePrivacyPolicy(policy, onReady = ready::countDown) }
        assertTrue("Safe-area policy was not acknowledged", ready.await(5, TimeUnit.SECONDS))
    }

    private fun updateInsets(
        fixture: SafeAreaFixture,
        forceNativeSafeArea: Boolean = false,
        isInsideSafeDrawingHost: Boolean = false,
        windowInsets: WindowInsetsCompat = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, NATIVE_TOP_PX, 0, 0))
            .build(),
    ) {
        fixture.scenario.onActivity {
            (fixture.webView as GeckoViewInsetHost).updateInsets(
                GeckoViewInsetRules.resolve(
                    safeArea = GeckoViewInsets(left = 0, top = NATIVE_TOP_PX, right = 0, bottom = 0),
                    forceNativeSafeArea = forceNativeSafeArea,
                    forceNativeTopSafeArea = false,
                    isFullscreenContent = false,
                    isInsideSafeDrawingHost = isInsideSafeDrawingHost,
                    useNativeCssSafeArea = true,
                ),
                windowInsets,
            )
        }
    }

    private fun awaitInsetFrames(fixture: SafeAreaFixture) {
        evaluate(
            fixture,
            "globalThis.__candySafeAreaInsetFrames = 0; " +
                "requestAnimationFrame(() => requestAnimationFrame(() => " +
                "globalThis.__candySafeAreaInsetFrames = 2))",
        )
        awaitReport(fixture, "Framework inset delivery did not reach the next layout frames") {
            it.getInt("insetFrames") == 2
        }
    }

    private fun awaitProtectedReport(fixture: SafeAreaFixture): JSONObject =
        awaitReport(fixture, "CSS safe-area protection did not settle") {
            abs(it.getDouble("bodyPadding") - cssInset(it)) <= CSS_TOLERANCE &&
                abs(it.getDouble("fixedTop") - (cssInset(it) + 3)) <= CSS_TOLERANCE &&
                abs(it.getDouble("stickyDeclaredTop") - cssInset(it)) <= CSS_TOLERANCE
        }

    private fun awaitAuthorReport(fixture: SafeAreaFixture): JSONObject =
        awaitReport(fixture, "Safe-area ownership did not restore author geometry") {
            abs(it.getDouble("bodyPadding") - 7) <= CSS_TOLERANCE &&
                abs(it.getDouble("fixedTop") - 3) <= CSS_TOLERANCE &&
                abs(it.getDouble("stickyDeclaredTop")) <= CSS_TOLERANCE
        }

    private fun assertProtectedGeometry(report: JSONObject) {
        val inset = cssInset(report)
        assertTrue("Fixture must exercise a nonzero inset: $report", inset > 0)
        assertEquals(inset, report.getDouble("bodyPadding"), CSS_TOLERANCE)
        assertEquals(inset, report.getDouble("flowTop"), CSS_TOLERANCE)
        assertEquals(inset + 3, report.getDouble("fixedTop"), CSS_TOLERANCE)
        assertEquals(inset, report.getDouble("stickyDeclaredTop"), CSS_TOLERANCE)
    }

    private fun cssInset(report: JSONObject): Double = NATIVE_TOP_PX / report.getDouble("density")

    private fun assertEdgeToEdge(fixture: SafeAreaFixture) {
        fixture.scenario.onActivity { activity ->
            val margins = fixture.webView.layoutParams as ViewGroup.MarginLayoutParams
            assertEquals(0, margins.topMargin)
            assertEquals(0, margins.bottomMargin)
            val location = IntArray(2)
            fixture.webView.getLocationInWindow(location)
            assertEquals(0, location[1])
            assertEquals(activity.window.decorView.height, fixture.webView.height)
        }
    }

    private fun awaitReport(
        fixture: SafeAreaFixture,
        message: String,
        predicate: (JSONObject) -> Boolean,
    ): JSONObject {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        var lastReport: JSONObject? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            val value = evaluate(fixture, REPORT_SCRIPT)
            lastReport = if (value == "null") null else {
                JSONObject(JSONObject("{\"result\":$value}").getString("result"))
            }
            if (lastReport != null && predicate(lastReport)) return lastReport
            SystemClock.sleep(POLL_MILLIS)
        }
        throw AssertionError("$message; last report=$lastReport")
    }

    private fun evaluate(fixture: SafeAreaFixture, expression: String): String {
        val result = AtomicReference<String>()
        val completed = CountDownLatch(1)
        fixture.scenario.onActivity {
            fixture.webView.evaluateJavascript(expression) { value ->
                result.set(value)
                completed.countDown()
            }
        }
        assertTrue("JavaScript evaluation timed out", completed.await(5, TimeUnit.SECONDS))
        return requireNotNull(result.get())
    }

    private data class SafeAreaFixture(
        val session: AndroidBrowserEngineSessionPort,
        val webView: WebView,
        val scenario: ActivityScenario<GeckoScrollTestActivity>,
        val server: EdgeToEdgeSiteFixtureServer,
    )

    private companion object {
        const val NATIVE_TOP_PX = 96
        const val CSS_TOLERANCE = 0.5
        const val TIMEOUT_MILLIS = 15_000L
        const val POLL_MILLIS = 50L
        val REPORT_SCRIPT = """
            (() => {
              const flow = document.querySelector('#flow');
              if (!flow || document.readyState !== 'complete') return null;
              const fixed = document.querySelector('#fixed');
              const sticky = document.querySelector('#sticky');
              return JSON.stringify({
                loaded: true,
                density: devicePixelRatio,
                envInset: Number.parseFloat(getComputedStyle(document.querySelector('#env')).paddingTop),
                bodyPadding: Number.parseFloat(getComputedStyle(document.body).paddingTop),
                bodyInlinePadding: document.body.style.paddingTop,
                flowTop: flow.getBoundingClientRect().top,
                fixedTop: fixed.getBoundingClientRect().top,
                fixedInlineTop: fixed.style.top,
                stickyTop: sticky.getBoundingClientRect().top,
                toolbarBottom: sticky.getBoundingClientRect().bottom,
                stickyDeclaredTop: Number.parseFloat(getComputedStyle(sticky).top),
                stickyInlineTop: sticky.style.top,
                negativeTop: document.querySelector('#negative').getBoundingClientRect().top,
                insetFrames: globalThis.__candySafeAreaInsetFrames || 0,
                scrollY
              });
            })()
        """.trimIndent()

        fun amazonToolbarHtml(): String = fixtureHtml(cover = false, authorEnv = false)
            .replace(
                "</style>",
                """
                    #sticky.s-mobile-toolbar-sticky { height: 96.76px; transform: translateY(0); }
                    #sticky.s-mobile-toolbar-offscreen { transform: translateY(-100%); }
                  </style>
                """.trimIndent(),
            )
            .replace(
                "id=\"sticky\"",
                "id=\"sticky\" class=\"s-mobile-toolbar-sticky s-mobile-toolbar-offscreen\"",
            )

        fun fixtureHtml(cover: Boolean, authorEnv: Boolean): String {
            val viewport = "width=device-width,initial-scale=1" + if (cover) ",viewport-fit=cover" else ""
            val bodyPadding = if (authorEnv) "env(safe-area-inset-top)" else "7px"
            val fixedTop = if (authorEnv) "env(safe-area-inset-top)" else "3px"
            return """
                <!doctype html>
                <html><head>
                  <title>Candy System WebView safe-area fixture</title>
                  <meta name="viewport" content="$viewport">
                  <style>
                    html, body { margin: 0; }
                    #flow { height: 24px; }
                    #sticky { position: sticky; height: 24px; }
                    #fixed { position: fixed; left: 0; width: 100%; height: 24px; }
                    #negative { position: fixed; top: -8px; left: 0; width: 20px; height: 24px; }
                    #env { position: absolute; visibility: hidden; padding-top: env(safe-area-inset-top); }
                    #space { height: 2000px; }
                  </style>
                </head><body style="padding-top: $bodyPadding">
                  <div id="flow">Normal flow</div>
                  <div id="sticky" style="top: 0px">Sticky content</div>
                  <div id="fixed" style="top: $fixedTop">Fixed content</div>
                  <div id="negative">Negative author top</div>
                  <div id="env"></div>
                  <div id="space"></div>
                </body></html>
            """.trimIndent()
        }
    }
}
