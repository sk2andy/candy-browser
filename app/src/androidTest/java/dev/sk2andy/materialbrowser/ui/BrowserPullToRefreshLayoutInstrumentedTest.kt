package dev.sk2andy.materialbrowser.ui

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class BrowserPullToRefreshLayoutInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun hostWrapsExistingContentContainerWhenRefreshIsEnabled() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val host = StatusBarStaticOverlayHost(
                context = instrumentation.targetContext,
                pullToRefreshEnabled = true,
            )

            val refreshLayout = host.getChildAt(0) as BrowserPullToRefreshLayout
            assertSame(host.contentContainer, refreshLayout.contentView)
            assertSame(refreshLayout, host.contentContainer.parent)
        }
    }

    @Test
    fun updateControlsGestureRefreshStateAndScrollAdmission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val host = StatusBarStaticOverlayHost(
                context = instrumentation.targetContext,
                pullToRefreshEnabled = true,
            )
            val refreshLayout = host.getChildAt(0) as BrowserPullToRefreshLayout

            host.updatePullToRefresh(
                enabled = true,
                refreshing = true,
                indicatorColor = android.graphics.Color.RED,
                indicatorContainerColor = android.graphics.Color.WHITE,
                indicatorTopInsetPx = 0,
                canChildScrollUp = { false },
                onRefresh = { true },
            )

            assertTrue(refreshLayout.isEnabled)
            assertTrue(refreshLayout.isRefreshing)
            assertFalse(refreshLayout.canChildScrollUp())

            host.updatePullToRefresh(
                enabled = false,
                refreshing = true,
                indicatorColor = android.graphics.Color.RED,
                indicatorContainerColor = android.graphics.Color.WHITE,
                indicatorTopInsetPx = 0,
                canChildScrollUp = { true },
                onRefresh = { false },
            )

            assertFalse(refreshLayout.isEnabled)
            assertFalse(refreshLayout.isRefreshing)
            assertTrue(refreshLayout.canChildScrollUp())
        }
    }

    @Test
    fun indicatorOffsetsBelowTopSafeArea() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val host = StatusBarStaticOverlayHost(
                context = instrumentation.targetContext,
                pullToRefreshEnabled = true,
            )
            val refreshLayout = host.getChildAt(0) as BrowserPullToRefreshLayout
            val defaultStartOffsetPx = refreshLayout.progressViewStartOffset
            val defaultEndOffsetPx = refreshLayout.progressViewEndOffset
            val topSafeAreaPx = 96

            host.updatePullToRefresh(
                enabled = true,
                refreshing = false,
                indicatorColor = android.graphics.Color.RED,
                indicatorContainerColor = android.graphics.Color.WHITE,
                indicatorTopInsetPx = topSafeAreaPx,
                canChildScrollUp = { false },
                onRefresh = { true },
            )

            assertEquals(
                defaultStartOffsetPx + topSafeAreaPx,
                refreshLayout.progressViewStartOffset,
            )
            assertEquals(
                defaultStartOffsetPx + defaultEndOffsetPx + topSafeAreaPx,
                refreshLayout.progressViewEndOffset,
            )
            assertTrue(refreshLayout.progressViewEndOffset >= topSafeAreaPx)
        }
    }

    @Test
    fun midPageSwipeReachesWebViewWithNestedScrollerAndEditor() {
        var refreshCount = 0
        var webViewMoveCount = 0
        lateinit var refreshLayout: BrowserPullToRefreshLayout
        lateinit var webView: WebView
        val focusCompleted = CountDownLatch(1)
        composeRule.runOnIdle {
            val activity = composeRule.activity
            val host = StatusBarStaticOverlayHost(
                context = activity,
                pullToRefreshEnabled = true,
            )
            refreshLayout = host.getChildAt(0) as BrowserPullToRefreshLayout
            webView = WebView(activity).apply {
                settings.javaScriptEnabled = true
                setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_MOVE) webViewMoveCount++
                    false
                }
                loadDataWithBaseURL(
                    "https://gesture.test/",
                    """
                        <html><body style="margin:0;overflow:hidden">
                          <div style="height:100vh;overflow-y:auto">
                            <div contenteditable="true" style="height:100px">Edit here</div>
                            <div style="height:200vh">Scrollable content</div>
                          </div>
                        </body></html>
                    """.trimIndent(),
                    "text/html",
                    "utf-8",
                    null,
                )
            }
            host.contentContainer.addView(
                webView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
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
            activity.addContentView(
                host,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            var ready = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                ready = webView.width > 0 && webView.height > 0 && webView.progress == 100
            }
            ready
        }
        composeRule.runOnIdle {
            assertEquals(0, webView.scrollY)
            val downTime = SystemClock.uptimeMillis()
            val x = refreshLayout.width / 2f
            val y = refreshLayout.height / 2f
            listOf(
                MotionEvent.ACTION_DOWN to y,
                MotionEvent.ACTION_MOVE to y + 80f,
                MotionEvent.ACTION_MOVE to y + 160f,
                MotionEvent.ACTION_UP to y + 160f,
            ).forEachIndexed { index, (action, eventY) ->
                MotionEvent.obtain(downTime, downTime + index * 16L, action, x, eventY, 0).also {
                    refreshLayout.dispatchTouchEvent(it)
                    it.recycle()
                }
            }
            assertTrue(webViewMoveCount > 0)
            assertEquals(0, refreshCount)
            assertFalse(refreshLayout.isRefreshing)
            webView.requestFocus()
            webView.evaluateJavascript(
                """
                    (() => {
                      const editor = document.querySelector('[contenteditable]');
                      editor.focus();
                      getSelection().collapse(editor.firstChild, editor.textContent.length);
                    })()
                """.trimIndent(),
            ) { focusCompleted.countDown() }
        }
        assertTrue(focusCompleted.await(5, TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_A)
        val editorText = AtomicReference<String?>()
        val completed = CountDownLatch(1)
        composeRule.runOnIdle {
            webView.evaluateJavascript(
                "document.querySelector('[contenteditable]').textContent",
            ) { value ->
                editorText.set(value)
                completed.countDown()
            }
        }
        assertTrue(completed.await(5, TimeUnit.SECONDS))
        assertEquals("\"Edit herea\"", editorText.get())
    }

    @Test
    fun pullInsideExpandedTopZoneStartsRefresh() {
        var refreshCount = 0
        lateinit var refreshLayout: BrowserPullToRefreshLayout
        composeRule.runOnIdle {
            val activity = composeRule.activity
            val host = StatusBarStaticOverlayHost(
                context = activity,
                pullToRefreshEnabled = true,
            )
            refreshLayout = host.getChildAt(0) as BrowserPullToRefreshLayout
            host.contentContainer.addView(
                View(activity).apply { isClickable = true },
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
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
            activity.addContentView(
                host,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            refreshLayout.height > 240f * refreshLayout.resources.displayMetrics.density
        }
        composeRule.runOnIdle {
            val downTime = SystemClock.uptimeMillis()
            val x = refreshLayout.width / 2f
            val density = refreshLayout.resources.displayMetrics.density
            listOf(
                MotionEvent.ACTION_DOWN to 80f * density,
                MotionEvent.ACTION_MOVE to 150f * density,
                MotionEvent.ACTION_MOVE to 220f * density,
                MotionEvent.ACTION_UP to 220f * density,
            ).forEachIndexed { index, (action, eventY) ->
                MotionEvent.obtain(downTime, downTime + index * 16L, action, x, eventY, 0).also {
                    refreshLayout.dispatchTouchEvent(it)
                    it.recycle()
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) { refreshCount == 1 }
        composeRule.runOnIdle { assertTrue(refreshLayout.isRefreshing) }
    }
}
