package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.BrowserChromeScrollRules
import dev.sk2andy.materialbrowser.browser.BrowserChromeScrollState
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserEngineScrollEvent
import dev.sk2andy.materialbrowser.browser.BrowserEngineScrollListener
import dev.sk2andy.materialbrowser.browser.actions.BrowserContentTargetListener
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.sync.AndroidSyncCacheStore
import dev.sk2andy.materialbrowser.data.sync.AndroidSyncVaultStore
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommand
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommandType
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.PanZoomController
import org.mozilla.geckoview.ScreenLength

@RunWith(AndroidJUnit4::class)
class GeckoBottomBarScrollInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences by lazy {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        clearState()
        GestureOnboardingStore(context).markCompleted()
        BrowserSessionStore(context).saveStartupAnimationEnabled(false)
    }

    @After
    fun tearDown() = clearState()

    @Test
    fun realGeckoScrollDelegateAndControllerKeepPillStateInSyncPerTab() {
        LongPageFixtureServer().use { server ->
            assertRealGeckoScrollDelegate(server)
        }
        assertControllerTabIsolation()
    }

    private fun assertRealGeckoScrollDelegate(server: LongPageFixtureServer) {
        val pageStopped = CountDownLatch(1)
        val firstComposite = CountDownLatch(1)
        val localPageStarted = AtomicBoolean(false)
        val scrollY = AtomicInteger(Int.MIN_VALUE)
        val compact = AtomicBoolean(false)
        val scrollState = AtomicReference(BrowserChromeScrollState())
        val engineScrollListener = BrowserEngineScrollListener { event ->
            val update = BrowserChromeScrollRules.update(
                state = scrollState.get(),
                event = event,
                collapseThresholdPx = 24f,
                expandThresholdPx = 16f,
            )
            scrollState.set(update.state)
            update.compact?.let(compact::set)
            scrollY.set(event.scrollYPx)
        }
        lateinit var runtime: GeckoRuntime
        lateinit var session: GeckoSession
        lateinit var view: GeckoView
        ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val runtimeHandle = GeckoRuntimeOwner.getOrCreate(activity)
                runtime = runtimeHandle
                    .javaClass
                    .getDeclaredField("runtime")
                    .apply { isAccessible = true }
                    .get(runtimeHandle) as GeckoRuntime
                session = GeckoSession()
                session.scrollDelegate = object : GeckoSession.ScrollDelegate {
                    override fun onScrollChanged(
                        session: GeckoSession,
                        scrollX: Int,
                        scrollY: Int,
                    ) = engineScrollListener.onScrollChanged(BrowserEngineScrollEvent(scrollY))
                }
                session.progressDelegate = object : GeckoSession.ProgressDelegate {
                    override fun onPageStart(session: GeckoSession, url: String) {
                        localPageStarted.set(url == server.url)
                    }

                    override fun onPageStop(session: GeckoSession, success: Boolean) {
                        if (success && localPageStarted.get()) pageStopped.countDown()
                    }
                }
                session.contentDelegate = object : GeckoSession.ContentDelegate {
                    override fun onFirstComposite(session: GeckoSession) {
                        if (localPageStarted.get()) firstComposite.countDown()
                    }
                }
                session.open(runtime)
                view = GeckoView(activity)
                view.setSession(session)
                activity.setContentView(view)
                session.loadUri(server.url)
            }
            try {
                assertTrue(
                    "Raw Gecko long page did not load",
                    pageStopped.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
                )
                assertTrue(
                    "Raw Gecko long page did not present content",
                    firstComposite.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
                )
                scenario.onActivity { view.scrollTo(yPx = 1.0) }
                awaitValue("Gecko did not report initial scroll") { scrollY.get() >= 1 }
                scenario.onActivity { view.scrollTo(yPx = 320.0) }
                awaitValue("Gecko downward scroll did not compact") { compact.get() }
                scenario.onActivity { view.scrollTo(yPx = 260.0) }
                awaitValue("Gecko upward scroll did not expand") { !compact.get() }
                scenario.onActivity { view.scrollTo(yPx = 360.0) }
                awaitValue("Gecko second downward scroll did not compact") { compact.get() }
                scenario.onActivity { view.scrollTo(yPx = 0.0) }
                awaitValue("Gecko document top did not expand") {
                    scrollY.get() == 0 && !compact.get()
                }
            } finally {
                scenario.onActivity {
                    view.releaseSession()
                    session.close()
                }
            }
        }
    }

    private fun assertControllerTabIsolation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var firstTabId: String
            lateinit var firstSession: FakeEngineSession
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertTrue(controller.usesGeckoEngine)
                firstTabId = controller.selectedTabId
                firstSession = FakeEngineSession(firstTabId)
                controller.installGeckoEngineSessionForTesting(firstSession)
                val density = activity.resources.displayMetrics.density
                firstSession.emitScroll(0)
                firstSession.emitScroll((30f * density).toInt())
            }
            awaitController(scenario) { controller -> controller.isBottomBarCompact }

            lateinit var secondTabId: String
            lateinit var secondSession: FakeEngineSession
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                secondTabId = controller.createTab()
                secondSession = FakeEngineSession(secondTabId)
                controller.installGeckoEngineSessionForTesting(secondSession)
                assertFalse(controller.isBottomBarCompact)
                firstSession.emitScroll(0)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertFalse(controller.isBottomBarCompact)
                controller.selectTab(firstTabId)
                assertTrue(controller.isBottomBarCompact)
            }

            lateinit var replacement: FakeEngineSession
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                replacement = FakeEngineSession(firstTabId)
                controller.installGeckoEngineSessionForTesting(replacement)
                firstSession.emitRetiredScroll(0)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertTrue(controller.isBottomBarCompact)
                replacement.emitScroll(0)
            }
            awaitController(scenario) { controller -> !controller.isBottomBarCompact }
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                controller.closeTab(firstTabId)
                assertTrue(controller.selectedTabId == secondTabId)
                assertFalse(controller.isBottomBarCompact)
                replacement.emitRetiredScroll(500)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertFalse(activity.browserControllerForTesting().isBottomBarCompact)
            }
        }
    }

    private fun GeckoView.scrollTo(yPx: Double) {
        panZoomController.scrollTo(
            ScreenLength.zero(),
            ScreenLength.fromPixels(yPx),
            PanZoomController.SCROLL_BEHAVIOR_AUTO,
        )
    }

    private fun awaitController(
        scenario: ActivityScenario<MainActivity>,
        condition: (BrowserController) -> Boolean,
    ) = awaitValue("Controller pill state did not settle") {
        instrumentation.waitForIdleSync()
        var result = false
        scenario.onActivity { activity ->
            result = condition(activity.browserControllerForTesting())
        }
        result
    }

    private fun awaitValue(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue(message, condition())
    }

    private fun clearState() {
        preferences.edit().clear().commit()
        context.getSharedPreferences("candy_sync_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        AndroidSyncVaultStore(context).clear()
        AndroidSyncCacheStore(context).clear()
    }

    private class FakeEngineSession(
        override val tabId: String,
    ) : AndroidBrowserEngineSessionPort {
        private var scrollListener: BrowserEngineScrollListener? = null
        private var retiredScrollListener: BrowserEngineScrollListener? = null

        override fun execute(command: BrowserEngineCommand) {
            if (command.type == BrowserEngineCommandType.Close) {
                retiredScrollListener = scrollListener
                scrollListener = null
            }
        }

        override fun setAudioMuted(muted: Boolean) = Unit

        override fun setScrollListener(listener: BrowserEngineScrollListener?) {
            scrollListener = listener
        }

        fun emitScroll(scrollYPx: Int) {
            scrollListener?.onScrollChanged(BrowserEngineScrollEvent(scrollYPx))
        }

        fun emitRetiredScroll(scrollYPx: Int) {
            retiredScrollListener?.onScrollChanged(BrowserEngineScrollEvent(scrollYPx))
        }

        override fun createView(context: Context): View = View(context)
        override fun awaitContentPresented(listener: () -> Unit) = listener()
        override fun releaseView(view: View) = Unit
        override fun setActive(active: Boolean) = Unit
        override fun setMediaStateListener(listener: GeckoMediaSessionStateListener?) = Unit
        override fun setContentTargetListener(listener: BrowserContentTargetListener?) = Unit
        override fun setNavigationRequestListener(listener: GeckoNavigationRequestListener?) = Unit
        override fun setVideoAutoplayBlocked(blocked: Boolean) = Unit
        override fun executeMediaCommand(command: GeckoMediaCommand) = Unit
        override fun seekMedia(positionMillis: Long) = Unit
        override fun goToHistoryIndex(index: Int) = Unit
        override fun historyUrlAtOffset(offset: Int): String? = null
        override fun clearFindInPage() = Unit
        override fun printPage(): Boolean = false
        override fun setDesktopMode(enabled: Boolean) = Unit
        override fun extractPageForReader(onComplete: (String?) -> Unit) = onComplete(null)

        override fun capturePreview(
            targetWidthPx: Int,
            visibleViewHeightPx: Int,
            maximumTargetHeightPx: Int,
            onComplete: (Bitmap?) -> Unit,
        ): BrowserEnginePreviewCapture? = null

        override fun findInPage(
            query: String,
            forward: Boolean,
            onComplete: (GeckoFindResult?) -> Unit,
        ) = onComplete(null)

        override fun updatePrivacyPolicy(
            policy: GeckoPrivacyPolicy,
            reloadOnCookiePermissionChange: Boolean,
            onReady: () -> Unit,
        ) = onReady()
    }

    private class LongPageFixtureServer : Closeable {
        private val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        private val thread = Thread(::serve, "gecko-bottom-bar-scroll-fixture").apply {
            isDaemon = true
            start()
        }
        val url = "http://127.0.0.1:${server.localPort}/scroll"

        private fun serve() {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: return
                socket.use { connection ->
                    runCatching {
                        connection.getInputStream().bufferedReader().apply {
                            readLine()
                            while (!readLine().isNullOrEmpty()) {
                                // Drain headers before serving the deterministic local page.
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
        val HTML =
            """
            <!doctype html>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Candy scroll fixture</title>
            <style>html,body{margin:0}main{height:6000px;background:linear-gradient(#f06,#09f)}</style>
            <main>Scrollable Candy Gecko fixture</main>
            """.trimIndent()
        const val TIMEOUT_MILLIS = 20_000L
        const val POLL_MILLIS = 50L
    }
}
