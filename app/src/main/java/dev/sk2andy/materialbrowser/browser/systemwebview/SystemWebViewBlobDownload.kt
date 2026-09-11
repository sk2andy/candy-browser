package dev.sk2andy.materialbrowser.browser.systemwebview

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadCancellation
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadFailure
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadNotifier
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadStreamEntry
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadStreamSink
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadTransferListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoDownloadTransferStart
import dev.sk2andy.materialbrowser.browser.gecko.MediaStoreDownloadStreamSink
import dev.sk2andy.materialbrowser.data.DownloadRuntimeRegistry
import dev.sk2andy.materialbrowser.data.SafeDownloadValues
import java.net.URI
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject

/** Resolves renderer-owned `blob:` downloads and streams them into scoped storage. */
internal class SystemWebViewBlobDownloadTransfer(
    context: Context,
    private val webView: WebView,
    private val sink: GeckoDownloadStreamSink = MediaStoreDownloadStreamSink(context),
    private val notifier: GeckoDownloadNotifier = GeckoDownloadNotifier(context),
) : AutoCloseable {
    private data class Operation(
        val id: Int,
        val blobUrl: String,
        val pageUrl: String,
        val contentDisposition: String?,
        val reportedMimeType: String?,
        val referrer: String?,
        val listener: GeckoDownloadTransferListener,
        val terminal: AtomicBoolean = AtomicBoolean(false),
        var entry: GeckoDownloadStreamEntry? = null,
        var start: GeckoDownloadTransferStart? = null,
        var nextSequence: Int = 0,
        var receivedBytes: Long = 0,
        var completed: Boolean = false,
        var timeout: Runnable? = null,
    )

    private val token = UUID.randomUUID().toString().replace("-", "")
    private val operations = ConcurrentHashMap<Int, Operation>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "candy-system-blob-download").apply { isDaemon = true }
    }
    private var listenerInstalled = false
    @Volatile
    private var closed = false

    init {
        installListener()
    }

    fun start(
        blobUrl: String,
        pageUrl: String?,
        contentDisposition: String?,
        mimeType: String?,
        referrer: String?,
        listener: GeckoDownloadTransferListener,
    ): GeckoDownloadCancellation? {
        val safePageUrl = pageUrl?.takeIf {
            SystemWebViewBlobDownloadRules.isSameOriginBlob(blobUrl, it)
        }
        if (safePageUrl == null || closed || !installListener()) {
            dispatch { listener.onFailed(GeckoDownloadFailure.InvalidRequest) }
            return null
        }
        val id = NEXT_ID.getAndUpdate { value -> if (value == Int.MAX_VALUE) 1 else value + 1 }
        val operation = Operation(
            id = id,
            blobUrl = blobUrl,
            pageUrl = safePageUrl,
            contentDisposition = contentDisposition,
            reportedMimeType = mimeType,
            referrer = referrer,
            listener = listener,
        )
        operations[id] = operation
        webView.evaluateJavascript(
            SystemWebViewBlobDownloadScript.create(
                token = token,
                id = id,
                blobUrl = blobUrl,
                reportedMimeType = mimeType,
            ),
            null,
        )
        scheduleTimeout(operation)
        return GeckoDownloadCancellation { cancel(id, GeckoDownloadFailure.Cancelled) }
    }

    fun cancelAll() {
        if (closed) return
        io.execute {
            operations.values.toList().forEach { operation ->
                fail(operation, GeckoDownloadFailure.Cancelled)
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        io.execute {
            operations.values.toList().forEach { operation ->
                fail(operation, GeckoDownloadFailure.Cancelled)
            }
        }
        io.shutdown()
        if (
            listenerInstalled &&
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        ) {
            runCatching { WebViewCompat.removeWebMessageListener(webView, BRIDGE_NAME) }
        }
        listenerInstalled = false
    }

    private fun installListener(): Boolean {
        if (listenerInstalled) return true
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return false
        return runCatching {
            WebViewCompat.addWebMessageListener(webView, BRIDGE_NAME, setOf("*")) {
                    _, message, sourceOrigin, isMainFrame, replyProxy,
                ->
                receive(message.data, sourceOrigin, isMainFrame, replyProxy)
            }
            listenerInstalled = true
        }.isSuccess
    }

    private fun receive(
        raw: String?,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        val message = SystemWebViewBlobDownloadMessage.parse(raw, token) ?: return
        val operation = operations[message.id] ?: return
        if (
            !isMainFrame ||
            !SystemWebViewBlobDownloadRules.isSameOrigin(sourceOrigin.toString(), operation.pageUrl)
        ) {
            io.execute {
                fail(operation, GeckoDownloadFailure.InvalidRequest)
                reply(replyProxy, message, accepted = false)
            }
            return
        }
        io.execute {
            val accepted = synchronized(operation) {
                if (operation.terminal.get() || operation.nextSequence != message.sequence) {
                    fail(operation, GeckoDownloadFailure.InvalidRequest)
                    false
                } else {
                    when (message) {
                        is SystemWebViewBlobDownloadMessage.Start -> start(operation, message)
                        is SystemWebViewBlobDownloadMessage.Chunk -> append(operation, message)
                        is SystemWebViewBlobDownloadMessage.Finish -> finish(operation, message)
                        is SystemWebViewBlobDownloadMessage.Error -> {
                            fail(operation, GeckoDownloadFailure.Network)
                        }
                    }
                    if (message is SystemWebViewBlobDownloadMessage.Finish) {
                        operation.completed
                    } else {
                        operations[operation.id] === operation && !operation.terminal.get()
                    }
                }
            }
            reply(replyProxy, message, accepted)
        }
    }

    private fun reply(
        replyProxy: JavaScriptReplyProxy,
        message: SystemWebViewBlobDownloadMessage,
        accepted: Boolean,
    ) {
        if (
            accepted &&
            message !is SystemWebViewBlobDownloadMessage.Finish &&
            message !is SystemWebViewBlobDownloadMessage.Error
        ) {
            operations[message.id]?.let(::scheduleTimeout)
        }
        mainHandler.post {
            if (!closed) {
                replyProxy.postMessage(SystemWebViewBlobDownloadMessage.reply(message, accepted))
            }
        }
    }

    private fun scheduleTimeout(operation: Operation) {
        val expectedSequence = operation.nextSequence
        val timeout = Runnable {
            val current = operations[operation.id]
            if (current === operation && current.nextSequence == expectedSequence) {
                cancel(operation.id, GeckoDownloadFailure.Network)
            }
        }
        synchronized(operation) {
            operation.timeout?.let(mainHandler::removeCallbacks)
            operation.timeout = timeout
        }
        mainHandler.postDelayed(timeout, MESSAGE_TIMEOUT_MILLIS)
    }

    private fun clearTimeout(operation: Operation) {
        synchronized(operation) {
            operation.timeout?.let(mainHandler::removeCallbacks)
            operation.timeout = null
        }
    }

    private fun start(
        operation: Operation,
        message: SystemWebViewBlobDownloadMessage.Start,
    ) {
        if (message.totalBytes !in 0..MAX_DOWNLOAD_BYTES) {
            fail(operation, GeckoDownloadFailure.InvalidRequest)
            return
        }
        val mimeType = SafeDownloadValues.mimeType(
            message.mimeType.ifBlank { operation.reportedMimeType },
        )
        val fileName = SafeDownloadValues.fileName(
            operation.blobUrl,
            operation.contentDisposition,
            mimeType,
        )
        val entry = runCatching { sink.open(fileName, mimeType) }.getOrElse {
            fail(operation, GeckoDownloadFailure.Storage)
            return
        }
        val start = GeckoDownloadTransferStart(
            id = operation.id,
            fileName = fileName,
            mimeType = mimeType,
            sourceUrl = operation.pageUrl,
            referrer = operation.referrer,
            startedAtMillis = System.currentTimeMillis(),
            totalBytes = message.totalBytes,
        )
        operation.entry = entry
        operation.start = start
        operation.nextSequence++
        DownloadRuntimeRegistry.started(
            id = start.id,
            name = start.fileName,
            source = start.sourceUrl,
            mime = start.mimeType,
            total = start.totalBytes,
            startedAt = start.startedAtMillis,
            mediaStoreId = runCatching {
                android.content.ContentUris.parseId(entry.uri)
            }.getOrNull(),
        )
        dispatch { operation.listener.onStarted(start) }
        notifier.started(start)
    }

    private fun append(
        operation: Operation,
        message: SystemWebViewBlobDownloadMessage.Chunk,
    ) {
        val entry = operation.entry ?: return fail(operation, GeckoDownloadFailure.InvalidRequest)
        val start = operation.start ?: return fail(operation, GeckoDownloadFailure.InvalidRequest)
        val bytes = runCatching { Base64.decode(message.encodedBytes, Base64.NO_WRAP) }
            .getOrElse {
                fail(operation, GeckoDownloadFailure.InvalidRequest)
                return
            }
        if (
            bytes.isEmpty() ||
            bytes.size > MAX_CHUNK_BYTES ||
            operation.receivedBytes + bytes.size > start.totalBytes
        ) {
            fail(operation, GeckoDownloadFailure.InvalidRequest)
            return
        }
        if (runCatching { entry.output.write(bytes) }.isFailure) {
            fail(operation, GeckoDownloadFailure.Storage)
            return
        }
        operation.receivedBytes += bytes.size
        operation.nextSequence++
        DownloadRuntimeRegistry.progress(
            id = operation.id,
            bytes = operation.receivedBytes,
            total = start.totalBytes,
            updatedAt = System.currentTimeMillis(),
        )
        dispatch { operation.listener.onProgress(operation.receivedBytes, start.totalBytes) }
        notifier.progress(start, operation.receivedBytes)
    }

    private fun finish(
        operation: Operation,
        message: SystemWebViewBlobDownloadMessage.Finish,
    ) {
        val entry = operation.entry ?: return fail(operation, GeckoDownloadFailure.InvalidRequest)
        val start = operation.start ?: return fail(operation, GeckoDownloadFailure.InvalidRequest)
        if (operation.receivedBytes != start.totalBytes) {
            fail(operation, GeckoDownloadFailure.Network)
            return
        }
        if (runCatching(entry::commit).isFailure) {
            fail(operation, GeckoDownloadFailure.Storage)
            return
        }
        operation.nextSequence = message.sequence + 1
        operation.completed = true
        operation.terminal.set(true)
        operations.remove(operation.id)
        clearTimeout(operation)
        DownloadRuntimeRegistry.completed(operation.id)
        dispatch { operation.listener.onComplete(operation.receivedBytes) }
        notifier.complete(start, entry.uri)
    }

    private fun cancel(id: Int, reason: GeckoDownloadFailure) {
        if (!closed) {
            io.execute { operations[id]?.let { operation -> fail(operation, reason) } }
        }
    }

    private fun fail(operation: Operation, reason: GeckoDownloadFailure) {
        if (!operation.terminal.compareAndSet(false, true)) return
        operations.remove(operation.id)
        clearTimeout(operation)
        runCatching { operation.entry?.abort() }
        if (operation.start != null) {
            DownloadRuntimeRegistry.failed(
                id = operation.id,
                cancelled = reason == GeckoDownloadFailure.Cancelled,
                updatedAt = System.currentTimeMillis(),
            )
            notifier.cancel(operation.id)
        }
        dispatch { operation.listener.onFailed(reason) }
    }

    private fun dispatch(action: () -> Unit) {
        mainHandler.post {
            if (!closed) action()
        }
    }

    private companion object {
        val NEXT_ID = AtomicInteger(1)
        const val BRIDGE_NAME = "CandySystemBlobDownloadBridge"
        const val MAX_CHUNK_BYTES = 24 * 1_024
        const val MAX_DOWNLOAD_BYTES = 1_073_741_824L
        const val MESSAGE_TIMEOUT_MILLIS = 30_000L
    }
}

internal object SystemWebViewBlobDownloadRules {
    fun isSameOriginBlob(blobUrl: String, pageUrl: String): Boolean {
        val nested = runCatching {
            val blob = URI(blobUrl.trim())
            if (!blob.scheme.equals("blob", ignoreCase = true)) return false
            URI(blob.schemeSpecificPart)
        }.getOrNull() ?: return false
        return isSameOrigin(nested.toString(), pageUrl)
    }

    fun isSameOrigin(firstUrl: String, secondUrl: String): Boolean {
        val first = origin(firstUrl) ?: return false
        val second = origin(secondUrl) ?: return false
        return first == second
    }

    private fun origin(value: String): Triple<String, String, Int>? = runCatching {
        val uri = URI(value.trim())
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.userInfo != null) return null
        val host = uri.host?.lowercase(Locale.ROOT)?.takeIf(String::isNotBlank) ?: return null
        val port = uri.port.takeUnless { it == -1 } ?: if (scheme == "https") 443 else 80
        Triple(scheme, host, port)
    }.getOrNull()
}

