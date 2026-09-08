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

    fun extractPageForReader(onResult: (String?) -> Unit)

    fun setPictureInPicturePlaybackExpected(expected: Boolean)

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
        var handshake: GeckoPrivacyBindingHandshake = GeckoPrivacyBindingHandshake(),
        var policy: GeckoPrivacyPolicy = GeckoPrivacyPolicy.Disabled,
        val policyReadyCallbacks: MutableList<() -> Unit> = mutableListOf(),
        var bound: (() -> Unit)? = null,
        var failed: ((String) -> Unit)? = null,
        var timeout: Runnable? = null,
        var readerRequestId: Long? = null,
        var readerResult: ((String?) -> Unit)? = null,
        var readerTimeout: Runnable? = null,
        var pictureInPicturePlaybackExpected: Boolean = false,
    )

    private val bindings = linkedMapOf<String, Binding>()
    private val pendingUntilReady = mutableListOf<() -> Unit>()
    private val initializationCallbacks = mutableListOf<(Boolean) -> Unit>()
    private var extension: WebExtension? = null
    private var port: WebExtension.Port? = null
    private var extensionRulesReady = false
    private var hostPermissionsReady = false
    private var initializationComplete = false
    private var failureDescription: String? = null
    private var nextReaderRequestId = 0L
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

    fun runAfterInitialization(action: (Boolean) -> Unit) {
        when {
            initializationComplete -> action(true)
            failureDescription != null -> action(false)
            else -> initializationCallbacks += action
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
        runWhenReady(binding) {
            if (bindings[token] !== binding) return@runWhenReady
            val installed = extension ?: return@runWhenReady
            session.webExtensionController.setMessageDelegate(
                installed,
                SessionMessageDelegate(binding),
                CandyPrivacyHostContract.NATIVE_APP,
            )
            publish(binding, policy)
        }
        return object : GeckoPrivacyBinding {
            override fun update(policy: GeckoPrivacyPolicy, onReady: () -> Unit) {
                binding.policy = policy
                runWhenReady(binding) { publish(binding, policy, onReady) }
            }

            override fun extractPageForReader(onResult: (String?) -> Unit) {
                if (bindings[token] !== binding) {
                    onResult(null)
                    return
                }
                failureDescription?.let {
                    onResult(null)
                    return
                }
                runWhenReady(binding) {
                    requestReaderExtraction(binding, onResult)
                }
            }

            override fun setPictureInPicturePlaybackExpected(expected: Boolean) {
                binding.pictureInPicturePlaybackExpected = expected
                runWhenReady(binding) {
                    if (
                        bindings[token] === binding &&
                        binding.pictureInPicturePlaybackExpected == expected
                    ) {
                        port?.postMessage(
                            pictureInPicturePlaybackMessage(
                                token = token,
                                revision = binding.handshake.publishedRevision,
                                expected = expected,
                            ),
                        )
                    }
                }
            }

            override fun close() {
                bindings.remove(token)
                cancelTimeout(binding)
                val readerResult = clearReaderRequest(binding)
                binding.policyReadyCallbacks.clear()
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
                readerResult?.invoke(null)
            }
        }
    }

    fun isTrustedBootstrapNavigation(
        session: GeckoSession,
        url: String,
        isDirectNavigation: Boolean,
        hasUserGesture: Boolean,
        isRedirect: Boolean,
    ): Boolean {
        val binding = bindings.values.firstOrNull { candidate -> candidate.session === session }
            ?: return false
        val extensionBaseUrl = extension?.metaData?.baseUrl ?: return false
        return CandyPrivacyHostContract.isTrustedBootstrapNavigation(
            url = url,
            extensionBaseUrl = extensionBaseUrl,
            token = binding.token,
            isDirectNavigation = isDirectNavigation,
            hasUserGesture = hasUserGesture,
            isRedirect = isRedirect,
        )
    }

    fun isBootstrapNavigation(session: GeckoSession, url: String?): Boolean {
        val binding = bindings.values.firstOrNull { candidate -> candidate.session === session }
            ?: return false
        val extensionBaseUrl = extension?.metaData?.baseUrl ?: return false
        return url == "${extensionBaseUrl}bootstrap.html?token=${binding.token}"
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
                hostPermissionsReady = true
                releaseIfReady()
            },
            { error -> fail(error ?: IllegalStateException("Privacy private policy failed")) },
        )
    }

    private fun publish(
        binding: Binding,
        policy: GeckoPrivacyPolicy,
        onReady: (() -> Unit)? = null,
    ) {
        if (bindings[binding.token] !== binding) return
        val readerResult = clearReaderRequest(binding)
        binding.policy = policy
        binding.handshake = GeckoPrivacyBindingHandshakeRules.publish(binding.handshake)
        onReady?.let(binding.policyReadyCallbacks::add)
        refreshTimeout(binding)
        port?.postMessage(
            policy.toMessage(binding.token, binding.handshake.publishedRevision),
        )
        readerResult?.invoke(null)
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
                val transition = GeckoPrivacyBindingHandshakeRules.acknowledgePolicy(
                    state = binding.handshake,
                    revision = value.optLong("revision", -1),
                )
                if (!transition.accepted) return
                binding.handshake = transition.state
                if (transition.startBootstrap) startBootstrap(binding)
                if (binding.handshake.isCurrentPolicyAcknowledged) {
                    val callbacks = binding.policyReadyCallbacks.toList()
                    binding.policyReadyCallbacks.clear()
                    callbacks.forEach { callback -> callback() }
                }
                refreshTimeout(binding)
                completeBindingIfReady(binding)
            }
            "session-bound" -> {
                val binding = bindings[value.optString("token")] ?: return
                val transition = GeckoPrivacyBindingHandshakeRules.bindSession(
                    state = binding.handshake,
                    revision = value.optLong("revision", -1),
                )
                if (!transition.accepted) return
                binding.handshake = transition.state
                refreshTimeout(binding)
                completeBindingIfReady(binding)
            }
            "events" -> acceptEvents(value)
            "safe-area-fallback" -> acceptSafeAreaFallback(value)
            "reader-result" -> acceptReaderResult(value)
            "failed" -> fail(IllegalStateException(value.optString("reason", "Privacy host failed")))
        }
    }

    private fun requestReaderExtraction(
        binding: Binding,
        onResult: (String?) -> Unit,
    ) {
        if (bindings[binding.token] !== binding || port == null) {
            onResult(null)
            return
        }
        val previousResult = clearReaderRequest(binding)
        nextReaderRequestId = if (nextReaderRequestId >= MAX_SAFE_JAVASCRIPT_INTEGER) {
            1L
        } else {
            nextReaderRequestId + 1L
        }
        val requestId = nextReaderRequestId
        binding.readerRequestId = requestId
        binding.readerResult = onResult
        binding.readerTimeout = Runnable {
            if (binding.readerRequestId == requestId) completeReaderRequest(binding, null)
        }.also { timeout ->
            mainHandler.postDelayed(timeout, READER_EXTRACTION_TIMEOUT_MILLIS)
        }
        val failedResult = runCatching {
            port?.postMessage(
                JSONObject()
                    .put("type", "reader-extract")
                    .put("protocolVersion", CandyPrivacyHostContract.PROTOCOL_VERSION)
                    .put("token", binding.token)
                    .put("revision", binding.handshake.publishedRevision)
                    .put("requestId", requestId),
            )
        }.exceptionOrNull()?.let { clearReaderRequest(binding) }
        previousResult?.invoke(null)
        failedResult?.invoke(null)
    }

    private fun acceptReaderResult(value: JSONObject) {
        val binding = bindings[value.optString("token")] ?: return
        if (binding.handshake.publishedRevision != value.optLong("revision", -1)) return
        if (binding.readerRequestId != value.optLong("requestId", -1)) return
        val result = value.optJSONObject("payload")?.toString()
        clearReaderRequest(binding)?.invoke(result)
    }

    private fun completeReaderRequest(binding: Binding, result: String?) {
        clearReaderRequest(binding)?.invoke(result)
    }

    private fun clearReaderRequest(binding: Binding): ((String?) -> Unit)? {
        binding.readerTimeout?.let(mainHandler::removeCallbacks)
        binding.readerTimeout = null
        binding.readerRequestId = null
        return binding.readerResult.also { binding.readerResult = null }
    }

    private fun acceptEvents(value: JSONObject) {
        val binding = bindings[value.optString("token")] ?: return
        val batchRevision = value.optLong("revision", -1)
        val isCurrentRevision = binding.handshake.publishedRevision == batchRevision
        // A candidate request can beat the navigation policy update. Only this non-blocking signal
        // may cross that race; BrowserController still requires its recorded page host to match.
        val isPastRevision = batchRevision in 1L until binding.handshake.publishedRevision
        if (!isCurrentRevision && !isPastRevision) return
        val events = value.optJSONArray("events") ?: return
        repeat(minOf(events.length(), MAX_EVENTS_PER_BATCH)) { index ->
            val event = events.optJSONObject(index) ?: return@repeat
            val isCompatibilityObservation = event.optBoolean(
                "compatibilityObservation",
                false,
            )
            if (!isCurrentRevision && !isCompatibilityObservation) return@repeat
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
                    isCompatibilityObservation = isCompatibilityObservation,
                ),
            )
        }
    }

    private fun acceptSafeAreaFallback(value: JSONObject) {
        val binding = bindings[value.optString("token")] ?: return
        if (binding.handshake.publishedRevision != value.optLong("revision", -1)) return
        val navigationGeneration = value.optInt("navigationGeneration", -1)
        if (
            navigationGeneration < 0 ||
            navigationGeneration != binding.policy.navigationGeneration
        ) {
            return
        }
        binding.sink.onEvent(
            GeckoPrivacyEvent(
                requestUrl = "",
                pageUrl = binding.policy.pageHost?.let { host -> "https://$host/" },
                ruleId = null,
                wasBlocked = false,
                isBuiltIn = false,
                isCompatibilityObservation = false,
                safeAreaFallbackNavigationGeneration = navigationGeneration,
            ),
        )
    }

    private fun runWhenReady(binding: Binding, action: () -> Unit) {
        if (bindings[binding.token] !== binding) return
        failureDescription?.let { description ->
            binding.failed?.invoke(description)
            return
        }
        if (extensionRulesReady && hostPermissionsReady) {
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

    private fun refreshTimeout(binding: Binding) {
        val phase = when {
            !binding.handshake.isCurrentPolicyAcknowledged ->
                "policy revision ${binding.handshake.publishedRevision}"
            binding.bound != null && !binding.handshake.sessionBound ->
                "session bootstrap binding"
            else -> null
        }
        if (phase == null) {
            cancelTimeout(binding)
        } else {
            armTimeout(binding, phase)
        }
    }

    private fun startBootstrap(binding: Binding) {
        if (bindings[binding.token] !== binding) return
        val installed = extension ?: return
        binding.session.loadUri(
            "${installed.metaData.baseUrl}bootstrap.html?token=${binding.token}",
        )
    }

    private fun completeBindingIfReady(binding: Binding) {
        if (!binding.handshake.isReady || bindings[binding.token] !== binding) return
        binding.bound?.also { binding.bound = null }?.invoke()
    }

    private fun cancelTimeout(binding: Binding) {
        binding.timeout?.let(mainHandler::removeCallbacks)
        binding.timeout = null
    }

    private fun releaseIfReady() {
        if (!extensionRulesReady || !hostPermissionsReady || failureDescription != null) return
        initializationComplete = true
        mainHandler.removeCallbacks(initializationTimeout)
        val actions = pendingUntilReady.toList()
        pendingUntilReady.clear()
        actions.forEach { action -> action() }
        val callbacks = initializationCallbacks.toList()
        initializationCallbacks.clear()
        callbacks.forEach { callback -> callback(true) }
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
        val callbacks = initializationCallbacks.toList()
        initializationCallbacks.clear()
        callbacks.forEach { callback -> callback(false) }
        val readerResults = bindings.values.mapNotNull { binding ->
            cancelTimeout(binding)
            val readerResult = clearReaderRequest(binding)
            binding.policyReadyCallbacks.clear()
            binding.bound = null
            binding.failed?.invoke(description)
            readerResult
        }
        readerResults.forEach { result -> result(null) }
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
            val accepted = CandyPrivacyHostContract.isTrustedSessionBindingMessage(
                nativeApp = nativeApp,
                extensionId = sender.webExtension.id,
                environmentType = sender.environmentType,
                expectedEnvironmentType = WebExtension.MessageSender.ENV_TYPE_EXTENSION,
                hasExpectedSession = sender.session === binding.session,
                messageType = value?.optString("type"),
                messageToken = value?.optString("token"),
                expectedToken = binding.token,
                messageRevision = value?.optLong("revision", -1) ?: -1,
                currentRevision = binding.handshake.publishedRevision,
                bootstrapStarted = binding.handshake.bootstrapStarted,
            )
            if (accepted) {
                val transition = GeckoPrivacyBindingHandshakeRules.authenticateSession(
                    state = binding.handshake,
                    revision = value?.optLong("revision", -1) ?: -1,
                )
                if (transition.accepted) binding.handshake = transition.state
            }
            return GeckoResult.fromValue(accepted)
        }
    }

    private companion object {
        const val TAG = "CandyPrivacyHost"
        const val MAX_EVENTS_PER_BATCH = 512
        const val MAX_URL_CHARS = 8 * 1_024
        const val MAX_FAILURE_DESCRIPTION_CHARS = 256
        const val INITIALIZATION_TIMEOUT_MILLIS = 15_000L
        const val BINDING_TIMEOUT_MILLIS = 15_000L
        const val READER_EXTRACTION_TIMEOUT_MILLIS = 15_000L
        const val MAX_SAFE_JAVASCRIPT_INTEGER = 9_007_199_254_740_991L
        const val PRIVACY_FAILURE_DESCRIPTION = "Candy Privacy protection failed to initialize"
    }
}

private fun closedBinding(): GeckoPrivacyBinding = object : GeckoPrivacyBinding {
    override fun update(policy: GeckoPrivacyPolicy, onReady: () -> Unit) = Unit

    override fun extractPageForReader(onResult: (String?) -> Unit) = onResult(null)

    override fun setPictureInPicturePlaybackExpected(expected: Boolean) = Unit

    override fun close() = Unit
}
