package dev.sk2andy.materialbrowser.browser.systemwebview

import android.provider.MediaStore
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadFailure
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadTransferListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadTransferStart
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    private var webView: WebView? = null
    private val fileName = "candy-blob-${System.nanoTime()}.jpg"

    @Before
    fun setUp() {
        deleteDownload()
    }

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            transfer?.close()
            webView?.destroy()
        }
        deleteDownload()
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

    private fun queryDownload(): StoredDownload? {
        val resolver = composeRule.activity.contentResolver
        return resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.MIME_TYPE),
            "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.IS_PENDING} = 0",
            arrayOf(fileName),
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

    private fun deleteDownload() {
        composeRule.activity.contentResolver.delete(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(fileName),
        )
    }

    private data class StoredDownload(
        val mimeType: String,
        val bytes: ByteArray,
    )

    private companion object {
        const val PAGE_URL = "https://blob-download.test/"
    }
}