internal sealed interface SystemWebViewBlobDownloadMessage {
    val id: Int
    val sequence: Int

    data class Start(
        override val id: Int,
        override val sequence: Int,
        val mimeType: String,
        val totalBytes: Long,
    ) : SystemWebViewBlobDownloadMessage

    data class Chunk(
        override val id: Int,
        override val sequence: Int,
        val encodedBytes: String,
    ) : SystemWebViewBlobDownloadMessage

    data class Finish(
        override val id: Int,
        override val sequence: Int,
    ) : SystemWebViewBlobDownloadMessage

    data class Error(
        override val id: Int,
        override val sequence: Int,
    ) : SystemWebViewBlobDownloadMessage

    companion object {
        fun parse(raw: String?, expectedToken: String): SystemWebViewBlobDownloadMessage? {
            if (raw == null || raw.length !in 1..MAX_MESSAGE_LENGTH) return null
            return runCatching {
                val json = JSONObject(raw)
                if (json.optInt("v") != 1 || json.optString("token") != expectedToken) return null
                val id = json.optInt("id").takeIf { it > 0 } ?: return null
                val sequence = json.optInt("sequence", -1).takeIf { it >= 0 } ?: return null
                when (json.optString("type")) {
                    "start" -> Start(
                        id = id,
                        sequence = sequence,
                        mimeType = json.optString("mime").take(MAX_MIME_LENGTH),
                        totalBytes = json.optLong("total", -1),
                    )
                    "chunk" -> Chunk(
                        id = id,
                        sequence = sequence,
                        encodedBytes = json.optString("data"),
                    )
                    "finish" -> Finish(id, sequence)
                    "error" -> Error(id, sequence)
                    else -> null
                }
            }.getOrNull()
        }

        fun reply(message: SystemWebViewBlobDownloadMessage, accepted: Boolean): String =
            JSONObject()
                .put("id", message.id)
                .put("sequence", message.sequence)
                .put("ok", accepted)
                .toString()

        private const val MAX_MESSAGE_LENGTH = 40_000
        private const val MAX_MIME_LENGTH = 256
    }
}

