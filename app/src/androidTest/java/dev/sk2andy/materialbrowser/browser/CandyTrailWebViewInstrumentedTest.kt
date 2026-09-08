package dev.sk2andy.materialbrowser.browser

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.gecko.AndroidBrowserEngineSessionPort
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEngineEventSink
import dev.sk2andy.materialbrowser.browser.gecko.GeckoCandyTrailHistoryEvent
import dev.sk2andy.materialbrowser.browser.gecko.GeckoCandyTrailHistoryEventSink
import dev.sk2andy.materialbrowser.browser.systemwebview.SystemWebViewBrowserEngineFactory
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommands
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CandyTrailWebViewInstrumentedTest {
    @Test
    fun pushStateCallbacksCreateDistinctAdapterHistorySnapshots() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val events = LinkedBlockingQueue<GeckoCandyTrailHistoryEvent>()
        val server = LocalPageServer()
        val root = server.rootUrl
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        lateinit var factory: SystemWebViewBrowserEngineFactory
        lateinit var session: AndroidBrowserEngineSessionPort
        lateinit var webView: WebView

        instrumentation.runOnMainSync {
            factory = SystemWebViewBrowserEngineFactory(activity)
            session = factory.create(
                tabId = TAB_ID,
                profileId = PROFILE_ID,
                isPrivate = false,
                trailHistoryEventSink = GeckoCandyTrailHistoryEventSink { _, event ->
                    events.offer(event)
                },
                eventSink = BrowserEngineEventSink {},
            )
            val host = session.createView(activity)
            webView = requireNotNull(host.findWebView())
            activity.setContentView(host)
            session.execute(BrowserEngineCommands.load(root))
        }

        try {
            awaitSnapshot(events, root)
            instrumentation.runOnMainSync {
                webView.evaluateJavascript("history.pushState({}, '', '/b')", null)
            }
            awaitSnapshot(events, server.url("/b"))
            instrumentation.runOnMainSync {
                webView.evaluateJavascript("history.pushState({}, '', '/c')", null)
            }
            val result = awaitSnapshot(events, server.url("/c"))

            assertEquals(
                listOf(root, server.url("/b"), server.url("/c")),
                result.snapshot.urls,
            )
            assertEquals(2, result.snapshot.currentIndex)
        } finally {
            instrumentation.runOnMainSync {
                session.execute(BrowserEngineCommands.close())
                factory.shutdown()
                activity.setContentView(FrameLayout(activity))
                activity.finish()
            }
            instrumentation.waitForIdleSync()
            server.close()
        }
    }

    private fun awaitSnapshot(
        events: LinkedBlockingQueue<GeckoCandyTrailHistoryEvent>,
        expectedUrl: String,
    ): GeckoCandyTrailHistoryEvent {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val event = events.poll(250, TimeUnit.MILLISECONDS) ?: continue
            if (event.snapshot.urls.getOrNull(event.snapshot.currentIndex) == expectedUrl) return event
        }
        fail("History snapshot timed out for $expectedUrl")
        error("unreachable")
    }

    private fun View.findWebView(): WebView? = when (this) {
        is WebView -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { index ->
            getChildAt(index).findWebView()
        }
        else -> null
    }

    private companion object {
        const val TAB_ID = "00000000-0000-0000-0000-000000000001"
        const val PROFILE_ID = "00000000-0000-0000-0000-000000000002"
    }

    private class LocalPageServer : Closeable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val thread = Thread(::serve, "candy-trail-test-server").apply {
            isDaemon = true
            start()
        }
        private val baseUrl = "http://127.0.0.1:${socket.localPort}"
        val rootUrl = url("/root")

        fun url(path: String): String = "$baseUrl$path"

        private fun serve() {
            while (!socket.isClosed) {
                val connection = runCatching { socket.accept() }.getOrNull() ?: return
                connection.use {
                    runCatching {
                        val input = connection.getInputStream()
                        val request = ByteArrayOutputStream()
                        var matched = 0
                        while (matched < HEADER_END.size) {
                            val next = input.read()
                            if (next < 0) break
                            request.write(next)
                            matched = if (next == HEADER_END[matched].toInt()) matched + 1 else 0
                        }
                        val body = "<html><body>Candy Trail</body></html>"
                            .toByteArray(Charsets.UTF_8)
                        connection.getOutputStream().use { output ->
                            output.write(
                                (
                                    "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: text/html; charset=utf-8\r\n" +
                                        "Content-Length: ${body.size}\r\n" +
                                        "Connection: close\r\n\r\n"
                                    ).toByteArray(Charsets.US_ASCII),
                            )
                            output.write(body)
                        }
                    }
                }
            }
        }

        override fun close() {
            socket.close()
            thread.join(2_000)
        }

        private companion object {
            val HEADER_END = byteArrayOf(13, 10, 13, 10)
        }
    }
}
