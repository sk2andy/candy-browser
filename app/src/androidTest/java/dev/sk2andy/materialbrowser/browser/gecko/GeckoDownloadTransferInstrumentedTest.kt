package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.data.DownloadRuntimeRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import org.mozilla.geckoview.WebResponse

@RunWith(AndroidJUnit4::class)
class GeckoDownloadTransferInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val savedNames = linkedSetOf<String>()

    @After
    fun cleanDownloadedFixture() {
        savedNames.forEach { name ->
            context.contentResolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf(name),
            )
        }
    }

    @Test
    fun streamsOriginalAuthenticatedStyleRequestIntoMediaStore() {
        val requestBody = "fixture-body".encodeToByteArray()
        val responseBody = "download-result".encodeToByteArray()
        val observedRequest = AtomicReference<String>()
        val server = ServerSocket(0)
        val serverFinished = CountDownLatch(1)
        Thread {
            server.use { socket ->
                socket.accept().use { client ->
                    val input = client.getInputStream().bufferedReader()
                    val requestLine = input.readLine()
                    val headers = linkedMapOf<String, String>()
                    while (true) {
                        val line = input.readLine()
                        if (line.isNullOrEmpty()) break
                        val separator = line.indexOf(':')
                        if (separator > 0) {
                            headers[line.substring(0, separator).lowercase()] =
                                line.substring(separator + 1).trim()
                        }
                    }
                    val length = headers["content-length"]?.toIntOrNull() ?: 0
                    val body = CharArray(length)
                    var offset = 0
                    while (offset < length) {
                        val count = input.read(body, offset, length - offset)
                        if (count < 0) break
                        offset += count
                    }
                    observedRequest.set(
                        listOf(requestLine, headers["x-candy-test"], body.concatToString()).joinToString("|"),
                    )
                    val output = client.getOutputStream()
                    output.write(
                        (
                            "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: application/octet-stream\r\n" +
                                "Content-Disposition: attachment; filename=\"candy-gecko-fixture.bin\"\r\n" +
                                "Content-Length: ${responseBody.size}\r\n" +
                                "Connection: close\r\n\r\n"
                            ).encodeToByteArray(),
                    )
                    output.write(responseBody)
                    output.flush()
                }
            }
            serverFinished.countDown()
        }.start()

        val managerRef = AtomicReference<GeckoDownloadTransferManager>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            managerRef.set(GeckoDownloadTransferManager(context, testExecutor()))
        }
        val manager = checkNotNull(managerRef.get())
        val completed = CountDownLatch(1)
        val failure = AtomicReference<GeckoDownloadFailure?>()
        val started = AtomicReference<GeckoDownloadTransferStart?>()
        val directBody = ByteBuffer.allocateDirect(requestBody.size).apply {
            put(requestBody)
            flip()
        }
        val request = WebRequest.Builder("http://127.0.0.1:${server.localPort}/download")
            .method("POST")
            .header("X-Candy-Test", "preserved")
            .body(directBody)
            .cacheMode(WebRequest.CACHE_MODE_NO_STORE)
            .beConservative(true)
            .build()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            manager.start(
                GeckoDownloadTransferRequest(
                    owner = GeckoDownloadOwner("test-profile", isPrivate = true, sessionKey = this),
                    request = request,
                    fetchFlags = GeckoWebExecutor.FETCH_FLAGS_NO_REDIRECTS,
                ),
                object : GeckoDownloadTransferListener {
                override fun onStarted(start: GeckoDownloadTransferStart) {
                    started.set(start)
                    savedNames += start.fileName
                }

                override fun onComplete(bytesReceived: Long) {
                    assertEquals(responseBody.size.toLong(), bytesReceived)
                    completed.countDown()
                }

                override fun onFailed(reason: GeckoDownloadFailure) {
                    failure.set(reason)
                    completed.countDown()
                }
                },
            )
        }

        assertTrue("server did not finish", serverFinished.await(20, TimeUnit.SECONDS))
        assertTrue("download did not finish", completed.await(20, TimeUnit.SECONDS))
        assertNull(failure.get())
        assertNotNull(started.get())
        assertEquals("POST /download HTTP/1.1|preserved|fixture-body", observedRequest.get())
        assertArrayEquals(responseBody, storedDownload("candy-gecko-fixture.bin"))

        val externalBody = "original-gecko-response".encodeToByteArray()
        val externalCompleted = CountDownLatch(1)
        val externalFailure = AtomicReference<GeckoDownloadFailure?>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            manager.startResponse(
                owner = GeckoDownloadOwner("test-profile", isPrivate = true, sessionKey = this),
                response = WebResponse.Builder("blob:https://example.com/candy-download")
                    .statusCode(200)
                    .header("Content-Type", "application/octet-stream")
                    .header(
                        "Content-Disposition",
                        "attachment; filename=\"candy-original-response.bin\"",
                    )
                    .header("Content-Length", externalBody.size.toString())
                    .body(ByteArrayInputStream(externalBody))
                    .build(),
                referrer = "https://example.com/private-form",
                listener = object : GeckoDownloadTransferListener {
                    override fun onStarted(start: GeckoDownloadTransferStart) {
                        savedNames += start.fileName
                    }

                    override fun onComplete(bytesReceived: Long) {
                        assertEquals(externalBody.size.toLong(), bytesReceived)
                        externalCompleted.countDown()
                    }

                    override fun onFailed(reason: GeckoDownloadFailure) {
                        externalFailure.set(reason)
                        externalCompleted.countDown()
                    }
                },
            )
        }
        assertTrue("external response did not finish", externalCompleted.await(20, TimeUnit.SECONDS))
        assertNull(externalFailure.get())
        assertArrayEquals(
            externalBody,
            storedDownload("candy-original-response.bin"),
        )
        manager.close()
    }

    @Test
    fun commitOwnsTerminalStateWhenCancelRacesWithPublication() {
        val commitEntered = CountDownLatch(1)
        val allowCommit = CountDownLatch(1)
        val cancelStarted = CountDownLatch(1)
        val cancelReturned = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val committed = AtomicBoolean(false)
        val aborted = AtomicBoolean(false)
        val failure = AtomicReference<GeckoDownloadFailure?>()
        val entry = object : GeckoDownloadStreamEntry {
            override val uri = android.net.Uri.parse("content://media/external/downloads/42")
            override val output = ByteArrayOutputStream()

            override fun commit() {
                commitEntered.countDown()
                check(allowCommit.await(10, TimeUnit.SECONDS))
                committed.set(true)
            }

            override fun abort() {
                aborted.set(true)
            }

            override fun close() = abort()
        }
        val managerRef = AtomicReference<GeckoDownloadTransferManager>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            managerRef.set(
                GeckoDownloadTransferManager(
                    context = context,
                    executor = testExecutor(),
                    sink = object : GeckoDownloadStreamSink {
                        override fun open(
                            fileName: String,
                            mimeType: String,
                        ): GeckoDownloadStreamEntry = entry
                    },
                ),
            )
        }
        val manager = checkNotNull(managerRef.get())
        val cancellation = checkNotNull(
            manager.startResponse(
                owner = GeckoDownloadOwner("test-profile", isPrivate = false, sessionKey = this),
                response = WebResponse.Builder("https://example.com/race.bin")
                    .statusCode(200)
                    .header("Content-Type", "application/octet-stream")
                    .header("Content-Length", "4")
                    .body(ByteArrayInputStream("race".encodeToByteArray()))
                    .build(),
                referrer = null,
                listener = object : GeckoDownloadTransferListener {
                    override fun onStarted(start: GeckoDownloadTransferStart) = Unit

                    override fun onComplete(bytesReceived: Long) {
                        completed.countDown()
                    }

                    override fun onFailed(reason: GeckoDownloadFailure) {
                        failure.set(reason)
                        completed.countDown()
                    }
                },
            ),
        )
        assertTrue("commit did not start", commitEntered.await(10, TimeUnit.SECONDS))

        val cancelThread = Thread {
            cancelStarted.countDown()
            cancellation.cancel()
            cancelReturned.countDown()
        }.apply { start() }
        assertTrue("cancel did not start", cancelStarted.await(10, TimeUnit.SECONDS))
        val blockDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (cancelThread.state != Thread.State.BLOCKED && System.nanoTime() < blockDeadline) {
            Thread.yield()
        }
        assertEquals(Thread.State.BLOCKED, cancelThread.state)
        assertFalse("cancel returned before commit finished", cancelReturned.await(10, TimeUnit.MILLISECONDS))
        allowCommit.countDown()

        assertTrue("download did not finish", completed.await(10, TimeUnit.SECONDS))
        assertTrue("cancel did not return", cancelReturned.await(10, TimeUnit.SECONDS))
        assertTrue(committed.get())
        assertFalse(aborted.get())
        assertNull(failure.get())
        manager.close()
    }

    @Test
    fun cancelDuringSinkOpenAbortsLateEntryWithoutStartingRuntimeProgress() {
        val openEntered = CountDownLatch(1)
        val allowOpen = CountDownLatch(1)
        val aborted = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val failure = AtomicReference<GeckoDownloadFailure?>()
        val entry = object : GeckoDownloadStreamEntry {
            override val uri = android.net.Uri.parse("content://media/external/downloads/43")
            override val output = ByteArrayOutputStream()

            override fun commit() = Unit

            override fun abort() {
                aborted.countDown()
            }

            override fun close() = abort()
        }
        val server = ServerSocket(0)
        Thread {
            server.use { socket ->
                socket.accept().use { client ->
                    val input = client.getInputStream().bufferedReader()
                    while (true) {
                        if (input.readLine().isNullOrEmpty()) break
                    }
                    client.getOutputStream().use { output ->
                        output.write(
                            (
                                "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: application/octet-stream\r\n" +
                                    "Content-Length: 4\r\n" +
                                    "Connection: close\r\n\r\n" +
                                    "race"
                                ).encodeToByteArray(),
                        )
                    }
                }
            }
        }.start()
        val managerRef = AtomicReference<GeckoDownloadTransferManager>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            managerRef.set(
                GeckoDownloadTransferManager(
                    context = context,
                    executor = testExecutor(),
                    sink = object : GeckoDownloadStreamSink {
                        override fun open(
                            fileName: String,
                            mimeType: String,
                        ): GeckoDownloadStreamEntry {
                            openEntered.countDown()
                            check(allowOpen.await(10, TimeUnit.SECONDS))
                            return entry
                        }
                    },
                ),
            )
        }
        val manager = checkNotNull(managerRef.get())
        val cancellationRef = AtomicReference<GeckoDownloadCancellation?>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            cancellationRef.set(
                manager.start(
                    transfer = GeckoDownloadTransferRequest(
                        owner = GeckoDownloadOwner(
                            "test-profile",
                            isPrivate = false,
                            sessionKey = this,
                        ),
                        request = WebRequest.Builder(
                            "http://127.0.0.1:${server.localPort}/setup-race",
                        ).build(),
                        fetchFlags = GeckoWebExecutor.FETCH_FLAGS_NONE,
                    ),
                    listener = object : GeckoDownloadTransferListener {
                        override fun onStarted(start: GeckoDownloadTransferStart) = Unit

                        override fun onFailed(reason: GeckoDownloadFailure) {
                            failure.set(reason)
                            finished.countDown()
                        }
                    },
                ),
            )
        }
        val cancellation = checkNotNull(cancellationRef.get())
        assertTrue("sink did not open", openEntered.await(10, TimeUnit.SECONDS))

        cancellation.cancel()
        allowOpen.countDown()

        assertTrue("cancel callback missing", finished.await(10, TimeUnit.SECONDS))
        assertTrue("late entry was not aborted", aborted.await(10, TimeUnit.SECONDS))
        assertEquals(GeckoDownloadFailure.Cancelled, failure.get())
        assertTrue(DownloadRuntimeRegistry.snapshot().isEmpty())
        manager.close()
    }

    private fun storedDownload(name: String): ByteArray? = context.contentResolver.query(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Downloads._ID),
        "${MediaStore.Downloads.DISPLAY_NAME} = ?",
        arrayOf(name),
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) null else {
            val id = cursor.getLong(0)
            context.contentResolver.openInputStream(
                android.content.ContentUris.withAppendedId(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    id,
                ),
            )?.use { input -> input.readBytes() }
        }
    }

    private fun testExecutor(): GeckoWebExecutor {
        val runtime = sharedRuntime ?: GeckoRuntime.create(
            context,
            GeckoRuntimeSettings.Builder().build(),
        ).also { created -> sharedRuntime = created }
        return GeckoWebExecutor(runtime)
    }

    private companion object {
        var sharedRuntime: GeckoRuntime? = null
    }
}