internal object SystemWebViewBlobDownloadScript {
    fun create(token: String, id: Int, blobUrl: String, reportedMimeType: String?): String {
        require(token.matches(Regex("[A-Za-z0-9_-]{32,80}")))
        require(id > 0)
        return """
            (async () => {
              const bridge = globalThis.CandySystemBlobDownloadBridge;
              if (!bridge || typeof bridge.postMessage !== 'function') return;
              const token = ${JSONObject.quote(token)};
              const id = $id;
              const runtime = globalThis.__candySystemBlobDownloads ||= { pending: new Map() };
              if (!runtime.listenerInstalled) {
                runtime.listenerInstalled = true;
                bridge.addEventListener('message', event => {
                  try {
                    const reply = JSON.parse(event.data);
                    const key = `${'$'}{reply.id}:${'$'}{reply.sequence}`;
                    const waiter = runtime.pending.get(key);
                    if (!waiter) return;
                    runtime.pending.delete(key);
                    reply.ok ? waiter.resolve() : waiter.reject(new Error('rejected'));
                  } catch (_) {}
                });
              }
              const send = payload => new Promise((resolve, reject) => {
                const key = `${'$'}{payload.id}:${'$'}{payload.sequence}`;
                const timeout = setTimeout(() => {
                  runtime.pending.delete(key);
                  reject(new Error('timeout'));
                }, 15000);
                runtime.pending.set(key, {
                  resolve: () => { clearTimeout(timeout); resolve(); },
                  reject: error => { clearTimeout(timeout); reject(error); }
                });
                bridge.postMessage(JSON.stringify(payload));
              });
              let sequence = 0;
              try {
                const response = await fetch(${JSONObject.quote(blobUrl)});
                if (!response.ok) throw new Error(`HTTP ${'$'}{response.status}`);
                const blob = await response.blob();
                await send({
                  v: 1,
                  token,
                  id,
                  sequence: sequence++,
                  type: 'start',
                  mime: blob.type || ${JSONObject.quote(reportedMimeType.orEmpty())},
                  total: blob.size
                });
                const chunkSize = 24 * 1024;
                for (let offset = 0; offset < blob.size; offset += chunkSize) {
                  const bytes = new Uint8Array(
                    await blob.slice(offset, Math.min(offset + chunkSize, blob.size)).arrayBuffer()
                  );
                  let binary = '';
                  for (let index = 0; index < bytes.length; index++) {
                    binary += String.fromCharCode(bytes[index]);
                  }
                  await send({
                    v: 1,
                    token,
                    id,
                    sequence: sequence++,
                    type: 'chunk',
                    data: btoa(binary)
                  });
                }
                await send({ v: 1, token, id, sequence, type: 'finish' });
              } catch (_) {
                bridge.postMessage(JSON.stringify({
                  v: 1,
                  token,
                  id,
                  sequence,
                  type: 'error'
                }));
              }
            })();
        """.trimIndent()
    }
}
