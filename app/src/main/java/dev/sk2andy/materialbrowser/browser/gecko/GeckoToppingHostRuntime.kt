package dev.sk2andy.materialbrowser.browser.gecko

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.UiThread
import dev.sk2andy.materialbrowser.browser.userscript.UserScript
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptBridgeContract
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptBridgeRequest
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptGrant
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptMenuCommand
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptOpenTabRequest
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptRules
import dev.sk2andy.materialbrowser.data.UserScriptValueStore
import org.json.JSONArray
import org.json.JSONObject
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
    private var port: WebExtension.Port? = null
    private var scriptsById: Map<String, UserScript> = emptyMap()
    private var interactionDelegate: GeckoToppingInteractionDelegate =
        GeckoToppingInteractionDelegate.None
    private var activeCandyTabId: String? = null
    private val candyTabIdsByGeckoTab = mutableMapOf<Long, String>()
    private val menuCommands = linkedMapOf<GeckoMenuCommandKey, String>()
    private val rateWindows = mutableMapOf<Pair<Long, String>, GeckoMessageRateWindow>()
    private val openTabRateWindows = mutableMapOf<Pair<Long, String>, GeckoMessageRateWindow>()
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
            candyTabIdsByGeckoTab[key.geckoTabId]
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
        candyTabIdsByGeckoTab.values.distinct().forEach(::publishCommands)
    }

    override fun setActiveTab(tabId: String?) {
        activeCandyTabId = tabId
        if (tabId != null) publishActiveTabBinding()
    }

    override fun invokeMenuCommand(command: UserScriptMenuCommand) {
        val entry = menuCommands.entries.firstOrNull { (key, caption) ->
            candyTabIdsByGeckoTab[key.geckoTabId] == command.tabId &&
                key.scriptId == command.scriptId &&
                key.commandId == command.commandId &&
                caption == command.caption
        } ?: return
        port?.postMessage(
            JSONObject()
                .put("type", "menu-invoke")
                .put("protocolVersion", CandyToppingHostContract.PROTOCOL_VERSION)
                .put("geckoTabId", entry.key.geckoTabId)
                .put("scriptId", entry.key.scriptId)
                .put("commandId", entry.key.commandId),
        )
    }

    override fun clearValues(scriptId: String) {
        valueStore.clear(scriptId)
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

    private fun publishActiveTabBinding() {
        val tabId = activeCandyTabId ?: return
        port?.postMessage(
            JSONObject()
                .put("type", "bind-active")
                .put("protocolVersion", CandyToppingHostContract.PROTOCOL_VERSION)
                .put("candyTabId", tabId),
        )
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
            "tab-bound" -> {
                bindTab(value)
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

    private fun bindTab(message: JSONObject) {
        val geckoTabId = message.optLong("geckoTabId", -1L).takeIf { it >= 0L } ?: return
        val candyTabId = message.optString("candyTabId").takeIf(String::isNotBlank) ?: return
        if (candyTabId != activeCandyTabId) return
        val previous = candyTabIdsByGeckoTab.put(geckoTabId, candyTabId)
        previous?.takeIf { it != candyTabId }?.let(::publishCommands)
        publishCommands(candyTabId)
    }

    private fun handleBridgeMessage(message: JSONObject, sourcePort: WebExtension.Port) {
        val bridgeId = message.optLong("bridgeId", -1L).takeIf { it in 1..Int.MAX_VALUE } ?: return
        val scriptId = message.optString("scriptId").takeIf(String::isNotBlank) ?: return
        val geckoTabId = message.optLong("geckoTabId", -1L).takeIf { it >= 0L } ?: return
        bindActiveBridgeTab(message, geckoTabId)
        val pageUrl = message.optString("url")
        val script = scriptsById[scriptId]
        val request = message.optString("payload").takeIf(String::isNotBlank)
            ?.let(UserScriptBridgeContract::parse)
        val accepted = script != null &&
            !message.optBoolean("incognito", true) &&
            request != null &&
            UserScriptRules.matches(script, pageUrl) &&
            rateWindows.getOrPut(geckoTabId to scriptId, ::GeckoMessageRateWindow)
                .accept(System.currentTimeMillis())
        val response = if (accepted) {
            applyBridgeRequest(
                geckoTabId = geckoTabId,
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

    private fun bindActiveBridgeTab(message: JSONObject, geckoTabId: Long) {
        val candyTabId = message.optString("candyTabId").takeIf(String::isNotBlank) ?: return
        if (!message.optBoolean("active", false) || candyTabId != activeCandyTabId) return
        val previous = candyTabIdsByGeckoTab.put(geckoTabId, candyTabId)
        previous?.takeIf { it != candyTabId }?.let(::publishCommands)
    }

    private fun applyBridgeRequest(
        geckoTabId: Long,
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
            val key = GeckoMenuCommandKey(geckoTabId, script.id, request.commandId)
            val scriptCount = menuCommands.keys.count { candidate ->
                candidate.geckoTabId == geckoTabId && candidate.scriptId == script.id
            }
            val tabCount = menuCommands.keys.count { candidate ->
                candidate.geckoTabId == geckoTabId
            }
            val succeeded = UserScriptGrant.RegisterMenuCommand in script.grants &&
                (key in menuCommands || scriptCount < MAX_MENU_COMMANDS_PER_SCRIPT) &&
                (key in menuCommands || tabCount < MAX_MENU_COMMANDS_PER_TAB)
            if (succeeded) {
                menuCommands[key] = request.caption
                candyTabIdsByGeckoTab[geckoTabId]?.let(::publishCommands)
            }
            JSONObject().put("ok", succeeded)
        }
        is UserScriptBridgeRequest.UnregisterMenu -> {
            val key = GeckoMenuCommandKey(geckoTabId, script.id, request.commandId)
            val succeeded = UserScriptGrant.UnregisterMenuCommand in script.grants &&
                menuCommands.remove(key) != null
            candyTabIdsByGeckoTab[geckoTabId]?.let(::publishCommands)
            JSONObject().put("ok", succeeded)
        }
        is UserScriptBridgeRequest.OpenTab -> {
            val candyTabId = candyTabIdsByGeckoTab[geckoTabId]
            val succeeded = candyTabId != null &&
                UserScriptGrant.OpenInTab in script.grants &&
                openTabRateWindows.getOrPut(geckoTabId to script.id) {
                    GeckoMessageRateWindow(
                        maxMessages = MAX_OPEN_TABS_PER_WINDOW,
                        windowMillis = OPEN_TAB_RATE_WINDOW_MILLIS,
                    )
                }.accept(System.currentTimeMillis())
            if (succeeded) {
                interactionDelegate.onOpenTab(
                    UserScriptOpenTabRequest(
                        tabId = requireNotNull(candyTabId),
                        scriptId = script.id,
                        url = request.url,
                        active = request.active,
                    ),
                )
            }
            JSONObject().put("ok", succeeded)
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
            if (candyTabIdsByGeckoTab[key.geckoTabId] != tabId) return@mapNotNull null
            val script = scriptsById[key.scriptId] ?: return@mapNotNull null
            UserScriptMenuCommand(
                tabId = tabId,
                scriptId = script.id,
                scriptName = script.name,
                commandId = key.commandId,
                caption = caption,
            )
        }
        interactionDelegate.onMenuCommandsChanged(tabId, commands)
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
            publishActiveTabBinding()
        }
    }

    private companion object {
        const val TAG = "CandyToppingHost"
        const val INITIALIZATION_TIMEOUT_MILLIS = 15_000L
        const val MAX_FAILURE_CHARS = 512
        const val MAX_MENU_COMMANDS_PER_SCRIPT = 32
        const val MAX_MENU_COMMANDS_PER_TAB = 128
        const val MAX_OPEN_TABS_PER_WINDOW = 4
        const val OPEN_TAB_RATE_WINDOW_MILLIS = 10_000L
    }
}

private data class GeckoMenuCommandKey(
    val geckoTabId: Long,
    val scriptId: String,
    val commandId: String,
)

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
    .put("allFrames", false)
    .put("world", "USER_SCRIPT")
