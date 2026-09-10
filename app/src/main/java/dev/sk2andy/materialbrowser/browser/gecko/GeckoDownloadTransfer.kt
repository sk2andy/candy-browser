package dev.sk2andy.materialbrowser.browser.gecko

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.BrowserDownloadRequestFactory
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.DownloadDirectoryRules
import dev.sk2andy.materialbrowser.data.DownloadRuntimeRegistry
import dev.sk2andy.materialbrowser.data.SafeDownloadValues
import java.io.Closeable
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import org.mozilla.geckoview.WebResponse

internal data class GeckoDownloadOwner(
    val profileId: String,
    val isPrivate: Boolean,
    val sessionKey: Any,
)

internal data class GeckoDownloadTransferRequest(
    val owner: GeckoDownloadOwner,
    val request: WebRequest,
    val fetchFlags: Int,
    val suggestedFileName: String? = null,
    val allowHttpErrors: Boolean = false,
)

internal data class GeckoDownloadTransferStart(
    val id: Int,
    val fileName: String,
    val mimeType: String,
    val sourceUrl: String,
    val referrer: String?,
    val startedAtMillis: Long,
    val totalBytes: Long,
)

internal interface GeckoDownloadTransferListener {
    fun onStarted(start: GeckoDownloadTransferStart)

    fun onProgress(bytesReceived: Long, totalBytes: Long) = Unit

    fun onComplete(bytesReceived: Long) = Unit

    fun onFailed(reason: GeckoDownloadFailure) = Unit
}

internal enum class GeckoDownloadFailure {
    InvalidRequest,
    Network,
    Http,
    Storage,
    Cancelled,
}

internal fun interface GeckoDownloadCancellation {
    fun cancel()
}

internal interface GeckoDownloadStreamSink {
    fun open(fileName: String, mimeType: String): GeckoDownloadStreamEntry
}

internal interface GeckoDownloadStreamEntry : Closeable {
    val uri: Uri
    val output: OutputStream

    fun commit()

    fun abort()
}

/** Scoped-storage writer. Incomplete and cancelled transfers never become visible downloads. */
internal class MediaStoreDownloadStreamSink(context: Context) : GeckoDownloadStreamSink {
    private val resolver = context.applicationContext.contentResolver
    private val settingsStore = BrowserSessionStore(context.applicationContext)

    override fun open(fileName: String, mimeType: String): GeckoDownloadStreamEntry {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                DownloadDirectoryRules.mediaStoreRelativePath(
                    settingsStore.loadDownloadSettings().downloadSubdirectory,
                ),
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = checkNotNull(
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values),
        ) { "MediaStore rejected download" }
        val output = resolver.openOutputStream(uri, "w") ?: run {
            resolver.delete(uri, null, null)
            error("MediaStore could not open download")
        }
        return MediaStoreDownloadStreamEntry(resolver, uri, output)
    }
}

private class MediaStoreDownloadStreamEntry(
    private val resolver: ContentResolver,
    override val uri: Uri,
    override val output: OutputStream,
) : GeckoDownloadStreamEntry {
    private val finished = AtomicBoolean(false)

    @Synchronized
    override fun commit() {
        if (finished.get()) return
        output.close()
        val updated = resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
            null,
            null,
        )
        check(updated == 1) { "MediaStore could not publish download" }
        finished.set(true)
    }

    @Synchronized
    override fun abort() {
        if (!finished.compareAndSet(false, true)) return
        runCatching(output::close)
        resolver.delete(uri, null, null)
    }

    override fun close() = abort()
}

/**
 * Fetches downloads inside Gecko, retaining its authenticated/private request context, then streams
 * into scoped storage. No cookie material crosses the engine boundary.
 */
