package dev.sk2andy.materialbrowser.browser.systemwebview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Base64
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
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.sk2andy.materialbrowser.browser.engine.BrowserEngineContentKind
import dev.sk2andy.materialbrowser.browser.gecko.AndroidBrowserEngineSessionPort
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEngineEventSink
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadCancellation
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadFailure
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadResponseListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadTransferListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadTransferStart
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommands
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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
    private var otherProfileWebView: WebView? = null
    private val profileNames = mutableSetOf<String>()
    private var popupFileName: String? = null
    private val fileName = "candy-blob-${System.nanoTime()}.png"

    @Before
    fun setUp() {
        deleteDownload()
    }

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            webView?.let(::detachWebView)
            otherProfileWebView?.let(::detachWebView)
            transfer?.close()
            session?.execute(BrowserEngineCommands.close())
            factory?.shutdown()
            if (session == null) webView?.destroy()
            otherProfileWebView?.destroy()
            profileNames.forEach { name ->
                runCatching { ProfileStore.getInstance().deleteProfile(name) }
            }
        }
        deleteDownload()
        popupFileName?.let(::deleteDownload)
        popupServer?.close()
    }

    @Test
    fun userOpenedBlobPopupDownloadsThroughOwningPage() = assertPopupDownload()

    @Test
    fun userOpenedBlobPopupWithSelfOnlyConnectionsDownloadsThroughOwningPage() =
        assertPopupDownload(
            "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; " +
                "img-src blob:; connect-src 'self'",
        )

    @Test
    fun privateBlobPopupWithSelfOnlyConnectionsDownloadsThroughOwningPage() =
        assertPopupDownload(
            contentSecurityPolicy = "connect-src 'self'",
            isPrivate = true,
        )

    private fun assertPopupDownload(
        contentSecurityPolicy: String? = null,
        isPrivate: Boolean = false,
    ) {
        val server = BlobPageServer(contentSecurityPolicy).also { popupServer = it }
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
                isPrivate = isPrivate,
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

        val fixtureReady = AtomicBoolean(false)
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.runOnIdle {
                webView?.evaluateJavascript(
                    "document.readyState === 'complete' && Boolean(document.querySelector('button'))",
                ) { result -> fixtureReady.set(result == "true") }
            }
            fixtureReady.get()
        }
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
        assertEquals("image/png", download.mimeType)
        assertTrue(download.fileName.endsWith(".png"))
        val stored = requireNotNull(queryDownload(download.fileName))
        assertEquals("image/png", stored.mimeType)
        assertImageContent(stored.bytes)
    }

    @Test
    fun generatedPngBlobStreamsFromPageContext() {
        val source = createBlobSource()
        assertSuccessfulDownload(downloadBlob(source))
    }

    @Test
    fun generatedPngLargerThanTwoChunksPreservesCompleteImage() {
        val random = Random(248)
        val pixels = IntArray(256 * 256) {
            Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
        }.apply {
            this[0] = Color.RED
            this[1] = Color.GREEN
        }
        val bitmap = Bitmap.createBitmap(pixels, 256, 256, Bitmap.Config.ARGB_8888)
        val imageBytes = try {
            ByteArrayOutputStream().use { output ->
                assertTrue(
                    "fixture PNG encoding failed",
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output),
                )
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
        assertTrue("fixture must require more than two chunks", imageBytes.size > 48 * 1_024)
        val source = createBlobSource(
            contentSecurityPolicy = "connect-src 'self'",
            imageBytes = imageBytes,
        )

        assertSuccessfulDownload(
            result = downloadBlob(source),
            expectedBytes = imageBytes,
            expectedWidth = 256,
            expectedHeight = 256,
        )
    }

    @Test
    fun isolatedProfileBlobWithSelfOnlyConnectionsDownloadsWithOriginalProfile() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        val source = createBlobSource(
            profileName = "candy-blob-source-${System.nanoTime()}",
            contentSecurityPolicy = "connect-src 'self'",
        )
        assertSuccessfulDownload(downloadBlob(source))
    }

    @Test
    fun blobFromDifferentProfileFailsWithoutCreatingDownload() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        val source = createBlobSource(profileName = "candy-blob-source-${System.nanoTime()}")
        val otherSource = createBlobSource(profileName = "candy-blob-other-${System.nanoTime()}")

        val result = downloadBlob(otherSource, blobUrl = source.url)

        assertEquals(GeckoDownloadFailure.Network, result.failure)
        assertNull(result.start)
        assertFalse("failed transfer left a MediaStore row", downloadRowExists())
    }

    @Test
    fun revokedBlobFailsWithoutCreatingDownload() {
        val source = createBlobSource()
        val revoked = CountDownLatch(1)
        composeRule.runOnIdle {
            source.view.evaluateJavascript("URL.revokeObjectURL(globalThis.candyBlobUrl)") {
                revoked.countDown()
            }
        }
        assertTrue("blob URL was not revoked", revoked.await(5, TimeUnit.SECONDS))

        val result = downloadBlob(source)

        assertEquals(GeckoDownloadFailure.Network, result.failure)
        assertNull(result.start)
        assertFalse("revoked blob left a MediaStore row", downloadRowExists())
    }

    @Test
    fun cancellationAfterStartRemovesPendingDownload() {
        val source = createBlobSource(contentSecurityPolicy = "connect-src 'self'")

        val result = downloadBlob(source, cancelOnStart = true)

        assertEquals(GeckoDownloadFailure.Cancelled, result.failure)
        assertEquals(fileName, requireNotNull(result.start).fileName)
        assertNull(result.completedBytes)
        assertFalse("cancelled transfer left a MediaStore row", downloadRowExists())
    }

    @Test
    fun closingPendingDownloadDestroysIsolatedHelperSynchronously() =
        assertPendingDownloadDestroysHelper("candy-blob-source-${System.nanoTime()}")

    @Test
    fun closingPendingDownloadDestroysPrivateHelperSynchronously() =
        assertPendingDownloadDestroysHelper("$INCOGNITO_WEBVIEW_PROFILE_PREFIX${System.nanoTime()}")

    private fun assertPendingDownloadDestroysHelper(profileName: String) {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        val source = createBlobSource(
            profileName = profileName,
            contentSecurityPolicy = "connect-src 'self'",
        )
        val helpersCreated = AtomicInteger()
        val helpersDestroyed = AtomicInteger()
        composeRule.runOnIdle {
            val testedTransfer = SystemWebViewBlobDownloadTransfer(
                composeRule.activity,
                source.view,
                helperFactory = { context ->
                    helpersCreated.incrementAndGet()
                    object : WebView(context) {
                        override fun destroy() {
                            helpersDestroyed.incrementAndGet()
                            super.destroy()
                        }
                    }
                },
            ).also { transfer = it }
            assertNull(queryDownload())
            requireNotNull(
                testedTransfer.start(
                    blobUrl = source.url,
                    pageUrl = PAGE_URL,
                    contentDisposition = "attachment; filename=\"$fileName\"",
                    mimeType = "image/png",
                    referrer = PAGE_URL,
                    listener = object : GeckoDownloadTransferListener {
                        override fun onStarted(start: GeckoDownloadTransferStart) = Unit
                        override fun onComplete(bytesReceived: Long) = Unit
                        override fun onFailed(reason: GeckoDownloadFailure) = Unit
                    },
                ),
            )
            assertEquals(1, helpersCreated.get())
            assertEquals(0, helpersDestroyed.get())
            testedTransfer.close()
            assertEquals("pending helper was not destroyed synchronously", 1, helpersDestroyed.get())
            testedTransfer.close()
            assertEquals("repeated close destroyed the helper twice", 1, helpersDestroyed.get())
            detachWebView(source.view)
            source.view.destroy()
            webView = null
        }
        assertFalse("closed transfer left a MediaStore row", downloadRowExists())
    }

    private fun createBlobSource(
        profileName: String? = null,
        contentSecurityPolicy: String? = null,
        imageBytes: ByteArray = Base64.decode(IMAGE_BASE64, Base64.DEFAULT),
    ): BlobSource {
        val pageLoaded = CountDownLatch(1)
        val imageBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        lateinit var sourceView: WebView
        composeRule.runOnIdle {
            sourceView = WebView(composeRule.activity).also { view ->
                if (webView == null) webView = view else otherProfileWebView = view
                if (profileName != null) {
                    profileNames.add(profileName)
                    WebViewCompat.setProfile(view, profileName)
                    assertEquals(profileName, WebViewCompat.getProfile(view).name)
                }
                view.settings.javaScriptEnabled = true
                view.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        pageLoaded.countDown()
                    }
                }
            }
            composeRule.activity.addContentView(
                sourceView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            val policy = contentSecurityPolicy?.let {
                "<meta http-equiv=\"Content-Security-Policy\" content=\"$it\">"
            }.orEmpty()
            sourceView.loadDataWithBaseURL(
                PAGE_URL,
                """
                    <html><head>$policy</head><body><script>
                      globalThis.candyBlobUrl = URL.createObjectURL(new Blob(
                        [Uint8Array.from(atob('$imageBase64'), value => value.charCodeAt(0))],
                        { type: 'image/png' }
                      ));
                    </script></body></html>
                """.trimIndent(),
                "text/html",
                "utf-8",
                null,
            )
        }
        assertTrue("source page did not load", pageLoaded.await(10, TimeUnit.SECONDS))
        val blobCreated = CountDownLatch(1)
        val blobUrl = AtomicReference<String>()
        composeRule.runOnIdle {
            sourceView.evaluateJavascript("globalThis.candyBlobUrl") { result ->
                blobUrl.set(result.removeSurrounding("\""))
                blobCreated.countDown()
            }
        }
        assertTrue("blob URL was not read", blobCreated.await(5, TimeUnit.SECONDS))
        assertTrue("source did not create a blob URL", blobUrl.get().startsWith("blob:"))
        return BlobSource(sourceView, blobUrl.get())
    }

    private fun downloadBlob(
        source: BlobSource,
        blobUrl: String = source.url,
        cancelOnStart: Boolean = false,
    ): DownloadResult {
        val started = AtomicReference<GeckoDownloadTransferStart>()
        val failed = AtomicReference<GeckoDownloadFailure>()
        val completedBytes = AtomicReference<Long>()
        val completed = CountDownLatch(1)
        val cancellation = AtomicReference<GeckoDownloadCancellation>()
        composeRule.runOnIdle {
            val testedTransfer = SystemWebViewBlobDownloadTransfer(
                composeRule.activity,
                source.view,
            ).also { transfer = it }
            cancellation.set(
                testedTransfer.start(
                    blobUrl = blobUrl,
                    pageUrl = PAGE_URL,
                    contentDisposition = "attachment; filename=\"$fileName\"",
                    mimeType = "image/png",
                    referrer = PAGE_URL,
                    listener = object : GeckoDownloadTransferListener {
                        override fun onStarted(start: GeckoDownloadTransferStart) {
                            started.set(start)
                            if (cancelOnStart) requireNotNull(cancellation.get()).cancel()
                        }

                        override fun onComplete(bytesReceived: Long) {
                            completedBytes.set(bytesReceived)
                            completed.countDown()
                        }

                        override fun onFailed(reason: GeckoDownloadFailure) {
                            failed.set(reason)
                            completed.countDown()
                        }
                    },
                ),
            )
        }
        assertTrue("blob download did not finish", completed.await(15, TimeUnit.SECONDS))
        return DownloadResult(started.get(), failed.get(), completedBytes.get())
    }

    private fun assertSuccessfulDownload(
        result: DownloadResult,
        expectedBytes: ByteArray = Base64.decode(IMAGE_BASE64, Base64.DEFAULT),
        expectedWidth: Int = 2,
        expectedHeight: Int = 1,
    ) {
        assertNull(result.failure)
        val start = requireNotNull(result.start)
        assertEquals(fileName, start.fileName)
        assertEquals("image/png", start.mimeType)
        assertEquals(
            expectedBytes.size.toLong(),
            requireNotNull(result.completedBytes),
        )
        val stored = requireNotNull(queryDownload())
        assertEquals("image/png", stored.mimeType)
        assertImageContent(
            bytes = stored.bytes,
            expectedBytes = expectedBytes,
            expectedWidth = expectedWidth,
            expectedHeight = expectedHeight,
        )
    }

    private fun downloadRowExists(): Boolean = composeRule.activity.contentResolver.query(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Downloads._ID),
        "${MediaStore.Downloads.DISPLAY_NAME} = ?",
        arrayOf(fileName),
        null,
    )?.use { it.moveToFirst() } ?: false

    private data class BlobSource(val view: WebView, val url: String)

    private data class DownloadResult(
        val start: GeckoDownloadTransferStart?,
        val failure: GeckoDownloadFailure?,
        val completedBytes: Long?,
    )

    private fun assertImageContent(
        bytes: ByteArray,
        expectedBytes: ByteArray = Base64.decode(IMAGE_BASE64, Base64.DEFAULT),
        expectedWidth: Int = 2,
        expectedHeight: Int = 1,
    ) {
        assertArrayEquals(expectedBytes, bytes)
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        try {
            assertEquals(expectedWidth, bitmap.width)
            assertEquals(expectedHeight, bitmap.height)
            assertEquals(Color.RED, bitmap.getPixel(0, 0))
            assertEquals(Color.GREEN, bitmap.getPixel(1, 0))
        } finally {
            bitmap.recycle()
        }
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

    private fun detachWebView(view: WebView) {
        (view.parent as? ViewGroup)?.removeView(view)
    }

    private data class StoredDownload(
        val mimeType: String,
        val bytes: ByteArray,
    )

    private class BlobPageServer(private val contentSecurityPolicy: String? = null) : Closeable {
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
                                  [Uint8Array.from(atob('$IMAGE_BASE64'), value => value.charCodeAt(0))],
                                  { type: 'image/png' }
                                )))
                              ">Save</button>
                            </body></html>
                        """.trimIndent().toByteArray()
                        connection.getOutputStream().buffered().use { output ->
                            output.write(
                                "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n"
                                    .toByteArray(),
                            )
                            contentSecurityPolicy?.let { policy ->
                                output.write("Content-Security-Policy: $policy\r\n".toByteArray())
                            }
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
        const val IMAGE_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAIAAAABCAIAAAB7QOjdAAAAD0lEQVR4nGP4z8DA8J8BAAf/Af8Bf4mnAAAAAElFTkSuQmCC"
    }
}
