package dev.sk2andy.materialbrowser.browser.gecko

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.UiThread
import dev.sk2andy.materialbrowser.browser.engine.BrowserEngineContentKind
import dev.sk2andy.materialbrowser.browser.userscript.UserScript
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptBridgeContract
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptBridgeRequest
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptGrant
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptMenuCommand
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptOpenTabRequest
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptRules
import dev.sk2andy.materialbrowser.data.UserScriptValueStore
import dev.sk2andy.materialbrowser.shared.topping.ToppingFrameScope
import java.net.URI
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/** GeckoView edge for Candy's private, bundled MV3 Topping host. */
internal class GeckoViewToppingHostRuntime(
    private val controller: WebExtensionController,
    private val valueStore: UserScriptValueStore,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) : GeckoToppingHostRuntime {
    private val gate = GeckoToppingInitializationGate()
    private var plan = GeckoToppingPlan(emptyList(), emptyMap())
    private var revision = 0L
    private var hostInstalled = false
    private var extension: WebExtension? = null
    private var port: WebExtension.Port? = null
    private var scriptsById: Map<String, UserScript> = emptyMap()
    private var interactionDelegate: GeckoToppingInteractionDelegate =
        GeckoToppingInteractionDelegate.None
    private val sessionBindings = mutableMapOf<String, SessionBinding>()
    private val menuCommands = linkedMapOf<GeckoMenuCommandKey, String>()
    private val rateWindows = mutableMapOf<Pair<String, String>, GeckoMessageRateWindow>()
    private val openTabRateWindows = mutableMapOf<Pair<String, String>, GeckoMessageRateWindow>()
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
        scriptsById = scripts.associateBy(UserScript::id)
        plan = CandyToppingHostCompiler.compile(scripts, valueStore::snapshot)
        val retainedIds = plan.registrations.mapTo(mutableSetOf(), GeckoToppingRegistration::scriptId)
        val affectedTabs = menuCommands.keys.mapNotNull { key ->
            sessionBindings[key.document.bindingToken]?.candyTabId
        }.toSet()
        menuCommands.keys.removeAll { key -> key.scriptId !in retainedIds }
        affectedTabs.forEach(::publishCommands)
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

    override fun setInteractionDelegate(delegate: GeckoToppingInteractionDelegate) {
        interactionDelegate = delegate
        sessionBindings.values.map(SessionBinding::candyTabId).distinct().forEach(::publishCommands)
    }

    override fun invokeMenuCommand(command: UserScriptMenuCommand) {
        val entry = menuCommands.entries.firstOrNull { (key, caption) ->
            sessionBindings[key.document.bindingToken]?.candyTabId == command.tabId &&
                key.scriptId == command.scriptId &&
                key.commandId == command.commandId &&
                key.document.documentId == command.documentId &&
                caption == command.caption
        } ?: return
        port?.postMessage(
            JSONObject()
                .put("type", "menu-invoke")
                .put("protocolVersion", CandyToppingHostContract.PROTOCOL_VERSION)
                .put("geckoTabId", entry.key.document.geckoTabId)
                .put("frameId", entry.key.document.frameId)
                .put("documentId", entry.key.document.documentId)
                .put("scriptId", entry.key.scriptId)
                .put("commandId", entry.key.commandId),
        )
    }

    override fun clearValues(scriptId: String) {
        valueStore.clear(scriptId)
    }

    @UiThread
    override fun bindSession(
        session: GeckoSession,
        tabId: String,
        isPrivate: Boolean,
        contentKind: BrowserEngineContentKind,
    ): GeckoToppingSessionBinding {
        val token = UUID.randomUUID().toString()
        val binding = SessionBinding(token, session, tabId, isPrivate, contentKind)
        sessionBindings[token] = binding
        extension?.let { installed -> attachSessionDelegate(installed, binding) }
        return GeckoToppingSessionBinding {
            if (sessionBindings.remove(token) !== binding) return@GeckoToppingSessionBinding
            val affected = menuCommands.keys.removeAll { key -> key.document.bindingToken == token }
            rateWindows.keys.removeAll { key -> key.first == token }
            openTabRateWindows.keys.removeAll { key -> key.first == token }
            if (affected) publishCommands(tabId)
        }
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
        this.extension = extension
        sessionBindings.values.forEach { binding -> attachSessionDelegate(extension, binding) }
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
        if (sourcePort !== port) return
        val value = message as? JSONObject ?: return
        if (value.optInt("protocolVersion", -1) != CandyToppingHostContract.PROTOCOL_VERSION) return
        when (value.optString("type")) {
            "bridge" -> {
                handleBridgeMessage(value, sourcePort)
                return
            }
            "document-disconnected" -> {
                handleDocumentDisconnected(value)
                return
            }
        }
        if (gate.state != GeckoToppingHostState.Initializing) return
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

    private fun handleBridgeMessage(message: JSONObject, sourcePort: WebExtension.Port) {
        val bridgeId = message.optLong("bridgeId", -1L).takeIf { it in 1..Int.MAX_VALUE } ?: return
        val scriptId = message.optString("scriptId").takeIf(String::isNotBlank) ?: return
        val geckoTabId = message.optLong("geckoTabId", -1L).takeIf { it >= 0L } ?: return
        val frameId = message.optLong("frameId", -1L).takeIf { it >= 0L } ?: return
        val documentId = message.optString("documentId")
            .takeIf { it.isNotBlank() && it.length <= MAX_DOCUMENT_ID_CHARS } ?: return
        val bindingToken = message.optString("bindingToken")
        val binding = sessionBindings[bindingToken]
        val document = GeckoToppingDocumentKey(
            bindingToken = bindingToken,
            geckoTabId = geckoTabId,
            frameId = frameId,
            documentId = documentId,
        )
        val pageUrl = message.optString("url")
        val topUrl = message.optString("topUrl")
        val script = scriptsById[scriptId]
        val request = message.optString("payload").takeIf(String::isNotBlank)
            ?.let(UserScriptBridgeContract::parse)
        val accepted = binding != null &&
            binding.allowsToppings &&
            script != null &&
            request != null &&
            GeckoToppingDocumentRules.isEligible(script, pageUrl, topUrl, frameId) &&
            rateWindows.getOrPut(bindingToken to scriptId, ::GeckoMessageRateWindow)
                .accept(System.currentTimeMillis())
        val response = if (accepted) {
            applyBridgeRequest(
                document = document,
                binding = requireNotNull(binding),
                script = requireNotNull(script),
                request = requireNotNull(request),
            )
        } else {
            bridgeFailure(request)
        }
        runCatching {
            sourcePort.postMessage(
                JSONObject()
                    .put("type", "bridge-response")
                    .put("protocolVersion", CandyToppingHostContract.PROTOCOL_VERSION)
                    .put("bridgeId", bridgeId)
                    .put("response", response),
            )
        }
    }

    private fun applyBridgeRequest(
        document: GeckoToppingDocumentKey,
        binding: SessionBinding,
        script: UserScript,
        request: UserScriptBridgeRequest,
    ): JSONObject = when (request) {
        is UserScriptBridgeRequest.SetValue -> {
            val succeeded = UserScriptGrant.SetValue in script.grants && valueStore.set(
                scriptId = script.id,
                key = request.key,
                encodedValue = request.encodedValue,
            )
            valueResponse(request.requestId, script.id, succeeded).also {
                if (succeeded) reconcile(scriptsById.values.toList())
            }
        }
        is UserScriptBridgeRequest.DeleteValue -> {
            val succeeded = UserScriptGrant.DeleteValue in script.grants &&
                valueStore.delete(script.id, request.key)
            valueResponse(request.requestId, script.id, succeeded).also {
                if (succeeded) reconcile(scriptsById.values.toList())
            }
        }
        is UserScriptBridgeRequest.RegisterMenu -> {
            val key = GeckoMenuCommandKey(document, script.id, request.commandId)
            val scriptCount = menuCommands.keys.count { candidate ->
                candidate.document.bindingToken == document.bindingToken &&
                    candidate.scriptId == script.id
            }
            val tabCount = menuCommands.keys.count { candidate ->
                candidate.document.bindingToken == document.bindingToken
            }
            val succeeded = UserScriptGrant.RegisterMenuCommand in script.grants &&
                (key in menuCommands || scriptCount < MAX_MENU_COMMANDS_PER_SCRIPT) &&
                (key in menuCommands || tabCount < MAX_MENU_COMMANDS_PER_TAB)
            if (succeeded) {
                menuCommands[key] = request.caption
                publishCommands(binding.candyTabId)
            }
            JSONObject().put("ok", succeeded)
        }
        is UserScriptBridgeRequest.UnregisterMenu -> {
            val key = GeckoMenuCommandKey(document, script.id, request.commandId)
            val succeeded = UserScriptGrant.UnregisterMenuCommand in script.grants &&
                menuCommands.remove(key) != null
            publishCommands(binding.candyTabId)
            JSONObject().put("ok", succeeded)
        }
        is UserScriptBridgeRequest.OpenTab -> {
            val succeeded = UserScriptGrant.OpenInTab in script.grants &&
                openTabRateWindows.getOrPut(binding.token to script.id) {
                    GeckoMessageRateWindow(
                        maxMessages = MAX_OPEN_TABS_PER_WINDOW,
                        windowMillis = OPEN_TAB_RATE_WINDOW_MILLIS,
                    )
                }.accept(System.currentTimeMillis())
            if (succeeded) {
                interactionDelegate.onOpenTab(
                    UserScriptOpenTabRequest(
                        tabId = binding.candyTabId,
                        scriptId = script.id,
                        url = request.url,
                        active = request.active,
                    ),
                )
            }
            JSONObject().put("ok", succeeded)
        }
        UserScriptBridgeRequest.DisposeDocument -> {
            menuCommands.keys.removeAll { key ->
                key.document == document && key.scriptId == script.id
            }
            publishCommands(binding.candyTabId)
            JSONObject().put("ok", true)
        }
    }

    private fun bridgeFailure(request: UserScriptBridgeRequest?): JSONObject = when (request) {
        is UserScriptBridgeRequest.SetValue -> JSONObject()
            .put("id", request.requestId)
            .put("ok", false)
        is UserScriptBridgeRequest.DeleteValue -> JSONObject()
            .put("id", request.requestId)
            .put("ok", false)
        else -> JSONObject().put("ok", false)
    }

    private fun valueResponse(requestId: Long, scriptId: String, succeeded: Boolean): JSONObject =
        JSONObject()
            .put("id", requestId)
            .put("ok", succeeded)
            .apply {
                if (succeeded) put("snapshot", valueStore.encodedSnapshot(scriptId))
            }

    private fun publishCommands(tabId: String) {
        val commands = menuCommands.mapNotNull { (key, caption) ->
            if (sessionBindings[key.document.bindingToken]?.candyTabId != tabId) {
                return@mapNotNull null
            }
            val script = scriptsById[key.scriptId] ?: return@mapNotNull null
            UserScriptMenuCommand(
                tabId = tabId,
                scriptId = script.id,
                scriptName = script.name,
                commandId = key.commandId,
                caption = caption,
                documentId = key.document.documentId,
            )
        }
        interactionDelegate.onMenuCommandsChanged(tabId, commands)
    }

    private fun handleDocumentDisconnected(message: JSONObject) {
        val document = message.documentKey() ?: return
        val scriptId = message.optString("scriptId").takeIf(String::isNotBlank) ?: return
        val binding = sessionBindings[document.bindingToken] ?: return
        val changed = menuCommands.keys.removeAll { key ->
            key.document == document && key.scriptId == scriptId
        }
        if (changed) publishCommands(binding.candyTabId)
    }

    private fun attachSessionDelegate(extension: WebExtension, binding: SessionBinding) {
        binding.session.webExtensionController.setMessageDelegate(
            extension,
            SessionMessageDelegate(binding),
            CandyToppingHostContract.NATIVE_APP,
        )
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

    private inner class SessionMessageDelegate(
        private val binding: SessionBinding,
    ) : WebExtension.MessageDelegate {
        override fun onMessage(
            nativeApp: String,
            message: Any,
            sender: WebExtension.MessageSender,
        ): GeckoResult<Any> {
            val value = message as? JSONObject
            val scriptId = value?.optString("scriptId").orEmpty()
            val script = scriptsById[scriptId]
            val frameId = value?.optLong("frameId", -1L) ?: -1L
            val pageUrl = value?.optString("url").orEmpty()
            val topUrl = value?.optString("topUrl").orEmpty()
            val challenge = value?.optString("challenge").orEmpty()
            val documentId = value?.optString("documentId").orEmpty()
            val allowed = nativeApp == CandyToppingHostContract.NATIVE_APP &&
                sender.webExtension.id == CandyToppingHostContract.EXTENSION_ID &&
                sender.environmentType == WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT &&
                sender.session === binding.session &&
                sessionBindings[binding.token] === binding &&
                value?.optString("type") == "authorize-session" &&
                value.optInt("protocolVersion", -1) == CandyToppingHostContract.PROTOCOL_VERSION &&
                challenge.isNotBlank() && challenge.length <= MAX_CHALLENGE_CHARS &&
                documentId.isNotBlank() && documentId.length <= MAX_DOCUMENT_ID_CHARS &&
                frameId >= 0L && sender.isTopLevel == (frameId == 0L) &&
                sender.url == pageUrl &&
                binding.allowsToppings &&
                script != null &&
                GeckoToppingDocumentRules.isEligible(script, pageUrl, topUrl, frameId)
            return GeckoResult.fromValue(
                JSONObject()
                    .put("allowed", allowed)
                    .put("challenge", challenge)
                    .apply {
                        if (allowed) {
                            put("bindingToken", binding.token)
                            put("candyTabId", binding.candyTabId)
                        }
                    }
                    .toString(),
            )
        }
    }

    private companion object {
        const val TAG = "CandyToppingHost"
        const val INITIALIZATION_TIMEOUT_MILLIS = 15_000L
        const val MAX_FAILURE_CHARS = 512
        const val MAX_CHALLENGE_CHARS = 128
        const val MAX_DOCUMENT_ID_CHARS = 256
        const val MAX_MENU_COMMANDS_PER_SCRIPT = 32
        const val MAX_MENU_COMMANDS_PER_TAB = 128
        const val MAX_OPEN_TABS_PER_WINDOW = 4
        const val OPEN_TAB_RATE_WINDOW_MILLIS = 10_000L
    }
}

private data class SessionBinding(
    val token: String,
    val session: GeckoSession,
    val candyTabId: String,
    val isPrivate: Boolean,
    val contentKind: BrowserEngineContentKind,
) {
    val allowsToppings: Boolean
        get() = GeckoToppingSessionRules.allows(isPrivate, contentKind)
}

private data class GeckoToppingDocumentKey(
    val bindingToken: String,
    val geckoTabId: Long,
    val frameId: Long,
    val documentId: String,
)

private data class GeckoMenuCommandKey(
    val document: GeckoToppingDocumentKey,
    val scriptId: String,
    val commandId: String,
)

private fun JSONObject.documentKey(): GeckoToppingDocumentKey? {
    val token = optString("bindingToken").takeIf(String::isNotBlank) ?: return null
    val geckoTabId = optLong("geckoTabId", -1L).takeIf { it >= 0L } ?: return null
    val frameId = optLong("frameId", -1L).takeIf { it >= 0L } ?: return null
    val documentId = optString("documentId").takeIf(String::isNotBlank) ?: return null
    return GeckoToppingDocumentKey(token, geckoTabId, frameId, documentId)
}

internal object GeckoToppingDocumentRules {
    fun isEligible(
        script: UserScript,
        pageUrl: String,
        topUrl: String,
        frameId: Long,
    ): Boolean {
        if (!UserScriptRules.matches(script, pageUrl)) return false
        return when (script.effectiveFrameScope) {
            ToppingFrameScope.Top -> frameId == 0L
            ToppingFrameScope.SameOrigin -> frameId == 0L || sameOrigin(pageUrl, topUrl)
            ToppingFrameScope.AllMatching -> true
        }
    }

    private fun sameOrigin(first: String, second: String): Boolean = runCatching {
        val firstUri = URI(first)
        val secondUri = URI(second)
        firstUri.scheme.equals(secondUri.scheme, ignoreCase = true) &&
            firstUri.host.equals(secondUri.host, ignoreCase = true) &&
            effectivePort(firstUri) == effectivePort(secondUri)
    }.getOrDefault(false)

    private fun effectivePort(uri: URI): Int = when {
        uri.port >= 0 -> uri.port
        uri.scheme.equals("http", ignoreCase = true) -> 80
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> -1
    }
}

internal object GeckoToppingSessionRules {
    fun allows(isPrivate: Boolean, contentKind: BrowserEngineContentKind): Boolean =
        !isPrivate && contentKind != BrowserEngineContentKind.LinkPeek
}

private class GeckoMessageRateWindow(
    private val maxMessages: Int = 128,
    private val windowMillis: Long = 1_000L,
) {
    private var startedAtMillis = 0L
    private var count = 0

    fun accept(nowMillis: Long): Boolean {
        if (nowMillis - startedAtMillis >= windowMillis) {
            startedAtMillis = nowMillis
            count = 0
        }
        if (count >= maxMessages) return false
        count++
        return true
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
    .put("scriptId", scriptId)
    .put("worldId", worldId)
    .put(
        "js",
        JSONArray().also { values ->
            javascriptSources.forEach { source -> values.put(JSONObject().put("code", source)) }
        },
    )
    .put("matches", JSONArray(matchPatterns))
    .put("includeGlobs", JSONArray(includeGlobs))
    .put("excludeGlobs", JSONArray(excludeGlobs))
    .put("runAt", runAt)
    .put("allFrames", allFrames)
    .put("world", "USER_SCRIPT")
