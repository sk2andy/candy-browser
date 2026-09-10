package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserPageHardwareInputInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences by lazy {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    private lateinit var server: HardwareInputFixtureServer

    @Before
    fun setUp() {
        server = HardwareInputFixtureServer()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
        server.close()
    }

    @Test
    fun geckoWebsiteAcceptsHardwareKeyboardInput() {
        assertWebsiteHardwareKeyboardInput(AndroidBrowserEngineKind.GeckoView)
    }

    @Test
    fun systemWebViewWebsiteAcceptsHardwareKeyboardInput() {
        assertWebsiteHardwareKeyboardInput(AndroidBrowserEngineKind.SystemWebView)
    }

    @Test
    fun geckoWebsiteReceivesFirstHardwareLetterAfterNonBrowserFocus() {
        assertFirstHardwareLetterAfterNonBrowserFocus(AndroidBrowserEngineKind.GeckoView)
    }

    @Test
    fun systemWebViewWebsiteReceivesFirstHardwareLetterAfterNonBrowserFocus() {
        assertFirstHardwareLetterAfterNonBrowserFocus(AndroidBrowserEngineKind.SystemWebView)
    }

    @Test
    fun geckoWebsiteAcceptsUnclassifiedWheelWithoutPriorClick() {
        assertWebsiteUnclassifiedWheelInput(AndroidBrowserEngineKind.GeckoView)
    }

    @Test
    fun systemWebViewWebsiteAcceptsUnclassifiedWheelWithoutPriorClick() {
        assertWebsiteUnclassifiedWheelInput(AndroidBrowserEngineKind.SystemWebView)
    }

    private fun assertWebsiteHardwareKeyboardInput(engineKind: AndroidBrowserEngineKind) {
        configureSession(engineKind, server.keyboardUrl)
        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
        ).use { scenario ->
            awaitEngineView(scenario)
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().requestSelectedBrowserEngineFocus()
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(PAGE_LAYOUT_SETTLE_MILLIS)
            injectHardwareKey(KeyEvent.KEYCODE_A)
            awaitCondition("$engineKind page did not receive hardware keyboard input") {
                selectedTitle(scenario) == TYPED_TITLE
            }
        }
    }

    private fun assertFirstHardwareLetterAfterNonBrowserFocus(
        engineKind: AndroidBrowserEngineKind,
    ) {
        configureSession(engineKind, server.keyRouteUrl)
        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
        ).use { scenario ->
            awaitEngineView(scenario)
            SystemClock.sleep(PAGE_LAYOUT_SETTLE_MILLIS)
            focusNonBrowserView(scenario)
            injectHeldHardwareKey(KeyEvent.KEYCODE_A)
            awaitCondition("$engineKind page did not receive the first hardware letter") {
                selectedTitle(scenario) == KEY_ROUTE_TITLE
            }
        }
    }

    private fun assertWebsiteUnclassifiedWheelInput(engineKind: AndroidBrowserEngineKind) {
        configureSession(engineKind, server.wheelUrl)
        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
        ).use { scenario ->
            awaitEngineView(scenario)
            SystemClock.sleep(PAGE_LAYOUT_SETTLE_MILLIS)
            focusNonBrowserView(scenario)
            repeat(4) {
                val event = unclassifiedWheelEvent(engineKind)
                scenario.onActivity { activity ->
                    try {
                        activity.dispatchGenericMotionEvent(event)
                    } finally {
                        event.recycle()
                    }
                }
            }
            awaitCondition("$engineKind page did not receive mouse-wheel scrolling") {
                selectedTitle(scenario)
                    .removePrefix(SCROLLED_TITLE_PREFIX)
                    .toIntOrNull()
                    ?.let { offset -> offset > 0 } == true
            }
        }
    }

    private fun configureSession(engineKind: AndroidBrowserEngineKind, url: String) {
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        BrowserSessionStore(context).apply {
            saveStartupAnimationEnabled(false)
            saveAndroidBrowserEngineKind(engineKind)
            val tab = BrowserTab(
                id = "hardware-input-fixture",
                lastAccessedAt = System.currentTimeMillis(),
                url = url,
            )
            assertTrue(saveTabsImmediately(listOf(tab), tab.id))
        }
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
    }

    private fun awaitEngineView(scenario: ActivityScenario<MainActivity>): View {
        var engineView: View? = null
        awaitCondition("Browser engine view did not become ready") {
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                engineView = controller.selectedBrowserEngineViewForTesting()
                    ?.takeIf { view ->
                        view.isAttachedToWindow &&
                            view.width > 0 &&
                            view.height > 0 &&
                            controller.selectedTab.title == READY_TITLE
                    }
            }
            engineView != null
        }
        return requireNotNull(engineView)
    }

    private fun injectHardwareKey(keyCode: Int) {
        val downTime = SystemClock.uptimeMillis()
        listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).forEach { action ->
            val event = KeyEvent(
                downTime,
                SystemClock.uptimeMillis(),
                action,
                keyCode,
                0,
                0,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                0,
                InputDevice.SOURCE_KEYBOARD,
            )
            assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
        }
        instrumentation.waitForIdleSync()
    }

    private fun injectHeldHardwareKey(keyCode: Int) {
        val downTime = SystemClock.uptimeMillis()
        listOf(
            KeyEvent.ACTION_DOWN to 0,
            KeyEvent.ACTION_DOWN to 1,
            KeyEvent.ACTION_UP to 0,
        ).forEach { (action, repeatCount) ->
            val event = KeyEvent(
                downTime,
                SystemClock.uptimeMillis(),
                action,
                keyCode,
                repeatCount,
                0,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                0,
                InputDevice.SOURCE_KEYBOARD,
            )
            assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
        }
        instrumentation.waitForIdleSync()
    }

    private fun focusNonBrowserView(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { activity ->
            val focusSink = View(activity).apply {
                isFocusable = true
                isFocusableInTouchMode = true
            }
            (activity.window.decorView as ViewGroup).addView(
                focusSink,
                ViewGroup.LayoutParams(1, 1),
            )
            assertTrue(focusSink.requestFocus())
            assertSame(focusSink, activity.currentFocus)
        }
        instrumentation.waitForIdleSync()
    }

    private fun unclassifiedWheelEvent(engineKind: AndroidBrowserEngineKind): MotionEvent {
        val eventTime = SystemClock.uptimeMillis()
        return MotionEvent.obtain(
            eventTime,
            eventTime,
            MotionEvent.ACTION_SCROLL,
            1,
            arrayOf(
                MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType = MotionEvent.TOOL_TYPE_UNKNOWN
                },
            ),
            arrayOf(
                MotionEvent.PointerCoords().apply {
                    val axis = when (engineKind) {
                        AndroidBrowserEngineKind.GeckoView -> MotionEvent.AXIS_VSCROLL
                        AndroidBrowserEngineKind.SystemWebView -> MotionEvent.AXIS_SCROLL
                    }
                    setAxisValue(axis, -5f)
                },
            ),
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_ROTARY_ENCODER,
            0,
        )
    }

    private fun selectedTitle(scenario: ActivityScenario<MainActivity>): String {
        var title = ""
        scenario.onActivity { activity ->
            title = activity.browserControllerForTesting().selectedTab.title
        }
        return title
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue(message, condition())
    }

    private class HardwareInputFixtureServer : AutoCloseable {
        private val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        private val thread = Thread(::serve, "hardware-input-fixture").apply {
            isDaemon = true
            start()
        }
        val keyboardUrl = "http://127.0.0.1:${server.localPort}/hardware-input"
        val keyRouteUrl = "http://127.0.0.1:${server.localPort}/hardware-key-route"
        val wheelUrl = "http://127.0.0.1:${server.localPort}/hardware-wheel"

        private fun serve() {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: return
                socket.use { connection ->
                    runCatching {
                        var requestLine = ""
                        connection.getInputStream().bufferedReader().apply {
                            requestLine = readLine().orEmpty()
                            while (!readLine().isNullOrEmpty()) {
                                // Drain request headers before serving deterministic fixture.
                            }
                        }
                        val html = when {
                            "/hardware-key-route" in requestLine -> KEY_ROUTE_HTML
                            "/hardware-wheel" in requestLine -> WHEEL_HTML
                            else -> KEYBOARD_HTML
                        }
                        val body = html.toByteArray(StandardCharsets.UTF_8)
                        connection.getOutputStream().buffered().use { output ->
                            output.write(
                                "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n"
                                    .toByteArray(StandardCharsets.US_ASCII),
                            )
                            output.write(
                                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                                    .toByteArray(StandardCharsets.US_ASCII),
                            )
                            output.write(body)
                        }
                    }
                }
            }
        }

        override fun close() {
            server.close()
            thread.join(1_000)
        }
    }

    private companion object {
        const val TEST_ACTIVITY_ACTION =
            "dev.sk2andy.materialbrowser.test.PAGE_HARDWARE_INPUT"
        const val READY_TITLE = "hardware-ready"
        const val TYPED_TITLE = "typed:a"
        const val KEY_ROUTE_TITLE = "key:1:1"
        const val SCROLLED_TITLE_PREFIX = "scrolled:"
        const val TIMEOUT_MILLIS = 15_000L
        const val POLL_MILLIS = 100L
        const val PAGE_LAYOUT_SETTLE_MILLIS = 500L
        const val KEYBOARD_HTML = """
            <!doctype html>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              body { margin: 0; min-height: 6000px; }
              input { position: fixed; inset: 0 0 auto; width: 100vw; height: 45vh; }
            </style>
            <input id="target">
            <script>
              document.title = 'hardware-ready';
              const target = document.querySelector('#target');
              target.addEventListener('input', () => document.title = 'typed:' + target.value);
              target.focus();
            </script>
        """
        const val KEY_ROUTE_HTML = """
            <!doctype html>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <script>
              document.title = 'hardware-ready';
              let downs = 0;
              let ups = 0;
              const update = () => document.title = 'key:' + downs + ':' + ups;
              addEventListener('keydown', event => {
                if (event.key.toLowerCase() === 'a') downs += 1;
                update();
              });
              addEventListener('keyup', event => {
                if (event.key.toLowerCase() === 'a') ups += 1;
                update();
              });
            </script>
        """
        const val WHEEL_HTML = """
            <!doctype html>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>body { margin: 0; } #spacer { height: 6000px; }</style>
            <body><div id="spacer"></div></body>
            <script>
              document.title = 'hardware-ready';
              addEventListener('scroll', () => document.title = 'scrolled:' + scrollY);
            </script>
        """
    }
}
