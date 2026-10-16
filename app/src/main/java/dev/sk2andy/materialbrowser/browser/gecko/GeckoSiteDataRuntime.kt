package dev.sk2andy.materialbrowser.browser.gecko

import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import java.math.BigInteger
import java.util.UUID
import org.json.JSONObject
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/** App-only privileged bridge; no page scripts or user extension can request deletion. */
internal class GeckoSiteDataRuntime(
    private val controller: WebExtensionController,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) {
    private data class Request(
        val message: JSONObject,
        val isCurrent: () -> Boolean,
        var onComplete: ((Boolean) -> Unit)?,
        val timeout: Runnable,
        var dispatched: Boolean = false,
    )

    private val requests = linkedMapOf<String, Request>()
    private var started = false
    private var port: WebExtension.Port? = null
    private var ready = false

    fun clear(
        url: String,
        contextId: String?,
        isPrivate: Boolean,
        isCurrent: () -> Boolean,
        onComplete: (Boolean) -> Unit,
    ) {
        val normalizedUrl = BrowserUriPolicy.normalizeHttpUrl(url)
        if (normalizedUrl == null || !isCurrent() || requests.isNotEmpty()) {
            onComplete(false)
            return
        }
        val id = UUID.randomUUID().toString()
        val timeout = Runnable {
            val request = requests[id]
            if (request?.dispatched == true) {
                // Native deletion cannot be cancelled. Keep its slot occupied until it settles.
                val callback = request.onComplete
                request.onComplete = null
                callback?.invoke(false)
            } else complete(id, false)
        }
        requests[id] = Request(
            message = JSONObject()
                .put("id", id)
                .put("url", normalizedUrl)
                // Match StorageController.createSafeSessionContextId in the pinned Gecko AAR.
                .put("contextId", contextId?.let {
                    if (it.isEmpty()) "gvctxempty"
                    else "gvctx${BigInteger(it.toByteArray(Charsets.UTF_8)).toString(16)}"
                } ?: "")
                .put("isPrivate", isPrivate),
            isCurrent = isCurrent,
            onComplete = onComplete,
            timeout = timeout,
        )
        mainHandler.postDelayed(timeout, TIMEOUT_MILLIS)
        if (!started) {
            started = true
            runCatching {
                controller.ensureBuiltIn(LOCATION, EXTENSION_ID).withHandler(mainHandler).accept(
                    { installed ->
                        if (installed == null || installed.id != EXTENSION_ID) fail("Unexpected host identity")
                        else installed.setMessageDelegate(MessageDelegate(installed.metaData.version), NATIVE_APP)
                    },
                    { fail("Host installation failed") },
                )
            }.onFailure { fail("Host installation could not start") }
        }
        dispatch()
    }

    private fun dispatch() {
        val connected = port ?: return
        if (!ready) return
        requests.toMap().forEach { (id, request) ->
            if (!request.dispatched) {
                if (!request.isCurrent()) complete(id, false)
                else {
                    request.dispatched = true
                    runCatching { connected.postMessage(request.message) }
                        .onFailure { fail("Native request could not be sent") }
                }
            }
        }
    }

    private fun complete(id: String, cleared: Boolean) {
        val request = requests.remove(id) ?: return
        if (!cleared) Log.w("GeckoSiteData", "Site data request rejected")
        mainHandler.removeCallbacks(request.timeout)
        request.onComplete?.invoke(cleared)
    }

    private fun fail(reason: String) {
        Log.w("GeckoSiteData", reason)
        val connected = port
        ready = false
        port = null
        started = false
        runCatching { connected?.disconnect() }
        requests.keys.toList().forEach { complete(it, false) }
    }

    private inner class MessageDelegate(private val version: String?) : WebExtension.MessageDelegate {
        override fun onConnect(connected: WebExtension.Port) {
            val sender = connected.sender
            if (connected.name != NATIVE_APP || sender.webExtension.id != EXTENSION_ID ||
                sender.environmentType != WebExtension.MessageSender.ENV_TYPE_EXTENSION ||
                sender.session != null || (port != null && ready)
            ) {
                connected.disconnect()
                return
            }
            val previous = port
            port = connected
            ready = false
            started = true
            connected.setDelegate(object : WebExtension.PortDelegate {
                override fun onPortMessage(message: Any, sourcePort: WebExtension.Port) {
                    if (sourcePort !== port) return
                    val value = message as? JSONObject ?: return
                    if (value.optString("type") == "ready") {
                        // An upgraded built-in host may still have a buffered old connection.
                        if (value.optString("version") != version) {
                            port = null
                            ready = false
                            sourcePort.disconnect()
                            return
                        }
                        ready = true
                        dispatch()
                    } else {
                        complete(value.optString("id"), value.optBoolean("cleared", false))
                    }
                }

                override fun onDisconnect(sourcePort: WebExtension.Port) {
                    if (sourcePort !== port) return
                    port = null
                    ready = false
                    // Upgrades disconnect a buffered old port before the new host is ready.
                    // Only an already sent request has an uncertain result; queued work is safe.
                    requests.filterValues(Request::dispatched).keys.toList().forEach {
                        complete(it, false)
                    }
                }
            })
            previous?.takeIf { it !== connected }?.disconnect()
        }
    }

    companion object {
        const val EXTENSION_ID = "candy-site-data@sk2andy.dev"
        private const val LOCATION = "resource://android/assets/candy_site_data/"
        private const val NATIVE_APP = "candySiteData"
        private const val TIMEOUT_MILLIS = 30_000L
    }
}
