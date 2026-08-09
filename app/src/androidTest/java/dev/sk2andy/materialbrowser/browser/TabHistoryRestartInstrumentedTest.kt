package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.TabWebViewStateStore
import java.io.ByteArrayInputStream
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
class TabHistoryRestartInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences by lazy {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    private val stateStore by lazy { TabWebViewStateStore(context) }
    private var activity: MainActivity? = null

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        stateStore.clear()
    }

    @After
    fun tearDown() {
        activity?.let { current ->
            instrumentation.runOnMainSync {
                current.setContentView(FrameLayout(current))
                current.finish()
            }
            instrumentation.waitForIdleSync()
        }
        preferences.edit().clear().commit()
        stateStore.clear()
    }

    @Test
    fun persistedBackHistoryRestoresOnColdStart() {
        val savedState = createSavedHistory()
        assertTrue(stateStore.save(TAB_ID, savedState))
        BrowserSessionStore(context).saveTabsImmediately(
            tabs = listOf(
                BrowserTab(
                    id = TAB_ID,
                    lastAccessedAt = 1L,
                    title = "C",
                    url = C_URL,
                ),
            ),
            selectedTabId = TAB_ID,
        )

        val launched = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        activity = launched
        val restoredWebView = selectedWebView(launched)
        val afterRestart = historySnapshot(restoredWebView)

        assertEquals(listOf(ROOT_URL, B_URL, C_URL), afterRestart.urls)
        assertEquals(2, afterRestart.currentIndex)
        assertTrue(afterRestart.canGoBack)

        instrumentation.runOnMainSync {
            launched.browserControllerForTesting().goBack()
        }
        val afterBack = awaitHistory(restoredWebView) { snapshot ->
            snapshot.currentIndex == 1 && snapshot.currentUrl == B_URL
        }
        assertEquals(B_URL, afterBack.currentUrl)

        finishActivity(launched)
        val relaunched = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        activity = relaunched
        val savedByLifecycle = historySnapshot(selectedWebView(relaunched))

        assertEquals(listOf(ROOT_URL, B_URL, C_URL), savedByLifecycle.urls)
        assertEquals(1, savedByLifecycle.currentIndex)
        assertTrue(savedByLifecycle.canGoBack)
        assertTrue(savedByLifecycle.canGoForward)
    }

    private fun createSavedHistory(): Bundle {
        val callbackLatch = AtomicReference<CountDownLatch?>()
        lateinit var webView: WebView
        instrumentation.runOnMainSync {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun doUpdateVisitedHistory(
                        view: WebView,
                        url: String?,
                        isReload: Boolean,
                    ) {
                        callbackLatch.getAndSet(null)?.countDown()
                    }

                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse = WebResourceResponse(
                        "text/html",
                        "utf-8",
                        ByteArrayInputStream("<html><body>History</body></html>".toByteArray()),
                    )
                }
            }
        }
        try {
            awaitCallback(callbackLatch) { webView.loadUrl(ROOT_URL) }
            awaitCallback(callbackLatch) {
                webView.evaluateJavascript("history.pushState({}, '', '/b')", null)
            }
            awaitCallback(callbackLatch) {
                webView.evaluateJavascript("history.pushState({}, '', '/c')", null)
            }
            val state = Bundle()
            instrumentation.runOnMainSync {
                val history = requireNotNull(webView.saveState(state))
                assertEquals(listOf(ROOT_URL, B_URL, C_URL), List(history.size) { index ->
                    history.getItemAtIndex(index).url.orEmpty()
                })
            }
            return state
        } finally {
            instrumentation.runOnMainSync(webView::destroy)
        }
    }

    private fun awaitCallback(
        callbackLatch: AtomicReference<CountDownLatch?>,
        action: () -> Unit,
    ) {
        val latch = CountDownLatch(1)
        callbackLatch.set(latch)
        instrumentation.runOnMainSync(action)
        assertTrue("WebView history callback timed out", latch.await(10, TimeUnit.SECONDS))
    }

    private fun selectedWebView(activity: MainActivity): WebView {
        val reference = AtomicReference<WebView>()
        instrumentation.runOnMainSync {
            reference.set(activity.browserControllerForTesting().selectedWebViewForTesting())
        }
        return reference.get()
    }

    private fun awaitHistory(
        webView: WebView,
        predicate: (HistorySnapshot) -> Boolean,
    ): HistorySnapshot {
        val deadline = System.currentTimeMillis() + 10_000L
        var latest = HistorySnapshot(emptyList(), -1, null, false, false)
        while (System.currentTimeMillis() < deadline) {
            latest = historySnapshot(webView)
            if (predicate(latest)) return latest
            Thread.sleep(50L)
        }
        throw AssertionError("WebView history timed out: $latest")
    }

    private fun historySnapshot(webView: WebView): HistorySnapshot {
        val reference = AtomicReference<HistorySnapshot>()
        instrumentation.runOnMainSync {
            val history = webView.copyBackForwardList()
            reference.set(
                HistorySnapshot(
                    urls = List(history.size) { index ->
                        history.getItemAtIndex(index).url.orEmpty()
                    },
                    currentIndex = history.currentIndex,
                    currentUrl = history.currentItem?.url,
                    canGoBack = webView.canGoBack(),
                    canGoForward = webView.canGoForward(),
                ),
            )
        }
        return reference.get()
    }

    private data class HistorySnapshot(
        val urls: List<String>,
        val currentIndex: Int,
        val currentUrl: String?,
        val canGoBack: Boolean,
        val canGoForward: Boolean,
    )

    private fun finishActivity(activity: MainActivity) {
        instrumentation.runOnMainSync {
            activity.setContentView(FrameLayout(activity))
            activity.finish()
        }
        instrumentation.waitForIdleSync()
        if (this.activity === activity) this.activity = null
    }

    private companion object {
        const val TAB_ID = "00000000-0000-0000-0000-000000000099"
        const val ROOT_URL = "https://a.example/root"
        const val B_URL = "https://a.example/b"
        const val C_URL = "https://a.example/c"
    }
}
