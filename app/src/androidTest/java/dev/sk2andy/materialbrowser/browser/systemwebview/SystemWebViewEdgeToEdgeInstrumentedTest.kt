package dev.sk2andy.materialbrowser.browser.systemwebview

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.DeveloperSettings
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemWebViewEdgeToEdgeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store by lazy { BrowserSessionStore(context) }
    private val preferences by lazy {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    private lateinit var server: EdgeToEdgeFixtureServer

    @Before
    fun setUp() {
        server = EdgeToEdgeFixtureServer()
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        store.saveStartupAnimationEnabled(false)
        store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.SystemWebView)
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        val tab = BrowserTab(
            id = "system-webview-edge-to-edge-fixture",
            lastAccessedAt = System.currentTimeMillis(),
            url = server.url,
        )
        assertTrue(store.saveTabsImmediately(listOf(tab), tab.id))
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
        server.close()
    }

    @Test
    fun selectedSystemWebViewUsesFullRendererBoundsAndProtectsScrollingControls() {
        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
        ).use { scenario ->
            val webView = awaitViewReady(scenario)
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                val view = requireNotNull(controller.selectedBrowserEngineViewForTesting())
                controller.onWindowInsetsChanged(
                    WindowInsetsCompat.Builder()
                        .setInsets(
                            WindowInsetsCompat.Type.statusBars(),
                            Insets.of(0, STATUS_BAR_INSET_PX, 0, 0),
                        )
                        .setInsets(
                            WindowInsetsCompat.Type.navigationBars(),
                            Insets.of(0, 0, 0, NAVIGATION_BAR_INSET_PX),
                        )
                        .build(),
                )

                assertWindowTop(view, expectedTop = 0)
                assertWindowTop(webView, expectedTop = 0)
                assertWindowBottom(view, activity.window.decorView.height)
                assertWindowBottom(webView, activity.window.decorView.height)
                assertEquals(0, (view.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
                assertEquals(0, (webView.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
            }

            var edgeState = "not sampled"
            awaitJavaScript({ "System WebView did not apply scrollable top inset: $edgeState" }) {
                val expectedCssPx = STATUS_BAR_INSET_PX / evaluateNumber(scenario, "devicePixelRatio")
                val menuTop = evaluateNumber(scenario, "menuTop")
                val spacer = evaluateNumber(scenario, "rootSpacerHeight")
                edgeState = "expected=$expectedCssPx menu=$menuTop spacer=$spacer"
                menuTop >= expectedCssPx + COMPACT_CONTROL_PADDING_CSS_PIXELS - CSS_TOLERANCE &&
                    spacer >= expectedCssPx - CSS_TOLERANCE
            }
            val documentToken = evaluateString(scenario, "globalThis.__candyDocumentToken")
            evaluateNumber(scenario, "scrollPage")
            awaitJavaScript({ "Sticky control entered status bar after scroll: $edgeState" }) {
                val expectedCssPx = STATUS_BAR_INSET_PX / evaluateNumber(scenario, "devicePixelRatio")
                val scrollY = evaluateNumber(scenario, "scrollY")
                val stickyTop = evaluateNumber(scenario, "stickyTop")
                edgeState = "expected=$expectedCssPx scroll=$scrollY sticky=$stickyTop"
                scrollY > 300.0 &&
                    stickyTop >= expectedCssPx - CSS_TOLERANCE
            }
            evaluateNumber(scenario, "scrollTop")
            awaitJavaScript({ "Compact menu entered status bar after returning: $edgeState" }) {
                val expectedCssPx = STATUS_BAR_INSET_PX / evaluateNumber(scenario, "devicePixelRatio")
                val scrollY = evaluateNumber(scenario, "scrollY")
                val menuTop = evaluateNumber(scenario, "menuTop")
                edgeState = "expected=$expectedCssPx scroll=$scrollY menu=$menuTop"
                scrollY <= CSS_TOLERANCE &&
                    menuTop >=
                    expectedCssPx + COMPACT_CONTROL_PADDING_CSS_PIXELS - CSS_TOLERANCE
            }
            SystemClock.sleep(FALLBACK_REGRESSION_WINDOW_MILLIS)

            assertEquals(
                "Scrolling reloaded the current document",
                documentToken,
                evaluateString(scenario, "globalThis.__candyDocumentToken"),
            )
            scenario.onActivity { activity ->
                val view = requireNotNull(
                    activity.browserControllerForTesting().selectedBrowserEngineViewForTesting(),
                )
                val currentWebView = requireNotNull(view.findSystemWebView())
                assertWindowTop(view, expectedTop = 0)
                assertWindowTop(currentWebView, expectedTop = 0)
                assertWindowBottom(view, activity.window.decorView.height)
                assertWindowBottom(currentWebView, activity.window.decorView.height)
                assertEquals(0, (view.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
                assertEquals(
                    0,
                    (currentWebView.layoutParams as ViewGroup.MarginLayoutParams).topMargin,
                )
            }
        }
    }

    @Test
    fun inputActivatedSearchHeaderClearsStatusBarWithoutNativeFallback() {
        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
        ).use { scenario ->
            val webView = awaitViewReady(scenario)
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().onWindowInsetsChanged(
                    WindowInsetsCompat.Builder()
                        .setInsets(
                            WindowInsetsCompat.Type.statusBars(),
                            Insets.of(0, STATUS_BAR_INSET_PX, 0, 0),
                        )
                        .setInsets(
                            WindowInsetsCompat.Type.navigationBars(),
                            Insets.of(0, 0, 0, NAVIGATION_BAR_INSET_PX),
                        )
                        .build(),
                )
            }
            awaitJavaScript({ "System WebView did not apply initial top inset" }) {
                evaluateNumber(scenario, "rootSpacerHeight") >=
                    STATUS_BAR_INSET_PX / evaluateNumber(scenario, "devicePixelRatio") -
                    CSS_TOLERANCE
            }
            val documentToken = evaluateString(scenario, "globalThis.__candyDocumentToken")

            evaluateNumber(scenario, "activateSearchHeader")
            val expectedTop =
                STATUS_BAR_INSET_PX / evaluateNumber(scenario, "devicePixelRatio")
            val deadline = SystemClock.elapsedRealtime() + IMMEDIATE_LAYOUT_TIMEOUT_MILLIS
            var headerTop = Double.NEGATIVE_INFINITY
            while (SystemClock.elapsedRealtime() < deadline) {
                headerTop = evaluateNumber(scenario, "searchHeaderTop")
                if (headerTop >= expectedTop - CSS_TOLERANCE) break
                SystemClock.sleep(FRAME_SETTLE_MILLIS)
            }
            assertTrue(
                "Input-activated search header remained in the status bar: " +
                    "top=$headerTop expected=$expectedTop",
                headerTop >= expectedTop - CSS_TOLERANCE,
            )
            SystemClock.sleep(FALLBACK_REGRESSION_WINDOW_MILLIS)

            assertEquals(
                "Search-header repair reloaded the current document",
                documentToken,
                evaluateString(scenario, "globalThis.__candyDocumentToken"),
            )
            scenario.onActivity { activity ->
                assertEquals(0, (webView.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
                assertWindowTop(webView, expectedTop = 0)
                assertWindowBottom(webView, activity.window.decorView.height)
            }
        }
    }

    @Test
    fun developerSafeAreaSettingsReachOpenDocumentWithoutReload() {
        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
        ).use { scenario ->
            awaitViewReady(scenario)
            val documentToken = evaluateString(scenario, "globalThis.__candyDocumentToken")
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().updateDeveloperSettings(
                    DeveloperSettings(
                        safeAreaLayoutQuietPeriodMillis = 250,
                        safeAreaRequiredFailureCount = 4,
                    ),
                )
            }

            assertEquals(
                250.0,
                evaluateRawNumber(
                    scenario,
                    "globalThis.CandyContentTopInset.safeAreaLayoutQuietPeriodMillis()",
                ),
                0.0,
            )
            assertEquals(
                4.0,
                evaluateRawNumber(
                    scenario,
                    "globalThis.CandyContentTopInset.safeAreaRequiredFailureCount()",
                ),
                0.0,
            )
            assertEquals(READY_TITLE, evaluateString(scenario, "document.title"))
            assertEquals(
                "Developer settings reloaded the current document",
                documentToken,
                evaluateString(scenario, "globalThis.__candyDocumentToken"),
            )
        }
    }

    private fun awaitViewReady(scenario: ActivityScenario<MainActivity>): android.webkit.WebView {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            var readyView: android.webkit.WebView? = null
            scenario.onActivity { activity ->
                readyView = activity.browserControllerForTesting()
                    .selectedBrowserEngineViewForTesting()
                    ?.let { view ->
                        view.findSystemWebView()?.takeIf { webView ->
                            view.isAttachedToWindow &&
                            view.width > 0 &&
                            view.height > 0 &&
                                webView.title == READY_TITLE
                        }
                    }
            }
            readyView?.let { return it }
            SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue("System WebView did not become ready", false)
        error("unreachable")
    }

    private fun View.findSystemWebView(): android.webkit.WebView? = when (this) {
        is android.webkit.WebView -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { index ->
            getChildAt(index).findSystemWebView()
        }
        else -> null
    }

    private fun assertWindowTop(view: View, expectedTop: Int) {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        assertEquals(expectedTop, location[1])
    }

    private fun assertWindowBottom(view: View, expectedBottom: Int) {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        assertEquals(expectedBottom, location[1] + view.height)
    }

    private fun evaluateNumber(
        scenario: ActivityScenario<MainActivity>,
        expression: String,
    ): Double = evaluateRawNumber(scenario, "globalThis.__candyEdgeToEdge.$expression")

    private fun evaluateRawNumber(
        scenario: ActivityScenario<MainActivity>,
        expression: String,
    ): Double = evaluate(scenario, expression).toDouble()

    private fun evaluateString(
        scenario: ActivityScenario<MainActivity>,
        expression: String,
    ): String = evaluate(scenario, expression).removeSurrounding("\"")

    private fun evaluate(
        scenario: ActivityScenario<MainActivity>,
        expression: String,
    ): String {
        val result = AtomicReference<String>()
        val completed = CountDownLatch(1)
        scenario.onActivity { activity ->
            val webView = requireNotNull(
                activity.browserControllerForTesting()
                    .selectedBrowserEngineViewForTesting()
                    ?.findSystemWebView(),
            )
            webView.evaluateJavascript(expression) { value ->
                result.set(value)
                completed.countDown()
            }
        }
        assertTrue("JavaScript result timed out for $expression", completed.await(5, TimeUnit.SECONDS))
        return requireNotNull(result.get())
    }

    private fun awaitJavaScript(
        message: () -> String,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue(message(), condition())
    }

    private class EdgeToEdgeFixtureServer : Closeable {
        private val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        private val thread = Thread(::serve, "system-webview-edge-to-edge-fixture").apply {
            isDaemon = true
            start()
        }
        val url = "http://127.0.0.1:${server.localPort}/edge-to-edge"

        private fun serve() {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: return
                socket.use { connection ->
                    runCatching {
                        connection.getInputStream().bufferedReader().apply {
                            readLine()
                            while (!readLine().isNullOrEmpty()) {
                                // Drain request headers before serving deterministic content.
                            }
                        }
                        val body = HTML.toByteArray()
                        connection.getOutputStream().buffered().use { output ->
                            output.write(
                                "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n"
                                    .toByteArray(),
                            )
                            output.write(
                                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                                    .toByteArray(),
                            )
                            output.write(body)
                        }
                    }
                }
            }
        }

        override fun close() {
            server.close()
            thread.join(1_000L)
        }
    }

    private companion object {
        const val TEST_ACTIVITY_ACTION =
            "dev.sk2andy.materialbrowser.test.SYSTEM_WEBVIEW_EDGE_TO_EDGE"
        const val READY_TITLE = "Candy System WebView edge ready"
        const val STATUS_BAR_INSET_PX = 96
        const val NAVIGATION_BAR_INSET_PX = 48
        const val COMPACT_CONTROL_PADDING_CSS_PIXELS = 8.0
        const val CSS_TOLERANCE = 1.0
        const val IMMEDIATE_LAYOUT_TIMEOUT_MILLIS = 250L
        const val FRAME_SETTLE_MILLIS = 16L
        const val FALLBACK_REGRESSION_WINDOW_MILLIS = 1_600L
        const val TIMEOUT_MILLIS = 15_000L
        const val POLL_MILLIS = 50L
        val HTML =
            """
            <!doctype html>
            <html><head><title>$READY_TITLE</title>
            <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
            <style>
              html,body { margin:0; }
              #drawer-shell, #drawer-backdrop {
                position:fixed; inset:0; z-index:9998;
              }
              #drawer-shell { background:transparent; }
              #drawer-backdrop { background:rgba(0,0,0,.6); z-index:9997; }
              #navd { position:absolute; top:0; left:0; width:60px; height:64px; }
              #header {
                position:fixed; top:40px; left:0; width:100%; height:80px;
                z-index:1; background:white;
              }
              #sign-in { position:absolute; top:8px; right:8px; }
              #search-header {
                position:fixed; top:0; left:0; width:100%; height:64px;
                z-index:10000; background:white;
              }
              #lead { height:200px; }
              #sticky { position:sticky; top:0; height:24px; background:blue; }
              main { height:4000px; }
            </style></head><body>
              <div id="drawer-backdrop"></div>
              <div id="drawer-shell"><div role="button" style="height:120px">Drawer</div></div>
              <div id="navd"><div><div id="menu" role="button">Menu</div></div></div>
              <header id="header"><button id="sign-in">Sign in</button></header>
              <input id="query" aria-label="Search" hidden>
              <form id="search-header" hidden>
                <button type="button">Add</button><input value="Vimeo sample video">
                <button type="button">Close</button>
              </form>
              <div id="lead"></div><nav id="sticky"></nav><main></main>
              <script>
                globalThis.__candyDocumentToken =
                  String(performance.timeOrigin) + ':' + Math.random().toString(36);
                document.querySelector('#query').addEventListener('input', () => {
                  document.querySelector('#search-header').hidden = false;
                });
                globalThis.__candyEdgeToEdge = {
                  get devicePixelRatio() { return globalThis.devicePixelRatio; },
                  get menuTop() { return document.querySelector('#menu').getBoundingClientRect().top; },
                  get searchHeaderTop() {
                    return document.querySelector('#search-header').getBoundingClientRect().top;
                  },
                  get stickyTop() { return document.querySelector('#sticky').getBoundingClientRect().top; },
                  get rootSpacerHeight() {
                    return Number.parseFloat(
                      getComputedStyle(document.documentElement, '::before').height
                    ) || 0;
                  },
                  get scrollY() { return globalThis.scrollY; },
                  get activateSearchHeader() {
                    document.querySelector('#query').dispatchEvent(
                      new InputEvent('input', {
                        bubbles: true,
                        inputType: 'insertText',
                        data: 'v'
                      })
                    );
                    return 0;
                  },
                  get scrollPage() { globalThis.scrollTo(0, 600); return 0; },
                  get scrollTop() { globalThis.scrollTo(0, 0); return 0; }
                };
                addEventListener('scroll', () => {
                  document.querySelector('#header').style.display = scrollY > 100 ? 'none' : 'block';
                }, { passive: true });
              </script>
            </body></html>
            """.trimIndent()
    }
}
