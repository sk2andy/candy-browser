package dev.sk2andy.materialbrowser.browser.gecko

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

internal interface GeckoPrivacyBinding {
    fun update(policy: GeckoPrivacyPolicy, onReady: () -> Unit = {})

    fun close()
}

internal class GeckoViewPrivacyHostRuntime(
    private val controller: WebExtensionController,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    initializationBarrier: GeckoToppingHostRuntime? = null,
) {
    private data class Binding(
        val token: String,
        val session: GeckoSession,
        val sink: GeckoPrivacyEventSink,
        var revision: Long = 0,
        var policy: GeckoPrivacyPolicy = GeckoPrivacyPolicy.Disabled,
        var policyReady: (() -> Unit)? = null,
        var bound: (() -> Unit)? = null,
        var failed: ((String) -> Unit)? = null,
        var timeout: Runnable? = null,
    )

    private val bindings = linkedMapOf<String, Binding>()
    private val pendingUntilReady = mutableListOf<() -> Unit>()
    private var extension: WebExtension? = null
    private var port: WebExtension.Port? = null
    private var extensionRulesReady = false
    private var privatePermissionReady = false
    private var initializationComplete = false
    private var failureDescription: String? = null
    private val initializationTimeout = Runnable {
        fail(IllegalStateException("Candy Privacy host initialization timed out"))
    }

    init {
        val start = { beginInitialization() }
        if (initializationBarrier == null) {
            start()
        } else {
            // Gecko applies private-browsing extension permissions asynchronously. Serialize the
            // two internal hosts so the Privacy host cannot race the Topping host's explicit
            // private denial and accidentally expose registered user scripts to private tabs.
            initializationBarrier.runAfterInitialization(start)
        }
    }

    private fun beginInitialization() {
        mainHandler.postDelayed(initializationTimeout, INITIALIZATION_TIMEOUT_MILLIS)
        controller.ensureBuiltIn(
            CandyPrivacyHostContract.EXTENSION_LOCATION,
            CandyPrivacyHostContract.EXTENSION_ID,
        ).withHandler(mainHandler).accept(
            { installed -> acceptExtension(installed) },
            { error -> fail(error ?: IllegalStateException("Privacy host installation failed")) },
        )
    }

    fun bind(
        session: GeckoSession,
        policy: GeckoPrivacyPolicy,
        sink: GeckoPrivacyEventSink,
        onBound: () -> Unit,
        onFailure: (String) -> Unit,
    ): GeckoPrivacyBinding {
        val token = UUID.randomUUID().toString()
        val binding = Binding(
            token = token,
            session = session,
            sink = sink,
            bound = onBound,
            failed = onFailure,
        )
        bindings[token] = binding
        failureDescription?.let { description ->
            bindings.remove(token)
            binding.failed?.invoke(description)
            return closedBinding()
        }
        armTimeout(binding, "initial session binding")
        runWhenReady(binding) {
            if (bindings[token] !== binding) return@runWhenReady
            val installed = extension ?: return@runWhenReady
            session.webExtensionController.setMessageDelegate(
                installed,
                SessionMessageDelegate(binding),
                CandyPrivacyHostContract.NATIVE_APP,
            )
            publish(binding, policy) {
                session.loadUri("${installed.metaData.baseUrl}bootstrap.html?token=$token")
            }
        }
        return object : GeckoPrivacyBinding {
            override fun update(policy: GeckoPrivacyPolicy, onReady: () -> Unit) {
                binding.policy = policy
                runWhenReady(binding) { publish(binding, policy, onReady) }
            }

            override fun close() {
                bindings.remove(token)
                cancelTimeout(binding)
                binding.policyReady = null
                binding.bound = null
                binding.failed = null
                extension?.let { installed ->
                    session.webExtensionController.setMessageDelegate(
                        installed,
                        null,
                        CandyPrivacyHostContract.NATIVE_APP,
                    )
                }
                port?.postMessage(
                    JSONObject()
                        .put("type", "remove")
                        .put("protocolVersion", CandyPrivacyHostContract.PROTOCOL_VERSION)
                        .put("token", token),
                )
            }
        }
    }

    private fun acceptExtension(installed: WebExtension?) {
        if (installed == null || installed.id != CandyPrivacyHostContract.EXTENSION_ID) {
            fail(IllegalStateException("Unexpected Privacy host identity"))
            return
        }
        extension = installed
        installed.setMessageDelegate(GlobalMessageDelegate(), CandyPrivacyHostContract.NATIVE_APP)
        controller.setAllowedInPrivateBrowsing(installed, true).withHandler(mainHandler).accept(
            {
                privatePermissionReady = true
                releaseIfReady()
            },
            { error -> fail(error ?: IllegalStateException("Privacy private policy failed")) },
        )
    }

    private fun publish(binding: Binding, policy: GeckoPrivacyPolicy, onReady: () -> Unit) {
        if (bindings[binding.token] !== binding) return
        binding.policy = policy
        binding.revision += 1
        binding.policyReady = onReady
        armTimeout(binding, "policy revision ${binding.revision}")
        port?.postMessage(policy.toMessage(binding.token, binding.revision))
    }

    private fun onPortMessage(message: Any, sourcePort: WebExtension.Port) {
        if (sourcePort !== port) return
        val value = message as? JSONObject ?: return
        if (value.optInt("protocolVersion", -1) != CandyPrivacyHostContract.PROTOCOL_VERSION) return
        when (value.optString("type")) {
            "ready" -> {
                extensionRulesReady = true
                releaseIfReady()
            }
            "policy-ready" -> {
                val binding = bindings[value.optString("token")] ?: return
                if (binding.revision != value.optLong("revision", -1)) return
                cancelTimeout(binding)
                binding.policyReady?.also { binding.policyReady = null }?.invoke()
                if (binding.bound != null) armTimeout(binding, "session bootstrap binding")
            }
            "events" -> acceptEvents(value)
            "failed" -> fail(IllegalStateException(value.optString("reason", "Privacy host failed")))
        }
    }

    private fun acceptEvents(value: JSONObject) {
        val binding = bindings[value.optString("token")] ?: return
        if (binding.revision != value.optLong("revision", -1)) return
        val events = value.optJSONArray("events") ?: return
        repeat(minOf(events.length(), MAX_EVENTS_PER_BATCH)) { index ->
            val event = events.optJSONObject(index) ?: return@repeat
            val requestUrl = event.optString("requestUrl").take(MAX_URL_CHARS)
            if (!requestUrl.startsWith("http://") && !requestUrl.startsWith("https://")) return@repeat
            binding.sink.onEvent(
                GeckoPrivacyEvent(
                    requestUrl = requestUrl,
                    pageUrl = event.optString("pageUrl").takeIf(String::isNotBlank)
                        ?.take(MAX_URL_CHARS),
                    ruleId = event.optString("ruleId").takeIf(String::isNotBlank)?.take(128),
                    wasBlocked = event.optString("action") == "B",
                    isBuiltIn = event.optBoolean("builtIn", false),
                ),
            )
        }
    }

    private fun runWhenReady(binding: Binding, action: () -> Unit) {
        if (bindings[binding.token] !== binding) return
        failureDescription?.let { description ->
            binding.failed?.invoke(description)
            return
        }
        if (extensionRulesReady && privatePermissionReady) {
            action()
        } else {
            pendingUntilReady += {
                if (bindings[binding.token] === binding && failureDescription == null) action()
            }
        }
    }

    private fun armTimeout(binding: Binding, phase: String) {
        cancelTimeout(binding)
        val timeout = Runnable {
            if (bindings[binding.token] === binding) {
                fail(
                    IllegalStateException(
                        "Candy Privacy host timed out during $phase",
                    ),
                )
            }
        }
        binding.timeout = timeout
        mainHandler.postDelayed(timeout, BINDING_TIMEOUT_MILLIS)
    }

    private fun cancelTimeout(binding: Binding) {
        binding.timeout?.let(mainHandler::removeCallbacks)
        binding.timeout = null
    }

    private fun releaseIfReady() {
        if (!extensionRulesReady || !privatePermissionReady || failureDescription != null) return
        initializationComplete = true
        mainHandler.removeCallbacks(initializationTimeout)
        val actions = pendingUntilReady.toList()
        pendingUntilReady.clear()
        actions.forEach { action -> action() }
    }

    private fun fail(error: Throwable) {
        if (failureDescription != null) return
        mainHandler.removeCallbacks(initializationTimeout)
        pendingUntilReady.clear()
        val description = error.message
            ?.takeIf(String::isNotBlank)
            ?.take(MAX_FAILURE_DESCRIPTION_CHARS)
            ?: PRIVACY_FAILURE_DESCRIPTION
        failureDescription = description
        bindings.values.forEach { binding ->
            cancelTimeout(binding)
            binding.policyReady = null
            binding.bound = null
            binding.failed?.invoke(description)
        }
        Log.e(TAG, "Candy Privacy host unavailable", error)
    }

    private inner class GlobalMessageDelegate : WebExtension.MessageDelegate {
        override fun onConnect(connectedPort: WebExtension.Port) {
            val sender = connectedPort.sender
            if (connectedPort.name != CandyPrivacyHostContract.NATIVE_APP ||
                sender.webExtension.id != CandyPrivacyHostContract.EXTENSION_ID ||
                sender.environmentType != WebExtension.MessageSender.ENV_TYPE_EXTENSION ||
                sender.session != null
            ) {
                connectedPort.disconnect()
                return
            }
            port?.takeIf { existing -> existing !== connectedPort }?.disconnect()
            port = connectedPort
            connectedPort.setDelegate(object : WebExtension.PortDelegate {
                override fun onPortMessage(message: Any, sourcePort: WebExtension.Port) {
                    this@GeckoViewPrivacyHostRuntime.onPortMessage(message, sourcePort)
                }

                override fun onDisconnect(sourcePort: WebExtension.Port) {
                    if (port === sourcePort) {
                        port = null
                        extensionRulesReady = false
                        if (initializationComplete) {
                            fail(IllegalStateException("Candy Privacy host disconnected"))
                        }
                    }
                }
            })
        }
    }

    private inner class SessionMessageDelegate(
        private val binding: Binding,
    ) : WebExtension.MessageDelegate {
        override fun onMessage(
            nativeApp: String,
            message: Any,
            sender: WebExtension.MessageSender,
        ): GeckoResult<Any> {
            val value = message as? JSONObject
            val accepted = nativeApp == CandyPrivacyHostContract.NATIVE_APP &&
                sender.webExtension.id == CandyPrivacyHostContract.EXTENSION_ID &&
                sender.environmentType == WebExtension.MessageSender.ENV_TYPE_EXTENSION &&
                sender.session === binding.session &&
                value?.optString("type") == "bound" &&
                value.optString("token") == binding.token &&
                value.optLong("revision", -1) == binding.revision
            if (accepted) {
                cancelTimeout(binding)
                binding.bound?.also { binding.bound = null }?.invoke()
            }
            return GeckoResult.fromValue(JSONObject().put("accepted", accepted))
        }
    }

    private companion object {
        const val TAG = "CandyPrivacyHost"
        const val MAX_EVENTS_PER_BATCH = 512
        const val MAX_URL_CHARS = 8 * 1_024
        const val MAX_FAILURE_DESCRIPTION_CHARS = 256
        const val INITIALIZATION_TIMEOUT_MILLIS = 15_000L
        const val BINDING_TIMEOUT_MILLIS = 15_000L
        const val PRIVACY_FAILURE_DESCRIPTION = "Candy Privacy protection failed to initialize"
    }
}

private fun closedBinding(): GeckoPrivacyBinding = object : GeckoPrivacyBinding {
    override fun update(policy: GeckoPrivacyPolicy, onReady: () -> Unit) = Unit

    override fun close() = Unit
}
