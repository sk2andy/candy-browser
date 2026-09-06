package dev.sk2andy.materialbrowser.browser.gecko

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.UiThread
import dev.sk2andy.materialbrowser.browser.userscript.UserScript
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/** GeckoView edge for Candy's private, bundled MV3 Topping host. */
internal class GeckoViewToppingHostRuntime(
    private val controller: WebExtensionController,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) : GeckoToppingHostRuntime {
    private val gate = GeckoToppingInitializationGate()
    private var plan = GeckoToppingPlan(emptyList(), emptyMap())
    private var revision = 0L
    private var hostInstalled = false
    private var port: WebExtension.Port? = null
    private val timeout = Runnable {
        if (gate.state == GeckoToppingHostState.Initializing) {
            fail(IllegalStateException("Candy Topping host initialization timed out"))
        }
    }

    override val state: GeckoToppingHostState
        get() = gate.state

    init {
        mainHandler.postDelayed(timeout, INITIALIZATION_TIMEOUT_MILLIS)
        ensureHost()
    }

    @UiThread
    override fun reconcile(scripts: List<UserScript>) {
        plan = CandyToppingHostCompiler.compile(scripts)
        revision += 1
        if (!gate.beginRevision()) return
        mainHandler.removeCallbacks(timeout)
        mainHandler.postDelayed(timeout, INITIALIZATION_TIMEOUT_MILLIS)
        publishPlanIfReady()
    }

    override fun runAfterInitialization(action: () -> Unit) {
        gate.runAfterInitialization(action)
    }

    override fun setStateListener(listener: (GeckoToppingHostState) -> Unit) {
        gate.setStateListener(listener)
    }

    private fun ensureHost() {
        controller.ensureBuiltIn(
            CandyToppingHostContract.EXTENSION_LOCATION,
            CandyToppingHostContract.EXTENSION_ID,
        ).withHandler(mainHandler).accept(
            { extension -> mainHandler.post { acceptHost(extension) } },
            { error -> fail(error ?: IllegalStateException("Topping host installation failed")) },
        )
    }

    @UiThread
    private fun acceptHost(extension: WebExtension?) {
        if (
            extension == null ||
            !extension.isBuiltIn ||
            extension.id != CandyToppingHostContract.EXTENSION_ID
        ) {
            fail(IllegalStateException("Unexpected Candy Topping host identity"))
            return
        }
        extension.setMessageDelegate(
            MessageDelegate(),
            CandyToppingHostContract.NATIVE_APP,
        )
        denyPrivateBrowsing(extension)
    }

    private fun denyPrivateBrowsing(extension: WebExtension) {
        controller.setAllowedInPrivateBrowsing(extension, false)
            .withHandler(mainHandler)
            .accept(
                { updated ->
                    if (updated?.id != CandyToppingHostContract.EXTENSION_ID) {
                        fail(IllegalStateException("Topping host private policy failed"))
                    } else {
                        // Gecko built-ins may remain marked private-capable. Every registered
                        // user script therefore also carries an in-world incognito guard.
                        grantUserScriptsPermission()
                    }
                },
                { error -> fail(error ?: IllegalStateException("Private policy failed")) },
            )
    }

    private fun grantUserScriptsPermission() {
        controller.addOptionalPermissions(
            CandyToppingHostContract.EXTENSION_ID,
            arrayOf(CandyToppingHostContract.OPTIONAL_PERMISSION),
            emptyArray(),
        ).withHandler(mainHandler).accept(
            { extension ->
                if (extension?.id != CandyToppingHostContract.EXTENSION_ID) {
                    fail(IllegalStateException("Topping host permission identity changed"))
                    return@accept
                }
                hostInstalled = true
                publishPlanIfReady()
            },
            { error -> fail(error ?: IllegalStateException("userScripts permission failed")) },
        )
    }

    private fun publishPlanIfReady() {
        val activePort = port ?: return
        if (!hostInstalled) return
        runCatching { activePort.postMessage(plan.toMessage(revision)) }
            .onFailure(::fail)
    }

    private fun onPortMessage(message: Any, sourcePort: WebExtension.Port) {
        if (gate.state != GeckoToppingHostState.Initializing) return
        if (sourcePort !== port) return
        val value = message as? JSONObject ?: return
        if (value.optInt("protocolVersion", -1) != CandyToppingHostContract.PROTOCOL_VERSION) return
        val responseRevision = value.optLong("revision", -1L)
        if (responseRevision != revision) return
        when (value.optString("type")) {
            "ready" -> {
                mainHandler.removeCallbacks(timeout)
                gate.complete(GeckoToppingHostState.Ready)
            }
            "failed" -> fail(
                IllegalStateException(
                    value.optString("reason", "Topping host reconciliation failed")
                        .take(MAX_FAILURE_CHARS),
                ),
            )
        }
    }

    private fun fail(error: Throwable) {
        Log.e(TAG, "Candy Topping host unavailable", error)
        mainHandler.removeCallbacks(timeout)
        gate.complete(GeckoToppingHostState.Unavailable)
    }

    private inner class MessageDelegate : WebExtension.MessageDelegate {
        override fun onConnect(connectedPort: WebExtension.Port) {
            val sender = connectedPort.sender
            if (
                connectedPort.name != CandyToppingHostContract.NATIVE_APP ||
                sender.webExtension.id != CandyToppingHostContract.EXTENSION_ID ||
                sender.environmentType != WebExtension.MessageSender.ENV_TYPE_EXTENSION ||
                sender.session != null
            ) {
                connectedPort.disconnect()
                return
            }
            port?.takeIf { existing -> existing !== connectedPort }?.disconnect()
            port = connectedPort
            connectedPort.setDelegate(
                object : WebExtension.PortDelegate {
                    override fun onPortMessage(message: Any, sourcePort: WebExtension.Port) {
                        this@GeckoViewToppingHostRuntime.onPortMessage(message, sourcePort)
                    }

                    override fun onDisconnect(sourcePort: WebExtension.Port) {
                        if (port === sourcePort) port = null
                    }
                },
            )
            publishPlanIfReady()
        }
    }

    private companion object {
        const val TAG = "CandyToppingHost"
        const val INITIALIZATION_TIMEOUT_MILLIS = 15_000L
        const val MAX_FAILURE_CHARS = 512
    }
}

private fun GeckoToppingPlan.toMessage(revision: Long): JSONObject = JSONObject()
    .put("type", "reconcile")
    .put("protocolVersion", CandyToppingHostContract.PROTOCOL_VERSION)
    .put("revision", revision)
    .put(
        "scripts",
        JSONArray().also { values ->
            registrations.forEach { registration -> values.put(registration.toJson()) }
        },
    )

private fun GeckoToppingRegistration.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("worldId", worldId)
    .put("js", JSONArray().put(JSONObject().put("code", javascript)))
    .put("matches", JSONArray(matchPatterns))
    .put("includeGlobs", JSONArray(includeGlobs))
    .put("excludeGlobs", JSONArray(excludeGlobs))
    .put("runAt", runAt)
    .put("allFrames", false)
    .put("world", "USER_SCRIPT")
