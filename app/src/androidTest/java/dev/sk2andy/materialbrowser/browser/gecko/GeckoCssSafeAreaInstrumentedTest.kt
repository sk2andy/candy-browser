package dev.sk2andy.materialbrowser.browser.gecko

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.data.GeckoSafeAreaSettings
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** CSS ownership and native input contracts; no CPU or checkerboard-absence assertion. */
@RunWith(AndroidJUnit4::class)
class GeckoCssSafeAreaInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun cssInsetProtectsBodyFlowAndFixedContentWithoutShrinkingNativeSurface() {
        withLoadedFixture { fixture ->
            val report = awaitProtectedReport(fixture)
            val inset = report.getDouble("envInset")
            assertEquals(inset, report.getDouble("bodyPadding"), 0.5)
            assertEquals(inset, report.getDouble("flowTop"), 0.5)
            assertEquals(inset, report.getDouble("fixedTop"), 0.5)
            assertEquals(inset, report.getDouble("stickyDeclaredTop"), 0.5)
            assertTrue(report.getBoolean("bodyProtected"))
            assertTrue(report.getBoolean("fixedProtected"))
            assertTrue(report.getBoolean("stickyProtected"))
            assertFalse("Initial traversal must not own a hidden menu", report.getBoolean("menuProtected"))
            assertFullSurface(fixture, report)
            assertEquals(1, fixture.server.documentRequestCount.get())
        }
    }

    @Test
    fun initiallyTopStickyHeaderDoesNotCoverTheFollowingFlowContent() {
        withLoadedFixture(mode = "top-sticky") { fixture ->
            val report = awaitProtectedReport(fixture)
            assertEquals(report.getDouble("envInset"), report.getDouble("stickyTop"), 0.5)
            assertEquals(report.getDouble("stickyBottom"), report.getDouble("flowTop"), 0.5)
            assertFullSurface(fixture, report)
            assertEquals(1, fixture.server.documentRequestCount.get())
        }
    }

    @Test
    fun scrollingKeepsCssStickyProtectionWithoutAuthorizingExistingMenuOrWritingInsetsAgain() {
        withLoadedFixture(mode = "scroll-menu") { fixture ->
            val before = awaitProtectedReport(fixture)
            scrollToSticky(fixture, before)
            val scrolled = awaitReport(fixture.title, "Scroll-only menu and sticky did not settle") { report ->
                report.getDouble("scrollY") >= 550 && report.getInt("scrollClassUpdates") == 1 &&
                    report.getString("menuDisplay") != "none" &&
                    abs(report.getDouble("stickyTop") - report.getDouble("envInset")) <= 0.5
            }
            // Observe beyond the configured mutation debounce; this is not a query/CPU assertion.
            SystemClock.sleep(STABILITY_MILLIS)
            val settled = awaitReport(fixture.title, "Scroll report disappeared") { report ->
                report.getInt("sequence") >= scrolled.getInt("sequence")
            }
            assertEquals(0, settled.getInt("trustedClicks"))
            assertEquals(0.0, settled.getDouble("menuTop"), 0.5)
            assertFalse("Scroll alone must not authorize hidden-menu discovery", settled.getBoolean("menuProtected"))
            assertEquals(before.getInt("insetStyleChanges"), settled.getInt("insetStyleChanges"))
            assertTrue(settled.getDouble("stickyTop") >= settled.getDouble("envInset") - 0.5)
            assertFullSurface(fixture, settled)
            assertEquals(1, fixture.server.documentRequestCount.get())
        }
    }

    @Test
    fun disablingCssOwnershipRestoresAuthorStylesWhileNativeEnvRemainsPositiveWithoutReload() {
        withLoadedFixture { fixture ->
            scrollToSticky(fixture, awaitProtectedReport(fixture))
            awaitReport(fixture.title, "Sticky did not become active before disabling") { report ->
                report.getDouble("scrollY") >= 550 &&
                    abs(report.getDouble("stickyTop") - report.getDouble("envInset")) <= 0.5
            }
            updatePolicy(fixture, cssPolicy().copy(geckoSafeAreaSettings = GeckoSafeAreaSettings(enabled = false)))
            val restored = awaitReport(
                fixture.title,
                "Disabling CSS safe-area did not restore author styles",
            ) { report ->
                !report.getBoolean("bodyProtected") && !report.getBoolean("fixedProtected") &&
                    !report.getBoolean("stickyProtected") && report.getDouble("bodyPadding") <= 0.5 &&
                    abs(report.getDouble("fixedTop")) <= 0.5 && abs(report.getDouble("stickyDeclaredTop")) <= 0.5
            }
            assertNativeInset(restored)
            assertEquals(0.0, restored.getDouble("bodyPadding"), 0.5)
            assertEquals(0.0, restored.getDouble("fixedTop"), 0.5)
            assertEquals(0.0, restored.getDouble("stickyTop"), 0.5)
            assertEquals("0px", restored.getString("bodyInlinePadding"))
            assertEquals("0px", restored.getString("fixedInlineTop"))
            assertEquals("0px", restored.getString("stickyInlineTop"))
            assertFullSurface(fixture, restored)
            assertEquals(
                "CSS setting changes must not reload the document",
                1,
                fixture.server.documentRequestCount.get(),
            )
        }
    }

    @Test
    fun trustedNativeClickRechecksExistingHiddenMenuAndRestoresLaterAuthorTop() {
        withLoadedFixture { fixture ->
            val initial = awaitProtectedReport(fixture)
            assertEquals("none", initial.getString("menuDisplay"))
            assertFalse(initial.getBoolean("menuProtected"))
            tapAuthorButton(fixture, initial)
            val opened = awaitReport(fixture.title, "Trusted click did not protect the existing menu") { report ->
                report.getInt("trustedClicks") == 1 && report.getBoolean("lastClickTrusted") &&
                    report.getString("menuDisplay") != "none" && report.getBoolean("menuProtected") &&
                    report.getDouble("menuTop") >= report.getDouble("envInset") - 0.5
            }
            assertEquals(opened.getDouble("envInset"), opened.getDouble("menuTop"), 0.5)
            tapAuthorButton(fixture, opened)
            val changed = awaitReport(
                fixture.title,
                "Trusted author top update did not regain CSS protection",
            ) { report ->
                report.getInt("trustedClicks") == 2 && report.getBoolean("lastClickTrusted") &&
                    report.getBoolean("menuProtected") && report.getString("menuInlineTop").contains("4px") &&
                    report.getDouble("menuTop") >= report.getDouble("envInset") - 0.5
            }
            assertFullSurface(fixture, changed)
            updatePolicy(fixture, cssPolicy().copy(geckoSafeAreaSettings = GeckoSafeAreaSettings(enabled = false)))
            val restored = awaitReport(
                fixture.title,
                "Disabling CSS safe-area lost the newer author menu top",
            ) { report ->
                !report.getBoolean("menuProtected") && report.getString("menuInlineTop") == "4px" &&
                    abs(report.getDouble("menuTop") - 4) <= 0.5
            }
            assertNativeInset(restored)
            assertEquals(4.0, restored.getDouble("menuTop"), 0.5)
            assertEquals(1, fixture.server.documentRequestCount.get())
        }
    }

    private fun withLoadedFixture(mode: String = "", action: (CssFixture) -> Unit) {
        val title = AtomicReference<String?>(null)
        val settled = AtomicBoolean(false)
        EdgeToEdgeSiteFixtureServer { target ->
            if (target.startsWith(FIXTURE_PATH)) HTML else "<!doctype html><title>Fixture resource</title>"
        }.use { server ->
            ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                lateinit var session: GeckoBrowserSession
                lateinit var view: View
                scenario.onActivity { activity ->
                    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                    session = GeckoRuntimeOwner.getOrCreate(activity).createSession(
                        profileId = "css-safe-area-${UUID.randomUUID()}",
                        isPrivate = false,
                        privacyPolicy = cssPolicy(),
                    )
                    session.bindExtensionTab("css-safe-area-${UUID.randomUUID()}", 1)
                    session.setStateListener { state ->
                        title.set(state.title)
                        settled.set(!state.isLoading && state.title?.startsWith(REPORT_PREFIX) == true)
                    }
                    view = session.createView(activity)
                    activity.setContentView(view)
                    session.setActive(true)
                    updateNativeTop(view, 0)
                    assertTrue(session.loadUrl(server.fixtureUrl("$FIXTURE_PATH?mode=$mode")))
                }
                try {
                    val loaded = awaitReport(title, "CSS safe-area fixture did not settle") { report ->
                        settled.get() && report.getBoolean("loaded") && report.getDouble("viewportHeight") > 0
                    }
                    val fixture = CssFixture(session, view, scenario, title, server, loaded.getDouble("viewportHeight"))
                    scenario.onActivity { updateNativeTop(view, NATIVE_TOP_PX) }
                    awaitReport(title, "Author env did not receive the native inset") { report ->
                        abs(report.getDouble("envInset") * report.getDouble("density") - NATIVE_TOP_PX) <= 0.5
                    }
                    updatePolicy(fixture, cssPolicy())
                    awaitProtectedReport(fixture)
                    SystemClock.sleep(STABILITY_MILLIS)
                    action(fixture)
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

    private fun cssPolicy(): GeckoPrivacyPolicy = GeckoPrivacyPolicy.Disabled.copy(
        cssSafeAreaTopInsetPx = NATIVE_TOP_PX,
        geckoSafeAreaSettings = GeckoSafeAreaSettings(),
    )

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

    private fun updatePolicy(fixture: CssFixture, policy: GeckoPrivacyPolicy) {
        val ready = CountDownLatch(1)
        fixture.scenario.onActivity { fixture.session.updatePrivacyPolicy(policy, onReady = ready::countDown) }
        assertTrue("CSS safe-area policy was not acknowledged", ready.await(30, TimeUnit.SECONDS))
    }

    private fun awaitProtectedReport(fixture: CssFixture): JSONObject =
        awaitReport(fixture.title, "CSS safe-area consumers did not reach the native env inset") { report ->
            report.getBoolean("bodyProtected") && report.getBoolean("fixedProtected") &&
                report.getBoolean("stickyProtected") && report.getDouble("envInset") > 0 &&
                abs(report.getDouble("bodyPadding") - report.getDouble("envInset")) <= 0.5 &&
                abs(report.getDouble("fixedTop") - report.getDouble("envInset")) <= 0.5
        }

    private fun awaitReport(
        title: AtomicReference<String?>,
        message: String,
        predicate: (JSONObject) -> Boolean,
    ): JSONObject {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            val current = title.get().orEmpty()
            val report = if (current.startsWith(REPORT_PREFIX) && current.length < MAX_REPORT_CHARACTERS) {
                runCatching { JSONObject(current.removePrefix(REPORT_PREFIX)) }.getOrNull()
            } else {
                null
            }
            if (report != null && predicate(report)) return report
            SystemClock.sleep(POLL_MILLIS)
        }
        throw AssertionError("$message; last title=${title.get()}")
    }

    private fun scrollToSticky(fixture: CssFixture, report: JSONObject) {
        val offsetPx = (SCROLL_OFFSET_CSS_PX * report.getDouble("density")).roundToInt()
        fixture.scenario.onActivity { fixture.session.scrollToVerticalOffset(offsetPx) }
    }

    private fun assertNativeInset(report: JSONObject) {
        assertEquals(NATIVE_TOP_PX.toDouble(), report.getDouble("envInset") * report.getDouble("density"), 0.5)
        assertTrue(report.getDouble("envInset") > 0)
    }

    private fun assertFullSurface(fixture: CssFixture, report: JSONObject) {
        assertNativeInset(report)
        assertEquals(fixture.initialViewportHeight, report.getDouble("viewportHeight"), 0.5)
        fixture.scenario.onActivity { activity ->
            assertEdgeToEdge(fixture.view, activity.window.decorView.height)
            assertEdgeToEdge((fixture.view as ViewGroup).getChildAt(0), activity.window.decorView.height)
        }
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

    private fun tapAuthorButton(fixture: CssFixture, report: JSONObject) {
        val location = IntArray(2)
        fixture.scenario.onActivity { fixture.view.getLocationOnScreen(location) }
        val density = report.getDouble("density").toFloat()
        val x = location[0] + report.getDouble("buttonX").toFloat() * density
        val y = location[1] + report.getDouble("buttonY").toFloat() * density
        val downTime = SystemClock.uptimeMillis()
        injectTouch(MotionEvent.ACTION_DOWN, downTime, downTime, x, y)
        SystemClock.sleep(50)
        injectTouch(MotionEvent.ACTION_UP, downTime, SystemClock.uptimeMillis(), x, y)
    }

    private fun injectTouch(action: Int, downTime: Long, eventTime: Long, x: Float, y: Float) {
        MotionEvent.obtain(downTime, eventTime, action, x, y, 0).also { event ->
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                assertTrue(
                    "Input injection failed for ${MotionEvent.actionToString(action)}",
                    instrumentation.uiAutomation.injectInputEvent(event, true),
                )
            } finally {
                event.recycle()
            }
        }
    }

    private data class CssFixture(
        val session: GeckoBrowserSession,
        val view: View,
        val scenario: ActivityScenario<GeckoScrollTestActivity>,
        val title: AtomicReference<String?>,
        val server: EdgeToEdgeSiteFixtureServer,
        val initialViewportHeight: Double,
    )

    private companion object {
        const val FIXTURE_PATH = "/site-matrix/css-safe-area"
        const val REPORT_PREFIX = "Candy CSS safe-area: "
        const val MAX_REPORT_CHARACTERS = 4_096
        const val NATIVE_TOP_PX = 144
        const val SCROLL_OFFSET_CSS_PX = 600
        const val STABILITY_MILLIS = 1_100L
        const val TIMEOUT_MILLIS = 30_000L
        const val POLL_MILLIS = 50L
        val HTML = """
            <!doctype html>
            <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
            <title>Preparing CSS safe-area</title>
            <style>
              html, body { margin: 0; }
              body { padding-top: 0; }
              #probe { position: absolute; top: 0; visibility: hidden; padding-top: env(safe-area-inset-top); }
              #fixed { position: fixed; left: 0; right: 0; height: 56px; background: #dce9ef; z-index: 20; }
              #open { margin: 12px 0 0 16px; width: 140px; height: 32px; }
              #lead { height: 400px; }
              #sticky { position: sticky; height: 40px; background: #a6c5d5; z-index: 10; }
              #tail { height: 2400px; background: linear-gradient(#315469, #a6c5d5); }
              #menu { display: none; position: fixed; left: 55%; right: 0; height: 32px; background: #efb873; z-index: 30; }
              #menu.open { display: block; }
            </style>
            <body style="padding-top: 0px;">
              <div id="probe"></div>
              <header id="fixed" style="top: 0px;"><button id="open">Open menu</button></header>
              <div id="lead"></div>
              <header id="sticky" style="top: 0px;">CSS sticky</header>
              <main id="tail">Scrollable CSS fixture</main>
              <aside id="menu" style="top: 0px;">Existing hidden menu</aside>
            </body>
            <script>
              const mode = new URLSearchParams(location.search).get('mode');
              const body = document.body;
              const fixed = document.querySelector('#fixed');
              const sticky = document.querySelector('#sticky');
              const menu = document.querySelector('#menu');
              const button = document.querySelector('#open');
              if (mode === 'top-sticky') body.insertBefore(sticky, document.querySelector('#lead'));
              let trustedClicks = 0;
              let lastClickTrusted = false;
              let scrollClassUpdates = 0;
              let insetStyleChanges = 0;
              const containsInset = (value) => value.includes('env(safe-area-inset-top');
              new MutationObserver((records) => {
                for (const record of records) {
                  if (record.attributeName === 'style' && (
                      containsInset(record.target.style.top) ||
                      record.target === body && containsInset(body.style.paddingTop))) insetStyleChanges++;
                }
              }).observe(document.documentElement, { attributes: true, attributeFilter: ['style'], subtree: true });
              button.addEventListener('click', (event) => {
                lastClickTrusted = event.isTrusted;
                if (!event.isTrusted) return;
                trustedClicks++;
                menu.classList.add('open');
                if (trustedClicks > 1) menu.style.top = '4px';
              });
              addEventListener('scroll', () => {
                if (mode === 'scroll-menu' && scrollY > 450 && scrollClassUpdates === 0) {
                  menu.classList.add('open');
                  scrollClassUpdates++;
                }
              }, { passive: true });
              let sequence = 0;
              let lastReport = '';
              function sample() {
                const buttonRect = button.getBoundingClientRect();
                const data = {
                  loaded: document.readyState === 'complete',
                  density: devicePixelRatio, viewportHeight: innerHeight, scrollY,
                  envInset: parseFloat(getComputedStyle(document.querySelector('#probe')).paddingTop),
                  bodyPadding: parseFloat(getComputedStyle(body).paddingTop),
                  bodyInlinePadding: body.style.paddingTop, bodyProtected: containsInset(body.style.paddingTop),
                  flowTop: document.querySelector('#lead').getBoundingClientRect().top + scrollY,
                  fixedTop: fixed.getBoundingClientRect().top,
                  fixedInlineTop: fixed.style.top, fixedProtected: containsInset(fixed.style.top),
                  stickyTop: sticky.getBoundingClientRect().top,
                  stickyBottom: sticky.getBoundingClientRect().bottom,
                  stickyDeclaredTop: parseFloat(getComputedStyle(sticky).top),
                  stickyInlineTop: sticky.style.top, stickyProtected: containsInset(sticky.style.top),
                  menuDisplay: getComputedStyle(menu).display, menuTop: menu.getBoundingClientRect().top,
                  menuInlineTop: menu.style.top, menuProtected: containsInset(menu.style.top),
                  buttonX: buttonRect.left + buttonRect.width / 2,
                  buttonY: buttonRect.top + buttonRect.height / 2,
                  trustedClicks, lastClickTrusted, scrollClassUpdates, insetStyleChanges,
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
