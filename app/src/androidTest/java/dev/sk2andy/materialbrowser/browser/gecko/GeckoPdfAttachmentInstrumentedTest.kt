package dev.sk2andy.materialbrowser.browser.gecko

import android.content.ContentUris
import android.content.Context
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the production runtime startup configuration with real Gecko navigation.
 *
 * Without `browser.download.open_pdf_attachments_inline = false`, GeckoView 159 starts the
 * `Content-Disposition: attachment` PDF navigation but never delivers an external response.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoPdfAttachmentInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val savedNames = CopyOnWriteArrayList<String>()

    @After
    fun cleanDownloadedFixtures() {
        savedNames.forEach { name ->
            context.contentResolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf(name),
            )
        }
    }

    @Test
    fun attachmentPdfReachesDownloadDelegateAndStreamsIntoDownloads() {
        PdfFixtureServer().use { server ->
            val session = createSession()
            try {
                val response = AtomicReference<GeckoExternalDownloadResponse>()
                val received = CountDownLatch(1)
                val latest = AtomicReference<GeckoBrowserSessionState>()
                instrumentation.runOnMainSync {
                    session.session.setStateListener(latest::set)
                    session.session.setDownloadResponseListener { download ->
                        response.set(download)
                        received.countDown()
                    }
                    assertTrue(session.session.loadUrl(server.attachmentUrl))
                }
                assertTrue(
                    "Attachment PDF did not reach the download delegate; state=${latest.get()}, " +
                        "requests=${server.requestPaths}",
                    received.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                )
                val download = checkNotNull(response.get())
                assertEquals(server.attachmentUrl, download.metadata.url)
                assertEquals(PDF_MIME_TYPE, download.metadata.mimeType)
                assertEquals(
                    "attachment; filename=\"${server.attachmentFileName}\"",
                    download.metadata.contentDisposition,
                )

                val started = AtomicReference<GeckoDownloadTransferStart>()
                val failure = AtomicReference<GeckoDownloadFailure?>()
                val bytes = AtomicReference<Long>()
                val finished = CountDownLatch(1)
                instrumentation.runOnMainSync {
                    assertNotNull(
                        download.start(
                            object : GeckoDownloadTransferListener {
                                override fun onStarted(start: GeckoDownloadTransferStart) {
                                    started.set(start)
                                    savedNames += start.fileName
                                }

                                override fun onComplete(bytesReceived: Long) {
                                    bytes.set(bytesReceived)
                                    finished.countDown()
                                }

                                override fun onFailed(reason: GeckoDownloadFailure) {
                                    failure.set(reason)
                                    finished.countDown()
                                }
                            },
                        ),
                    )
                }
                assertTrue("PDF transfer did not finish", finished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                assertNull(failure.get())
                assertEquals(server.attachmentFileName, started.get()?.fileName)
                assertEquals(server.pdf.size.toLong(), bytes.get())
                assertArrayEquals(server.pdf, storedDownload(server.attachmentFileName))
                assertEquals(1, server.requestPaths.count { it == ATTACHMENT_PATH })
            } finally {
                close(session)
            }
        }
    }

    @Test
    fun inlinePdfOpensInBrowserWithoutDownload() {
        PdfFixtureServer().use { server ->
            val session = createSession()
            try {
                val downloads = CopyOnWriteArrayList<GeckoExternalDownloadResponse>()
                val latest = AtomicReference<GeckoBrowserSessionState>()
                instrumentation.runOnMainSync {
                    session.session.setStateListener(latest::set)
                    session.session.setDownloadResponseListener { download ->
                        downloads += download
                        download.close()
                    }
                    assertTrue(session.session.loadUrl(server.inlineUrl))
                }
                // pdf.js publishes the document /Title only after it parses and renders the PDF.
                awaitCondition(
                    message = "Inline PDF did not open in Gecko's viewer",
                    details = {
                        "state=${latest.get()}, downloads=${downloads.size}, " +
                            "requests=${server.requestPaths}"
                    },
                ) {
                    val state = latest.get()
                    state?.url == server.inlineUrl &&
                        state.title?.contains(server.title) == true &&
                        !state.isLoading
                }
                assertTrue(downloads.isEmpty())
                assertEquals(true, latest.get().lastNavigationSucceeded)
            } finally {
                close(session)
            }
        }
    }

    private data class Session(val session: GeckoBrowserSession, val view: View)

    private fun createSession(): Session {
        lateinit var result: Session
        instrumentation.runOnMainSync {
            // The production owner writes the startup config file before creating GeckoRuntime.
            val session = GeckoRuntimeOwner.getOrCreate(context).createSession(
                profileId = "pdf-attachment-${UUID.randomUUID()}",
                isPrivate = false,
            )
            result = Session(session, session.createView(context))
        }
        return result
    }

    private fun close(value: Session) {
        instrumentation.runOnMainSync {
            value.session.setDownloadResponseListener(null)
            value.session.setStateListener(null)
            value.session.releaseView(value.view)
            value.session.close()
        }
    }

    private fun awaitCondition(
        message: String,
        details: () -> String,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue("$message; ${details()}", condition())
    }

    private fun storedDownload(name: String): ByteArray? = context.contentResolver.query(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Downloads._ID),
        "${MediaStore.Downloads.DISPLAY_NAME} = ?",
        arrayOf(name),
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) {
            null
        } else {
            context.contentResolver.openInputStream(
                ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0)),
            )?.use { input -> input.readBytes() }
        }
    }

    private class PdfFixtureServer : AutoCloseable {
        private val socket = ServerSocket(0, REQUEST_BACKLOG, InetAddress.getByName("127.0.0.1"))
        private val requests = CopyOnWriteArrayList<String>()
        private val baseUrl = "http://127.0.0.1:${socket.localPort}"
        val attachmentUrl = "$baseUrl$ATTACHMENT_PATH"
        val inlineUrl = "$baseUrl$INLINE_PATH"
        val attachmentFileName = "candy-pdf-attachment-${socket.localPort}.pdf"
        val title = "Candy PDF fixture ${UUID.randomUUID()}"
        val pdf = minimalPdf(title)
        val requestPaths: List<String>
            get() = requests.toList()
        private val thread = Thread(::serve, "gecko-pdf-attachment-fixture").apply {
            isDaemon = true
            start()
        }

        private fun serve() {
            while (!socket.isClosed) {
                val connection = runCatching { socket.accept() }.getOrNull() ?: return
                connection.use { client ->
                    try {
                        client.soTimeout = CLIENT_READ_TIMEOUT_MILLIS
                        val reader = client.getInputStream().bufferedReader()
                        val path = reader.readLine()?.split(' ')?.getOrNull(1) ?: return@use
                        requests += path
                        while (!reader.readLine().isNullOrEmpty()) {
                            // Drain request headers before responding.
                        }
                        val disposition = when (path) {
                            ATTACHMENT_PATH ->
                                "Content-Disposition: attachment; filename=\"$attachmentFileName\"\r\n"
                            INLINE_PATH -> ""
                            else -> null
                        }
                        val output = client.getOutputStream()
                        if (disposition == null) {
                            output.write(
                                "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                                    .toByteArray(StandardCharsets.US_ASCII),
                            )
                        } else {
                            output.write(
                                (
                                    "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: $PDF_MIME_TYPE\r\n" +
                                        disposition +
                                        "Content-Length: ${pdf.size}\r\n" +
                                        "Cache-Control: no-store\r\n" +
                                        "Connection: close\r\n\r\n"
                                    ).toByteArray(StandardCharsets.US_ASCII),
                            )
                            output.write(pdf)
                        }
                        output.flush()
                    } catch (_: IOException) {
                        // Gecko may cancel a response after reading its headers.
                    }
                }
            }
        }

        override fun close() {
            socket.close()
            thread.join(1_000L)
        }
    }

    private companion object {
        const val ATTACHMENT_PATH = "/attachment.pdf"
        const val INLINE_PATH = "/inline.pdf"
        const val PDF_MIME_TYPE = "application/pdf"
        const val REQUEST_BACKLOG = 8
        const val CLIENT_READ_TIMEOUT_MILLIS = 2_000
        const val TIMEOUT_SECONDS = 30L
        const val POLL_MILLIS = 50L

        /** One-page PDF with a document title, so pdf.js rendering is observable as the tab title. */
        fun minimalPdf(title: String): ByteArray {
            val objects = listOf(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Contents 4 0 R >>",
                "<< /Length 0 >>\nstream\n\nendstream",
                "<< /Title ($title) >>",
            )
            val output = ByteArrayOutputStream()
            fun write(value: String) = output.write(value.toByteArray(StandardCharsets.US_ASCII))
            write("%PDF-1.4\n")
            val offsets = objects.mapIndexed { index, body ->
                output.size().also { write("${index + 1} 0 obj\n$body\nendobj\n") }
            }
            val xref = output.size()
            write("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
            offsets.forEach { offset -> write("%010d 00000 n \n".format(offset)) }
            write("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R /Info 5 0 R >>\n")
            write("startxref\n$xref\n%%EOF\n")
            return output.toByteArray()
        }
    }
}
