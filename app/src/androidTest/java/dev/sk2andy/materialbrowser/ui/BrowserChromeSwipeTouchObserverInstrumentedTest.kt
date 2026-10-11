package dev.sk2andy.materialbrowser.ui

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserChromeSwipeTouchObserverInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()
    private var gestureDownTime = 0L

    @Test
    fun nonScrollingWebViewReceivesSwipesWhilePillChanges() {
        lateinit var host: StatusBarStaticOverlayHost
        lateinit var webView: WebView
        val compactChanges = mutableListOf<Boolean>()
        var pageMoveCount = 0
        composeRule.runOnIdle {
            host = createHost()
            webView = WebView(composeRule.activity).apply {
                setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_MOVE) pageMoveCount++
                    false
                }
                loadDataWithBaseURL(
                    "https://swipe.test/",
                    "<html><body style='margin:0;height:100vh;overflow:hidden'>Canvas page</body></html>",
                    "text/html",
                    "utf-8",
                    null,
                )
            }
            host.contentContainer.addView(webView, matchParentParams())
            host.updateChromeSwipe(true, "first", 0, compactChanges::add)
        }
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            var ready = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                ready = webView.width > 0 && webView.height > 0 && webView.progress == 100
            }
            ready
        }
        composeRule.runOnIdle {
            try {
                dispatchSwipe(host, distanceDp = -40f)
                dispatchSwipe(host, distanceDp = 24f)
                assertEquals(listOf(true, false), compactChanges)
                assertTrue(pageMoveCount >= 2)
                assertEquals(0, webView.scrollY)
            } finally {
                host.contentContainer.removeView(webView)
                webView.destroy()
            }
        }
    }

    @Test
    fun disabledAndHorizontalGesturesKeepPageTouchDelivery() {
        val compactChanges = mutableListOf<Boolean>()
        var pageMoveCount = 0
        composeRule.runOnIdle {
            val host = createHost()
            host.contentContainer.addView(
                View(composeRule.activity).apply {
                    setOnTouchListener { view, event ->
                        view.parent.requestDisallowInterceptTouchEvent(true)
                        if (event.actionMasked == MotionEvent.ACTION_MOVE) pageMoveCount++
                        true
                    }
                },
                matchParentParams(),
            )
            host.measure(
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            )
            host.layout(0, 0, 600, 1000)
            host.updateChromeSwipe(false, "first", 0, compactChanges::add)
            dispatchSwipe(host, distanceDp = -40f)
            host.updateChromeSwipe(true, "first", 0, compactChanges::add)
            dispatch(host, MotionEvent.ACTION_DOWN, x = 100f, y = 500f)
            dispatch(host, MotionEvent.ACTION_MOVE, x = 250f, y = 505f)
            dispatch(host, MotionEvent.ACTION_MOVE, x = 250f, y = 100f)
            dispatch(host, MotionEvent.ACTION_UP, x = 250f, y = 100f)
            assertTrue(compactChanges.isEmpty())
            assertEquals(4, pageMoveCount)
        }
    }

    @Test
    fun cancellationMultitouchAndRendererReplacementResetPendingSwipe() {
        val compactChanges = mutableListOf<Boolean>()
        composeRule.runOnIdle {
            val observer = BrowserChromeSwipeTouchObserver(composeRule.activity)
            observer.update(true, "first", 0, compactChanges::add)
            dispatch(observer, MotionEvent.ACTION_DOWN, y = 500f)
            dispatch(observer, MotionEvent.ACTION_CANCEL, y = 500f)
            dispatch(observer, MotionEvent.ACTION_MOVE, y = 100f)
            dispatch(observer, MotionEvent.ACTION_DOWN, y = 500f)
            dispatch(observer, MotionEvent.ACTION_POINTER_DOWN, y = 500f)
            dispatch(observer, MotionEvent.ACTION_MOVE, y = 100f)
            dispatch(observer, MotionEvent.ACTION_DOWN, y = 500f)
            observer.update(true, "second", 0, compactChanges::add)
            dispatch(observer, MotionEvent.ACTION_MOVE, y = 100f)
            dispatch(observer, MotionEvent.ACTION_DOWN, y = 500f)
            observer.update(true, "second", 1, compactChanges::add)
            dispatch(observer, MotionEvent.ACTION_MOVE, y = 100f)
            dispatch(observer, MotionEvent.ACTION_DOWN, y = 500f)
            observer.update(false, "second", 1, compactChanges::add)
            observer.update(true, "second", 1, compactChanges::add)
            dispatch(observer, MotionEvent.ACTION_MOVE, y = 100f)
            assertTrue(compactChanges.isEmpty())
            dispatch(observer, MotionEvent.ACTION_DOWN, y = 500f)
            dispatch(observer, MotionEvent.ACTION_MOVE, y = 100f)
            assertEquals(listOf(true), compactChanges)
        }
    }

    @Test
    fun pullToRefreshStillCompletesWithSwipeObservation() {
        lateinit var host: StatusBarStaticOverlayHost
        val compactChanges = mutableListOf<Boolean>()
        var refreshCount = 0
        composeRule.runOnIdle {
            host = createHost(pullToRefresh = true)
            host.contentContainer.addView(
                View(composeRule.activity).apply { isClickable = true },
                matchParentParams(),
            )
            host.updateChromeSwipe(true, "first", 0, compactChanges::add)
            host.updatePullToRefresh(
                enabled = true,
                refreshing = false,
                indicatorColor = android.graphics.Color.RED,
                indicatorContainerColor = android.graphics.Color.WHITE,
                indicatorTopInsetPx = 0,
                canChildScrollUp = { false },
                onRefresh = {
                    refreshCount++
                    true
                },
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) { host.height > 0 }
        composeRule.runOnIdle { dispatchSwipe(host, distanceDp = 140f) }
        composeRule.waitUntil(timeoutMillis = 5_000L) { refreshCount == 1 }
        composeRule.runOnIdle {
            val refreshLayout = host.getChildAt(0) as BrowserPullToRefreshLayout
            try {
                assertTrue(compactChanges.isNotEmpty())
                assertTrue(compactChanges.none { it })
                assertTrue(refreshLayout.isRefreshing)
            } finally {
                refreshLayout.isRefreshing = false
            }
        }
    }

    private fun createHost(pullToRefresh: Boolean = false): StatusBarStaticOverlayHost =
        StatusBarStaticOverlayHost(
            context = composeRule.activity,
            pullToRefreshEnabled = pullToRefresh,
        ).also { host -> composeRule.activity.addContentView(host, matchParentParams()) }

    private fun matchParentParams() = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )

    private fun dispatchSwipe(host: View, distanceDp: Float) {
        val y = host.height / 2f
        val distancePx = distanceDp * host.resources.displayMetrics.density
        dispatch(host, MotionEvent.ACTION_DOWN, y = y)
        dispatch(host, MotionEvent.ACTION_MOVE, y = y + distancePx / 2f)
        dispatch(host, MotionEvent.ACTION_MOVE, y = y + distancePx)
        dispatch(host, MotionEvent.ACTION_UP, y = y + distancePx)
    }

    private fun dispatch(host: View, action: Int, x: Float = 100f, y: Float) {
        val time = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) gestureDownTime = time
        val event = MotionEvent.obtain(gestureDownTime, time, action, x, y, 0)
        try {
            host.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun dispatch(observer: BrowserChromeSwipeTouchObserver, action: Int, y: Float) {
        val time = SystemClock.uptimeMillis()
        val event = if (action == MotionEvent.ACTION_POINTER_DOWN) {
            MotionEvent.obtain(
                time,
                time,
                action or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                2,
                Array(2) { index -> MotionEvent.PointerProperties().apply { id = index } },
                Array(2) { index ->
                    MotionEvent.PointerCoords().apply {
                        x = 100f + index * 20f
                        this.y = y
                    }
                },
                0, 0, 1f, 1f, 0, 0, 0, 0,
            )
        } else {
            MotionEvent.obtain(time, time, action, 100f, y, 0)
        }
        try {
            observer.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }
}
