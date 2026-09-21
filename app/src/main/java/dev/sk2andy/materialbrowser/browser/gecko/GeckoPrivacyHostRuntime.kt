package dev.sk2andy.materialbrowser.browser.gecko

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.browser.BrowserEngineScrollMetrics
import dev.sk2andy.materialbrowser.browser.BrowserViewportRect
import dev.sk2andy.materialbrowser.browser.TextInputOcclusionProbeMode
import dev.sk2andy.materialbrowser.browser.TextInputOcclusionProbeResult
import dev.sk2andy.materialbrowser.browser.WebRtcProtectionMode
import dev.sk2andy.materialbrowser.browser.WebRtcProtectionRules
import java.util.UUID
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

internal interface GeckoPrivacyBinding {
    fun update(policy: GeckoPrivacyPolicy, onReady: () -> Unit = {})

    fun extractPageForReader(onResult: (String?) -> Unit)

    fun probeTextInputOcclusion(
        viewportRect: BrowserViewportRect,
        mode: TextInputOcclusionProbeMode,
        onResult: (TextInputOcclusionProbeResult) -> Unit,
    )

    fun probeDom(onResult: (String?) -> Unit)

    fun cancelDomProbe()

    fun scrollMetrics(): BrowserEngineScrollMetrics?

    fun setPictureInPicturePlaybackExpected(expected: Boolean)

    fun preparePictureInPicturePlayback(
        identity: GeckoInlineVideoIdentity,
        onResult: (GeckoPictureInPicturePreparation?) -> Unit,
    )

    fun restorePictureInPicturePresentation(onResult: (Boolean) -> Unit)

