package dev.sk2andy.materialbrowser.browser

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoAutoplayBlockerScriptInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val webView = AtomicReference<WebView>()

    @After
    fun tearDown() {
        webView.getAndSet(null)?.let { view ->
            instrumentation.runOnMainSync { view.destroy() }
        }
    }

    @Test
    fun blocksMutedAutoplayAndProgrammaticVideoPlayback() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val view = loadPageWithBlocker()

        assertEquals("false", evaluate(view, "String(document.querySelector('video').autoplay)"))
        assertEquals(
            "function",
            evaluate(view, "typeof window.__candyVideoAutoplayBlockerCleanup"),
        )
        evaluate(view, "window.blockedPlayReference = HTMLMediaElement.prototype.play")

        evaluate(
            view,
            """
                window.videoPlayResult = 'pending';
                document.querySelector('video').play().then(
                  () => { window.videoPlayResult = 'resolved'; },
                  error => { window.videoPlayResult = error.name; }
                );
            """.trimIndent(),
        )

        assertEquals("NotAllowedError", awaitJavaScriptValue(view, "window.videoPlayResult"))

        evaluate(view, VideoAutoplayBlockerScript.cleanupScript)
        assertEquals(
            "undefined",
            evaluate(view, "typeof window.__candyVideoAutoplayBlockerCleanup"),
        )
        assertEquals(
            "false",
            evaluate(
                view,
                "String(window.blockedPlayReference === HTMLMediaElement.prototype.play)",
            ),
        )
    }

    @Test
    fun unrelatedTapDoesNotUnlockVideoPlaybackBlocker() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val view = loadPageWithBlocker()

        dispatchTap(view)

        assertEquals(
            "function",
            evaluate(view, "typeof window.__candyVideoAutoplayBlockerCleanup"),
        )
        requestVideoPlayback(view)
        assertEquals("NotAllowedError", awaitJavaScriptValue(view, "window.videoPlayResult"))
    }

    @Test
    fun explicitPlayTapAllowsVideoPlaybackAttemptWithoutPermanentlyUnlockingPage() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val view = loadPageWithBlocker(
            """
                <html><body>
                  <button style="position:fixed;inset:0;width:200px;height:200px" onclick="
                    window.videoPlayResult = 'pending';
                    document.querySelector('video').play().then(
                      () => { window.videoPlayResult = 'resolved'; },
                      error => { window.videoPlayResult = error.name; }
                    );
                  ">Play</button>
                  <video></video>
                </body></html>
            """.trimIndent(),
        )

        dispatchTap(view)

        assertNotEquals("NotAllowedError", awaitJavaScriptValue(view, "window.videoPlayResult"))
        assertEquals(
            "function",
            evaluate(view, "typeof window.__candyVideoAutoplayBlockerCleanup"),
        )
    }

    @Test
    fun scrollStartingOnVideoDoesNotGrantPlayback() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val view = loadPageWithBlocker(
            "<html><body><video style=\"position:fixed;inset:0;width:200px;height:200px\"></video></body></html>",
        )

        dispatchScroll(view)
        requestVideoPlayback(view)

        assertEquals("NotAllowedError", awaitJavaScriptValue(view, "window.videoPlayResult"))
    }

    @Test
    fun delayedCustomPlayerControlGrantsOnlyItsAssociatedVideo() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val view = loadPageWithBlocker(
            """
                <html><body>
                  <div class="video-player">
                    <button style="position:fixed;inset:0;width:200px;height:200px" onclick="
                      setTimeout(() => {
                        window.intendedPlayResult = 'pending';
                        document.querySelector('#intended').play().then(
                          () => { window.intendedPlayResult = 'resolved'; },
                          error => { window.intendedPlayResult = error.name; }
                        );
                      }, 100);
                    ">Play</button>
                    <video id="intended"></video>
                  </div>
                  <video id="unrelated"></video>
                </body></html>
            """.trimIndent(),
        )

        dispatchTap(view)

        assertNotEquals(
            "NotAllowedError",
            awaitJavaScriptValue(view, "window.intendedPlayResult"),
        )
        evaluate(
            view,
            """
                window.unrelatedPlayResult = 'pending';
                document.querySelector('#unrelated').play().then(
                  () => { window.unrelatedPlayResult = 'resolved'; },
                  error => { window.unrelatedPlayResult = error.name; }
                );
            """.trimIndent(),
        )
        assertEquals(
            "NotAllowedError",
            awaitJavaScriptValue(view, "window.unrelatedPlayResult"),
        )
    }

    private fun loadPageWithBlocker(
        html: String = "<html><body><video autoplay muted></video></body></html>",
    ): WebView {
        val pageLoaded = CountDownLatch(1)
        val createdView = AtomicReference<WebView>()
        instrumentation.runOnMainSync {
            createdView.set(
                WebView(instrumentation.targetContext).apply {
                    settings.javaScriptEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    WebViewCompat.addDocumentStartJavaScript(
                        this,
                        VideoAutoplayBlockerScript.installScript,
                        setOf("*"),
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            pageLoaded.countDown()
                        }
                    }
                    loadDataWithBaseURL(
                        "https://autoplay.test/",
                        html,
                        "text/html",
                        "utf-8",
                        null,
                    )
                },
            )
        }
        assertTrue(pageLoaded.await(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        return createdView.get().also(webView::set)
    }

    private fun awaitJavaScriptValue(view: WebView, script: String): String? {
        val deadline = SystemClock.uptimeMillis() + RESULT_TIMEOUT_SECONDS * 1_000
        while (SystemClock.uptimeMillis() < deadline) {
            val value = evaluate(view, script)
            if (value != "pending") return value
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return null
    }

    private fun requestVideoPlayback(view: WebView) {
        evaluate(
            view,
            """
                window.videoPlayResult = 'pending';
                document.querySelector('video').play().then(
                  () => { window.videoPlayResult = 'resolved'; },
                  error => { window.videoPlayResult = error.name; }
                );
            """.trimIndent(),
        )
    }

    private fun dispatchTap(view: WebView) {
        dispatchGesture(view, listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP))
    }

    private fun dispatchScroll(view: WebView) {
        dispatchGesture(
            view,
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP),
        )
    }

    private fun dispatchGesture(view: WebView, actions: List<Int>) {
        instrumentation.runOnMainSync {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(VIEW_SIZE_PIXELS, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(VIEW_SIZE_PIXELS, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, VIEW_SIZE_PIXELS, VIEW_SIZE_PIXELS)
            val downTime = SystemClock.uptimeMillis()
            val isScroll = MotionEvent.ACTION_MOVE in actions
            actions.forEach { action ->
                val coordinate = if (isScroll && action != MotionEvent.ACTION_DOWN) {
                    SCROLL_END_COORDINATE
                } else {
                    TAP_COORDINATE
                }
                MotionEvent.obtain(
                    downTime,
                    SystemClock.uptimeMillis(),
                    action,
                    TAP_COORDINATE,
                    coordinate,
                    0,
                ).let { event ->
                    view.dispatchTouchEvent(event)
                    event.recycle()
                }
            }
        }
    }

    private fun evaluate(view: WebView, script: String): String? {
        val result = AtomicReference<String?>()
        val evaluated = CountDownLatch(1)
        instrumentation.runOnMainSync {
            view.evaluateJavascript(script) { value ->
                result.set(value?.removeSurrounding("\""))
                evaluated.countDown()
            }
        }
        assertTrue(evaluated.await(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        return result.get()
    }

    private companion object {
        const val RESULT_TIMEOUT_SECONDS = 10L
        const val POLL_INTERVAL_MILLIS = 50L
        const val VIEW_SIZE_PIXELS = 200
        const val TAP_COORDINATE = 50f
        const val SCROLL_END_COORDINATE = 150f
    }
}
