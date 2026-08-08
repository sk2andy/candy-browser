package dev.sk2andy.materialbrowser.ui

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.absoluteValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinkPeekTouchListenerInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun downwardPeekCommitCannotFallThroughToPullRefreshStream() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = RecordingTouchView(context)
        val delegatedActions = mutableListOf<Int>()
        var peekVisible = false
        val dismissals = AtomicInteger()
        val opens = AtomicInteger()
        val pointerStarts = AtomicInteger()
        val pointerEnds = AtomicInteger()
        val listener = LinkPeekTouchListener(
            threshold = { 100f },
            touchSlop = 8f,
            delegate = View.OnTouchListener { _, event ->
                delegatedActions += event.actionMasked
                false
            },
            isVisible = { peekVisible },
            onProgress = { _, _ -> },
            onOpen = opens::incrementAndGet,
            onDismiss = {
                dismissals.incrementAndGet()
                peekVisible = false
            },
            onThresholdHaptic = {},
            onPointerDown = pointerStarts::incrementAndGet,
            onPointerEnd = pointerEnds::incrementAndGet,
        )

        assertFalse(dispatch(listener, view, MotionEvent.ACTION_DOWN, x = 100f, y = 100f))
        peekVisible = true
        assertTrue(dispatch(listener, view, MotionEvent.ACTION_MOVE, x = 100f, y = 210f))
        assertTrue(dispatch(listener, view, MotionEvent.ACTION_UP, x = 100f, y = 210f))

        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), delegatedActions)
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), view.actions)
        assertEquals(0, dismissals.get())
        assertEquals(1, opens.get())
        assertEquals(1, pointerStarts.get())
        assertEquals(1, pointerEnds.get())
        assertEquals(true, peekVisible)
    }

    @Test
    fun upwardMovementKeepsPeekUntilReleaseThenDismissesWithoutOpening() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = RecordingTouchView(context)
        val delegatedActions = mutableListOf<Int>()
        var peekVisible = false
        val dismissals = AtomicInteger()
        val opens = AtomicInteger()
        val listener = LinkPeekTouchListener(
            threshold = { 100f },
            touchSlop = 8f,
            delegate = View.OnTouchListener { _, event ->
                delegatedActions += event.actionMasked
                false
            },
            isVisible = { peekVisible },
            onProgress = { _, _ -> },
            onOpen = opens::incrementAndGet,
            onDismiss = {
                dismissals.incrementAndGet()
                peekVisible = false
            },
            onThresholdHaptic = {},
            onPointerDown = {},
            onPointerEnd = {},
        )

        dispatch(listener, view, MotionEvent.ACTION_DOWN, x = 100f, y = 200f)
        peekVisible = true
        assertTrue(dispatch(listener, view, MotionEvent.ACTION_MOVE, x = 100f, y = 180f))
        assertEquals(0, dismissals.get())
        assertTrue(peekVisible)

        dispatch(listener, view, MotionEvent.ACTION_UP, x = 100f, y = 180f)

        assertEquals(1, dismissals.get())
        assertEquals(0, opens.get())
        assertFalse(peekVisible)
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), delegatedActions)
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), view.actions)
    }

    @Test
    fun visiblePeekCancelsARealScrollableWebViewGesture() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            var peekVisible = false
            val delegatedActions = mutableListOf<Int>()
            val webView = attachScrollableWebView(
                scenario = scenario,
                listener = LinkPeekTouchListener(
                    threshold = { 100f },
                    touchSlop = 8f,
                    delegate = View.OnTouchListener { _, event ->
                        delegatedActions += event.actionMasked
                        false
                    },
                    isVisible = { peekVisible },
                    onProgress = { _, _ -> },
                    onOpen = {},
                    onDismiss = { peekVisible = false },
                    onThresholdHaptic = {},
                    onPointerDown = {},
                    onPointerEnd = {},
                ),
            )
            instrumentation.runOnMainSync { webView.scrollTo(0, 4_000) }
            awaitScrollNear(webView, 4_000)
            val before = webView.scrollY
            val x = webView.width / 2f
            val startY = webView.height / 2f
            val downTime = SystemClock.uptimeMillis()

            dispatch(webView, downTime, MotionEvent.ACTION_DOWN, x, startY)
            peekVisible = true
            dispatch(webView, downTime, MotionEvent.ACTION_MOVE, x, startY + 180f)
            dispatch(webView, downTime, MotionEvent.ACTION_UP, x, startY + 180f)
            SystemClock.sleep(100)

            assertEquals(before, webView.scrollY)
            assertEquals(
                listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL),
                delegatedActions,
            )
            assertEquals(1, webView.actions.count { it == MotionEvent.ACTION_CANCEL })
            assertFalse(webView.actions.contains(MotionEvent.ACTION_MOVE))
            assertFalse(webView.actions.contains(MotionEvent.ACTION_UP))
        }
    }

    @Test
    fun realWebViewGestureStillScrollsBeforePeekTakesOwnership() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val webView = attachScrollableWebView(
                scenario = scenario,
                listener = LinkPeekTouchListener(
                    threshold = { 100f },
                    touchSlop = 8f,
                    delegate = View.OnTouchListener { _, _ -> false },
                    isVisible = { false },
                    onProgress = { _, _ -> },
                    onOpen = {},
                    onDismiss = {},
                    onThresholdHaptic = {},
                    onPointerDown = {},
                    onPointerEnd = {},
                ),
            )
            instrumentation.runOnMainSync { webView.scrollTo(0, 4_000) }
            awaitScrollNear(webView, 4_000)
            val before = webView.scrollY
            val x = webView.width / 2f
            val startY = webView.height / 2f
            val downTime = SystemClock.uptimeMillis()

            dispatch(webView, downTime, MotionEvent.ACTION_DOWN, x, startY)
            repeat(4) { index ->
                dispatch(
                    webView,
                    downTime,
                    MotionEvent.ACTION_MOVE,
                    x,
                    startY + (index + 1) * 45f,
                )
            }
            dispatch(webView, downTime, MotionEvent.ACTION_UP, x, startY + 180f)
            SystemClock.sleep(100)

            assertTrue(
                "Control WebView did not scroll: before=$before after=${webView.scrollY}",
                (webView.scrollY - before).absoluteValue > 40,
            )
            assertEquals(0, webView.actions.count { it == MotionEvent.ACTION_CANCEL })
        }
    }

    private fun dispatch(
        listener: View.OnTouchListener,
        view: View,
        action: Int,
        x: Float,
        y: Float,
    ): Boolean {
        var consumed = false
        MotionEvent.obtain(1L, 2L, action, x, y, 0).also { event ->
            consumed = listener.onTouch(view, event)
            if (!consumed) view.onTouchEvent(event)
            event.recycle()
        }
        return consumed
    }

    private class RecordingTouchView(context: Context) : View(context) {
        val actions = mutableListOf<Int>()

        override fun onTouchEvent(event: MotionEvent): Boolean {
            actions += event.actionMasked
            return true
        }
    }

    private class ScrollRecordingWebView(context: Context) : WebView(context) {
        val actions = mutableListOf<Int>()

        override fun onTouchEvent(event: MotionEvent): Boolean {
            actions += event.actionMasked
            return super.onTouchEvent(event)
        }
    }

    private fun attachScrollableWebView(
        scenario: ActivityScenario<ComponentActivity>,
        listener: View.OnTouchListener,
    ): ScrollRecordingWebView {
        val result = AtomicReference<ScrollRecordingWebView>()
        val pageLoaded = CountDownLatch(1)
        scenario.onActivity { activity ->
            val webView = ScrollRecordingWebView(activity)
            webView.setOnTouchListener(listener)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    pageLoaded.countDown()
                }
            }
            webView.loadDataWithBaseURL(
                "https://example.test/",
                """
                    <!doctype html>
                    <html>
                      <head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
                      <body style="min-height:24000px"><div>Link Peek scroll test</div></body>
                    </html>
                """.trimIndent(),
                "text/html",
                "utf-8",
                null,
            )
            activity.addContentView(
                webView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            result.set(webView)
        }
        val webView = result.get()
        assertTrue("Scrollable test page did not load", pageLoaded.await(10, TimeUnit.SECONDS))
        repeat(500) {
            var ready = false
            instrumentation.runOnMainSync {
                ready = webView.width > 0 &&
                    webView.height > 0 &&
                    webView.contentHeight > webView.height
            }
            if (ready) return webView
            SystemClock.sleep(20)
        }
        throw AssertionError("Scrollable test WebView was not ready")
    }

    private fun awaitScrollNear(webView: WebView, expected: Int) {
        repeat(100) {
            if ((webView.scrollY - expected).absoluteValue < 20) return
            SystemClock.sleep(10)
        }
        throw AssertionError("WebView scroll was ${webView.scrollY}, expected $expected")
    }

    private fun dispatch(
        webView: WebView,
        downTime: Long,
        action: Int,
        x: Float,
        y: Float,
    ) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        try {
            instrumentation.runOnMainSync { webView.dispatchTouchEvent(event) }
        } finally {
            event.recycle()
        }
    }
}
