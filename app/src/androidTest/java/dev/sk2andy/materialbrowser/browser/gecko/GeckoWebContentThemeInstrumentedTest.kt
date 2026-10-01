package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.engine.BrowserWebContentColorScheme
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoView

@RunWith(AndroidJUnit4::class)
class GeckoWebContentThemeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun unpaintedGeckoSurfaceUsesDarkBackground() {
        assertUnpaintedSurfaceBackground(
            nightMode = Configuration.UI_MODE_NIGHT_YES,
            hostColor = Color.rgb(18, 18, 18),
        )
    }

    @Test
    fun unpaintedGeckoSurfaceUsesLightBackground() {
        assertUnpaintedSurfaceBackground(
            nightMode = Configuration.UI_MODE_NIGHT_NO,
            hostColor = Color.rgb(250, 246, 242),
        )
    }

    @Test
    fun darkLoadingSurfaceReleasesForFirstPaintAndReload() {
        assertLoadingSurfaceAndFirstPaint(BrowserWebContentColorScheme.Dark, DARK_TITLE)
    }

    @Test
    fun lightLoadingSurfaceReleasesForFirstPaintAndReload() {
        assertLoadingSurfaceAndFirstPaint(BrowserWebContentColorScheme.Light, LIGHT_TITLE)
    }

    private fun assertLoadingSurfaceAndFirstPaint(
        colorScheme: BrowserWebContentColorScheme,
        expectedTitle: String,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val requestReceived = CountDownLatch(1)
        val allowResponse = CountDownLatch(1)
        val title = AtomicReference<String>()
        val contentPresented = AtomicBoolean(false)
        lateinit var runtime: GeckoRuntimeHandle
        lateinit var session: GeckoBrowserSession
        lateinit var view: View
        ThemeFixtureServer {
            requestReceived.countDown()
            check(allowResponse.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        }.use { server ->
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    runtime = GeckoRuntimeOwner.getOrCreate(context)
                    runtime.setWebContentColorScheme(colorScheme)
                    session = runtime.createSession(
                        profileId = "loading-theme-${UUID.randomUUID()}",
                        isPrivate = false,
                    )
                    view = session.createView(activity)
                    val host = FrameLayout(activity).apply {
                        setBackgroundColor(
                            if (colorScheme == BrowserWebContentColorScheme.Dark) Color.BLACK else Color.WHITE,
                        )
                        addView(view, matchParentLayoutParams())
                    }
                    activity.setContentView(host)
                    session.setStateListener { state -> title.set(state.title) }
                    session.awaitContentPresented { contentPresented.set(true) }
                    session.setActive(true)
                    assertTrue(session.loadUrl(server.url))
                }
                try {
                    assertTrue(
                        "Delayed fixture was not requested",
                        requestReceived.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
                    )
                    val loadingPixel = screenshotCenterPixel()
                    if (colorScheme == BrowserWebContentColorScheme.Dark) {
                        assertTrue(
                            "Dark loading surface flashed ${Integer.toHexString(loadingPixel)}",
                            Color.red(loadingPixel) < 90,
                        )
                    } else {
                        assertTrue(
                            "Light loading surface turned dark: ${Integer.toHexString(loadingPixel)}",
                            Color.red(loadingPixel) > 220,
                        )
                    }
                    allowResponse.countDown()
                    assertTrue(awaitTitle(title, expectedTitle))
                    awaitCondition("Gecko did not present its first page") { contentPresented.get() }
                    awaitCondition("First paint did not replace loading surface") {
                        screenshotCenterPixel() == PAGE_COLOR
                    }
                    scenario.onActivity {
                        val engineView = (view as ViewGroup).getChildAt(0) as GeckoView
                        engineView.setSession(requireNotNull(engineView.session))
                        assertEquals(
                            "Binding the same session must not cover an already painted page",
                            Color.TRANSPARENT,
                            (requireNotNull(view.findSurfaceView()).background as ColorDrawable).color,
                        )
                        title.set(null)
                        session.reload()
                    }
                    assertTrue(awaitTitle(title, expectedTitle))
                    awaitCondition("Reload did not retain visible page content") {
                        screenshotCenterPixel() == PAGE_COLOR
                    }
                    scenario.onActivity {
                        title.set(null)
                        assertTrue(session.loadUrl("${server.url}?second"))
                    }
                    assertTrue(awaitTitle(title, expectedTitle))
                    awaitCondition("Second navigation did not reveal page content") {
                        screenshotCenterPixel() == PAGE_COLOR
                    }
                    scenario.onActivity {
                        session.releaseView(view)
                        (view.parent as ViewGroup).removeView(view)
                        view = session.createView(it)
                        val host = it.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
                        host.addView(view, matchParentLayoutParams())
                    }
                    awaitCondition("Reattached view did not reveal loaded page") {
                        screenshotCenterPixel() == PAGE_COLOR
                    }
                } finally {
                    allowResponse.countDown()
                    scenario.onActivity {
                        runtime.setWebContentColorScheme(BrowserWebContentColorScheme.System)
                        session.releaseView(view)
                        session.setActive(false)
                        session.close()
                    }
                }
            }
        }
    }

    private fun matchParentLayoutParams(): FrameLayout.LayoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT,
    )

    private fun screenshotCenterPixel(): Int {
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        return try {
            bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        } finally {
            bitmap.recycle()
        }
    }

    private fun View.findSurfaceView(): SurfaceView? {
        if (this is SurfaceView) return this
        if (this !is ViewGroup) return null
        for (index in 0 until childCount) getChildAt(index).findSurfaceView()?.let { return it }
        return null
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue(message, condition())
    }

    private fun assertUnpaintedSurfaceBackground(nightMode: Int, hostColor: Int) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(context.resources.configuration).withNightMode(nightMode)
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        try {
            instrumentation.runOnMainSync {
                val themedContext = context.createConfigurationContext(configuration)
                val host = FrameLayout(themedContext).apply {
                    setBackgroundColor(hostColor)
                    addView(
                        CandyGeckoView(themedContext),
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        ),
                    )
                }
                val size = View.MeasureSpec.makeMeasureSpec(64, View.MeasureSpec.EXACTLY)
                host.measure(size, size)
                host.layout(0, 0, 64, 64)
                host.draw(Canvas(bitmap))
            }
            val pixel = bitmap.getPixel(32, 32)
            if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
                assertTrue(
                    "Unpainted dark surface flashed ${Integer.toHexString(pixel)}",
                    Color.red(pixel) < 90,
                )
            } else {
                assertEquals("Unpainted light surface must remain white", Color.WHITE, pixel)
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun preferredColorSchemeChangesReachExistingWebsitesAfterReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = ThemeFixtureServer()
        val title = AtomicReference<String>()
        lateinit var runtime: GeckoRuntimeHandle
        lateinit var session: GeckoBrowserSession
        lateinit var view: View
        instrumentation.runOnMainSync {
            runtime = GeckoRuntimeOwner.getOrCreate(context)
            runtime.setWebContentColorScheme(BrowserWebContentColorScheme.Light)
            session = runtime.createSession(
                profileId = "theme-${UUID.randomUUID()}",
                isPrivate = false,
            )
            view = session.createView(context)
            session.setStateListener { state -> title.set(state.title) }
            session.setActive(true)
            assertTrue(session.loadUrl(server.url))
        }

        try {
            assertTrue(awaitTitle(title, LIGHT_TITLE))

            instrumentation.runOnMainSync {
                runtime.setWebContentColorScheme(BrowserWebContentColorScheme.Dark)
                session.reload()
            }

            assertTrue(awaitTitle(title, DARK_TITLE))
            assertEquals(DARK_TITLE, title.get())
        } finally {
            instrumentation.runOnMainSync {
                runtime.setWebContentColorScheme(BrowserWebContentColorScheme.System)
                session.releaseView(view)
                session.setActive(false)
                session.close()
            }
            server.close()
        }
    }

    @Test
    fun systemPreferredColorSchemeFollowsRuntimeConfigurationChanges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalConfiguration = Configuration(context.resources.configuration)
        val server = ThemeFixtureServer()
        val title = AtomicReference<String>()
        lateinit var runtime: GeckoRuntimeHandle
        lateinit var session: GeckoBrowserSession
        lateinit var view: View
        instrumentation.runOnMainSync {
            runtime = GeckoRuntimeOwner.getOrCreate(context)
            runtime.setWebContentColorScheme(BrowserWebContentColorScheme.System)
            runtime.onConfigurationChanged(
                originalConfiguration.withNightMode(Configuration.UI_MODE_NIGHT_NO),
            )
            session = runtime.createSession(
                profileId = "theme-system-${UUID.randomUUID()}",
                isPrivate = false,
            )
            view = session.createView(context)
            session.setStateListener { state -> title.set(state.title) }
            session.setActive(true)
            assertTrue(session.loadUrl(server.url))
        }

        try {
            assertTrue(awaitTitle(title, LIGHT_TITLE))

            instrumentation.runOnMainSync {
                runtime.onConfigurationChanged(
                    originalConfiguration.withNightMode(Configuration.UI_MODE_NIGHT_YES),
                )
                session.reload()
            }

            assertTrue(awaitTitle(title, DARK_TITLE))
            assertEquals(DARK_TITLE, title.get())
        } finally {
            instrumentation.runOnMainSync {
                runtime.onConfigurationChanged(originalConfiguration)
                runtime.setWebContentColorScheme(BrowserWebContentColorScheme.System)
                session.releaseView(view)
                session.setActive(false)
                session.close()
            }
            server.close()
        }
    }

    private fun awaitTitle(title: AtomicReference<String>, expected: String): Boolean {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (title.get() == expected) return true
            SystemClock.sleep(POLL_MILLIS)
        }
        return title.get() == expected
    }

    private fun Configuration.withNightMode(nightMode: Int): Configuration =
        Configuration(this).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }

    private class ThemeFixtureServer(private val beforeResponse: () -> Unit = {}) : AutoCloseable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val thread = Thread({ serve() }, "gecko-theme-fixture").apply {
            isDaemon = true
            start()
        }

        val url = "http://127.0.0.1:${socket.localPort}/"

        private fun serve() {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { connection ->
                        val reader = connection.getInputStream().bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) {
                            // Consume request headers.
                        }
                        beforeResponse()
                        val body = """
                            <!doctype html><html><head><style>html,body { margin: 0; min-height: 100vh; background: #336699; }</style><script>
                            const scheme = matchMedia('(prefers-color-scheme: dark)');
                            const publishScheme = () => {
                                document.title = scheme.matches ? '$DARK_TITLE' : '$LIGHT_TITLE';
                            };
                            scheme.addEventListener('change', publishScheme);
                            publishScheme();
                            </script></head><body>theme probe</body></html>
                        """.trimIndent().toByteArray()
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

        override fun close() {
            socket.close()
            thread.join(1_000L)
        }
    }

    private companion object {
        val PAGE_COLOR = Color.rgb(51, 102, 153)
        const val LIGHT_TITLE = "theme-light"
        const val DARK_TITLE = "theme-dark"
        const val TIMEOUT_MILLIS = 20_000L
        const val POLL_MILLIS = 50L
    }
}
