package dev.sk2andy.materialbrowser.browser.systemwebview

import android.provider.MediaStore
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.engine.BrowserEngineContentKind
import dev.sk2andy.materialbrowser.browser.gecko.AndroidBrowserEngineSessionPort
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEngineEventSink
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadFailure
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadResponseListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadTransferListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadTransferStart
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommands
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class SystemWebViewBlobDownloadInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var transfer: SystemWebViewBlobDownloadTransfer? = null
    private var factory: SystemWebViewBrowserEngineFactory? = null
    private var session: AndroidBrowserEngineSessionPort? = null
    private var popupServer: BlobPageServer? = null
    private var webView: WebView? = null
    private var popupFileName: String? = null
    private val fileName = "candy-blob-${System.nanoTime()}.jpg"

    @Before
    fun setUp() {
        deleteDownload()
    }

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            transfer?.close()
            session?.execute(BrowserEngineCommands.close())
            factory?.shutdown()
            if (session == null) webView?.destroy()
        }
        deleteDownload()
        popupFileName?.let(::deleteDownload)
        popupServer?.close()
    }

    @Test
    fun userOpenedBlobPopupDownloadsThroughOwningPage() {
        val server = BlobPageServer().also { popupServer = it }
        val pageLoaded = CountDownLatch(1)
        val started = AtomicReference<GeckoDownloadTransferStart>()
        val failed = AtomicReference<GeckoDownloadFailure>()
        val responses = AtomicInteger()
        val completed = CountDownLatch(1)
        composeRule.runOnIdle {
            val createdFactory = SystemWebViewBrowserEngineFactory(composeRule.activity)
                .also { factory = it }
            val createdSession = createdFactory.create(
                tabId = "popup-blob-test",
                profileId = "default",
                isPrivate = false,
                contentKind = BrowserEngineContentKind.RegularTab,
                eventSink = BrowserEngineEventSink { event ->
                    if (event.type == BrowserEngineEventType.NavigationCommitted) {
                        pageLoaded.countDown()
                    }
                },
            ).also { session = it }
            createdSession.setDownloadResponseListener(GeckoDownloadResponseListener { response ->
                responses.incrementAndGet()
                response.start(object : GeckoDownloadTransferListener {
                    override fun onStarted(start: GeckoDownloadTransferStart) {
                        started.set(start)
                        popupFileName = start.fileName
                    }

                    override fun onComplete(bytesReceived: Long) {
                        completed.countDown()
                    }

                    override fun onFailed(reason: GeckoDownloadFailure) {
                        failed.set(reason)
                        completed.countDown()
                    }
                })
            })
            val host = createdSession.createView(composeRule.activity)
            val testedWebView = requireNotNull(host.findWebView()).also { webView = it }
            composeRule.activity.setContentView(host)
            testedWebView.loadUrl(server.url)
        }
        assertTrue("page did not load", pageLoaded.await(10, TimeUnit.SECONDS))

        val location = IntArray(2)
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            var laidOut = false
            composeRule.runOnIdle {
                laidOut = (webView?.width ?: 0) > 0 && (webView?.height ?: 0) > 0
            }
            laidOut
        }
        composeRule.runOnIdle {
            requireNotNull(webView).let { view ->
                view.getLocationOnScreen(location)
                location[0] += view.width / 2
                location[1] += view.height / 2
            }
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            MotionEvent.obtain(
                downTime,
                SystemClock.uptimeMillis(),
                action,
                location[0].toFloat(),
                location[1].toFloat(),
                0,
            ).let { event ->
                instrumentation.sendPointerSync(event)
                event.recycle()
            }
        }
        instrumentation.waitForIdleSync()
        val clicked = AtomicReference<String>()
        val clickChecked = CountDownLatch(1)
        composeRule.runOnIdle {
            webView?.evaluateJavascript("Boolean(globalThis.clicked)") { result ->
                clicked.set(result)
                clickChecked.countDown()
            }
        }
        assertTrue("click state was not read", clickChecked.await(5, TimeUnit.SECONDS))
        assertEquals("button click was not delivered", "true", clicked.get())
        assertTrue("popup blob download did not finish", completed.await(10, TimeUnit.SECONDS))

        assertNull(failed.get())
        assertEquals(1, responses.get())
        val download = requireNotNull(started.get())
        assertEquals("image/jpeg", download.mimeType)
        val stored = requireNotNull(queryDownload(download.fileName))
        assertEquals("image/jpeg", stored.mimeType)
        assertArrayEquals(byteArrayOf(-1, -40, -1, -39), stored.bytes)
    }

    @Test
    fun generatedJpegBlobStreamsFromPageContext() {
        val pageLoaded = CountDownLatch(1)
        lateinit var testedTransfer: SystemWebViewBlobDownloadTransfer
        lateinit var testedWebView: WebView
        composeRule.runOnIdle {
            testedWebView = WebView(composeRule.activity).also { view ->
                webView = view
                view.settings.javaScriptEnabled = true
                view.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        pageLoaded.countDown()
                    }
                }
            }
            testedTransfer = SystemWebViewBlobDownloadTransfer(
                composeRule.activity,
                testedWebView,
            ).also { transfer = it }
            composeRule.activity.addContentView(
                testedWebView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            testedWebView.loadDataWithBaseURL(
                PAGE_URL,
                """
                    <html><body><script>
                      globalThis.candyBlobUrl = URL.createObjectURL(new Blob(
                        [new Uint8Array([255, 216, 255, 217])],
                        { type: 'image/jpeg' }
                      ));
                    </script></body></html>
                """.trimIndent(),
                "text/html",
                "utf-8",
                null,
            )
        }
        assertTrue("page did not load", pageLoaded.await(10, TimeUnit.SECONDS))

        val blobUrl = AtomicReference<String>()
        val blobCreated = CountDownLatch(1)
        composeRule.runOnIdle {
            testedWebView.evaluateJavascript("globalThis.candyBlobUrl") { result ->
                blobUrl.set(result.removeSurrounding("\""))
                blobCreated.countDown()
            }
        }
        assertTrue("blob URL was not created", blobCreated.await(5, TimeUnit.SECONDS))

        val started = AtomicReference<GeckoDownloadTransferStart>()
        val failed = AtomicReference<GeckoDownloadFailure>()
        val completed = CountDownLatch(1)
        composeRule.runOnIdle {
            testedTransfer.start(
                blobUrl = blobUrl.get(),
                pageUrl = PAGE_URL,
                contentDisposition = "attachment; filename=\"$fileName\"",
                mimeType = "image/jpeg",
                referrer = PAGE_URL,
                listener = object : GeckoDownloadTransferListener {
                    override fun onStarted(start: GeckoDownloadTransferStart) {
                        started.set(start)
                    }

                    override fun onComplete(bytesReceived: Long) {
                        completed.countDown()
                    }

                    override fun onFailed(reason: GeckoDownloadFailure) {
                        failed.set(reason)
                        completed.countDown()
                    }
                },
            )
        }
        assertTrue("blob download did not finish", completed.await(10, TimeUnit.SECONDS))

        assertNull(failed.get())
        assertEquals(fileName, started.get().fileName)
        assertEquals("image/jpeg", started.get().mimeType)
        val stored = requireNotNull(queryDownload())
        assertEquals("image/jpeg", stored.mimeType)
        assertArrayEquals(byteArrayOf(-1, -40, -1, -39), stored.bytes)
    }

    private fun queryDownload(name: String = fileName): StoredDownload? {
        val resolver = composeRule.activity.contentResolver
        return resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.MIME_TYPE),
            "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.IS_PENDING} = 0",
            arrayOf(name),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val uri = android.content.ContentUris.withAppendedId(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                cursor.getLong(0),
            )
            StoredDownload(
                mimeType = cursor.getString(1),
                bytes = resolver.openInputStream(uri)?.use { input -> input.readBytes() }
                    ?: return@use null,
            )
        }
    }

    private fun deleteDownload(name: String = fileName) {
        composeRule.activity.contentResolver.delete(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(name),
        )
    }

    private fun View.findWebView(): WebView? = when (this) {
        is WebView -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { index ->
            getChildAt(index).findWebView()
        }
        else -> null
    }

    private data class StoredDownload(
        val mimeType: String,
        val bytes: ByteArray,
    )

    private class BlobPageServer : Closeable {
        private val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        private val thread = Thread(::serve, "system-webview-blob-popup-fixture").apply {
            isDaemon = true
            start()
        }
        val url = "http://127.0.0.1:${server.localPort}/image"

        private fun serve() {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: return
                socket.use { connection ->
                    runCatching {
                        connection.getInputStream().bufferedReader().apply {
                            readLine()
                            while (!readLine().isNullOrEmpty()) {
                                // Drain request headers before returning the fixture page.
                            }
                        }
                        val body = """
                            <html><body style="margin:0">
                              <button style="position:fixed;inset:0;width:100vw;height:100vh" onclick="
                                globalThis.clicked = true;
                                window.open(window.URL.createObjectURL(new Blob(
                                  [new Uint8Array([255, 216, 255, 217])],
                                  { type: 'image/jpeg' }
                                )))
                              ">Save</button>
                            </body></html>
                        """.trimIndent().toByteArray()
                        connection.getOutputStream().buffered().use { output ->
                            output.write(
                                "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n"
                                    .toByteArray(),
                            )
                            output.write(
                                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                                    .toByteArray(),
                            )
                            output.write(body)
                        }
                    }
                }
            }
        }

        override fun close() {
            server.close()
            thread.join(1_000L)
        }
    }

    private companion object {
        const val PAGE_URL = "https://blob-download.test/"
    }
}
