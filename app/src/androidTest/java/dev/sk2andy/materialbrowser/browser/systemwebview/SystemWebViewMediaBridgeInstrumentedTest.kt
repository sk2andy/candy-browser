package dev.sk2andy.materialbrowser.browser.systemwebview

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEngineEventSink
import dev.sk2andy.materialbrowser.browser.gecko.AndroidBrowserEngineSessionPort
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaSessionState
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaSessionStateListener
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommands
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
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
