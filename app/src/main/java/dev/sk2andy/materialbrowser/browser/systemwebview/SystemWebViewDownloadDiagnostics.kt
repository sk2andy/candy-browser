package dev.sk2andy.materialbrowser.browser.systemwebview

import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadFailure
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadTransferListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadTransferStart
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExternalDownloadResponse
import dev.sk2andy.materialbrowser.data.AppLogEvent
import dev.sk2andy.materialbrowser.data.AppLogging

/** Session identity stays captured after a private tab closes; no request metadata is logged. */
internal class SystemWebViewDownloadDiagnostics(
    private val isPrivate: Boolean,
    private val recorder: (AppLogEvent, Throwable?) -> Unit = { event, error ->
        AppLogging.recordDiagnostic(event, isPrivate, error)
    },
) {
    fun record(event: AppLogEvent, error: Throwable? = null) {
        if (!isPrivate) recorder(event, error)
    }

    fun response(response: GeckoExternalDownloadResponse): GeckoExternalDownloadResponse {
        record(AppLogEvent.SystemDownloadResponseReceived)
        return GeckoExternalDownloadResponse(
            metadata = response.metadata,
            startTransfer = { listener ->
                record(AppLogEvent.SystemDownloadRequested)
                response.start(transferListener(listener))
            },
            discard = response::close,
        )
    }

    fun transferListener(listener: GeckoDownloadTransferListener): GeckoDownloadTransferListener =
        object : GeckoDownloadTransferListener {
            override fun onStarted(start: GeckoDownloadTransferStart) {
                record(AppLogEvent.SystemDownloadStarted)
                listener.onStarted(start)
            }

            override fun onProgress(bytesReceived: Long, totalBytes: Long) {
                listener.onProgress(bytesReceived, totalBytes)
            }

            override fun onComplete(bytesReceived: Long) {
                record(AppLogEvent.SystemDownloadCompleted)
                listener.onComplete(bytesReceived)
            }

            override fun onFailed(reason: GeckoDownloadFailure) {
                record(
                    when (reason) {
                        GeckoDownloadFailure.InvalidRequest -> AppLogEvent.SystemDownloadInvalidRequest
                        GeckoDownloadFailure.Network -> AppLogEvent.SystemDownloadNetworkFailed
                        GeckoDownloadFailure.Http -> AppLogEvent.SystemDownloadHttpFailed
                        GeckoDownloadFailure.Storage -> AppLogEvent.SystemDownloadStorageFailed
                        GeckoDownloadFailure.Cancelled -> AppLogEvent.SystemDownloadCancelled
                    },
                )
                listener.onFailed(reason)
            }
        }
}
