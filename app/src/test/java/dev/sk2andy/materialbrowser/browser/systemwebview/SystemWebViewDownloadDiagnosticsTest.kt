package dev.sk2andy.materialbrowser.browser.systemwebview

import dev.sk2andy.materialbrowser.browser.BrowserEngineDownloadResponse
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadCancellation
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadFailure
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadTransferListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadTransferStart
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExternalDownloadResponse
import dev.sk2andy.materialbrowser.data.AppLogEvent
import dev.sk2andy.materialbrowser.data.AppLogRules
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemWebViewDownloadDiagnosticsTest {
    @Test
    fun `transfer callbacks preserve original values while progress stays out of logs`() {
        val events = mutableListOf<AppLogEvent>()
        val diagnostics = SystemWebViewDownloadDiagnostics(isPrivate = false) { event, error ->
            assertNull(error)
            events.add(event)
        }
        val listener = RecordingTransferListener()
        val wrapped = diagnostics.transferListener(listener)
        val start = transferStart()

        wrapped.onStarted(start)
        wrapped.onProgress(24_576L, 80_000L)
        wrapped.onProgress(49_152L, 80_000L)
        wrapped.onComplete(80_000L)

        assertSame(start, listener.starts.single())
        assertEquals(listOf(24_576L to 80_000L, 49_152L to 80_000L), listener.progress)
        assertEquals(listOf(80_000L), listener.completions)
        assertTrue(listener.failures.isEmpty())
        assertEquals(
            listOf(AppLogEvent.SystemDownloadStarted, AppLogEvent.SystemDownloadCompleted),
            events,
        )
    }

    @Test
    fun `all download failures retain their reason and distinct diagnostic event`() {
        val events = mutableListOf<AppLogEvent>()
        val diagnostics = SystemWebViewDownloadDiagnostics(isPrivate = false) { event, error ->
            assertNull(error)
            events.add(event)
        }
        val listener = RecordingTransferListener()
        val wrapped = diagnostics.transferListener(listener)

        GeckoDownloadFailure.entries.forEach(wrapped::onFailed)

        assertEquals(GeckoDownloadFailure.entries, listener.failures)
        assertEquals(
            listOf(
                AppLogEvent.SystemDownloadInvalidRequest,
                AppLogEvent.SystemDownloadNetworkFailed,
                AppLogEvent.SystemDownloadHttpFailed,
                AppLogEvent.SystemDownloadStorageFailed,
                AppLogEvent.SystemDownloadCancelled,
            ),
            events,
        )
    }

    @Test
    fun `response preserves metadata cancellation and one shot transfer ownership`() {
        val events = mutableListOf<AppLogEvent>()
        val diagnostics = SystemWebViewDownloadDiagnostics(isPrivate = false) { event, _ ->
            events.add(event)
        }
        var starts = 0
        var discards = 0
        var cancellations = 0
        val cancellation = GeckoDownloadCancellation { cancellations += 1 }
        val listener = RecordingTransferListener()
        val start = transferStart()
        val original = response(
            start = { wrappedListener ->
                starts += 1
                wrappedListener.onStarted(start)
                cancellation
            },
            discard = { discards += 1 },
        )
        val wrapped = diagnostics.response(original)

        assertSame(original.metadata, wrapped.metadata)
        val returnedCancellation = wrapped.start(listener)
        assertSame(cancellation, returnedCancellation)
        requireNotNull(returnedCancellation).cancel()
        assertNull(wrapped.start(listener))
        wrapped.close()
        wrapped.close()
        assertNull(original.start(listener))

        assertEquals(1, starts)
        assertEquals(0, discards)
        assertEquals(1, cancellations)
        assertSame(start, listener.starts.single())
        assertEquals(
            listOf(
                AppLogEvent.SystemDownloadResponseReceived,
                AppLogEvent.SystemDownloadRequested,
                AppLogEvent.SystemDownloadStarted,
            ),
            events,
        )
    }

    @Test
    fun `closing wrapped unclaimed response discards original exactly once`() {
        val events = mutableListOf<AppLogEvent>()
        val diagnostics = SystemWebViewDownloadDiagnostics(isPrivate = false) { event, _ ->
            events.add(event)
        }
        var starts = 0
        var discards = 0
        val original = response(
            start = {
                starts += 1
                GeckoDownloadCancellation {}
            },
            discard = { discards += 1 },
        )
        val wrapped = diagnostics.response(original)

        wrapped.close()
        wrapped.close()
        original.close()

        assertNull(wrapped.start(RecordingTransferListener()))
        assertNull(original.start(RecordingTransferListener()))
        assertEquals(0, starts)
        assertEquals(1, discards)
        assertEquals(listOf(AppLogEvent.SystemDownloadResponseReceived), events)
    }

    @Test
    fun `private identity suppresses diagnostics after session changes while deferred callbacks still forward`() {
        val events = mutableListOf<AppLogEvent>()
        var sessionIsPrivate = true
        val diagnostics = SystemWebViewDownloadDiagnostics(isPrivate = sessionIsPrivate) { event, _ ->
            events.add(event)
        }
        val listener = RecordingTransferListener()
        lateinit var deferredListener: GeckoDownloadTransferListener
        val original = response(
            start = {
                deferredListener = it
                GeckoDownloadCancellation {}
            },
            discard = {},
        )
        val wrapped = diagnostics.response(original)
        requireNotNull(wrapped.start(listener))
        sessionIsPrivate = false
        assertFalse(sessionIsPrivate)
        wrapped.close()
        diagnostics.record(AppLogEvent.SystemDownloadHelperSetupFailed, downloadFailure())
        val start = transferStart()
        deferredListener.onStarted(start)
        deferredListener.onProgress(24_576L, 80_000L)
        deferredListener.onComplete(80_000L)
        val executor = Executors.newSingleThreadExecutor()
        try {
            executor.submit {
                deferredListener.onFailed(GeckoDownloadFailure.Storage)
            }.get(5, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        assertTrue(events.isEmpty())
        assertSame(start, listener.starts.single())
        assertEquals(listOf(24_576L to 80_000L), listener.progress)
        assertEquals(listOf(80_000L), listener.completions)
        assertEquals(listOf(GeckoDownloadFailure.Storage), listener.failures)
    }

    @Test
    fun `recorded exception keeps real stack identity while rendering excludes sensitive values`() {
        val rendered = mutableListOf<String>()
        val error = downloadFailure()
        val originalFrames = error.stackTrace.copyOf()
        val diagnostics = SystemWebViewDownloadDiagnostics(isPrivate = false) { event, recordedError ->
            assertSame(error, recordedError)
            rendered.add(AppLogRules.render(event, 0L, recordedError))
        }

        diagnostics.record(AppLogEvent.SystemDownloadSinkWriteFailed, error)

        val log = rendered.single()
        val firstFrame = originalFrames.first()
        assertTrue(log.startsWith("1970-01-01T00:00:00Z SystemDownloadSinkWriteFailed\n"))
        assertTrue(log.contains("exception java.lang.IllegalStateException"))
        assertTrue(log.contains("exception java.lang.IllegalArgumentException"))
        assertTrue(log.contains("at ${firstFrame.className}.${firstFrame.methodName}:${firstFrame.lineNumber}"))
        assertFalse(log.contains("https://"))
        assertFalse(log.contains("auth-cookie-value"))
        assertFalse(log.contains("request-token"))
        assertFalse(log.contains("/storage/emulated/0/Download/private-image.png"))
        assertFalse(log.contains("profile-secret"))
        assertArrayEquals(originalFrames, error.stackTrace)
    }

    private fun downloadFailure(): Throwable = IllegalStateException(
        "https://images.example/generated?token=request-token Cookie: auth-cookie-value " +
            "/storage/emulated/0/Download/private-image.png",
        IllegalArgumentException("profile-secret"),
    )

    private fun response(
        start: (GeckoDownloadTransferListener) -> GeckoDownloadCancellation?,
        discard: () -> Unit,
    ) = GeckoExternalDownloadResponse(
        metadata = BrowserEngineDownloadResponse(
            url = "https://images.example/generated?token=request-token",
            contentDisposition = "attachment; filename=private-image.png",
            mimeType = "image/png",
        ),
        startTransfer = start,
        discard = discard,
    )

    private fun transferStart() = GeckoDownloadTransferStart(
        id = 248,
        fileName = "private-image.png",
        mimeType = "image/png",
        sourceUrl = "https://images.example/generated?token=request-token",
        referrer = "https://images.example/account/profile-secret",
        startedAtMillis = 1_000L,
        totalBytes = 80_000L,
    )

    private class RecordingTransferListener : GeckoDownloadTransferListener {
        val starts = mutableListOf<GeckoDownloadTransferStart>()
        val progress = mutableListOf<Pair<Long, Long>>()
        val completions = mutableListOf<Long>()
        val failures = mutableListOf<GeckoDownloadFailure>()

        override fun onStarted(start: GeckoDownloadTransferStart) {
            starts.add(start)
        }

        override fun onProgress(bytesReceived: Long, totalBytes: Long) {
            progress.add(bytesReceived to totalBytes)
        }

        override fun onComplete(bytesReceived: Long) {
            completions.add(bytesReceived)
        }

        override fun onFailed(reason: GeckoDownloadFailure) {
            failures.add(reason)
        }
    }
}
