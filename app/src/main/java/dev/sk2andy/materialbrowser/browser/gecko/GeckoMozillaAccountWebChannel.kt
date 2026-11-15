package dev.sk2andy.materialbrowser.browser.gecko

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

internal class GeckoMozillaAccountWebChannel(
    private val session: GeckoSession,
    private val handler: (String) -> String?,
) : AutoCloseable {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var extension: WebExtension? = null
    private val ports = mutableSetOf<WebExtension.Port>()
    private var closed = false

    fun install(controller: WebExtensionController, onReady: (Boolean) -> Unit) {
        controller.ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)
            .withHandler(mainHandler)
            .accept(
                { installed ->
                    if (!closed) {
                        if (installed?.isBuiltIn == true && installed.id == EXTENSION_ID) {
                            extension = installed
                            session.webExtensionController.setMessageDelegate(installed, MessageDelegate(), NATIVE_APP)
                            onReady(true)
                        } else {
                            onReady(false)
                        }
                    }
                },
                { if (!closed) onReady(false) },
            )
    }

    private inner class MessageDelegate : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            val sender = port.sender
            if (
                closed || port.name != NATIVE_APP || sender.session !== session ||
                sender.webExtension.id != EXTENSION_ID ||
                sender.environmentType != WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT ||
                !MozillaAccountWebChannelRules.acceptsSender(sender.url, sender.isTopLevel, session.settings.usePrivateMode)
            ) {
                port.disconnect()
                return
            }
            ports += port
            port.setDelegate(object : WebExtension.PortDelegate {
                override fun onPortMessage(message: Any, sourcePort: WebExtension.Port) {
                    if (closed || sourcePort !in ports || message !is JSONObject) return
                    handler(message.toString())?.let { response ->
                        if (!closed) sourcePort.postMessage(JSONObject(response))
                    }
                }

                override fun onDisconnect(sourcePort: WebExtension.Port) {
                    ports -= sourcePort
                }
            })
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        extension?.let { session.webExtensionController.setMessageDelegate(it, null, NATIVE_APP) }
        ports.toList().forEach(WebExtension.Port::disconnect)
        ports.clear()
        extension = null
    }

    companion object {
        private const val EXTENSION_LOCATION = "resource://android/assets/candy_fxa/"
        private const val EXTENSION_ID = "fxa@mozac.org"
        private const val NATIVE_APP = "mozacWebchannel"
    }
}
