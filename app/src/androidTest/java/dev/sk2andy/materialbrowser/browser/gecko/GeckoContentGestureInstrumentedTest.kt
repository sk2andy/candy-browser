package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.actions.WebContentTarget
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.io.FileInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assume.assumeTrue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoView

@RunWith(AndroidJUnit4::class)
class GeckoContentGestureInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences by lazy {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        BrowserSessionStore(context).saveStartupAnimationEnabled(false)
        ReleaseNotesStore(context).markPresented(BuildConfig.VERSION_CODE.toLong())
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun windowFocusLossDispatchesCancelAndPreventsGeckoLongPress() {
        withLoadedSession { session, view, _ ->
            val actions = CopyOnWriteArrayList<Int>()
            val contentTargets = CopyOnWriteArrayList<WebContentTarget>()
            instrumentation.runOnMainSync {
                val engineView = view.findGeckoView()
                engineView.setOnTouchListener { _, event ->
                    actions += event.actionMasked
                    false
                }
                session.setContentTargetListener(contentTargets::add)
                val downTime = SystemClock.uptimeMillis()
                dispatchTouch(view, MotionEvent.ACTION_DOWN, downTime, downTime)
                view.dispatchWindowFocusChanged(false)
                dispatchTouch(
                    engineView,
                    MotionEvent.ACTION_CANCEL,
                    downTime,
                    SystemClock.uptimeMillis(),
                )
                dispatchTouch(
                    engineView,
                    MotionEvent.ACTION_MOVE,
                    downTime,
                    SystemClock.uptimeMillis(),
                )
                dispatchTouch(
                    engineView,
                    MotionEvent.ACTION_UP,
                    downTime,
                    SystemClock.uptimeMillis(),
                )
            }

            awaitCondition("Synthetic ACTION_CANCEL did not reach GeckoView") {
                actions.contains(MotionEvent.ACTION_CANCEL)
            }
            SystemClock.sleep(LONG_PRESS_SETTLE_MILLIS)

            assertEquals(
                listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL),
                actions,
            )
            assertTrue("Focus-loss gesture became Gecko context target", contentTargets.isEmpty())
        }
    }

    @Test
    fun ordinaryLongPressStillProducesGeckoContextTarget() {
        withLoadedSession { session, view, _ ->
            val target = CountDownLatch(1)
            session.setContentTargetListener { target.countDown() }
            val downTime = SystemClock.uptimeMillis()
            instrumentation.runOnMainSync {
                dispatchTouch(
                    view = view,
                    action = MotionEvent.ACTION_DOWN,
                    downTime = downTime,
                    eventTime = downTime,
                    y = view.height * 0.7f,
                )
            }

            assertTrue(
                "Normal Gecko long press did not produce context target",
                target.await(LONG_PRESS_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            instrumentation.runOnMainSync {
                dispatchTouch(
                    view = view,
                    action = MotionEvent.ACTION_UP,
                    downTime = downTime,
                    eventTime = SystemClock.uptimeMillis(),
                    y = view.height * 0.7f,
                )
            }
        }
    }

    @Test
    fun ordinaryDragStillScrollsWithoutContextTarget() {
        withLoadedSession { session, view, _ ->
            val contentTargets = CopyOnWriteArrayList<WebContentTarget>()
            val actions = CopyOnWriteArrayList<Int>()
            val scrollY = AtomicInteger()
            session.setContentTargetListener(contentTargets::add)
            session.setScrollListener { event -> scrollY.set(event.scrollYPx) }
            val location = IntArray(2)
            instrumentation.runOnMainSync {
                view.findGeckoView().setOnTouchListener { _, event ->
                    actions += event.actionMasked
                    false
                }
                view.getLocationOnScreen(location)
            }
            val centerX = location[0] + view.width / 2f
            val startY = location[1] + view.height * 0.75f
            val endY = location[1] + view.height * 0.25f
            val downTime = SystemClock.uptimeMillis()
            injectTouch(MotionEvent.ACTION_DOWN, downTime, downTime, centerX, startY)
            repeat(DRAG_STEPS) { index ->
                SystemClock.sleep(DRAG_STEP_MILLIS)
                val progress = (index + 1f) / DRAG_STEPS
                injectTouch(
                    action = MotionEvent.ACTION_MOVE,
                    downTime = downTime,
                    eventTime = SystemClock.uptimeMillis(),
                    x = centerX,
                    y = startY + (endY - startY) * progress,
                )
            }
            SystemClock.sleep(DRAG_STEP_MILLIS)
            injectTouch(
                action = MotionEvent.ACTION_UP,
                downTime = downTime,
                eventTime = SystemClock.uptimeMillis(),
                x = centerX,
                y = endY,
            )

            awaitCondition("Normal Gecko drag did not scroll document") {
                scrollY.get() > 0
            }
            assertEquals(MotionEvent.ACTION_DOWN, actions.firstOrNull())
            assertEquals(MotionEvent.ACTION_UP, actions.lastOrNull())
            assertTrue(actions.contains(MotionEvent.ACTION_MOVE))
            assertTrue("Normal drag became Gecko context target", contentTargets.isEmpty())
        }
    }

    @Test
    fun pausedAndStoppedControllerRejectsLateGeckoContextTarget() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                controller.onPause()
                controller.onStop()
                controller.dispatchSelectedGeckoContentTargetForTesting(
                    WebContentTarget(linkUrl = "https://example.test/late"),
                )
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertFalse(controller.contentActions.isVisible)
                controller.onStart()
                controller.onResume()
            }
        }
    }

    @Test
    fun homeSwipeEndsGeckoTouchBeforeActivityStops() {
        assumeTrue("Test requires Android gesture navigation", navigationMode() == "2")
        withLoadedSession { session, view, scenario ->
            val actions = CopyOnWriteArrayList<Int>()
            val contentTargets = CopyOnWriteArrayList<WebContentTarget>()
            val focusChanges = CopyOnWriteArrayList<Boolean>()
            instrumentation.runOnMainSync {
                view.findGeckoView().setOnTouchListener { _, event ->
                    actions += event.actionMasked
                    false
                }
                session.setContentTargetListener(contentTargets::add)
                view.viewTreeObserver.addOnWindowFocusChangeListener(focusChanges::add)
            }

            val (displayWidth, displayHeight) = physicalDisplaySize()
            val x = displayWidth / 2
            val startY = displayHeight - 1
            val endY = (displayHeight * 0.45f).toInt()
            var attempts = 0
            while (scenario.state != Lifecycle.State.CREATED && attempts < HOME_SWIPE_ATTEMPTS) {
                executeShellCommand(
                    "input touchscreen swipe $x $startY $x $endY " +
                        HOME_SWIPE_DURATION_MILLIS,
                )
                SystemClock.sleep(HOME_SWIPE_SETTLE_MILLIS)
                attempts++
            }

            awaitCondition("Home swipe did not stop Gecko Activity") {
                scenario.state == Lifecycle.State.CREATED
            }
            SystemClock.sleep(LONG_PRESS_SETTLE_MILLIS)
            assertEquals(MotionEvent.ACTION_DOWN, actions.firstOrNull())
            assertEquals(1, actions.count { action -> action == MotionEvent.ACTION_CANCEL })
            assertTrue("Window focus never left Gecko Activity", focusChanges.contains(false))
            assertTrue("Home swipe became Gecko context target", contentTargets.isEmpty())
        }
    }

    private fun withLoadedSession(
        action: (
            GeckoBrowserSession,
            View,
            ActivityScenario<GeckoScrollTestActivity>,
        ) -> Unit,
    ) {
        GestureFixtureServer().use { server ->
            lateinit var session: GeckoBrowserSession
            lateinit var view: View
            val loaded = CountDownLatch(1)
            ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                    session = GeckoRuntimeOwner.getOrCreate(activity).createSession(
                        profileId = "gecko-gesture-${UUID.randomUUID()}",
                        isolationEnabled = true,
                        isPrivate = false,
                        privacyPolicy = GeckoPrivacyPolicy.Disabled,
                    )
                    view = session.createView(activity)
                    session.setStateListener { state ->
                        if (state.url == server.url && state.lastNavigationSucceeded == true) {
                            loaded.countDown()
                        }
                    }
                    session.setActive(true)
                    activity.setContentView(view)
                    assertTrue(session.loadUrl(server.url))
                }
                try {
                    assertTrue(
                        "Gecko gesture fixture did not load",
                        loaded.await(PAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    )
                    awaitCondition("Gecko gesture view did not lay out") {
                        view.isAttachedToWindow && view.width > 0 && view.height > 0
                    }
                    action(session, view, scenario)
                } finally {
                    instrumentation.runOnMainSync {
                        session.setActive(false)
                        session.releaseView(view)
                        session.close()
                    }
                }
            }
        }
    }

    private fun dispatchTouch(
        view: View,
        action: Int,
        downTime: Long,
        eventTime: Long,
        x: Float = view.width / 2f,
        y: Float = view.height / 3f,
    ) {
        MotionEvent.obtain(downTime, eventTime, action, x, y, 0).also { event ->
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                assertTrue(
                    "GeckoView rejected ${MotionEvent.actionToString(action)}",
                    view.dispatchTouchEvent(event),
                )
            } finally {
                event.recycle()
            }
        }
    }

    private fun injectTouch(
        action: Int,
        downTime: Long,
        eventTime: Long,
        x: Float,
        y: Float,
    ) {
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

    private fun navigationMode(): String {
        return executeShellCommand("settings get secure navigation_mode").trim()
    }

    private fun physicalDisplaySize(): Pair<Int, Int> {
        val match = DISPLAY_SIZE_PATTERN.find(executeShellCommand("wm size"))
        checkNotNull(match) { "Physical display size is unavailable" }
        return match.groupValues[1].toInt() to match.groupValues[2].toInt()
    }

    private fun executeShellCommand(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return FileInputStream(descriptor.fileDescriptor).bufferedReader().use { reader ->
            reader.readText()
        }.also { descriptor.close() }
    }

    private fun View.findGeckoView(): GeckoView {
        if (this is GeckoView) return this
        if (this !is ViewGroup) error("GeckoView descendant is missing")
        for (index in 0 until childCount) {
            runCatching { getChildAt(index).findGeckoView() }.getOrNull()?.let { return it }
        }
        error("GeckoView descendant is missing")
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + CONDITION_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue(message, condition())
    }

    private class GestureFixtureServer : AutoCloseable {
        private val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        private val thread = Thread(::serve, "gecko-gesture-fixture").apply {
            isDaemon = true
            start()
        }
        val url = "http://127.0.0.1:${server.localPort}/gesture"

        private fun serve() {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: return
                socket.use { connection ->
                    runCatching {
                        connection.getInputStream().bufferedReader().apply {
                            readLine()
                            while (!readLine().isNullOrEmpty()) {
                                // Drain request headers before serving deterministic fixture.
                            }
                        }
                        val body = HTML.toByteArray(StandardCharsets.UTF_8)
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
            thread.join(1_000L)
        }
    }

    private companion object {
        val HTML =
            """
            <!doctype html>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
              html,body{margin:0}
              a,p{display:flex;height:45vh;margin:0;align-items:center;justify-content:center}
              main{height:6000px;background:linear-gradient(#f06,#09f)}
            </style>
            <p id="selection">Selectable Gecko text remains selectable</p>
            <a href="https://example.test/target">Candy long-press target</a>
            <main>Scrollable Gecko gesture fixture</main>
            """.trimIndent()
        const val DRAG_STEPS = 8
        const val DRAG_STEP_MILLIS = 16L
        const val HOME_SWIPE_DURATION_MILLIS = 400L
        const val HOME_SWIPE_SETTLE_MILLIS = 750L
        const val HOME_SWIPE_ATTEMPTS = 2
        const val LONG_PRESS_SETTLE_MILLIS = 1_000L
        const val PAGE_TIMEOUT_SECONDS = 60L
        const val LONG_PRESS_TIMEOUT_SECONDS = 5L
        const val CONDITION_TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 25L
        val DISPLAY_SIZE_PATTERN = Regex("Physical size: (\\d+)x(\\d+)")
    }
}