    fun setInlineVideoPresentation(
        identity: GeckoInlineVideoIdentity?,
        expected: Boolean,
        onResult: (Boolean) -> Unit,
    )

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
        val domProbe: GeckoDomProbeRequest,
        val textInputOcclusionProbe: GeckoTextInputOcclusionRequest,
        var pictureInPicturePlaybackExpected: Boolean = false,
        var pictureInPicturePlaybackRequestGeneration: Long = 0,
        var pictureInPicturePreparationRequestId: Long? = null,
        var pictureInPicturePreparationIdentity: GeckoInlineVideoIdentity? = null,
        var pictureInPicturePreparationResult: ((GeckoPictureInPicturePreparation?) -> Unit)? = null,
        var pictureInPicturePreparationTimeout: Runnable? = null,
        var pictureInPictureRestorationRequestId: Long? = null,
        var pictureInPictureRestorationResult: ((Boolean) -> Unit)? = null,
        var pictureInPictureRestorationTimeout: Runnable? = null,
        var inlineVideoPresentationRequestId: Long? = null,
        var inlineVideoPresentationResult: ((Boolean) -> Unit)? = null,
        var inlineVideoPresentationTimeout: Runnable? = null,
        var inlineVideoPresentationDesiredIdentity: GeckoInlineVideoIdentity? = null,
        var inlineVideoPresentationDesiredExpected: Boolean = false,
        var inlineVideoPresentationDesiredSet: Boolean = false,
        var inlineVideoPresentationDeferredResult: ((Boolean) -> Unit)? = null,
        var inlineVideoPresented: Boolean = false,
        val inlineVideoOpenGate: GeckoInlineVideoOpenRequestGate =
            GeckoInlineVideoOpenRequestGate(SystemClock::uptimeMillis),
        var inlineVideoOpenTimeout: Runnable? = null,
        var scrollMetrics: BrowserEngineScrollMetrics? = null,
        val onScrollMetrics: (BrowserEngineScrollMetrics) -> Unit,
        val onMainFrameResponse: (GeckoMainFrameResponse) -> Unit,
        val onInlineVideoState: (GeckoInlineVideoState) -> Unit,
        val onInlineVideoOpenRequest: (GeckoInlineVideoOpenRequest) -> Unit,
        val onInlineVideoGestureHaptic: (GeckoInlineVideoGestureHaptic) -> Unit,
    )

    private val bindings = linkedMapOf<String, Binding>()
    private val performanceDiagnosticsStateListener: () -> Unit = {
        mainHandler.post {
            bindings.values.toList().forEach { binding ->
                if (!binding.session.settings.usePrivateMode) {
                    runWhenReady(binding) { publishPerformanceDiagnosticsState(binding) }
                }
            }
        }
    }
    private val performanceDiagnosticsGapListener: () -> Unit = {
        mainHandler.post {
            if (GeckoPerformanceDiagnostics.isRecording) {
                bindings.values.toList().forEach { binding ->
                    if (!binding.session.settings.usePrivateMode &&
                        binding.handshake.publishedRevision >= 1
                    ) {
                        port?.postMessage(
                            JSONObject()
                                .put("type", "performance-diagnostics-gap")
                                .put("protocolVersion", CandyPrivacyHostContract.PROTOCOL_VERSION)
                                .put("token", binding.token)
                                .put("revision", binding.handshake.publishedRevision),
                        )
                    }
                }
            }
        }
    }
    private val pendingUntilReady = mutableListOf<() -> Unit>()
    private val initializationCallbacks = mutableListOf<(Boolean) -> Unit>()
    private var extension: WebExtension? = null
    private var port: WebExtension.Port? = null
    private var extensionRulesReady = false
    private var hostPermissionsReady = false
    private var webRtcPolicyReady = false
    private var webRtcProtectionMode = WebRtcProtectionMode.Default
    private var webRtcPolicyRevision = 1L
    private val webRtcPolicyReadyCallbacks = mutableListOf<() -> Unit>()
    private var initializationComplete = false
    private var failureDescription: String? = null
    private var nextReaderRequestId = 0L
    private var nextPictureInPicturePreparationRequestId = 0L
    private var nextInlineVideoPresentationRequestId = 0L
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

    fun setWebRtcProtectionMode(mode: WebRtcProtectionMode, onReady: () -> Unit = {}) {
        if (webRtcProtectionMode == mode && webRtcPolicyReady) {
            onReady()
            return
        }
        webRtcPolicyReadyCallbacks += onReady
        if (webRtcProtectionMode == mode) return
        webRtcProtectionMode = mode
        webRtcPolicyRevision++
        webRtcPolicyReady = false
        publishWebRtcPolicy()
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
        onScrollMetrics: (BrowserEngineScrollMetrics) -> Unit,
        onMainFrameResponse: (GeckoMainFrameResponse) -> Unit,
        onInlineVideoState: (GeckoInlineVideoState) -> Unit,
        onInlineVideoOpenRequest: (GeckoInlineVideoOpenRequest) -> Unit,
        onInlineVideoGestureHaptic: (GeckoInlineVideoGestureHaptic) -> Unit,
        onBound: () -> Unit,
        onFailure: (String) -> Unit,
    ): GeckoPrivacyBinding {
        val token = UUID.randomUUID().toString()
        val binding = Binding(
            token = token,
            session = session,
            domProbe = GeckoDomProbeRequest(mainHandler),
            textInputOcclusionProbe = GeckoTextInputOcclusionRequest(mainHandler),
            sink = sink,
            onScrollMetrics = onScrollMetrics,
            onMainFrameResponse = onMainFrameResponse,
            onInlineVideoState = onInlineVideoState,
            onInlineVideoOpenRequest = onInlineVideoOpenRequest,
            onInlineVideoGestureHaptic = onInlineVideoGestureHaptic,
            bound = onBound,
            failed = onFailure,
        )
        bindings[token] = binding
        failureDescription?.let { description ->
            bindings.remove(token)
            binding.failed?.invoke(description)
            return closedBinding()
        }
        if (BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS && bindings.size == 1) {
            GeckoPerformanceDiagnostics.addStateListener(performanceDiagnosticsStateListener)
            GeckoPerformanceDiagnostics.addGapListener(performanceDiagnosticsGapListener)
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
                binding.inlineVideoOpenGate.invalidatePolicy(policy, session.settings.usePrivateMode)
                refreshInlineVideoOpenTimeout(binding)
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

            override fun scrollMetrics(): BrowserEngineScrollMetrics? = binding.scrollMetrics

            override fun probeTextInputOcclusion(
                viewportRect: BrowserViewportRect,
                mode: TextInputOcclusionProbeMode,
                onResult: (TextInputOcclusionProbeResult) -> Unit,
            ) {
                if (
                    bindings[token] !== binding ||
                    failureDescription != null
                ) {
                    onResult(TextInputOcclusionProbeResult.NoFocusedTextInput)
                    return
                }
                runWhenReady(binding) {
                    if (binding.handshake.isCurrentPolicyAcknowledged) {
                        requestTextInputOcclusion(binding, viewportRect, mode, onResult)
                    } else {
                        binding.policyReadyCallbacks += {
                            requestTextInputOcclusion(binding, viewportRect, mode, onResult)
                        }
                    }
                }
            }

            override fun probeDom(onResult: (String?) -> Unit) {
                val currentPort = port
                if (!BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS || session.settings.usePrivateMode ||
                    bindings[token] !== binding || currentPort == null || failureDescription != null ||
                    !binding.handshake.isCurrentPolicyAcknowledged
                ) {
                    onResult(null)
                    return
                }
                binding.domProbe.start(
                    token = token,
                    revision = binding.handshake.publishedRevision,
                    navigationGeneration = binding.policy.navigationGeneration.toLong(),
                    post = currentPort::postMessage,
                    onResult = onResult,
                )
            }

            override fun cancelDomProbe() = binding.domProbe.cancel()

            override fun setPictureInPicturePlaybackExpected(expected: Boolean) {
                binding.pictureInPicturePlaybackExpected = expected
                if (expected) {
                    binding.pictureInPicturePlaybackRequestGeneration++
                    clearPictureInPictureRestorationRequest(binding)?.invoke(false)
                } else {
                    clearPictureInPicturePreparationRequest(binding)?.invoke(null)
                }
                runWhenReady(binding) {
                    if (
                        bindings[token] === binding &&
                        binding.pictureInPicturePlaybackExpected == expected
                    ) {
                        publishDesiredPictureInPicturePlayback(binding)
                    }
                }
            }

            override fun preparePictureInPicturePlayback(
                identity: GeckoInlineVideoIdentity,
                onResult: (GeckoPictureInPicturePreparation?) -> Unit,
            ) {
                binding.pictureInPicturePlaybackRequestGeneration++
                clearPictureInPictureRestorationRequest(binding)?.invoke(false)
                requestPictureInPicturePreparation(
                    binding = binding,
                    identity = identity,
                    onResult = onResult,
                )
            }

            override fun restorePictureInPicturePresentation(onResult: (Boolean) -> Unit) {
                binding.pictureInPicturePlaybackExpected = false
                clearPictureInPicturePreparationRequest(binding)?.invoke(null)
                val requestGeneration = ++binding.pictureInPicturePlaybackRequestGeneration
                runWhenReady(binding) {
                    if (
                        binding.pictureInPicturePlaybackRequestGeneration == requestGeneration &&
                        !binding.pictureInPicturePlaybackExpected
                    ) {
                        requestPictureInPictureRestoration(binding, onResult)
                    } else {
                        onResult(false)
                    }
                }
            }

            override fun setInlineVideoPresentation(
                identity: GeckoInlineVideoIdentity?,
                expected: Boolean,
                onResult: (Boolean) -> Unit,
            ) {
                if (!expected) cancelPendingInlineVideoOpen(binding)
                if (bindings[token] !== binding || binding.session.settings.usePrivateMode) {
                    onResult(false)
                    return
                }
                clearInlineVideoPresentationRequest(binding)?.invoke(false)
                clearDeferredInlineVideoPresentationResult(binding)?.invoke(false)
                binding.inlineVideoPresentationDesiredIdentity = identity
                binding.inlineVideoPresentationDesiredExpected = expected
                binding.inlineVideoPresentationDesiredSet = true
                binding.inlineVideoPresentationDeferredResult = onResult
                runWhenReady(binding) {
                    dispatchDesiredInlineVideoPresentation(binding)
                }
            }

            override fun close() {
                cancelPendingInlineVideoOpen(binding)
                bindings.remove(token)
                binding.domProbe.cancel()
                binding.textInputOcclusionProbe.cancel()
                clearPictureInPicturePreparationRequest(binding)?.invoke(null)
                clearPictureInPictureRestorationRequest(binding)?.invoke(false)
                clearInlineVideoPresentationRequest(binding)?.invoke(false)
                clearDeferredInlineVideoPresentationResult(binding)?.invoke(false)
                if (BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS && bindings.isEmpty()) {
                    GeckoPerformanceDiagnostics.removeStateListener(performanceDiagnosticsStateListener)
                    GeckoPerformanceDiagnostics.removeGapListener(performanceDiagnosticsGapListener)
                }
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
        binding.domProbe.cancel()
        binding.textInputOcclusionProbe.cancel()
        val readerResult = clearReaderRequest(binding)
        val pictureInPicturePreparationResult =
            clearPictureInPicturePreparationRequest(binding)
        val pictureInPictureRestorationResult =
            clearPictureInPictureRestorationRequest(binding)
        val inlineVideoPresentationResult = clearInlineVideoPresentationRequest(binding)
        if (inlineVideoPresentationResult != null) {
            clearDeferredInlineVideoPresentationResult(binding)?.invoke(false)
            binding.inlineVideoPresentationDeferredResult = inlineVideoPresentationResult
        }
        binding.policy = policy
        binding.scrollMetrics = null
        binding.handshake = GeckoPrivacyBindingHandshakeRules.publish(binding.handshake)
        binding.inlineVideoOpenGate.publish(
            next = policy,
            revision = binding.handshake.publishedRevision,
            isPrivate = binding.session.settings.usePrivateMode,
        )
        refreshInlineVideoOpenTimeout(binding)
        onReady?.let(binding.policyReadyCallbacks::add)
        refreshTimeout(binding)
        port?.postMessage(
            policy.toMessage(binding.token, binding.handshake.publishedRevision)
                .put("domDiagnosticsEnabled", BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS && !binding.session.settings.usePrivateMode)
                .put(
                    "performanceDiagnosticsEnabled",
                    BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS &&
                        GeckoPerformanceDiagnostics.isRecording &&
                        !binding.session.settings.usePrivateMode,
                ),
        )
        readerResult?.invoke(null)
        pictureInPicturePreparationResult?.invoke(null)
        pictureInPictureRestorationResult?.invoke(false)
    }

    private fun onPortMessage(message: Any, sourcePort: WebExtension.Port) {
        if (sourcePort !== port) return
        val value = message as? JSONObject ?: return
        if (value.optInt("protocolVersion", -1) != CandyPrivacyHostContract.PROTOCOL_VERSION) return
        when (value.optString("type")) {
            "ready" -> {
                extensionRulesReady = true
                publishWebRtcPolicy()
            }
            "webrtc-policy-ready" -> {
                if (value.optLong("revision", -1) != webRtcPolicyRevision) return
                webRtcPolicyReady = true
                releaseIfReady()
                val callbacks = webRtcPolicyReadyCallbacks.toList()
                webRtcPolicyReadyCallbacks.clear()
                callbacks.forEach { callback -> callback() }
            }
            "policy-ready" -> {
                val binding = bindings[value.optString("token")] ?: return
                val transition = GeckoPrivacyBindingHandshakeRules.acknowledgePolicy(
                    state = binding.handshake,
                    revision = value.optLong("revision", -1),
                )
                if (!transition.accepted) return
                binding.handshake = transition.state
                binding.inlineVideoOpenGate.acknowledge(value.optLong("revision", -1))
                if (transition.startBootstrap) startBootstrap(binding)
                if (binding.handshake.isCurrentPolicyAcknowledged) {
                    publishPerformanceDiagnosticsState(binding)
                    val callbacks = binding.policyReadyCallbacks.toList()
                    binding.policyReadyCallbacks.clear()
                    callbacks.forEach { callback -> callback() }
                    publishDesiredPictureInPicturePlayback(binding)
                    dispatchDesiredInlineVideoPresentation(binding)
                    dispatchPendingInlineVideoOpen(binding)
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
            "main-frame-response" -> acceptMainFrameResponse(value)
            "safe-area-fallback" -> acceptSafeAreaFallback(value)
            "scroll-metrics" -> acceptScrollMetrics(value)
            "inline-video-state" -> acceptInlineVideoState(value)
            "inline-video-open-request" -> acceptInlineVideoOpenRequest(value)
            "inline-video-gesture-haptic" -> acceptInlineVideoGestureHaptic(value)
            "inline-video-presentation-result" -> acceptInlineVideoPresentationResult(value)
            "picture-in-picture-playback-result" ->
                acceptPictureInPicturePlaybackResult(value)
            "reader-result" -> acceptReaderResult(value)
            "text-input-occlusion-result" -> {
                val binding = bindings[value.optString("token")] ?: return
                if (
                    binding.handshake.publishedRevision != value.optLong("revision", -1) ||
                    binding.policy.navigationGeneration !=
                    value.optInt("navigationGeneration", -1)
                ) {
                    return
                }
                binding.textInputOcclusionProbe.accept(value)
            }
            "dom-probe-result" -> {
                if (!BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS) return
                val binding = bindings[value.optString("token")] ?: return
                if (binding.session.settings.usePrivateMode ||
                    binding.handshake.publishedRevision != value.optLong("revision", -1) ||
                    binding.policy.navigationGeneration != value.optInt("navigationGeneration", -1)
                ) return
                binding.domProbe.accept(value)
            }
            "failed" -> fail(IllegalStateException(value.optString("reason", "Privacy host failed")))
        }
    }

    private fun publishPerformanceDiagnosticsState(binding: Binding) {
        if (!BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS) return
        if (bindings[binding.token] !== binding || binding.handshake.publishedRevision < 1) return
        port?.postMessage(
            JSONObject()
                .put("type", "performance-diagnostics-state")
                .put("protocolVersion", CandyPrivacyHostContract.PROTOCOL_VERSION)
                .put("token", binding.token)
                .put("revision", binding.handshake.publishedRevision)
                .put(
                    "performanceDiagnosticsEnabled",
                    GeckoPerformanceDiagnostics.isRecording &&
                        !binding.session.settings.usePrivateMode,
                ),
        )
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

    private fun requestTextInputOcclusion(
        binding: Binding,
        viewportRect: BrowserViewportRect,
        mode: TextInputOcclusionProbeMode,
        onResult: (TextInputOcclusionProbeResult) -> Unit,
    ) {
        val connectedPort = port
        if (
            bindings[binding.token] !== binding ||
            connectedPort == null ||
            !binding.handshake.isCurrentPolicyAcknowledged
        ) {
            onResult(TextInputOcclusionProbeResult.NoFocusedTextInput)
            return
        }
        binding.textInputOcclusionProbe.start(
            token = binding.token,
            revision = binding.handshake.publishedRevision,
            navigationGeneration = binding.policy.navigationGeneration,
            viewportRect = viewportRect,
            mode = mode,
            post = connectedPort::postMessage,
            onResult = onResult,
        )
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

    private fun acceptMainFrameResponse(value: JSONObject) {
        val binding = bindings[value.optString("token")] ?: return
        if (binding.handshake.publishedRevision != value.optLong("revision", -1)) return
        val response = GeckoMainFrameResponseRules.resolve(
            url = value.optString("url"),
            statusCode = value.optInt("statusCode", -1),
            navigationGeneration = value.optInt("navigationGeneration", -1),
            isCloudflareChallenge = value.optBoolean("cloudflareChallenge", false),
        ) ?: return
        if (response.navigationGeneration != binding.policy.navigationGeneration) return
        if (response.isCloudflareChallenge) {
            binding.sink.onEvent(
                GeckoPrivacyEvent(
                    requestUrl = response.url,
                    pageUrl = response.url,
                    ruleId = null,
                    wasBlocked = false,
                    isBuiltIn = false,
                    isCompatibilityObservation = false,
                    isCloudflareChallengeResponse = true,
                ),
            )
        }
        binding.onMainFrameResponse(response)
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
                safeAreaFallbackThemeColor = value.optString("themeColor")
                    .takeIf { color ->
                        color.isNotBlank() && color.length <= MAX_THEME_COLOR_LENGTH
                    },
                safeAreaFallbackIsTopHeader = value.optBoolean("topHeader", false),
            ),
        )
    }

    private fun acceptScrollMetrics(value: JSONObject) {
        val binding = bindings[value.optString("token")] ?: return
        val metrics = geckoScrollMetricsFromMessage(
            message = value,
            currentRevision = binding.handshake.publishedRevision,
        ) ?: return
        binding.scrollMetrics = metrics
        binding.onScrollMetrics(metrics)
    }

    private fun acceptInlineVideoState(value: JSONObject) {
        val binding = bindings[value.optString("token")] ?: return
        if (binding.session.settings.usePrivateMode) return
        val state = geckoInlineVideoStateFromMessage(
            message = value,
            currentRevision = binding.handshake.publishedRevision,
            currentNavigationGeneration = binding.policy.navigationGeneration,
        ) ?: return
        if (!binding.policy.inlineMediaPlayerEnabled && state.isActive) return
        if (
            shouldClearInlineVideoPresentationDesire(
                wasPresented = binding.inlineVideoPresented,
                isPresented = state.isPresented,
                presentationDesired = binding.inlineVideoPresentationDesiredExpected,
            )
        ) {
            binding.inlineVideoPresentationDesiredIdentity = null
            binding.inlineVideoPresentationDesiredExpected = false
            binding.inlineVideoPresentationDesiredSet = true
        }
        binding.inlineVideoPresented = state.isPresented
        binding.inlineVideoOpenGate.updateCandidate(value.optLong("revision", -1), state)
        binding.onInlineVideoState(state)
        dispatchPendingInlineVideoOpen(binding)
    }

    private fun acceptInlineVideoOpenRequest(value: JSONObject) {
        val binding = bindings[value.optString("token")] ?: return
        if (
            binding.session.settings.usePrivateMode ||
            !binding.policy.inlineMediaPlayerEnabled
        ) {
            cancelPendingInlineVideoOpen(binding)
            return
        }
        val revision = value.optLong("revision", -1)
        val request = geckoInlineVideoOpenRequestFromMessage(
            message = value,
            currentRevision = if (revision == binding.handshake.publishedRevision) {
                binding.handshake.publishedRevision
            } else {
                binding.handshake.acknowledgedRevision
            },
            currentNavigationGeneration = binding.policy.navigationGeneration,
        ) ?: return
        val accepted = binding.inlineVideoOpenGate.accept(
            request = request,
            revision = revision,
            mode = value.optString("mode").takeIf(String::isNotEmpty),
        )
        refreshInlineVideoOpenTimeout(binding)
        accepted?.let(binding.onInlineVideoOpenRequest)
    }

    private fun dispatchPendingInlineVideoOpen(binding: Binding) {
        val request = binding.inlineVideoOpenGate.takeReady()
        refreshInlineVideoOpenTimeout(binding)
        if (bindings[binding.token] === binding) request?.let(binding.onInlineVideoOpenRequest)
    }

    private fun cancelPendingInlineVideoOpen(binding: Binding) {
        binding.inlineVideoOpenGate.cancel()
        refreshInlineVideoOpenTimeout(binding)
    }

    private fun refreshInlineVideoOpenTimeout(binding: Binding) {
        binding.inlineVideoOpenTimeout?.let(mainHandler::removeCallbacks)
        binding.inlineVideoOpenTimeout = null
        val deadline = binding.inlineVideoOpenGate.pendingDeadlineMillis ?: return
        binding.inlineVideoOpenTimeout = Runnable {
            if (bindings[binding.token] === binding &&
                binding.inlineVideoOpenGate.pendingDeadlineMillis == deadline
            ) {
                cancelPendingInlineVideoOpen(binding)
            }
        }.also { timeout ->
            mainHandler.postDelayed(timeout, (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0))
        }
    }

    private fun acceptInlineVideoGestureHaptic(value: JSONObject) {
        val binding = bindings[value.optString("token")] ?: return
        if (
            binding.session.settings.usePrivateMode ||
            !binding.policy.inlineMediaPlayerEnabled ||
            !binding.handshake.isCurrentPolicyAcknowledged ||
            !binding.inlineVideoPresented
        ) {
            return
        }
        val haptic = geckoInlineVideoGestureHapticFromMessage(
            message = value,
            currentRevision = binding.handshake.publishedRevision,
            currentNavigationGeneration = binding.policy.navigationGeneration,
        ) ?: return
        binding.onInlineVideoGestureHaptic(haptic)
    }

    private fun publishDesiredPictureInPicturePlayback(binding: Binding) {
        if (
            bindings[binding.token] !== binding ||
            !binding.handshake.isCurrentPolicyAcknowledged ||
            // A policy-ready callback may already own a request-bound restoration. Sending
            // another expected=false would invalidate its content-side frame acknowledgement.
            binding.pictureInPictureRestorationRequestId != null
        ) {
            return
        }
        port?.postMessage(
            pictureInPicturePlaybackMessage(
                token = binding.token,
                revision = binding.handshake.publishedRevision,
                expected = binding.pictureInPicturePlaybackExpected,
            ),
        )
    }

    private fun requestPictureInPicturePreparation(
        binding: Binding,
        identity: GeckoInlineVideoIdentity,
        onResult: (GeckoPictureInPicturePreparation?) -> Unit,
    ) {
        val connectedPort = port
        if (
            bindings[binding.token] !== binding ||
            binding.session.settings.usePrivateMode ||
            connectedPort == null ||
            !binding.handshake.isCurrentPolicyAcknowledged ||
            !binding.pictureInPicturePlaybackExpected
        ) {
            onResult(null)
            return
        }
        clearPictureInPictureRestorationRequest(binding)?.invoke(false)
        clearPictureInPicturePreparationRequest(binding)?.invoke(null)
        nextPictureInPicturePreparationRequestId =
            if (nextPictureInPicturePreparationRequestId >= MAX_SAFE_JAVASCRIPT_INTEGER) {
                1L
            } else {
                nextPictureInPicturePreparationRequestId + 1L
            }
        val requestId = nextPictureInPicturePreparationRequestId
        binding.pictureInPicturePreparationRequestId = requestId
        binding.pictureInPicturePreparationIdentity = identity
        binding.pictureInPicturePreparationResult = onResult
        binding.pictureInPicturePreparationTimeout = Runnable {
            if (binding.pictureInPicturePreparationRequestId == requestId) {
                clearPictureInPicturePreparationRequest(binding)?.invoke(null)
            }
        }.also { timeout ->
            mainHandler.postDelayed(timeout, PICTURE_IN_PICTURE_PREPARATION_TIMEOUT_MILLIS)
        }
        connectedPort.postMessage(
            pictureInPicturePreparationMessage(
                token = binding.token,
                revision = binding.handshake.publishedRevision,
                navigationGeneration = binding.policy.navigationGeneration,
                requestId = requestId,
                identity = identity,
            ),
        )
    }

    private fun requestPictureInPictureRestoration(
        binding: Binding,
        onResult: (Boolean) -> Unit,
    ) {
        val connectedPort = port
        if (
            bindings[binding.token] !== binding ||
            binding.session.settings.usePrivateMode ||
            connectedPort == null ||
            !binding.handshake.isCurrentPolicyAcknowledged
        ) {
            onResult(false)
            return
        }
        clearPictureInPictureRestorationRequest(binding)?.invoke(false)
        nextPictureInPicturePreparationRequestId =
            if (nextPictureInPicturePreparationRequestId >= MAX_SAFE_JAVASCRIPT_INTEGER) {
                1L
            } else {
                nextPictureInPicturePreparationRequestId + 1L
            }
        val requestId = nextPictureInPicturePreparationRequestId
        binding.pictureInPictureRestorationRequestId = requestId
        binding.pictureInPictureRestorationResult = onResult
        binding.pictureInPictureRestorationTimeout = Runnable {
            if (binding.pictureInPictureRestorationRequestId == requestId) {
                clearPictureInPictureRestorationRequest(binding)?.invoke(false)
            }
        }.also { timeout ->
            mainHandler.postDelayed(timeout, PICTURE_IN_PICTURE_PREPARATION_TIMEOUT_MILLIS)
        }
        connectedPort.postMessage(
            pictureInPictureRestorationMessage(
                token = binding.token,
                revision = binding.handshake.publishedRevision,
                navigationGeneration = binding.policy.navigationGeneration,
                requestId = requestId,
            ),
        )
    }

    private fun acceptPictureInPicturePlaybackResult(value: JSONObject) {
        val binding = bindings[value.optString("token")] ?: return
        when (
            geckoPictureInPicturePlaybackResultRoute(
                message = value,
                preparationRequestId = binding.pictureInPicturePreparationRequestId,
                restorationRequestId = binding.pictureInPictureRestorationRequestId,
            )
        ) {
            GeckoPictureInPicturePlaybackResultRoute.Restoration -> {
                val requestId = binding.pictureInPictureRestorationRequestId ?: return
                if (
                    isGeckoPictureInPictureRestorationResult(
                        message = value,
                        currentRevision = binding.handshake.publishedRevision,
                        currentNavigationGeneration = binding.policy.navigationGeneration,
                        expectedRequestId = requestId,
                    )
                ) {
                    clearPictureInPictureRestorationRequest(binding)?.invoke(true)
                }
            }
            GeckoPictureInPicturePlaybackResultRoute.Preparation -> {
                val requestId = binding.pictureInPicturePreparationRequestId ?: return
                val identity = binding.pictureInPicturePreparationIdentity ?: return
                val preparation = geckoPictureInPicturePreparationFromMessage(
                    message = value,
                    currentRevision = binding.handshake.publishedRevision,
                    currentNavigationGeneration = binding.policy.navigationGeneration,
                    expectedRequestId = requestId,
                    expectedIdentity = identity,
                )
                clearPictureInPicturePreparationRequest(binding)?.invoke(preparation)
            }
            GeckoPictureInPicturePlaybackResultRoute.Stale -> Unit
        }
    }

    private fun clearPictureInPictureRestorationRequest(
        binding: Binding,
    ): ((Boolean) -> Unit)? {
        binding.pictureInPictureRestorationTimeout?.let(mainHandler::removeCallbacks)
        binding.pictureInPictureRestorationTimeout = null
        binding.pictureInPictureRestorationRequestId = null
        return binding.pictureInPictureRestorationResult.also {
            binding.pictureInPictureRestorationResult = null
        }
    }

    private fun clearPictureInPicturePreparationRequest(
        binding: Binding,
    ): ((GeckoPictureInPicturePreparation?) -> Unit)? {
        binding.pictureInPicturePreparationTimeout?.let(mainHandler::removeCallbacks)
        binding.pictureInPicturePreparationTimeout = null
        binding.pictureInPicturePreparationRequestId = null
        binding.pictureInPicturePreparationIdentity = null
        return binding.pictureInPicturePreparationResult.also {
            binding.pictureInPicturePreparationResult = null
        }
    }

    private fun dispatchDesiredInlineVideoPresentation(binding: Binding) {
        if (
            bindings[binding.token] !== binding ||
            !binding.handshake.isCurrentPolicyAcknowledged ||
            !binding.inlineVideoPresentationDesiredSet ||
            binding.inlineVideoPresentationRequestId != null
        ) {
            return
        }
        val onResult = clearDeferredInlineVideoPresentationResult(binding) ?: {}
        requestInlineVideoPresentation(
            binding = binding,
            identity = binding.inlineVideoPresentationDesiredIdentity,
            expected = binding.inlineVideoPresentationDesiredExpected,
            onResult = onResult,
        )
    }

    private fun requestInlineVideoPresentation(
        binding: Binding,
        identity: GeckoInlineVideoIdentity?,
        expected: Boolean,
        onResult: (Boolean) -> Unit,
    ) {
        val connectedPort = port
        if (
            bindings[binding.token] !== binding ||
            connectedPort == null ||
            !binding.handshake.isCurrentPolicyAcknowledged ||
            (expected && identity == null)
        ) {
            onResult(false)
            return
        }
        clearInlineVideoPresentationRequest(binding)?.invoke(false)
        nextInlineVideoPresentationRequestId =
            if (nextInlineVideoPresentationRequestId >= MAX_SAFE_JAVASCRIPT_INTEGER) {
                1L
            } else {
                nextInlineVideoPresentationRequestId + 1L
            }
        val requestId = nextInlineVideoPresentationRequestId
        binding.inlineVideoPresentationRequestId = requestId
        binding.inlineVideoPresentationResult = onResult
        binding.inlineVideoPresentationTimeout = Runnable {
            if (binding.inlineVideoPresentationRequestId == requestId) {
                clearInlineVideoPresentationRequest(binding)?.invoke(false)
            }
        }.also { timeout ->
            mainHandler.postDelayed(timeout, INLINE_VIDEO_PRESENTATION_TIMEOUT_MILLIS)
        }
        connectedPort.postMessage(
            inlineVideoPresentationMessage(
                token = binding.token,
                revision = binding.handshake.publishedRevision,
                navigationGeneration = binding.policy.navigationGeneration,
                requestId = requestId,
                identity = identity,
                expected = expected,
            ),
        )
    }

    private fun acceptInlineVideoPresentationResult(value: JSONObject) {
        val binding = bindings[value.optString("token")] ?: return
        if (
            binding.handshake.publishedRevision != value.optLong("revision", -1) ||
            binding.policy.navigationGeneration != value.optInt("navigationGeneration", -1) ||
            binding.inlineVideoPresentationRequestId != value.optLong("requestId", -1)
        ) {
            return
        }
        clearInlineVideoPresentationRequest(binding)?.invoke(value.optBoolean("accepted", false))
    }

    private fun clearInlineVideoPresentationRequest(binding: Binding): ((Boolean) -> Unit)? {
        binding.inlineVideoPresentationTimeout?.let(mainHandler::removeCallbacks)
        binding.inlineVideoPresentationTimeout = null
        binding.inlineVideoPresentationRequestId = null
        return binding.inlineVideoPresentationResult.also {
            binding.inlineVideoPresentationResult = null
        }
    }

    private fun clearDeferredInlineVideoPresentationResult(
        binding: Binding,
    ): ((Boolean) -> Unit)? = binding.inlineVideoPresentationDeferredResult.also {
        binding.inlineVideoPresentationDeferredResult = null
    }

    private fun runWhenReady(binding: Binding, action: () -> Unit) {
        if (bindings[binding.token] !== binding) return
        failureDescription?.let { description ->
            binding.failed?.invoke(description)
            return
        }
        if (extensionRulesReady && hostPermissionsReady && webRtcPolicyReady) {
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
        if (
            !extensionRulesReady ||
            !hostPermissionsReady ||
            !webRtcPolicyReady ||
            failureDescription != null
        ) return
        initializationComplete = true
        mainHandler.removeCallbacks(initializationTimeout)
        val actions = pendingUntilReady.toList()
        pendingUntilReady.clear()
        actions.forEach { action -> action() }
        val callbacks = initializationCallbacks.toList()
        initializationCallbacks.clear()
        callbacks.forEach { callback -> callback(true) }
    }

    private fun publishWebRtcPolicy() {
        val connectedPort = port ?: return
        if (!extensionRulesReady || failureDescription != null) return
        val policy = WebRtcProtectionRules.geckoPolicy(webRtcProtectionMode)
        connectedPort.postMessage(
            JSONObject()
                .put("type", "webrtc-policy")
                .put("protocolVersion", CandyPrivacyHostContract.PROTOCOL_VERSION)
                .put("revision", webRtcPolicyRevision)
                .put("peerConnectionsEnabled", policy.peerConnectionsEnabled)
                .put("ipHandlingPolicy", policy.ipHandlingPolicy ?: JSONObject.NULL),
        )
    }

    private fun fail(error: Throwable) {
        if (failureDescription != null) return
        mainHandler.removeCallbacks(initializationTimeout)
        pendingUntilReady.clear()
        webRtcPolicyReadyCallbacks.clear()
        val description = error.message
            ?.takeIf(String::isNotBlank)
            ?.take(MAX_FAILURE_DESCRIPTION_CHARS)
            ?: PRIVACY_FAILURE_DESCRIPTION
        failureDescription = description
        if (BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS) {
            GeckoPerformanceDiagnostics.removeStateListener(performanceDiagnosticsStateListener)
            GeckoPerformanceDiagnostics.removeGapListener(performanceDiagnosticsGapListener)
        }
        val callbacks = initializationCallbacks.toList()
        initializationCallbacks.clear()
        callbacks.forEach { callback -> callback(false) }
        val readerResults = bindings.values.mapNotNull { binding ->
            cancelPendingInlineVideoOpen(binding)
            binding.domProbe.cancel()
            binding.textInputOcclusionProbe.cancel()
            cancelTimeout(binding)
            clearPictureInPicturePreparationRequest(binding)?.invoke(null)
            clearInlineVideoPresentationRequest(binding)?.invoke(false)
            clearDeferredInlineVideoPresentationResult(binding)?.invoke(false)
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
                        webRtcPolicyReady = false
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
        const val MAX_THEME_COLOR_LENGTH = 32
        const val MAX_FAILURE_DESCRIPTION_CHARS = 256
        const val INITIALIZATION_TIMEOUT_MILLIS = 15_000L
        const val BINDING_TIMEOUT_MILLIS = 15_000L
        const val READER_EXTRACTION_TIMEOUT_MILLIS = 15_000L
        const val INLINE_VIDEO_PRESENTATION_TIMEOUT_MILLIS = 2_000L
        const val PICTURE_IN_PICTURE_PREPARATION_TIMEOUT_MILLIS = 2_000L
        const val MAX_SAFE_JAVASCRIPT_INTEGER = 9_007_199_254_740_991L
        const val PRIVACY_FAILURE_DESCRIPTION = "Candy Privacy protection failed to initialize"
    }
}

private fun closedBinding(): GeckoPrivacyBinding = object : GeckoPrivacyBinding {
    override fun update(policy: GeckoPrivacyPolicy, onReady: () -> Unit) = Unit

    override fun extractPageForReader(onResult: (String?) -> Unit) = onResult(null)

    override fun probeTextInputOcclusion(
        viewportRect: BrowserViewportRect,
        mode: TextInputOcclusionProbeMode,
        onResult: (TextInputOcclusionProbeResult) -> Unit,
    ) = onResult(TextInputOcclusionProbeResult.NoFocusedTextInput)

    override fun probeDom(onResult: (String?) -> Unit) = onResult(null)

    override fun cancelDomProbe() = Unit

    override fun scrollMetrics(): BrowserEngineScrollMetrics? = null

    override fun setPictureInPicturePlaybackExpected(expected: Boolean) = Unit

    override fun preparePictureInPicturePlayback(
        identity: GeckoInlineVideoIdentity,
        onResult: (GeckoPictureInPicturePreparation?) -> Unit,
    ) = onResult(null)

    override fun restorePictureInPicturePresentation(onResult: (Boolean) -> Unit) =
        onResult(false)

    override fun setInlineVideoPresentation(
        identity: GeckoInlineVideoIdentity?,
        expected: Boolean,
        onResult: (Boolean) -> Unit,
    ) = onResult(false)

    override fun close() = Unit
}
