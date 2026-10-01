package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class BrowserLoadingProgressInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            controller?.destroy()
            composeRule.activity
                .getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
    }

    @Test
    fun geckoReportsIntermediateProgressForSlowLoadReloadAndStop() {
        assertEngineProgress(AndroidBrowserEngineKind.GeckoView)
    }

    @Test
    fun systemWebViewReportsIntermediateProgressForSlowLoadReloadAndStop() {
        assertEngineProgress(AndroidBrowserEngineKind.SystemWebView)
    }

    private fun assertEngineProgress(engine: AndroidBrowserEngineKind) {
        SlowResourceServer().use { server ->
            lateinit var browserController: BrowserController
            composeRule.runOnIdle {
                val activity = composeRule.activity
                activity.getSharedPreferences(
                    BrowserSessionStore.PREFERENCES_NAME,
                    Context.MODE_PRIVATE,
                )
                    .edit().clear()
                    .putString(BrowserSessionStore.KEY_ANDROID_BROWSER_ENGINE, engine.stableId)
                    .commit()
                browserController = BrowserController(activity)
                controller = browserController
                val host = FrameLayout(activity)
                activity.addContentView(
                    host,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
                browserController.attachSelectedBrowserEngineView(host)
                browserController.submitAddress(server.url)
            }
            awaitIntermediateProgress(browserController, server, expectedRequests = 1)
            server.releaseDocument()
            composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
                server.resourceRequests.get() >= 1
            }
            server.releaseResource()
            awaitCompletion(browserController)

            composeRule.runOnIdle { browserController.reload() }
            awaitIntermediateProgress(browserController, server, expectedRequests = 2)
            server.releaseDocument()
            composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
                server.resourceRequests.get() >= 2
            }
            server.releaseResource()
            awaitCompletion(browserController)

            composeRule.runOnIdle { browserController.reload() }
            composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
                server.documentRequests.get() >= 3 && browserController.selectedTab.isLoading
            }
            composeRule.runOnIdle { browserController.stopLoading() }
            composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
                !browserController.selectedTab.isLoading
            }
            server.releaseDocument()
            server.releaseResource()
            composeRule.runOnIdle {
                assertFalse(browserController.selectedTab.isLoading)
                assertTrue(browserController.selectedTab.progress in 0..100)
            }
        }
    }

    private fun awaitIntermediateProgress(
        browserController: BrowserController,
        server: SlowResourceServer,
        expectedRequests: Int,
    ) {
        val observed = AtomicReference<BrowserTab?>()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            val tab = browserController.selectedTab
            val hasIntermediateProgress = server.documentRequests.get() >= expectedRequests &&
                tab.isLoading && tab.progress in 1..99
            if (hasIntermediateProgress) observed.set(tab)
            hasIntermediateProgress
        }
        val tab = requireNotNull(observed.get())
        assertEquals(server.url, tab.url)
        assertTrue(tab.isLoading)
        assertTrue(tab.progress in 1..99)
    }

    private fun awaitCompletion(browserController: BrowserController) {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            !browserController.selectedTab.isLoading && browserController.selectedTab.progress == 100
        }
        composeRule.runOnIdle {
            assertEquals("Slow progress fixture", browserController.selectedTab.title)
        }
    }

    private class SlowResourceServer : Closeable {
        private val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        private val documentPermits = Semaphore(0)
        private val resourcePermits = Semaphore(0)
        val documentRequests = AtomicInteger()
        val resourceRequests = AtomicInteger()
        val url = "http://127.0.0.1:${server.localPort}/index.html"
        private val thread = Thread({
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (_: SocketException) {
                    break
                }
                Thread({
                    socket.use {
                        val reader = it.getInputStream().bufferedReader()
                        val path = reader.readLine()?.split(' ')?.getOrNull(1).orEmpty()
                        while (!reader.readLine().isNullOrEmpty()) Unit
                        val image = path.startsWith("/slow.svg")
                        val document = path.startsWith("/index.html")
                        if (image) {
                            resourceRequests.incrementAndGet()
                            if (!resourcePermits.tryAcquire(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                                return@use
                            }
                        }
                        val body = when {
                            image -> SVG_BODY
                            document -> HTML_PREFIX + HTML_SUFFIX
                            else -> ""
                        }.toByteArray()
                        val type = if (image) "image/svg+xml" else "text/html"
                        val headers = listOf(
                            "HTTP/1.1 200 OK",
                            "Content-Type: $type",
                            "Cache-Control: no-store",
                            "Content-Length: ${body.size}",
                            "Connection: close",
                            "",
                            "",
                        ).joinToString("\r\n")
                        it.getOutputStream().apply {
                            write(headers.toByteArray())
                            if (document) {
                                write(HTML_PREFIX.toByteArray())
                                flush()
                                documentRequests.incrementAndGet()
                                val documentReleased = documentPermits.tryAcquire(
                                    TIMEOUT_MILLIS,
                                    TimeUnit.MILLISECONDS,
                                )
                                if (!documentReleased) return@use
                                write(HTML_SUFFIX.toByteArray())
                            } else {
                                write(body)
                            }
                            flush()
                        }
                    }
                }, "loading-progress-response").apply { isDaemon = true }.start()
            }
        }, "loading-progress-fixture").apply {
            isDaemon = true
            start()
        }

        fun releaseDocument() = documentPermits.release()

        fun releaseResource() = resourcePermits.release()

        override fun close() {
            documentPermits.release(10)
            resourcePermits.release(10)
            server.close()
            thread.join(1_000L)
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 20_000L
        val HTML_PREFIX =
            "<html><head><title>Slow progress fixture</title></head><body><p>Loading" +
                " ".repeat(8_192) + "</p>"
        const val HTML_SUFFIX = "<img src=\"/slow.svg\"/></body></html>"
        const val SVG_BODY =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\">" +
                "<rect width=\"10\" height=\"10\"/></svg>"
    }
}
