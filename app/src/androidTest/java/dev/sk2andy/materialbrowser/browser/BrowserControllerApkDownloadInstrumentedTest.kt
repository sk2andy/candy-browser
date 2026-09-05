package dev.sk2andy.materialbrowser.browser

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserControllerApkDownloadInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private var controller: BrowserController? = null
    private val downloadUrls = mutableSetOf<String>()

    @After
    fun tearDown() {
        activityRule.scenario.onActivity { activity ->
            controller?.destroy()
            controller = null
            val downloadIds = downloadUrls.flatMap { url ->
                downloadRows(url).map(DownloadRow::id)
            }
            if (downloadIds.isNotEmpty()) {
                activity.getSystemService(DownloadManager::class.java)
                    .remove(*downloadIds.toLongArray())
            }
            downloadUrls.clear()
            activity.getSharedPreferences(
                BrowserSessionStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
        }
    }

    @Test
    fun externalPreviewRoutesTappedApkBeforeWebViewNavigation() {
        val apkUrl = "https://downloads.example.invalid/Candy-${System.nanoTime()}.apk"
        downloadUrls += apkUrl

        activityRule.scenario.onActivity { activity ->
            activity.getSharedPreferences(
                BrowserSessionStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
            val browserController = BrowserController(activity).also { controller = it }
            assertTrue(browserController.openExternalLinkPreview(SOURCE_URL))
            val preview = requireNotNull(browserController.externalLinkPreviewState)
            assertTrue(browserController.prepareExternalLinkPreview(preview.sessionId))
            val previewWebView = requireNotNull(
                browserController.externalLinkPreviewWebViewForTesting(),
            )

            val handled = previewWebView.webViewClient.shouldOverrideUrlLoading(
                previewWebView,
                TestWebResourceRequest(
                    url = apkUrl,
                    hasGesture = true,
                ),
            )

            assertTrue(handled)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val rows = downloadRows(apkUrl)
        assertEquals(1, rows.size)
        assertTrue(rows.single().title.endsWith(".apk", ignoreCase = true))
        activityRule.scenario.onActivity {
            assertNull(controller?.externalLinkPreviewState)
        }
    }

    @Test
    fun externalPreviewKeepsDownloadAuthorizationAcrossRedirect() {
        val apkUrl = "https://downloads.example.invalid/Candy-${System.nanoTime()}.apk"
        downloadUrls += apkUrl

        activityRule.scenario.onActivity { activity ->
            activity.getSharedPreferences(
                BrowserSessionStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
            val browserController = BrowserController(activity).also { controller = it }
            assertTrue(browserController.openExternalLinkPreview(SOURCE_URL))
            val preview = requireNotNull(browserController.externalLinkPreviewState)
            assertTrue(browserController.prepareExternalLinkPreview(preview.sessionId))
            val previewWebView = requireNotNull(
                browserController.externalLinkPreviewWebViewForTesting(),
            )
            val client = previewWebView.webViewClient

            assertFalse(
                client.shouldOverrideUrlLoading(
                    previewWebView,
                    TestWebResourceRequest(
                        url = "https://downloads.example.invalid/latest",
                        hasGesture = true,
                    ),
                ),
            )
            assertTrue(
                client.shouldOverrideUrlLoading(
                    previewWebView,
                    TestWebResourceRequest(
                        url = apkUrl,
                        hasGesture = false,
                        isRedirect = true,
                    ),
                ),
            )
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val rows = downloadRows(apkUrl)
        assertEquals(1, rows.size)
        activityRule.scenario.onActivity {
            assertNull(controller?.externalLinkPreviewState)
        }
    }

    @Test
    fun realPreviewTapRoutesRedirectedServerDeclaredApkDownload() {
        LocalDownloadServer().use { server ->
            downloadUrls += server.downloadUrl
            lateinit var previewWebView: WebView
            activityRule.scenario.onActivity { activity ->
                activity.getSharedPreferences(
                    BrowserSessionStore.PREFERENCES_NAME,
                    Context.MODE_PRIVATE,
                ).edit().clear().commit()
                val container = FrameLayout(activity)
                activity.setContentView(container)
                val browserController = BrowserController(activity).also { controller = it }
                browserController.onStart()
                browserController.onResume()
                assertTrue(browserController.openExternalLinkPreview(server.startUrl))
                val preview = requireNotNull(browserController.externalLinkPreviewState)
                assertTrue(browserController.prepareExternalLinkPreview(preview.sessionId))
                previewWebView = requireNotNull(
                    browserController.externalLinkPreviewWebViewForTesting(),
                )
                browserController.attachExternalLinkPreview(container)
            }
            awaitPreviewLoaded(server.startUrl)

            tapDownloadLink(previewWebView)

            val rows = awaitDownloadRows(server.downloadUrl)
            assertEquals(1, rows.size)
            assertEquals(server.fileName, rows.single().title)
            assertEquals(ANDROID_PACKAGE_MIME_TYPE, rows.single().mimeType)
            awaitPreviewDismissed()
        }
    }

    @Test
    fun staleRegularWebViewCannotRouteApkDownload() {
        val apkUrl = "https://downloads.example.invalid/Stale-${System.nanoTime()}.apk"
        downloadUrls += apkUrl

        activityRule.scenario.onActivity { activity ->
            activity.getSharedPreferences(
                BrowserSessionStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
            val browserController = BrowserController(activity).also { controller = it }
            val staleTabId = browserController.selectedTabId
            val staleWebView = browserController.selectedWebViewForTesting()
            val staleClient = staleWebView.webViewClient
            browserController.createTab()
            browserController.closeTab(staleTabId)

            assertTrue(
                staleClient.shouldOverrideUrlLoading(
                    staleWebView,
                    TestWebResourceRequest(
                        url = apkUrl,
                        hasGesture = true,
                    ),
                ),
            )
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertTrue(downloadRows(apkUrl).isEmpty())
    }

    private fun awaitPreviewLoaded(expectedUrl: String) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            var loaded = false
            activityRule.scenario.onActivity {
                val state = controller?.externalLinkPreviewState
                loaded = state?.currentUrl == expectedUrl && !state.isLoading
            }
            if (loaded) return
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        throw AssertionError("External preview did not load $expectedUrl")
    }

    private fun tapDownloadLink(webView: WebView) {
        val location = IntArray(2)
        activityRule.scenario.onActivity {
            webView.getLocationOnScreen(location)
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            MotionEvent.obtain(
                downTime,
                SystemClock.uptimeMillis(),
                action,
                location[0] + TAP_COORDINATE,
                location[1] + TAP_COORDINATE,
                0,
            ).let { event ->
                instrumentation.sendPointerSync(event)
                event.recycle()
            }
        }
        instrumentation.waitForIdleSync()
    }

    private fun awaitDownloadRows(url: String): List<DownloadRow> {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            downloadRows(url).takeIf(List<DownloadRow>::isNotEmpty)?.let { return it }
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        throw AssertionError("DownloadManager did not receive $url")
    }

    private fun awaitPreviewDismissed() {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            var dismissed = false
            activityRule.scenario.onActivity {
                dismissed = controller?.externalLinkPreviewState == null
            }
            if (dismissed) return
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        throw AssertionError("External preview was not dismissed")
    }

    private fun downloadRows(url: String): List<DownloadRow> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(DownloadManager::class.java)
        return manager.query(DownloadManager.Query()).use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val mediaTypeColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE)
            val titleColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
            val uriColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)
            buildList {
                while (cursor.moveToNext()) {
                    if (cursor.getString(uriColumn) == url) {
                        add(
                            DownloadRow(
                                id = cursor.getLong(idColumn),
                                mimeType = cursor.getString(mediaTypeColumn),
                                title = cursor.getString(titleColumn),
                            ),
                        )
                    }
                }
            }
        }
    }

    private data class DownloadRow(
        val id: Long,
        val mimeType: String,
        val title: String,
    )

    private class TestWebResourceRequest(
        url: String,
        private val hasGesture: Boolean,
        private val isRedirect: Boolean = false,
    ) : WebResourceRequest {
        private val uri = Uri.parse(url)

        override fun getUrl(): Uri = uri

        override fun isForMainFrame(): Boolean = true

        override fun isRedirect(): Boolean = isRedirect

        override fun hasGesture(): Boolean = hasGesture

        override fun getMethod(): String = "GET"

        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }

    private companion object {
        const val SOURCE_URL = "https://example.invalid/source"
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_INTERVAL_MILLIS = 50L
        const val TAP_COORDINATE = 100f
        const val ANDROID_PACKAGE_MIME_TYPE = "application/vnd.android.package-archive"
    }

    private class LocalDownloadServer : Closeable {
        private val serverSocket = ServerSocket(
            0,
            8,
            InetAddress.getByName("127.0.0.1"),
        )
        private val serverThread = Thread({ serve() }, "candy-apk-download-test-server").apply {
            isDaemon = true
            start()
        }

        private val baseUrl = "http://127.0.0.1:${serverSocket.localPort}"
        val startUrl = "$baseUrl/start.html"
        val downloadUrl = "$baseUrl/artifact"
        val fileName = "Candy-${serverSocket.localPort}.apk"

        private fun serve() {
            while (!serverSocket.isClosed) {
                val socket = runCatching { serverSocket.accept() }.getOrNull() ?: return
                socket.use { connection ->
                    connection.soTimeout = 2_000
                    runCatching {
                        val input = connection.getInputStream()
                        val requestBytes = ByteArrayOutputStream()
                        var matchedHeaderBytes = 0
                        while (matchedHeaderBytes < HTTP_HEADER_END.size) {
                            val next = input.read()
                            if (next < 0) break
                            requestBytes.write(next)
                            matchedHeaderBytes = if (
                                next == HTTP_HEADER_END[matchedHeaderBytes].toInt()
                            ) {
                                matchedHeaderBytes + 1
                            } else {
                                0
                            }
                        }
                        val requestPath = requestBytes.toString(Charsets.US_ASCII.name())
                            .lineSequence()
                            .firstOrNull()
                            ?.split(' ')
                            ?.getOrNull(1)
                        connection.getOutputStream().use { output ->
                            when (requestPath) {
                                "/start.html" -> {
                                    val body = (
                                        "<!doctype html><html><body>" +
                                            "<a href=\"$baseUrl/redirect\" " +
                                            "style=\"display:block;width:200px;height:200px\">" +
                                            "Download</a></body></html>"
                                        ).toByteArray(Charsets.UTF_8)
                                    output.write(
                                        (
                                            "HTTP/1.1 200 OK\r\n" +
                                                "Content-Type: text/html; charset=utf-8\r\n" +
                                                "Content-Length: ${body.size}\r\n" +
                                                "Cache-Control: no-store\r\n" +
                                                "Connection: close\r\n\r\n"
                                            ).toByteArray(Charsets.US_ASCII),
                                    )
                                    output.write(body)
                                }
                                "/redirect" -> output.write(
                                    (
                                        "HTTP/1.1 302 Found\r\n" +
                                            "Location: $downloadUrl\r\n" +
                                            "Content-Length: 0\r\n" +
                                            "Cache-Control: no-store\r\n" +
                                            "Connection: close\r\n\r\n"
                                        ).toByteArray(Charsets.US_ASCII),
                                )
                                "/artifact" -> {
                                    val body = "not-a-real-apk".toByteArray(Charsets.UTF_8)
                                    output.write(
                                        (
                                            "HTTP/1.1 200 OK\r\n" +
                                                "Content-Type: application/octet-stream\r\n" +
                                                "Content-Disposition: attachment; " +
                                                "filename=\"$fileName\"\r\n" +
                                                "Content-Length: ${body.size}\r\n" +
                                                "Cache-Control: no-store\r\n" +
                                                "Connection: close\r\n\r\n"
                                            ).toByteArray(Charsets.US_ASCII),
                                    )
                                    output.write(body)
                                }
                                else -> output.write(
                                    (
                                        "HTTP/1.1 404 Not Found\r\n" +
                                            "Content-Length: 0\r\n" +
                                            "Connection: close\r\n\r\n"
                                        ).toByteArray(Charsets.US_ASCII),
                                )
                            }
                            output.flush()
                        }
                    }
                }
            }
        }

        override fun close() {
            serverSocket.close()
            serverThread.join(2_000L)
        }

        private companion object {
            val HTTP_HEADER_END = byteArrayOf(13, 10, 13, 10)
        }
    }
}
