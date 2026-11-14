package dev.sk2andy.materialbrowser.browser.gecko

import android.Manifest
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.DownloadRepository
import dev.sk2andy.materialbrowser.data.DownloadRuntimeRegistry
import dev.sk2andy.materialbrowser.data.DownloadSourceRules
import dev.sk2andy.materialbrowser.data.DownloadStatus
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.ServerSocket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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
                    .statusCode(0)
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
    fun largeTransferCompletesWhileDisplayIsOff() {
        val byteCount = 12 * 1024 * 1024
        val completed = CountDownLatch(1)
        val received = AtomicReference<Long>()
        val body = object : java.io.InputStream() {
            private var remaining = byteCount

            override fun read(): Int {
                if (remaining == 0) return -1
                remaining--
                return 1
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (remaining == 0) return -1
                val count = minOf(length, remaining)
                buffer.fill(1, offset, offset + count)
                remaining -= count
                return count
            }
        }
        var outputBytes = 0L
        val entry = object : GeckoDownloadStreamEntry {
            override val uri = android.net.Uri.parse("content://media/external/downloads/47")
            override val output = object : java.io.OutputStream() {
                override fun write(value: Int) { outputBytes++ }
                override fun write(buffer: ByteArray, offset: Int, length: Int) { outputBytes += length }
            }
            override fun commit() = Unit
            override fun abort() = Unit
            override fun close() = Unit
        }
        val managerRef = AtomicReference<GeckoDownloadTransferManager>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val manager = GeckoDownloadTransferManager(
                context = context,
                executor = testExecutor(),
                sink = object : GeckoDownloadStreamSink {
                    override fun open(fileName: String, mimeType: String): GeckoDownloadStreamEntry = entry
                },
            )
            managerRef.set(manager)
            manager.startResponse(
                owner = GeckoDownloadOwner("test-profile", isPrivate = false, sessionKey = this),
                response = WebResponse.Builder("https://example.com/large.bin")
                    .statusCode(200)
                    .header("Content-Type", "application/octet-stream")
                    .header("Content-Length", byteCount.toString())
                    .body(body)
                    .build(),
                referrer = null,
                listener = object : GeckoDownloadTransferListener {
                    override fun onStarted(start: GeckoDownloadTransferStart) = Unit

                    override fun onComplete(bytesReceived: Long) {
                        received.set(bytesReceived)
                        completed.countDown()
                    }
                },
            )
        }
        try {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            device.pressHome()
            device.sleep()
            assertTrue("large transfer did not finish", completed.await(20, TimeUnit.SECONDS))
            assertEquals(byteCount.toLong(), received.get())
            assertEquals(byteCount.toLong(), outputBytes)
        } finally {
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).wakeUp()
            managerRef.get()?.close()
        }
    }

    @Test
    fun rangeDownloadCanPauseAndResumeWithoutLosingBytes() = runRangeDownload(changedEtag = false)

    @Test
    fun rangeDownloadRejectsChangedResource() = runRangeDownload(changedEtag = true)

    @Test
    fun interruptedRangeDownloadContinuesAutomatically() = runRangeDownload(changedEtag = false, interrupted = true)

    @Test
    fun interruptedRangeDownloadCanResumeAfterServerReturns() =
        runRangeDownload(changedEtag = false, interrupted = true, failRetry = true)

    @Test
    fun interruptedRangeDownloadRetriesBeforeFirstByte() =
        runRangeDownload(changedEtag = false, interrupted = true, emptyFirstResponse = true)

    @Test
    fun pauseWhileResumeWaitsIgnoresLateError() = pauseWhileResumeWaits(staleStatus = 503)

    @Test
    fun pauseWhileResumeWaitsIgnoresLateSuccessfulResponse() = pauseWhileResumeWaits(staleStatus = 206)

    @Test
    fun pauseWhileResumeWaitsIgnoresLateConnectionFailure() = pauseWhileResumeWaits(staleStatus = 0)

    private fun pauseWhileResumeWaits(staleStatus: Int) {
        val payload = ByteArray(128 * 1024) { (it % 251).toByte() }
        val initialProgress = CountDownLatch(1)
        val releaseInitial = CountDownLatch(1)
        val staleRequest = CountDownLatch(1)
        val currentRequest = CountDownLatch(1)
        val releaseStale = CountDownLatch(1)
        val staleSent = CountDownLatch(1)
        val releaseCurrent = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val failure = AtomicReference<GeckoDownloadFailure>()
        val started = AtomicReference<GeckoDownloadTransferStart>()
        val serverFailure = AtomicReference<Exception>()
        val stoppingServer = AtomicBoolean(false)
        val existingEntries = DownloadRuntimeRegistry.snapshot().mapTo(mutableSetOf()) { it.id }
        val server = ServerSocket(0)
        val serverThread = Thread {
            try {
                fun headers(client: java.net.Socket): Map<String, String> {
                    client.soTimeout = 10_000
                    val input = client.getInputStream().bufferedReader()
                    input.readLine()
                    val result = mutableMapOf<String, String>()
                    while (true) {
                        val line = input.readLine()
                        if (line.isNullOrEmpty()) break
                        val separator = line.indexOf(':')
                        if (separator > 0) result[line.substring(0, separator).lowercase()] =
                            line.substring(separator + 1).trim()
                    }
                    return result
                }

                fun writeRange(client: java.net.Socket, offset: Int, byteCount: Int = payload.size - offset) {
                    val output = client.getOutputStream()
                    output.write(
                        ("HTTP/1.1 206 Partial Content\r\nContent-Length: ${payload.size - offset}\r\n" +
                            "Content-Range: bytes $offset-${payload.lastIndex}/${payload.size}\r\n" +
                            "ETag: \"fixture\"\r\nConnection: close\r\n\r\n").encodeToByteArray(),
                    )
                    output.write(payload, offset, byteCount)
                    output.flush()
                }

                server.use { socket ->
                    socket.accept().use { initial ->
                        headers(initial)
                        val output = initial.getOutputStream()
                        output.write(
                            ("HTTP/1.1 200 OK\r\nContent-Length: ${payload.size}\r\n" +
                                "Accept-Ranges: bytes\r\nETag: \"fixture\"\r\nConnection: close\r\n\r\n")
                                .encodeToByteArray(),
                        )
                        output.write(payload, 0, 16 * 1024)
                        output.flush()
                        check(releaseInitial.await(20, TimeUnit.SECONDS))
                    }
                    socket.accept().use { stale ->
                        val staleHeaders = headers(stale)
                        val staleOffset = requireNotNull(staleHeaders["range"]).removePrefix("bytes=")
                            .removeSuffix("-").toInt()
                        staleRequest.countDown()
                        socket.accept().use { current ->
                            val currentHeaders = headers(current)
                            val currentOffset = requireNotNull(currentHeaders["range"]).removePrefix("bytes=")
                                .removeSuffix("-").toInt()
                            check(staleOffset == currentOffset)
                            currentRequest.countDown()
                            check(releaseStale.await(20, TimeUnit.SECONDS))
                            try {
                                when (staleStatus) {
                                    206 -> {
                                        writeRange(stale, staleOffset, minOf(16 * 1024, payload.size - staleOffset))
                                        check(stale.getInputStream().read() == -1) { "stale response remained open" }
                                    }
                                    503 -> {
                                        stale.getOutputStream().write(
                                            "HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                                                .encodeToByteArray(),
                                        )
                                        stale.getOutputStream().flush()
                                    }
                                    else -> stale.close()
                                }
                            } catch (_: SocketException) {
                                // The obsolete response can be closed while its body is being written.
                            }
                            staleSent.countDown()
                            check(releaseCurrent.await(20, TimeUnit.SECONDS))
                            writeRange(current, currentOffset)
                        }
                    }
                }
            } catch (error: Exception) {
                if (!stoppingServer.get() || error !is SocketException) serverFailure.set(error)
            }
        }.apply { isDaemon = true; start() }
        val downloaded = ByteArrayOutputStream()
        val entry = object : GeckoDownloadStreamEntry {
            override val uri = Uri.parse("content://fixture/pending-range")
            override val output = downloaded
            override fun commit() = Unit
            override fun abort() = Unit
            override fun close() = Unit
        }
        val manager = AtomicReference<GeckoDownloadTransferManager>()
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val transferManager = GeckoDownloadTransferManager(
                    context,
                    testExecutor(),
                    sink = object : GeckoDownloadStreamSink {
                        override fun open(fileName: String, mimeType: String) = entry
                    },
                )
                manager.set(transferManager)
                transferManager.start(
                    GeckoDownloadTransferRequest(
                        owner = GeckoDownloadOwner("test-profile", isPrivate = false, sessionKey = this),
                        request = WebRequest.Builder("http://127.0.0.1:${server.localPort}/pending-range.bin").build(),
                        fetchFlags = GeckoWebExecutor.FETCH_FLAGS_NONE,
                    ),
                    object : GeckoDownloadTransferListener {
                        override fun onStarted(start: GeckoDownloadTransferStart) { started.set(start) }
                        override fun onProgress(bytesReceived: Long, totalBytes: Long) { initialProgress.countDown() }
                        override fun onComplete(bytesReceived: Long) { finished.countDown() }
                        override fun onFailed(reason: GeckoDownloadFailure) { failure.set(reason); finished.countDown() }
                    },
                )
            }
            assertTrue("initial bytes missing", initialProgress.await(10, TimeUnit.SECONDS))
            val id = requireNotNull(started.get()).id
            assertTrue(DownloadRuntimeRegistry.togglePauseTransfer(id))
            releaseInitial.countDown()
            assertTrue(DownloadRuntimeRegistry.togglePauseTransfer(id))
            assertTrue("first range request missing", staleRequest.await(10, TimeUnit.SECONDS))
            val pausedBytes = requireNotNull(DownloadRuntimeRegistry.entryForTransfer(id)).bytes
            assertTrue(DownloadRuntimeRegistry.togglePauseTransfer(id))
            assertTrue(DownloadRuntimeRegistry.togglePauseTransfer(id))
            assertTrue("second range request waited for the first", currentRequest.await(10, TimeUnit.SECONDS))
            releaseStale.countDown()
            assertTrue("stale response was not sent", staleSent.await(10, TimeUnit.SECONDS))
            SystemClock.sleep(500)
            assertEquals(DownloadStatus.Running, DownloadRuntimeRegistry.entryForTransfer(id)?.status)
            assertEquals(pausedBytes, DownloadRuntimeRegistry.entryForTransfer(id)?.bytes)
            assertEquals(1L, finished.count)
            releaseCurrent.countDown()
            assertTrue("current request did not finish", finished.await(20, TimeUnit.SECONDS))
            assertNull(failure.get())
            assertArrayEquals(payload, downloaded.toByteArray())
            assertNull("download test server failed", serverFailure.get())
        } finally {
            releaseInitial.countDown()
            releaseStale.countDown()
            releaseCurrent.countDown()
            stoppingServer.set(true)
            server.close()
            manager.get()?.close()
            DownloadRuntimeRegistry.clear(
                DownloadRuntimeRegistry.snapshot()
                    .filter { it.id !in existingEntries && it.name == "pending-range.bin" }
                    .map { it.id },
            )
            serverThread.join(10_000)
        }
    }

    @Test
    fun storageFailureDuringPauseAbortsPartialDownload() {
        val server = ServerSocket(0)
        val serverThread = Thread {
            runCatching {
                server.accept().use { client ->
                    val input = client.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) Unit
                    client.getOutputStream().use { output ->
                        output.write(
                            ("HTTP/1.1 200 OK\r\nContent-Length: 65536\r\n" +
                                "Accept-Ranges: bytes\r\nETag: \"fixture\"\r\nConnection: close\r\n\r\n")
                                .encodeToByteArray(),
                        )
                        output.write(ByteArray(65536))
                    }
                }
            }
        }.apply { isDaemon = true; start() }
        val writeStarted = CountDownLatch(1)
        val failWrite = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val started = AtomicReference<GeckoDownloadTransferStart>()
        val failure = AtomicReference<GeckoDownloadFailure>()
        val aborted = AtomicBoolean(false)
        val committed = AtomicBoolean(false)
        val partialBytes = ByteArrayOutputStream()
        val entry = object : GeckoDownloadStreamEntry {
            override val uri = Uri.parse("content://fixture/write-failure")
            override val output = object : java.io.OutputStream() {
                override fun write(value: Int) = error("Unexpected single-byte write")
                override fun write(buffer: ByteArray, offset: Int, length: Int) {
                    partialBytes.write(buffer, offset, minOf(length, 1))
                    writeStarted.countDown()
                    check(failWrite.await(10, TimeUnit.SECONDS))
                    throw IOException("Partial write failed")
                }
            }
            override fun commit() { committed.set(true) }
            override fun abort() { aborted.set(true) }
            override fun close() = abort()
        }
        val manager = AtomicReference<GeckoDownloadTransferManager>()
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val transferManager = GeckoDownloadTransferManager(
                    context,
                    testExecutor(),
                    sink = object : GeckoDownloadStreamSink {
                        override fun open(fileName: String, mimeType: String) = entry
                    },
                )
                manager.set(transferManager)
                transferManager.start(
                    GeckoDownloadTransferRequest(
                        owner = GeckoDownloadOwner("test-profile", isPrivate = false, sessionKey = this),
                        request = WebRequest.Builder("http://127.0.0.1:${server.localPort}/write-failure").build(),
                        fetchFlags = GeckoWebExecutor.FETCH_FLAGS_NONE,
                    ),
                    object : GeckoDownloadTransferListener {
                        override fun onStarted(start: GeckoDownloadTransferStart) { started.set(start) }
                        override fun onFailed(reason: GeckoDownloadFailure) {
                            failure.set(reason)
                            finished.countDown()
                        }
                    },
                )
            }
            assertTrue("download did not start writing", writeStarted.await(10, TimeUnit.SECONDS))
            val id = requireNotNull(started.get()).id
            assertTrue(DownloadRuntimeRegistry.togglePauseTransfer(id))
            failWrite.countDown()
            assertTrue("storage failure was not reported", finished.await(10, TimeUnit.SECONDS))
            assertEquals(GeckoDownloadFailure.Storage, failure.get())
            assertEquals(1, partialBytes.size())
            assertTrue("partial download was not aborted", aborted.get())
            assertFalse("partial download was published", committed.get())
            assertEquals(DownloadStatus.Failed, DownloadRuntimeRegistry.entryForTransfer(id)?.status)
            assertFalse(DownloadRuntimeRegistry.togglePauseTransfer(id))
        } finally {
            failWrite.countDown()
            manager.get()?.close()
            started.get()?.id?.let { id ->
                DownloadRuntimeRegistry.entryForTransfer(id)?.let { DownloadRuntimeRegistry.clear(listOf(it.id)) }
            }
            server.close()
            serverThread.join(10_000)
        }
    }

    @Test
    fun failedPostBeforeResponseIsNotRetried() {
        val previousEntries = DownloadRuntimeRegistry.snapshot().mapTo(mutableSetOf()) { it.id }
        val requests = AtomicInteger()
        val serverFinished = CountDownLatch(1)
        val serverFailure = AtomicReference<Throwable>()
        val server = ServerSocket(0).apply { soTimeout = 3_000 }
        val serverThread = Thread {
            runCatching {
                server.use { socket ->
                    socket.accept().use { client ->
                        requests.incrementAndGet()
                        val input = client.getInputStream().bufferedReader()
                        while (!input.readLine().isNullOrEmpty()) Unit
                    }
                    try {
                        socket.accept().use { requests.incrementAndGet() }
                    } catch (_: SocketTimeoutException) {
                        // No second request is expected.
                    }
                }
            }.onFailure { serverFailure.set(it) }
            serverFinished.countDown()
        }.apply { isDaemon = true; start() }
        val failed = CountDownLatch(1)
        val failure = AtomicReference<GeckoDownloadFailure>()
        val manager = AtomicReference<GeckoDownloadTransferManager>()
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val transferManager = GeckoDownloadTransferManager(context, testExecutor())
                manager.set(transferManager)
                transferManager.start(
                    GeckoDownloadTransferRequest(
                        owner = GeckoDownloadOwner("test-profile", isPrivate = false, sessionKey = this),
                        request = WebRequest.Builder("http://127.0.0.1:${server.localPort}/post-no-retry")
                            .method("POST")
                            .build(),
                        fetchFlags = GeckoWebExecutor.FETCH_FLAGS_NONE,
                    ),
                    object : GeckoDownloadTransferListener {
                        override fun onStarted(start: GeckoDownloadTransferStart) = Unit
                        override fun onFailed(reason: GeckoDownloadFailure) {
                            failure.set(reason)
                            failed.countDown()
                        }
                    },
                )
            }
            assertTrue("network failure not reported", failed.await(10, TimeUnit.SECONDS))
            assertEquals(GeckoDownloadFailure.Network, failure.get())
            assertTrue("server did not finish", serverFinished.await(10, TimeUnit.SECONDS))
            assertNull(serverFailure.get())
            assertEquals(1, requests.get())
        } finally {
            manager.get()?.close()
            server.close()
            serverThread.join(10_000)
            DownloadRuntimeRegistry.clear(
                DownloadRuntimeRegistry.snapshot()
                    .filter { it.id !in previousEntries && it.name == "post-no-retry" }
                    .map { it.id },
            )
        }
    }

    @Test
    fun pendingRequestIsVisibleAndCanBeCancelledBeforeResponse() {
        val previousEntries = DownloadRuntimeRegistry.snapshot().mapTo(mutableSetOf()) { it.id }
        val accepted = CountDownLatch(1)
        val releaseResponse = CountDownLatch(1)
        val serverFinished = CountDownLatch(1)
        val failed = CountDownLatch(1)
        val failure = AtomicReference<GeckoDownloadFailure>()
        val serverFailure = AtomicReference<Exception>()
        val started = AtomicBoolean(false)
        val manager = AtomicReference<GeckoDownloadTransferManager>()
        val ownerKey = Any()
        val server = ServerSocket(0)
        val serverThread = Thread {
            try {
                server.accept().use { client ->
                    val input = client.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) Unit
                    accepted.countDown()
                    check(releaseResponse.await(20, TimeUnit.SECONDS))
                    client.getOutputStream().use { output ->
                        output.write("HTTP/1.1 200 OK\r\nContent-Length: 4\r\nConnection: close\r\n\r\ntest".encodeToByteArray())
                    }
                }
            } catch (_: SocketException) {
                // Cancellation may close the request before the server replies.
            } catch (error: Exception) {
                serverFailure.set(error)
            } finally {
                serverFinished.countDown()
            }
        }.apply { isDaemon = true; start() }
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val transferManager = GeckoDownloadTransferManager(context, testExecutor())
                manager.set(transferManager)
                transferManager.start(
                    GeckoDownloadTransferRequest(
                        owner = GeckoDownloadOwner("test-profile", isPrivate = true, sessionKey = ownerKey),
                        request = WebRequest.Builder(
                            "http://127.0.0.1:${server.localPort}/candy-waiting-353.bin?token=private",
                        ).build(),
                        fetchFlags = GeckoWebExecutor.FETCH_FLAGS_NONE,
                    ),
                    object : GeckoDownloadTransferListener {
                        override fun onStarted(start: GeckoDownloadTransferStart) { started.set(true) }
                        override fun onFailed(reason: GeckoDownloadFailure) {
                            failure.set(reason)
                            failed.countDown()
                        }
                    },
                )
            }
            assertTrue("server did not receive request", accepted.await(10, TimeUnit.SECONDS))
            val repository = DownloadRepository(context)
            val waiting = repository.snapshot().single { it.name == "candy-waiting-353.bin" }
            assertEquals(DownloadStatus.Pending, waiting.status)
            assertEquals("", waiting.source)
            assertTrue(waiting.isPrivate)
            assertTrue(waiting.supportsCancel)
            assertTrue(requireNotNull(manager.get()).hasActiveForOwner(ownerKey))
            assertTrue(repository.cancel(waiting))
            assertTrue("cancel was not reported", failed.await(10, TimeUnit.SECONDS))
            assertEquals(GeckoDownloadFailure.Cancelled, failure.get())
            assertEquals(DownloadStatus.Cancelled, repository.snapshot().single { it.id == waiting.id }.status)
            assertFalse(requireNotNull(manager.get()).hasActiveForOwner(ownerKey))
            releaseResponse.countDown()
            assertTrue("server did not finish", serverFinished.await(10, TimeUnit.SECONDS))
            assertNull("server failed", serverFailure.get())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertFalse("cancelled request started a download", started.get())
        } finally {
            releaseResponse.countDown()
            manager.get()?.close()
            server.close()
            serverThread.join(10_000)
            DownloadRuntimeRegistry.clear(
                DownloadRuntimeRegistry.snapshot()
                    .filter { it.id !in previousEntries && it.name == "candy-waiting-353.bin" }
                    .map { it.id },
            )
        }
    }

    private fun runRangeDownload(
        changedEtag: Boolean,
        interrupted: Boolean = false,
        failRetry: Boolean = false,
        emptyFirstResponse: Boolean = false,
    ) {
        val payload = ByteArray(128 * 1024) { (it % 251).toByte() }
        val firstChunkSent = CountDownLatch(1)
        val releaseFirstConnection = CountDownLatch(1)
        val releaseRetryBody = CountDownLatch(1)
        val progress = CountDownLatch(1)
        val startedSignal = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val requestHeaders = AtomicReference<Map<String, String>>()
        val failure = AtomicReference<GeckoDownloadFailure>()
        val started = AtomicReference<GeckoDownloadTransferStart>()
        val serverFailure = AtomicReference<Exception>()
        val stoppingServer = AtomicBoolean(false)
        val existingEntries = DownloadRuntimeRegistry.snapshot().mapTo(mutableSetOf()) { it.id }
        val server = ServerSocket(0)
        val serverThread = Thread {
            try {
                server.use { socket ->
                    socket.accept().use { client ->
                        val input = client.getInputStream().bufferedReader()
                        while (!input.readLine().isNullOrEmpty()) Unit
                        val output = client.getOutputStream()
                        output.write(
                            ("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\n" +
                                "Content-Length: ${payload.size}\r\nAccept-Ranges: bytes\r\n" +
                                "ETag: \"fixture\"\r\nConnection: close\r\n\r\n").encodeToByteArray(),
                        )
                        if (!emptyFirstResponse) output.write(payload, 0, 16 * 1024)
                        output.flush()
                        firstChunkSent.countDown()
                        check(releaseFirstConnection.await(20, TimeUnit.SECONDS)) { "first response was not released" }
                    }
                    if (failRetry) {
                        socket.accept().use { client ->
                            val input = client.getInputStream().bufferedReader()
                            while (!input.readLine().isNullOrEmpty()) Unit
                            client.getOutputStream().write(
                                "HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".encodeToByteArray(),
                            )
                        }
                    }
                    socket.accept().use { client ->
                        val input = client.getInputStream().bufferedReader()
                        input.readLine()
                        val headers = linkedMapOf<String, String>()
                        while (true) {
                            val line = input.readLine()
                            if (line.isNullOrEmpty()) break
                            val separator = line.indexOf(':')
                            if (separator > 0) headers[line.substring(0, separator).lowercase()] = line.substring(separator + 1).trim()
                        }
                        requestHeaders.set(headers)
                        val output = client.getOutputStream()
                        if (emptyFirstResponse) {
                            output.write(
                                ("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\n" +
                                    "Content-Length: ${payload.size}\r\nAccept-Ranges: bytes\r\n" +
                                    "ETag: \"fixture\"\r\nConnection: close\r\n\r\n").encodeToByteArray(),
                            )
                            output.write(payload, 0, 16 * 1024)
                            output.flush()
                            check(releaseRetryBody.await(20, TimeUnit.SECONDS)) { "retry body was not released" }
                            output.write(payload, 16 * 1024, payload.size - 16 * 1024)
                            output.flush()
                        } else {
                            val offset = requireNotNull(headers["range"]).removePrefix("bytes=").removeSuffix("-").toInt()
                            output.write(
                                ("HTTP/1.1 206 Partial Content\r\nContent-Type: application/octet-stream\r\n" +
                                    "Content-Length: ${payload.size - offset}\r\n" +
                                    "Content-Range: bytes $offset-${payload.lastIndex}/${payload.size}\r\n" +
                                    "ETag: ${if (changedEtag) "\"changed\"" else "\"fixture\""}\r\nConnection: close\r\n\r\n").encodeToByteArray(),
                            )
                            try {
                                output.write(payload, offset, payload.size - offset)
                                output.flush()
                            } catch (error: SocketException) {
                                if (!changedEtag) throw error
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                if (!stoppingServer.get() || error !is SocketException) serverFailure.set(error)
            }
        }.apply { start() }
        val downloaded = ByteArrayOutputStream()
        val aborted = AtomicBoolean(false)
        val entry = object : GeckoDownloadStreamEntry {
            override val uri = Uri.parse("content://media/external/downloads/48")
            override val output = downloaded
            override fun commit() = Unit
            override fun abort() { aborted.set(true) }
            override fun close() = Unit
        }
        val ownerKey = Any()
        val manager = AtomicReference<GeckoDownloadTransferManager>()
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val transferManager = GeckoDownloadTransferManager(
                    context, testExecutor(), sink = object : GeckoDownloadStreamSink {
                        override fun open(fileName: String, mimeType: String) = entry
                    },
                )
                manager.set(transferManager)
                transferManager.start(
                    GeckoDownloadTransferRequest(
                        owner = GeckoDownloadOwner("test-profile", isPrivate = false, sessionKey = ownerKey),
                        request = WebRequest.Builder("http://127.0.0.1:${server.localPort}/range.bin").build(),
                        fetchFlags = GeckoWebExecutor.FETCH_FLAGS_NONE,
                    ),
                    object : GeckoDownloadTransferListener {
                        override fun onStarted(start: GeckoDownloadTransferStart) {
                            started.set(start)
                            startedSignal.countDown()
                        }
                        override fun onProgress(bytesReceived: Long, totalBytes: Long) { progress.countDown() }
                        override fun onComplete(bytesReceived: Long) { finished.countDown() }
                        override fun onFailed(reason: GeckoDownloadFailure) { failure.set(reason); finished.countDown() }
                    },
                )
            }
            assertTrue(firstChunkSent.await(10, TimeUnit.SECONDS))
            if (emptyFirstResponse) releaseFirstConnection.countDown()
            val responseStarted = startedSignal.await(10, TimeUnit.SECONDS)
            assertTrue("response did not start: ${failure.get()}", responseStarted)
            if (!emptyFirstResponse) assertTrue(progress.await(10, TimeUnit.SECONDS))
            val transferManager = requireNotNull(manager.get())
            val id = requireNotNull(started.get()).id
            assertTrue(transferManager.hasActiveForOwner(ownerKey))
            assertFalse(transferManager.hasActiveForOwner(Any()))
            assertTrue(DownloadRuntimeRegistry.entryForTransfer(id)?.supportsPause == true)
            if (!interrupted) {
                assertTrue(waitForService(foreground = true))
                assertTrue(DownloadRuntimeRegistry.togglePauseTransfer(id))
                assertEquals(DownloadStatus.Paused, DownloadRuntimeRegistry.entryForTransfer(id)?.status)
                assertTrue(waitForService(foreground = false))
            }
            releaseFirstConnection.countDown()
            releaseRetryBody.countDown()
            if (!interrupted) assertTrue(DownloadRuntimeRegistry.togglePauseTransfer(id))
            if (failRetry) {
                val deadline = SystemClock.elapsedRealtime() + 20_000
                while (DownloadRuntimeRegistry.entryForTransfer(id)?.status != DownloadStatus.Paused &&
                    SystemClock.elapsedRealtime() < deadline
                ) SystemClock.sleep(50)
                assertEquals(DownloadStatus.Paused, DownloadRuntimeRegistry.entryForTransfer(id)?.status)
                assertTrue(DownloadRuntimeRegistry.togglePauseTransfer(id))
            }
            val downloadFinished = finished.await(20, TimeUnit.SECONDS)
            assertTrue(
                "download did not finish: server=${serverFailure.get()}, failure=${failure.get()}, " +
                    "status=${DownloadRuntimeRegistry.entryForTransfer(id)?.status}, headers=${requestHeaders.get()}",
                downloadFinished,
            )
            serverThread.join(10_000)
            assertNull("download test server failed", serverFailure.get())
            if (changedEtag) {
                assertEquals(GeckoDownloadFailure.Network, failure.get())
                assertTrue(aborted.get())
            } else {
                assertNull(failure.get())
                assertArrayEquals(payload, downloaded.toByteArray())
            }
            if (emptyFirstResponse) {
                assertNull(requestHeaders.get()["range"])
                assertNull(requestHeaders.get()["if-range"])
            } else {
                assertEquals("\"fixture\"", requestHeaders.get()["if-range"])
            }
            assertFalse(transferManager.hasActiveForOwner(ownerKey))
        } finally {
            releaseFirstConnection.countDown()
            releaseRetryBody.countDown()
            stoppingServer.set(true)
            server.close()
            manager.get()?.close()
            started.get()?.id?.let { id ->
                DownloadRuntimeRegistry.entryForTransfer(id)?.let { DownloadRuntimeRegistry.clear(listOf(it.id)) }
            }
            DownloadRuntimeRegistry.clear(
                DownloadRuntimeRegistry.snapshot()
                    .filter { it.id !in existingEntries && it.name == "range.bin" }
                    .map { it.id },
            )
            serverThread.join(10_000)
        }
    }

    @Suppress("DEPRECATION")
    private fun waitForService(foreground: Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        do {
            val running = context.getSystemService(ActivityManager::class.java)
                .getRunningServices(Int.MAX_VALUE)
                .any { it.service.className == GeckoDownloadForegroundService::class.java.name && it.foreground }
            if (running == foreground) return true
            SystemClock.sleep(50)
        } while (SystemClock.elapsedRealtime() < deadline)
        return false
    }

    @Test
    fun failureBeforeResponseKeepsPrivateProfileBoundary() =
        assertFailureBeforeResponseDoesNotExposeSource(isPrivate = true)

    @Test
    fun failureBeforeResponseKeepsRegularProfileBoundary() =
        assertFailureBeforeResponseDoesNotExposeSource(isPrivate = false)

    private fun assertFailureBeforeResponseDoesNotExposeSource(isPrivate: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        runCatching {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        val notifications = context.getSystemService(NotificationManager::class.java)
        assumeTrue(context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        assumeTrue(notifications.areNotificationsEnabled())
        val server = ServerSocket(0)
        val fileName = "candy-pre-response-failure-$isPrivate.bin"
        val source = "http://127.0.0.1:${server.localPort}/$fileName"
        val serverThread = Thread { server.use { it.accept().close() } }.apply { isDaemon = true; start() }
        val failed = CountDownLatch(1)
        val manager = AtomicReference<GeckoDownloadTransferManager>()
        try {
            instrumentation.runOnMainSync {
                manager.set(GeckoDownloadTransferManager(context, testExecutor()).also { transferManager ->
                    transferManager.start(
                        transfer = GeckoDownloadTransferRequest(
                            owner = GeckoDownloadOwner("test-profile", isPrivate = isPrivate, sessionKey = this),
                            request = WebRequest.Builder(source).build(),
                            fetchFlags = GeckoWebExecutor.FETCH_FLAGS_NONE,
                        ),
                        listener = object : GeckoDownloadTransferListener {
                            override fun onStarted(start: GeckoDownloadTransferStart) = Unit
                            override fun onFailed(reason: GeckoDownloadFailure) { failed.countDown() }
                        },
                    )
                })
            }
            assertTrue("download did not fail", failed.await(20, TimeUnit.SECONDS))
            val entry = DownloadRuntimeRegistry.snapshot().single { it.name == fileName }
            assertEquals(DownloadStatus.Failed, entry.status)
            assertEquals(isPrivate, entry.isPrivate)
            assertEquals("", entry.source)
            assertFalse(DownloadSourceRules.canOpenSource(entry))
            val deadline = SystemClock.elapsedRealtime() + 10_000L
            var notification: Notification? = null
            while (notification == null && SystemClock.elapsedRealtime() < deadline) {
                notification = notifications.activeNotifications.firstOrNull {
                    it.notification.extras.getString(Notification.EXTRA_TITLE) == entry.name &&
                        it.notification.extras.getString(Notification.EXTRA_TEXT) ==
                        context.getString(R.string.download_notification_failed)
                }?.notification
                if (notification == null) SystemClock.sleep(50)
            }
            assertEquals(
                context.getString(R.string.download_notification_failed),
                checkNotNull(notification).extras.getString(Notification.EXTRA_TEXT),
            )
        } finally {
            manager.get()?.close()
            DownloadRuntimeRegistry.snapshot().filter { it.name == fileName }.forEach { entry ->
                notifications.activeNotifications.filter {
                    it.notification.extras.getString(Notification.EXTRA_TITLE) == entry.name
                }.forEach { notifications.cancel(it.id) }
                DownloadRuntimeRegistry.clear(listOf(entry.id))
            }
            server.close()
            serverThread.join(5_000)
        }
    }

    @Test
    fun interruptedStreamFailsAndAbortsPartialStorage() {
        val failed = CountDownLatch(1)
        val aborted = AtomicBoolean(false)
        val failure = AtomicReference<GeckoDownloadFailure>()
        val partialOutput = ByteArrayOutputStream()
        val body = object : ByteArrayInputStream(ByteArray(64 * 1024)) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (this.pos >= 16 * 1024) throw java.io.IOException("connection reset")
                return super.read(buffer, offset, minOf(length, 16 * 1024 - this.pos))
            }
        }
        val entry = object : GeckoDownloadStreamEntry {
            override val uri = android.net.Uri.parse("content://media/external/downloads/45")
            override val output = partialOutput
            override fun commit() = error("Interrupted transfer must not commit")
            override fun abort() { aborted.set(true) }
            override fun close() = Unit
        }
        val managerRef = AtomicReference<GeckoDownloadTransferManager>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val manager = GeckoDownloadTransferManager(
                context = context,
                executor = testExecutor(),
                sink = object : GeckoDownloadStreamSink {
                    override fun open(fileName: String, mimeType: String): GeckoDownloadStreamEntry = entry
                },
            )
            managerRef.set(manager)
            manager.startResponse(
                owner = GeckoDownloadOwner("test-profile", isPrivate = true, sessionKey = this),
                response = WebResponse.Builder("https://example.com/interrupted.bin")
                    .statusCode(200)
                    .header("Content-Type", "application/octet-stream")
                    .body(body)
                    .build(),
                referrer = null,
                listener = object : GeckoDownloadTransferListener {
                    override fun onStarted(start: GeckoDownloadTransferStart) {
                        assertFalse(DownloadRuntimeRegistry.entryForTransfer(start.id)?.supportsPause ?: true)
                    }

                    override fun onFailed(reason: GeckoDownloadFailure) {
                        failure.set(reason)
                        failed.countDown()
                    }
                },
            )
        }
        val manager = requireNotNull(managerRef.get())
        try {
            assertTrue("interrupted transfer did not fail", failed.await(10, TimeUnit.SECONDS))
            assertEquals(GeckoDownloadFailure.Network, failure.get())
            assertEquals(16 * 1024, partialOutput.size())
            assertTrue(aborted.get())
            val terminal = DownloadRuntimeRegistry.snapshot().single { it.name == "interrupted.bin" }
            assertEquals(DownloadStatus.Failed, terminal.status)
            DownloadRuntimeRegistry.clear(listOf(terminal.id))
        } finally {
            manager.close()
        }
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
        try {
            val cancellation = checkNotNull(cancellationRef.get())
            assertTrue("sink did not open", openEntered.await(10, TimeUnit.SECONDS))
            val pending = DownloadRuntimeRegistry.snapshot().single { it.name == "setup-race" }
            assertEquals(DownloadStatus.Pending, pending.status)

            cancellation.cancel()
            allowOpen.countDown()

            assertTrue("cancel callback missing", finished.await(10, TimeUnit.SECONDS))
            assertTrue("late entry was not aborted", aborted.await(10, TimeUnit.SECONDS))
            assertEquals(GeckoDownloadFailure.Cancelled, failure.get())
            assertEquals(DownloadStatus.Cancelled, DownloadRuntimeRegistry.snapshot().single { it.id == pending.id }.status)
        } finally {
            allowOpen.countDown()
            manager.close()
            DownloadRuntimeRegistry.clear(
                DownloadRuntimeRegistry.snapshot().filter { it.name == "setup-race" }.map { it.id },
            )
        }
    }

    @Test
    fun timeoutShowsTransferAsFailedInDownloads() {
        val started = AtomicReference<GeckoDownloadTransferStart>()
        val failed = AtomicReference<GeckoDownloadFailure>()
        val finished = CountDownLatch(1)
        val reading = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val aborted = AtomicBoolean(false)
        val body = object : InputStream() {
            override fun read(): Int {
                reading.countDown()
                check(closed.await(30, TimeUnit.SECONDS))
                throw IOException("stream closed")
            }

            override fun close() { closed.countDown() }
        }
        val entry = object : GeckoDownloadStreamEntry {
            override val uri = Uri.parse("content://fixture/timeout")
            override val output = ByteArrayOutputStream()
            override fun commit() = error("Timed-out transfer must not commit")
            override fun abort() { aborted.set(true) }
            override fun close() = abort()
        }
        val manager = AtomicReference<GeckoDownloadTransferManager>()
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val transferManager = GeckoDownloadTransferManager(
                    context,
                    testExecutor(),
                    sink = object : GeckoDownloadStreamSink {
                        override fun open(fileName: String, mimeType: String): GeckoDownloadStreamEntry = entry
                    },
                )
                manager.set(transferManager)
                transferManager.startResponse(
                    owner = GeckoDownloadOwner("test-profile", isPrivate = true, sessionKey = this),
                    response = WebResponse.Builder("https://example.com/timeout-353.bin")
                        .statusCode(200)
                        .body(body)
                        .build(),
                    referrer = null,
                    listener = object : GeckoDownloadTransferListener {
                        override fun onStarted(start: GeckoDownloadTransferStart) { started.set(start) }
                        override fun onFailed(reason: GeckoDownloadFailure) {
                            failed.set(reason)
                            finished.countDown()
                        }
                    },
                )
            }
            assertTrue("transfer did not start reading", reading.await(10, TimeUnit.SECONDS))
            val transferId = checkNotNull(started.get()).id
            assertEquals(DownloadStatus.Running, DownloadRuntimeRegistry.entryForTransfer(transferId)?.status)
            val serviceField = GeckoDownloadForegroundService::class.java.getDeclaredField("service")
            serviceField.isAccessible = true
            val deadline = SystemClock.elapsedRealtime() + 10_000L
            var service: GeckoDownloadForegroundService? = null
            while (service == null && SystemClock.elapsedRealtime() < deadline) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    service = serviceField.get(null) as? GeckoDownloadForegroundService
                }
                if (service == null) SystemClock.sleep(50)
            }
            val runningService = checkNotNull(service) { "service did not start" }
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                runningService.onTimeout(0, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            }
            assertTrue("transfer did not fail", finished.await(10, TimeUnit.SECONDS))
            assertEquals(GeckoDownloadFailure.Network, failed.get())
            assertTrue("partial download was not aborted", aborted.get())
            val visibleEntry = DownloadRepository(context).snapshot().single { it.name == "timeout-353.bin" }
            assertEquals(DownloadStatus.Failed, visibleEntry.status)
        } finally {
            closed.countDown()
            manager.get()?.close()
            started.get()?.id?.let { id ->
                DownloadRuntimeRegistry.entryForTransfer(id)?.let { DownloadRuntimeRegistry.clear(listOf(it.id)) }
            }
        }
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
