package dev.sk2andy.materialbrowser.browser.systemwebview

import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.browser.engine.BrowserEngineContentKind
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEngineEventSink
import dev.sk2andy.materialbrowser.browser.gecko.AndroidBrowserEngineSessionPort
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaSessionState
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaSessionStateListener
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommands
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemWebViewMediaBridgeInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)
    private var factory: SystemWebViewBrowserEngineFactory? = null
    private var session: AndroidBrowserEngineSessionPort? = null

    @After
    fun tearDown() {
        activityRule.scenario.onActivity {
            session?.execute(BrowserEngineCommands.close())
            factory?.shutdown()
            session = null
            factory = null
        }
    }

    @Test
    fun documentMediaEventsReachEngineSessionListener() {
        val reported = AtomicReference<GeckoMediaSessionState>()
        val ready = CountDownLatch(1)

        activityRule.scenario.onActivity { activity ->
            val createdFactory = SystemWebViewBrowserEngineFactory(activity)
            factory = createdFactory
            val createdSession = createdFactory.create(
                tabId = TAB_ID,
                profileId = PROFILE_ID,
                isPrivate = false,
                contentKind = BrowserEngineContentKind.RegularTab,
                eventSink = BrowserEngineEventSink {},
            )
            session = createdSession
            createdSession.setMediaStateListener(
                GeckoMediaSessionStateListener { state ->
                    if (state.isActive) {
                        reported.set(state)
                        ready.countDown()
                    }
                },
            )
            val host = createdSession.createView(activity)
            val webView = requireNotNull(host.findWebView())
            activity.setContentView(host)
            webView.loadDataWithBaseURL(
                "https://media.test/",
                "<html><head><title>Candy video</title></head><body><video></video></body></html>",
                "text/html",
                "utf-8",
                null,
            )
            webView.postDelayed(
                {
                    webView.evaluateJavascript(
                        """
                            (() => {
                              const video = document.querySelector('video');
                              video.dispatchEvent(new Event('loadedmetadata'));
                            })()
                        """.trimIndent(),
                        null,
                    )
                },
                500,
            )
        }

        assertTrue("Media bridge timed out", ready.await(10, TimeUnit.SECONDS))
        assertEquals("Candy video", reported.get().title)
    }

    @Test
    fun customGameFullscreenKeepsUnrelatedVideoInlineAndVideoFullscreenStillReports() {
        val reported = AtomicReference<GeckoMediaSessionState>()
        val ready = CountDownLatch(1)
        val refreshed = CountDownLatch(1)
        val videoFullscreen = CountDownLatch(1)
        val videoRestored = CountDownLatch(1)
        lateinit var webView: WebView
        var hidden = false

        activityRule.scenario.onActivity { activity ->
            val createdFactory = SystemWebViewBrowserEngineFactory(activity)
            factory = createdFactory
            val createdSession = createdFactory.create(
                tabId = TAB_ID,
                profileId = PROFILE_ID,
                isPrivate = false,
                contentKind = BrowserEngineContentKind.RegularTab,
                eventSink = BrowserEngineEventSink {},
            )
            session = createdSession
            createdSession.setMediaStateListener(
                GeckoMediaSessionStateListener { state ->
                    if (state.isActive) {
                        reported.set(state)
                        ready.countDown()
                        if (state.title == "During game fullscreen") refreshed.countDown()
                        if (state.isFullscreen) {
                            videoFullscreen.countDown()
                        } else if (videoFullscreen.count == 0L) {
                            videoRestored.countDown()
                        }
                    }
                },
            )
            val host = createdSession.createView(activity)
            webView = requireNotNull(host.findWebView())
            activity.setContentView(host)
            webView.loadDataWithBaseURL(
                "https://media.test/",
                """
                    <html><head><meta name="viewport" content="width=device-width">
                    <title>Before game fullscreen</title></head><body>
                    <video></video><div id="game">Game</div>
                    <button style="position:fixed;left:50%;top:50%;width:50%;height:64px;
                      transform:translate(-50%,-50%)"
                      onclick="document.querySelector('video').requestFullscreen()">Video fullscreen</button>
                    <script>
                      const video = document.querySelector('video');
                      Object.defineProperty(video, 'videoWidth', {value: 1920});
                      Object.defineProperty(video, 'videoHeight', {value: 1080});
                      video.dispatchEvent(new Event('loadedmetadata'));
                    </script></body></html>
                """.trimIndent(),
                "text/html",
                "utf-8",
                null,
            )
        }

        assertTrue("Initial media bridge timed out", ready.await(10, TimeUnit.SECONDS))
        assertEquals(1_920, reported.get().videoWidth)
        assertFalse(reported.get().isFullscreen)
        activityRule.scenario.onActivity { activity ->
            requireNotNull(webView.webChromeClient).onShowCustomView(
                View(activity),
                object : WebChromeClient.CustomViewCallback {
                    override fun onCustomViewHidden() {
                        hidden = true
                    }
                },
            )
            assertFalse(reported.get().isFullscreen)
            webView.evaluateJavascript(
                "document.title = 'During game fullscreen'; " +
                    "document.querySelector('video').dispatchEvent(new Event('loadedmetadata'))",
                null,
            )
        }
        assertTrue("Fullscreen media refresh timed out", refreshed.await(10, TimeUnit.SECONDS))
        assertFalse(reported.get().isFullscreen)
        activityRule.scenario.onActivity {
            requireNotNull(webView.webChromeClient).onHideCustomView()
            assertTrue(hidden)
            assertFalse(reported.get().isFullscreen)
        }

        val videoButton = IntArray(2)
        activityRule.scenario.onActivity {
            webView.getLocationOnScreen(videoButton)
            videoButton[0] += webView.width / 2
            videoButton[1] += webView.height / 2
        }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertTrue(device.click(videoButton[0], videoButton[1]))
        assertTrue("Video fullscreen timed out", videoFullscreen.await(10, TimeUnit.SECONDS))
        assertTrue(reported.get().isFullscreen)
        activityRule.scenario.onActivity { requireNotNull(session).exitFullscreen() }
        assertTrue("Video fullscreen exit timed out", videoRestored.await(10, TimeUnit.SECONDS))
        assertFalse(reported.get().isFullscreen)
    }

    @Test
    fun fullscreenScrollRestoreIgnoresReentrantReplacementView() {
        val ready = CountDownLatch(1)
        lateinit var webView: WebView
        activityRule.scenario.onActivity { activity ->
            val createdFactory = SystemWebViewBrowserEngineFactory(activity)
            factory = createdFactory
            val createdSession = createdFactory.create(
                tabId = TAB_ID,
                profileId = PROFILE_ID,
                isPrivate = false,
                contentKind = BrowserEngineContentKind.RegularTab,
                eventSink = BrowserEngineEventSink {},
            )
            session = createdSession
            createdSession.setMediaStateListener(
                GeckoMediaSessionStateListener { state ->
                    if (state.isActive) ready.countDown()
                },
            )
            val host = createdSession.createView(activity)
            webView = requireNotNull(host.findWebView())
            activity.setContentView(host)
            webView.loadDataWithBaseURL(
                "https://fullscreen.test/",
                """
                    <!doctype html><html><head><meta name="viewport" content="width=device-width"></head>
                    <body><div style="height:4000px"><video></video>Scrollable game</div>
                    <script>document.querySelector('video').dispatchEvent(new Event('loadedmetadata'));</script>
                    </body></html>
                """.trimIndent(),
                "text/html",
                "utf-8",
                null,
            )
        }
        assertTrue("Scrollable fixture timed out", ready.await(10, TimeUnit.SECONDS))
        val drawn = CountDownLatch(1)
        activityRule.scenario.onActivity {
            webView.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    drawn.countDown()
                }
            })
        }
        assertTrue("Scrollable fixture did not render", drawn.await(10, TimeUnit.SECONDS))
        val finalOffset = AtomicReference<Int>()
        val restored = CountDownLatch(1)
        activityRule.scenario.onActivity { activity ->
            val chromeClient = requireNotNull(webView.webChromeClient)
            webView.scrollTo(0, 1_000)
            assertEquals(1_000, webView.scrollY)
            chromeClient.onShowCustomView(
                View(activity),
                object : WebChromeClient.CustomViewCallback {
                    override fun onCustomViewHidden() {
                        webView.scrollTo(0, 200)
                        chromeClient.onShowCustomView(
                            View(activity),
                            object : WebChromeClient.CustomViewCallback {
                                override fun onCustomViewHidden() = Unit
                            },
                        )
                        chromeClient.onHideCustomView()
                    }
                },
            )
            chromeClient.onHideCustomView()
            webView.postVisualStateCallback(100, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    webView.postOnAnimation {
                        finalOffset.set(webView.scrollY)
                        restored.countDown()
                    }
                }
            })
        }
        assertTrue("Fullscreen restoration frame timed out", restored.await(10, TimeUnit.SECONDS))
        assertEquals(200, finalOffset.get().toInt())
    }

    private fun View.findWebView(): WebView? = when (this) {
        is WebView -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { index ->
            getChildAt(index).findWebView()
        }
        else -> null
    }

    private companion object {
        const val TAB_ID = "00000000-0000-0000-0000-000000000011"
        const val PROFILE_ID = "00000000-0000-0000-0000-000000000012"
    }
}