internal class GeckoDownloadTransferManager(
    context: Context,
    private val executor: GeckoWebExecutor,
    private val sink: GeckoDownloadStreamSink = MediaStoreDownloadStreamSink(context),
    private val notifier: GeckoDownloadNotifier = GeckoDownloadNotifier(context),
) : AutoCloseable {
    private class Operation(
        val ownerKey: Any,
        val cancelled: AtomicBoolean = AtomicBoolean(false),
        val terminal: AtomicBoolean = AtomicBoolean(false),
    ) {
        @Volatile var response: WebResponse? = null
        @Volatile var entry: GeckoDownloadStreamEntry? = null
    }

    private val io = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "candy-gecko-download").apply { isDaemon = true }
    }
    private val nextId = AtomicInteger(1)
    private val operations = ConcurrentHashMap<Int, Operation>()
    private val closed = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("WrongConstant") // Gecko combines these @IntDef bit flags at runtime.
    fun start(
        transfer: GeckoDownloadTransferRequest,
        listener: GeckoDownloadTransferListener,
    ): GeckoDownloadCancellation? {
        if (closed.get() || !isValid(transfer)) {
            dispatch { listener.onFailed(GeckoDownloadFailure.InvalidRequest) }
            return null
        }
        val id = nextId.getAndUpdate { value -> if (value == Int.MAX_VALUE) 1 else value + 1 }
        val operation = Operation(transfer.owner.sessionKey)
        operations[id] = operation
        val flags = transfer.fetchFlags or if (transfer.owner.isPrivate) {
            GeckoWebExecutor.FETCH_FLAGS_PRIVATE
        } else {
            GeckoWebExecutor.FETCH_FLAGS_NONE
        }
        executor.fetch(transfer.request, flags).accept(
            { response ->
                if (response == null) {
                    fail(id, operation, listener, GeckoDownloadFailure.Network)
                } else {
                    receiveResponse(
                        id = id,
                        operation = operation,
                        response = response,
                        suggestedFileName = transfer.suggestedFileName,
                        referrer = transfer.request.referrer,
                        allowHttpErrors = transfer.allowHttpErrors,
                        listener = listener,
                    )
                }
            },
            { fail(id, operation, listener, GeckoDownloadFailure.Network) },
        )
        return GeckoDownloadCancellation {
            cancel(id, operation, listener)
        }
    }

    /** Consumes Gecko's original response body without a second, context-losing network request. */
    fun startResponse(
        owner: GeckoDownloadOwner,
        response: WebResponse,
        referrer: String?,
        listener: GeckoDownloadTransferListener,
    ): GeckoDownloadCancellation? {
        if (closed.get() || owner.profileId.isBlank() || response.body == null) {
            runCatching { response.body?.close() }
            dispatch { listener.onFailed(GeckoDownloadFailure.InvalidRequest) }
            return null
        }
        val id = nextId.getAndUpdate { value -> if (value == Int.MAX_VALUE) 1 else value + 1 }
        val operation = Operation(owner.sessionKey)
        operations[id] = operation
        receiveResponse(
            id = id,
            operation = operation,
            response = response,
            suggestedFileName = null,
            referrer = referrer,
            allowHttpErrors = false,
            listener = listener,
        )
        return GeckoDownloadCancellation { cancel(id, operation, listener) }
    }

    fun cancelOwner(ownerKey: Any) {
        operations.entries
            .filter { (_, operation) -> operation.ownerKey === ownerKey }
            .forEach { (id, operation) -> cancel(id, operation, null) }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        operations.entries.toList().forEach { (id, operation) -> cancel(id, operation, null) }
        io.shutdownNow()
    }

    private fun receiveResponse(
        id: Int,
        operation: Operation,
        response: WebResponse,
        suggestedFileName: String?,
        referrer: String?,
        allowHttpErrors: Boolean,
        listener: GeckoDownloadTransferListener,
    ) {
        operation.response = response
        val body = response.body
        if (body == null) {
            fail(id, operation, listener, GeckoDownloadFailure.Network)
            return
        }
        if (operation.cancelled.get()) {
            runCatching(body::close)
            operations.remove(id, operation)
            return
        }
        if (!allowHttpErrors && response.statusCode !in 200..299) {
            runCatching(body::close)
            fail(id, operation, listener, GeckoDownloadFailure.Http)
            return
        }
        val contentType = response.header("content-type")
        val contentDisposition = response.header("content-disposition")
            ?: suggestedFileName?.let { name -> "attachment; filename=\"$name\"" }
        val safeMimeType = SafeDownloadValues.mimeType(contentType, response.uri)
        val safeFileName = SafeDownloadValues.fileName(
            response.uri,
            contentDisposition,
            safeMimeType,
        )
        val totalBytes = response.header("content-length")?.toLongOrNull()?.coerceAtLeast(-1L) ?: -1L
        val entry = runCatching { sink.open(safeFileName, safeMimeType) }.getOrElse {
            runCatching(body::close)
            fail(id, operation, listener, GeckoDownloadFailure.Storage)
            return
        }
        val started = GeckoDownloadTransferStart(
            id = id,
            fileName = safeFileName,
            mimeType = safeMimeType,
            sourceUrl = response.uri,
            referrer = referrer,
            startedAtMillis = System.currentTimeMillis(),
            totalBytes = totalBytes,
        )
        synchronized(operation) {
            if (operation.cancelled.get() || operation.terminal.get()) {
                runCatching(body::close)
                runCatching(entry::abort)
                operations.remove(id, operation)
                return
            }
            operation.entry = entry
            DownloadRuntimeRegistry.started(
                id = started.id,
                name = started.fileName,
                source = started.sourceUrl,
                mime = started.mimeType,
                total = started.totalBytes,
                startedAt = started.startedAtMillis,
                mediaStoreId = runCatching { ContentUris.parseId(entry.uri) }.getOrNull(),
            )
            dispatch { listener.onStarted(started) }
            notifier.started(started)
            io.execute { copy(id, operation, body, started, listener) }
        }
    }

    private fun copy(
        id: Int,
        operation: Operation,
        body: java.io.InputStream,
        started: GeckoDownloadTransferStart,
        listener: GeckoDownloadTransferListener,
    ) {
        var received = 0L
        val result = runCatching {
            body.use { input ->
                val entry = checkNotNull(operation.entry)
                entry.output.use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        if (operation.cancelled.get()) throw DownloadCancelledException()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        received += count
                        val progress = received
                        synchronized(operation) {
                            if (operation.cancelled.get() || operation.terminal.get()) {
                                throw DownloadCancelledException()
                            }
                            DownloadRuntimeRegistry.progress(
                                id = id,
                                bytes = progress,
                                total = started.totalBytes,
                                updatedAt = System.currentTimeMillis(),
                            )
                            dispatch { listener.onProgress(progress, started.totalBytes) }
                            notifier.progress(started, received)
                        }
                    }
                }
            }
        }
        if (operation.cancelled.get() || result.exceptionOrNull() is DownloadCancelledException) {
            finishFailure(id, operation, listener, GeckoDownloadFailure.Cancelled)
        } else if (result.isFailure) {
            finishFailure(id, operation, listener, GeckoDownloadFailure.Network)
        } else {
            synchronized(operation) {
                if (operation.terminal.get()) return
                runCatching { operation.entry?.commit() }.fold(
                    onSuccess = {
                        if (!operation.terminal.compareAndSet(false, true)) return@fold
                        operations.remove(id, operation)
                        DownloadRuntimeRegistry.completed(id)
                        dispatch { listener.onComplete(received) }
                        operation.entry?.uri?.let { uri -> notifier.complete(started, uri) }
                    },
                    onFailure = {
                        finishFailure(id, operation, listener, GeckoDownloadFailure.Storage)
                    },
                )
            }
        }
    }

    private fun cancel(
        id: Int,
        operation: Operation,
        listener: GeckoDownloadTransferListener?,
    ) {
        synchronized(operation) {
            if (!operation.terminal.compareAndSet(false, true)) return
            operation.cancelled.set(true)
            runCatching { operation.response?.body?.close() }
            runCatching { operation.entry?.abort() }
            operations.remove(id, operation)
            DownloadRuntimeRegistry.failed(
                id = id,
                cancelled = true,
                updatedAt = System.currentTimeMillis(),
            )
            notifier.cancel(id)
            listener?.let { target -> dispatch { target.onFailed(GeckoDownloadFailure.Cancelled) } }
        }
    }

    private fun fail(
        id: Int,
        operation: Operation,
        listener: GeckoDownloadTransferListener,
        failure: GeckoDownloadFailure,
    ) {
        synchronized(operation) {
            if (!operation.terminal.compareAndSet(false, true)) return
            operations.remove(id, operation)
            DownloadRuntimeRegistry.failed(
                id = id,
                cancelled = failure == GeckoDownloadFailure.Cancelled,
                updatedAt = System.currentTimeMillis(),
            )
            dispatch { listener.onFailed(failure) }
            notifier.cancel(id)
        }
    }

    private fun finishFailure(
        id: Int,
        operation: Operation,
        listener: GeckoDownloadTransferListener,
        failure: GeckoDownloadFailure,
    ) {
        synchronized(operation) {
            if (!operation.terminal.compareAndSet(false, true)) return
            operation.cancelled.set(true)
            runCatching { operation.entry?.abort() }
            operations.remove(id, operation)
            DownloadRuntimeRegistry.failed(
                id = id,
                cancelled = failure == GeckoDownloadFailure.Cancelled,
                updatedAt = System.currentTimeMillis(),
            )
            dispatch { listener.onFailed(failure) }
            notifier.cancel(id)
        }
    }

    private fun isValid(transfer: GeckoDownloadTransferRequest): Boolean =
        transfer.owner.profileId.isNotBlank() &&
            BrowserDownloadRequestFactory.create(url = transfer.request.uri) != null

    private fun dispatch(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    private fun WebResponse.header(name: String): String? = headers.entries.firstOrNull {
        (headerName, _) -> headerName.equals(name, ignoreCase = true)
    }?.value

    private class DownloadCancelledException : Exception()

    private companion object {
        const val COPY_BUFFER_BYTES = 32 * 1024
    }
}

