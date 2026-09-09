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
                val fixedTop = evaluateNumber(scenario, "fixedTop")
                val spacer = evaluateNumber(scenario, "rootSpacerHeight")
                edgeState = "expected=$expectedCssPx fixed=$fixedTop spacer=$spacer"
                fixedTop >= expectedCssPx - CSS_TOLERANCE &&
                    spacer >= expectedCssPx - CSS_TOLERANCE
            }
            evaluateNumber(scenario, "scrollPage")
            awaitJavaScript({ "Sticky control entered status bar after scroll: $edgeState" }) {
                val expectedCssPx = STATUS_BAR_INSET_PX / evaluateNumber(scenario, "devicePixelRatio")
                val scrollY = evaluateNumber(scenario, "scrollY")
                val stickyTop = evaluateNumber(scenario, "stickyTop")
                edgeState = "expected=$expectedCssPx scroll=$scrollY sticky=$stickyTop"
                scrollY > 300.0 &&
                    stickyTop >= expectedCssPx - CSS_TOLERANCE
            }
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
    ): Double {
        val result = AtomicReference<String>()
        val completed = CountDownLatch(1)
        scenario.onActivity { activity ->
            val webView = requireNotNull(
                activity.browserControllerForTesting()
                    .selectedBrowserEngineViewForTesting()
                    ?.findSystemWebView(),
            )
            webView.evaluateJavascript("globalThis.__candyEdgeToEdge.$expression") { value ->
                result.set(value)
                completed.countDown()
            }
        }
        assertTrue("JavaScript result timed out for $expression", completed.await(5, TimeUnit.SECONDS))
        return requireNotNull(result.get()).toDouble()
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
        const val CSS_TOLERANCE = 1.0
        const val TIMEOUT_MILLIS = 15_000L
        const val POLL_MILLIS = 50L
        val HTML =
            """
            <!doctype html>
            <html><head><title>$READY_TITLE</title>
            <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
            <style>
              html,body { margin:0; }
              #fixed { position:fixed; inset:0 0 auto; height:24px; background:red; }
              #lead { height:200px; }
              #sticky { position:sticky; top:0; height:24px; background:blue; }
              main { height:4000px; }
            </style></head><body>
              <header id="fixed"></header><div id="lead"></div><nav id="sticky"></nav><main></main>
              <script>
                globalThis.__candyEdgeToEdge = {
                  get devicePixelRatio() { return globalThis.devicePixelRatio; },
                  get fixedTop() { return document.querySelector('#fixed').getBoundingClientRect().top; },
                  get stickyTop() { return document.querySelector('#sticky').getBoundingClientRect().top; },
                  get rootSpacerHeight() {
                    return Number.parseFloat(
                      getComputedStyle(document.documentElement, '::before').height
                    ) || 0;
                  },
                  get scrollY() { return globalThis.scrollY; },
                  get scrollPage() { globalThis.scrollTo(0, 600); return 0; }
                };
                addEventListener('scroll', () => {
                  document.querySelector('#fixed').style.display = 'none';
                }, { once: true });
              </script>
            </body></html>
            """.trimIndent()
    }
}
