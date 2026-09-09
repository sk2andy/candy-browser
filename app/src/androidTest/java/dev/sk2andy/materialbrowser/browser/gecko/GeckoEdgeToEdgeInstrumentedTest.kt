package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoEdgeToEdgeInstrumentedTest {
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
        val tab = BrowserTab(
            id = "gecko-edge-to-edge-fixture",
            lastAccessedAt = System.currentTimeMillis(),
            url = TEST_URL,
        )
        assertTrue(store.saveTabsImmediately(listOf(tab), tab.id))
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun selectedGeckoViewProtectsFixedHeadersAndUsesDirectSurfaceBackend() {
        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
        ).use { scenario ->
            awaitViewReady(scenario)
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                val view = requireNotNull(controller.selectedGeckoViewForTesting())
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

                assertMargins(view, left = 0, top = 0, right = 0, bottom = 0)
                assertWindowTop(view, expectedTop = 0)
                assertWindowBottom(view, expectedBottom = activity.window.decorView.height)
                assertScreenTop(view, expectedTop = 0)
                assertMargins(
                    view.engineView(),
                    left = 0,
                    top = 0,
                    right = 0,
                    bottom = 0,
                )
                assertWindowTop(view.engineView(), expectedTop = 0)
                assertWindowBottom(
                    view.engineView(),
                    expectedBottom = activity.window.decorView.height,
                )
                assertScreenTop(view.engineView(), expectedTop = 0)
                val surfaceView = requireNotNull(view.findSurfaceView())
                assertWindowTop(surfaceView, expectedTop = 0)
                assertWindowBottom(surfaceView, expectedBottom = activity.window.decorView.height)
                assertScreenTop(surfaceView, expectedTop = 0)
                assertTrue(
                    "GeckoView must use SurfaceView to avoid copying every page frame",
                    view.hasSurfaceView(),
                )
            }
        }
    }

    @Test
    fun forcedSafeAreaUsesInnerNativeMarginsWithoutShrinkingOuterHost() {
        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
        ).use { scenario ->
            awaitViewReady(scenario)
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                val view = requireNotNull(controller.selectedGeckoViewForTesting())
                assertTrue(controller.setForceSafeArea(controller.selectedTabId, true))
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

                assertMargins(view, left = 0, top = 0, right = 0, bottom = 0)
                assertWindowTop(view, expectedTop = 0)
                assertMargins(
                    view.engineView(),
                    left = 0,
                    top = STATUS_BAR_INSET_PX,
                    right = 0,
                    bottom = NAVIGATION_BAR_INSET_PX,
                )
            }
        }
    }

    @Test
    fun googleLikeHeaderStaysEdgeToEdgeAfterScrollToTop() {
        EdgeToEdgeFixtureServer().use { server ->
            val tab = BrowserTab(
                id = "gecko-google-like-safe-area-fixture",
                lastAccessedAt = System.currentTimeMillis(),
                url = server.pageUrl,
            )
            assertTrue(store.saveTabsImmediately(listOf(tab), tab.id))

            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
            ).use { scenario ->
                awaitViewReady(scenario)
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
                awaitSelectedTabTitle(scenario, GOOGLE_LIKE_SAFE_TITLE)
                val documentRequestsBeforeScroll = server.documentRequestCount.get()
                assertTrue(documentRequestsBeforeScroll > 0)
                scenario.onActivity { activity ->
                    assertTrue(
                        activity.browserControllerForTesting()
                            .scrollSelectedBrowserEngineToVerticalOffset(SCROLL_OFFSET_PX),
                    )
                }
                awaitSelectedTabTitle(scenario, GOOGLE_LIKE_SCROLLED_TITLE)
                scenario.onActivity { activity ->
                    assertTrue(
                        activity.browserControllerForTesting()
                            .scrollSelectedBrowserEngineToVerticalOffset(0),
                    )
                }
                awaitSelectedTabTitle(scenario, GOOGLE_LIKE_RETURNED_SAFE_TITLE)
                SystemClock.sleep(FALLBACK_REGRESSION_WINDOW_MILLIS)

                scenario.onActivity { activity ->
                    val view = requireNotNull(
                        activity.browserControllerForTesting().selectedGeckoViewForTesting(),
                    )
                    assertMargins(view, left = 0, top = 0, right = 0, bottom = 0)
                    assertMargins(
                        view.engineView(),
                        left = 0,
                        top = 0,
                        right = 0,
                        bottom = 0,
                    )
                    assertWindowTop(view, expectedTop = 0)
                    assertWindowTop(view.engineView(), expectedTop = 0)
                    assertWindowBottom(view, expectedBottom = activity.window.decorView.height)
                    assertWindowBottom(
                        view.engineView(),
                        expectedBottom = activity.window.decorView.height,
                    )
                    assertEquals(
                        GOOGLE_LIKE_RETURNED_SAFE_TITLE,
                        activity.browserControllerForTesting().selectedTabForTesting().title,
                    )
                }
                assertEquals(documentRequestsBeforeScroll, server.documentRequestCount.get())
            }
        }
    }

    private fun awaitViewReady(scenario: ActivityScenario<MainActivity>) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            var ready = false
            scenario.onActivity { activity ->
                ready = activity.browserControllerForTesting()
                    .selectedGeckoViewForTesting()
                    ?.let { view ->
                        view.isAttachedToWindow &&
                            view.width > 0 &&
                            view.height > 0 &&
                            view.hasSurfaceView()
                    } == true
            }
            if (ready) return
            SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue("Gecko SurfaceView did not become ready", false)
    }

    private fun awaitSelectedTabTitle(
        scenario: ActivityScenario<MainActivity>,
        expectedTitle: String,
    ) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            var title = ""
            scenario.onActivity { activity ->
                title = activity.browserControllerForTesting().selectedTabForTesting().title
            }
            if (title == expectedTitle) return
            SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue("Selected Gecko tab did not reach title $expectedTitle", false)
    }

    private class EdgeToEdgeFixtureServer : AutoCloseable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val documentRequestCount = AtomicInteger()
        val pageUrl = "http://127.0.0.1:${socket.localPort}/google-like-safe-area"
        private val thread = Thread({ serve() }, "gecko-edge-to-edge-fixture").apply {
            isDaemon = true
            start()
        }

        private fun serve() {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { connection ->
                        val reader = connection.getInputStream().bufferedReader()
                        val requestLine = reader.readLine().orEmpty()
                        if (requestLine.startsWith("GET /google-like-safe-area ")) {
                            documentRequestCount.incrementAndGet()
                        }
                        while (true) {
                            val header = reader.readLine() ?: break
                            if (header.isEmpty()) break
                        }
                        val body = page().toByteArray()
                        connection.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\n".toByteArray())
                            write("Content-Type: text/html; charset=utf-8\r\n".toByteArray())
                            write("Content-Length: ${body.size}\r\n".toByteArray())
                            write("Connection: close\r\n\r\n".toByteArray())
                            write(body)
                            flush()
                        }
                    }
                } catch (error: SocketException) {
                    if (socket.isClosed) return
                }
            }
        }

        private fun page(): String = """
            <!doctype html>
            <html><head><title>Preparing edge-to-edge fixture</title>
            <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
            <style>
              html, body { margin: 0; min-height: 300vh; }
              #drawer-shell, #drawer-backdrop {
                position: fixed; inset: 0; z-index: 9998;
              }
              #drawer-shell { background: transparent; }
              #drawer-backdrop { background: rgba(0, 0, 0, .6); z-index: 9997; }
              #navd { position: absolute; top: 0; left: 0; width: 60px; height: 64px; }
              #header {
                position: fixed; top: 40px; left: 0; width: 100%; height: 80px;
                z-index: 1; background: white;
              }
              #sign-in { position: absolute; top: 8px; right: 8px; }
              main { padding-top: 160px; height: 3000px; }
            </style></head><body>
            <div id="drawer-backdrop"></div>
            <div id="drawer-shell"><div role="button" style="height:120px">Drawer</div></div>
            <div id="navd"><div><div id="menu" role="button">Menu</div></div></div>
            <header id="header"><button id="sign-in">Sign in</button></header>
            <main>Scrollable content</main>
            <script>
              let sawScroll = false;
              let initialSafe = false;
              const inspect = () => {
                const expectedInset = $STATUS_BAR_INSET_PX / devicePixelRatio;
                const menuTop = document.querySelector('#menu').getBoundingClientRect().top;
                const rootInset = Number.parseFloat(
                  getComputedStyle(document.documentElement, '::before').height
                );
                const safe = menuTop >= expectedInset + 8 - 0.5 &&
                  Math.abs(rootInset - expectedInset) <= 0.5;
                if (safe && !initialSafe) {
                  initialSafe = true;
                  document.title = '$GOOGLE_LIKE_SAFE_TITLE';
                }
                if (scrollY > 100) {
                  sawScroll = true;
                  document.title = '$GOOGLE_LIKE_SCROLLED_TITLE';
                }
                if (safe && sawScroll && scrollY <= 0) {
                  document.title = '$GOOGLE_LIKE_RETURNED_SAFE_TITLE';
                }
              };
              addEventListener('scroll', inspect, { passive: true });
              setInterval(inspect, 25);
            </script></body></html>
        """.trimIndent()

        override fun close() {
            socket.close()
            thread.join(2_000)
        }
    }

    private fun View.hasSurfaceView(): Boolean =
        findSurfaceView() != null

    private fun View.findSurfaceView(): SurfaceView? = when (this) {
        is SurfaceView -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { index ->
            getChildAt(index).findSurfaceView()
        }
        else -> null
    }

    private fun View.engineView(): View = (this as ViewGroup).getChildAt(0)

    private fun assertMargins(
        view: View,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        val margins = view.layoutParams as ViewGroup.MarginLayoutParams
        assertEquals(left, margins.leftMargin)
        assertEquals(top, margins.topMargin)
        assertEquals(right, margins.rightMargin)
        assertEquals(bottom, margins.bottomMargin)
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

    private fun assertScreenTop(view: View, expectedTop: Int) {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        assertEquals(expectedTop, location[1])
    }

    private companion object {
        const val TEST_ACTIVITY_ACTION = "dev.sk2andy.materialbrowser.test.GECKO_EDGE_TO_EDGE"
        const val TEST_URL = "https://example.invalid/candy-edge-to-edge"
        const val STATUS_BAR_INSET_PX = 96
        const val NAVIGATION_BAR_INSET_PX = 48
        const val SCROLL_OFFSET_PX = 600
        const val FALLBACK_REGRESSION_WINDOW_MILLIS = 1_600L
        const val GOOGLE_LIKE_SAFE_TITLE = "Google-like edge-to-edge safe"
        const val GOOGLE_LIKE_SCROLLED_TITLE = "Google-like edge-to-edge scrolled"
        const val GOOGLE_LIKE_RETURNED_SAFE_TITLE = "Google-like edge-to-edge returned safe"
        const val TIMEOUT_MILLIS = 15_000L
        const val POLL_MILLIS = 50L
    }
}