internal class GeckoDownloadNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NotificationManager::class.java)

    fun started(download: GeckoDownloadTransferStart) {
        notify(
            download.id,
            builder(download.fileName)
                .setOngoing(true)
                .setProgress(0, 0, true)
                .build(),
        )
    }

    fun progress(download: GeckoDownloadTransferStart, received: Long) {
        val total = download.totalBytes
        val builder = builder(download.fileName).setOngoing(true)
        if (total > 0L && total <= Int.MAX_VALUE) {
            builder.setProgress(total.toInt(), received.coerceAtMost(total).toInt(), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        notify(download.id, builder.build())
    }

    fun complete(download: GeckoDownloadTransferStart, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, download.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            download.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        notify(
            download.id,
            builder(download.fileName)
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setProgress(0, 0, false)
                .build(),
        )
    }

    fun cancel(id: Int) {
        manager.cancel(id)
    }

    private fun builder(fileName: String): NotificationCompat.Builder {
        ensureChannel()
        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reader_download)
            .setContentTitle(fileName)
            .setContentText(appContext.getString(R.string.download_notification_description))
            .setOnlyAlertOnce(true)
    }

    private fun notify(id: Int, notification: android.app.Notification) {
        if (
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            manager.notify(id, notification)
        }
    }

    private fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.download_notification_description),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private companion object {
        const val CHANNEL_ID = "candy_downloads"
    }
}
