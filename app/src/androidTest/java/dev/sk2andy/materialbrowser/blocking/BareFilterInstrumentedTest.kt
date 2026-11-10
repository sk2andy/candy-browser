package dev.sk2andy.materialbrowser.blocking

import android.os.SystemClock
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONTokener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BareFilterInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val blocker = ContentBlocker(instrumentation.targetContext)
    private val webView = AtomicReference<WebView>()

    @After
    fun tearDown() {
        webView.getAndSet(null)?.let { view ->
            instrumentation.runOnMainSync { view.destroy() }
        }
    }

    @Test
    fun redditHidesAppPromptsAndRestoresScrolling() {
        val view = load(
            "https://www.reddit.com/",
            """<body style="overflow:hidden"><div id="configured-xpromo-test"></div>
                <div id="xpromo-small-header"></div><div id="content">Hello</div></body>""",
        )

        assertEquals(
            "none|none|auto|block",
            evaluate(view, """['configured-xpromo-test','xpromo-small-header','content']
                .map(id => getComputedStyle(document.getElementById(id)).display)
                .reduce((a, v, i) => i === 2 ? a + '|' + getComputedStyle(document.body).overflow + '|' + v : a + '|' + v)"""),
        )
    }

    @Test
    fun facebookMatchesTextExactlyAndKeepsOtherContent() {
        val view = load(
            "https://m.facebook.com/",
            """<body>
                <div id="banner" style="pointer-events: none; background: red">Get Facebook for Android</div>
                <div id="app" data-mcomponent="MContainer"><div data-mcomponent="MContainer"><div role="button">Open app</div></div></div>
                <div id="other" data-mcomponent="MContainer"><div data-mcomponent="MContainer"><div role="button">Open app settings</div></div></div>
                <div id="reels" role="button" aria-label="Open app"></div>
                <div id="search" aria-label="Search" role="button"></div><div id="profile" aria-label="Navigate to your Reels profile"></div>
                </body>""",
        )

        assertEquals("none|none|block|none", evaluate(view, """
            ['banner','app','other','reels'].map(id => getComputedStyle(document.getElementById(id)).display).join('|')
        """))
        assertEquals("true|true", evaluate(view, """
            [getComputedStyle(document.getElementById('search')).marginLeft !== '0px',
             getComputedStyle(document.getElementById('profile')).marginLeft !== '0px'].join('|')
        """))
        evaluate(view, "document.querySelector('#app [role=button]').textContent = 'Open app settings'")
        awaitValue(view, "getComputedStyle(document.getElementById('app')).display", "block")
        evaluate(view, "document.querySelector('#other [role=button]').textContent = 'Open app'")
        awaitValue(view, "getComputedStyle(document.getElementById('other')).display", "none")
        evaluate(view, "document.querySelector('#other [role=button]').removeAttribute('role')")
        awaitValue(view, "getComputedStyle(document.getElementById('other')).display", "block")
    }

    @Test
    fun youtubeRuleIsScopedAndHonorsPausedSites() {
        val url = "https://m.youtube.com/"
        val view = load(url, """<body><div id="app" class="mobile-topbar-header-sign-in-button"><a aria-label="Open App"></a></div><div id="other" class="mobile-topbar-header-sign-in-button"><a aria-label="Sign in"></a></div></body>""")

        assertEquals("none|block", evaluate(view, """
            ['app','other'].map(id => getComputedStyle(document.getElementById(id)).display).join('|')
        """))
        assertTrue(blocker.bareFilterScript(url, listOf("youtube.com")).isEmpty())
        assertTrue(blocker.bareFilterScript("https://m.youtube.com.example.org/", emptyList()).isEmpty())
        assertTrue(blocker.bareFilterScript("https://youtube.com/", emptyList()).isEmpty())
    }

    private fun load(url: String, html: String): WebView {
        val loaded = CountDownLatch(1)
        val view = AtomicReference<WebView>()
        instrumentation.runOnMainSync {
            view.set(WebView(instrumentation.targetContext).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        loaded.countDown()
                    }
                }
                loadDataWithBaseURL(url, html, "text/html", "utf-8", null)
            })
        }
        assertTrue(loaded.await(20, TimeUnit.SECONDS))
        return view.get().also {
            webView.set(it)
            evaluate(it, blocker.bareFilterScript(url, emptyList()))
        }
    }

    private fun evaluate(view: WebView, script: String): String {
        val result = AtomicReference<String>()
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync {
            view.evaluateJavascript(script) { value ->
                result.set(value)
                done.countDown()
            }
        }
        assertTrue(done.await(20, TimeUnit.SECONDS))
        return JSONTokener(result.get()).nextValue().toString()
    }

    private fun awaitValue(view: WebView, script: String, expected: String) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (evaluate(view, script) == expected) return
            SystemClock.sleep(50)
        }
        assertEquals(expected, evaluate(view, script))
    }
}
