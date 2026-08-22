package dev.sk2andy.materialbrowser.browser.userscript

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptExecutionWorld
import androidx.webkit.JavaScriptExecutionException
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewOutcomeReceiver
import dev.sk2andy.materialbrowser.data.UserScriptValueStore
import java.net.URI
import org.json.JSONTokener
import org.json.JSONObject

internal class UserScriptRuntime(
    private val valueStore: UserScriptValueStore,
    private val onMenuCommandsChanged: (String, List<UserScriptMenuCommand>) -> Unit = { _, _ -> },
    private val onOpenTab: (UserScriptOpenTabRequest) -> Unit = {},
) {
    private val installed = mutableMapOf<WebView, List<InstalledRegistration>>()

    fun install(
        tabId: String,
        webView: WebView,
        scripts: List<UserScript>,
        isPrivate: Boolean,
    ) {
        remove(webView)
        if (
            !WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD)
        ) return
        val registrations = UserScriptRules.selectForRegistration(scripts, isPrivate)
            .mapNotNull { script -> registration(tabId, webView, script) }
        val injectedBytes = registrations.sumOf { registration ->
            registration.sources.guardSource.toByteArray(Charsets.UTF_8).size.toLong() +
                registration.sources.userSource.toByteArray(Charsets.UTF_8).size.toLong()
        }
        if (injectedBytes > MAX_RUNTIME_WRAPPER_BYTES) return

        val active = mutableListOf<InstalledRegistration>()
        fun rollback() {
            active.forEach { registration -> remove(webView, registration) }
            active.clear()
        }
        registrations.forEach { registration ->
            if (registration.needsBridge) {
                val added = runCatching {
                    WebViewCompat.addWebMessageListener(
                        webView,
                        UserScriptBridgeContract.BRIDGE_NAME,
                        registration.allowedOrigins,
                        registration.executionWorld,
                    ) { sourceView, message, sourceOrigin, isMainFrame, replyProxy ->
                        handleMessage(
                            expectedView = webView,
                            registration = registration,
                            sourceView = sourceView,
                            sourceOrigin = sourceOrigin,
                            isMainFrame = isMainFrame,
                            rawMessage = message.data,
                            replyProxy = replyProxy,
                        )
                    }
                }.isSuccess
                if (!added) {
                    rollback()
                    return
                }
                registration.bridgeInstalled = true
            }
            active += registration
        }
        registrations.forEach { registration ->
            val handler = runCatching {
                WebViewCompat.addJavaScriptOnEvent(
                    webView,
                    registration.sources.guardSource,
                    WebViewCompat.INJECTION_EVENT_DOCUMENT_START,
                    registration.allowedOrigins,
                    registration.executionWorld,
                )
            }.getOrNull()
            if (handler == null) {
                rollback()
                return
            }
            registration.handlers += handler
        }
        registrations.forEach { registration ->
            val injectionEvent = when (registration.script.runAt) {
                UserScriptRunAt.DocumentStart -> WebViewCompat.INJECTION_EVENT_DOCUMENT_START
                UserScriptRunAt.DocumentEnd -> WebViewCompat.INJECTION_EVENT_DOCUMENT_END
            }
            val handler = runCatching {
                WebViewCompat.addJavaScriptOnEvent(
                    webView,
                    registration.sources.userSource,
                    injectionEvent,
                    registration.allowedOrigins,
                    registration.executionWorld,
                )
            }.getOrNull()
            if (handler == null) {
                rollback()
                return
            }
            registration.handlers += handler
        }
        if (active.isNotEmpty()) installed[webView] = active
    }

    fun remove(webView: WebView) {
        val removed = installed.remove(webView).orEmpty()
        removed.forEach { registration ->
            remove(webView, registration)
        }
        removed.map(InstalledRegistration::tabId).distinct().forEach(::publishCommands)
    }

    fun clearValues(scriptId: String) = valueStore.clear(scriptId)

    fun clearMenuCommands(webView: WebView) {
        val registrations = installed[webView].orEmpty()
        registrations.forEach { registration ->
            registration.replyProxy = null
            registration.documentUrl = null
            registration.menuCommands.clear()
        }
        registrations.map(InstalledRegistration::tabId).distinct().forEach(::publishCommands)
    }

    fun invokeMenuCommand(command: UserScriptMenuCommand) {
        val registration = installed.values.firstNotNullOfOrNull { registrations ->
            registrations.firstOrNull { candidate ->
                candidate.tabId == command.tabId &&
                    candidate.script.id == command.scriptId &&
                    candidate.menuCommands[command.commandId] == command.caption &&
                    candidate.documentUrl?.let { url ->
                        UserScriptRules.matches(candidate.script, url)
                    } == true
            }
        } ?: return
        val replyProxy = registration.replyProxy ?: return
        runCatching {
            replyProxy.postMessage(
                JSONObject()
                    .put("type", "menu-invoke")
                    .put("commandId", command.commandId)
                    .toString(),
            )
        }
    }

    private fun registration(
        tabId: String,
        webView: WebView,
        script: UserScript,
    ): InstalledRegistration? {
        val allowedOrigins = UserScriptRules.allowedOriginRules(script)
        if (allowedOrigins.isEmpty()) return null
        val sources = UserScriptInjection.sources(
            script = script,
            encodedValues = valueStore.snapshot(script.id),
        ) ?: return null
        val executionWorld = runCatching {
            WebViewCompat.getExecutionWorld(
                webView,
                UserScriptInjection.executionWorldName(script.id),
            )
        }.getOrNull() ?: return null
        return InstalledRegistration(
            tabId = tabId,
            script = script,
            allowedOrigins = allowedOrigins,
            sources = sources,
            executionWorld = executionWorld,
            needsBridge = script.grants.any(BRIDGE_GRANTS::contains),
        )
    }

    private fun handleMessage(
        expectedView: WebView,
        registration: InstalledRegistration,
        sourceView: WebView,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        rawMessage: String?,
        replyProxy: JavaScriptReplyProxy,
    ) {
        if (
            sourceView !== expectedView ||
            !isMainFrame ||
            rawMessage == null ||
            installed[sourceView]?.any { candidate -> candidate === registration } != true
        ) return
        if (sourceOrigin.scheme?.lowercase() !in WEB_SCHEMES) return
        val request = UserScriptBridgeContract.parse(rawMessage) ?: return
        if (!registration.rateWindow.accept(System.currentTimeMillis())) {
            request.requestId()?.let { requestId ->
                reply(replyProxy, requestId, succeeded = false)
            }
            return
        }
        runCatching {
            replyProxy.executeJavaScript(
                "String(location.href)",
                object : WebViewOutcomeReceiver<String, JavaScriptExecutionException> {
                    override fun onResult(result: String) {
                        val sourceUrl = runCatching {
                            JSONTokener(result).nextValue() as? String
                        }.getOrNull() ?: result
                        if (
                            installed[sourceView]?.any { candidate ->
                                candidate === registration
                            } != true ||
                            !sameOrigin(sourceUrl, sourceOrigin) ||
                            !UserScriptRules.matches(registration.script, sourceUrl)
                        ) return
                        bindDocument(registration, replyProxy, sourceUrl)
                        val succeeded = applyMessage(registration, request)
                        request.requestId()?.let { requestId ->
                            reply(
                                replyProxy = replyProxy,
                                requestId = requestId,
                                succeeded = succeeded,
                                encodedSnapshot = if (succeeded) {
                                    valueStore.encodedSnapshot(registration.script.id)
                                } else {
                                    null
                                },
                            )
                        }
                    }
                },
            )
        }
    }

    private fun applyMessage(
        registration: InstalledRegistration,
        request: UserScriptBridgeRequest,
    ): Boolean = when (request) {
        is UserScriptBridgeRequest.SetValue -> {
            UserScriptGrant.SetValue in registration.script.grants &&
                valueStore.set(
                    scriptId = registration.script.id,
                    key = request.key,
                    encodedValue = request.encodedValue,
                )
        }
        is UserScriptBridgeRequest.DeleteValue -> {
            UserScriptGrant.DeleteValue in registration.script.grants &&
                valueStore.delete(registration.script.id, request.key)
        }
        is UserScriptBridgeRequest.RegisterMenu -> {
            val scriptLimitReached =
                request.commandId !in registration.menuCommands &&
                registration.menuCommands.size >= MAX_MENU_COMMANDS_PER_SCRIPT
            val tabCommandCount = installed.values.flatten()
                .filter { candidate -> candidate.tabId == registration.tabId }
                .sumOf { candidate -> candidate.menuCommands.size }
            val tabLimitReached = request.commandId !in registration.menuCommands &&
                tabCommandCount >= MAX_MENU_COMMANDS_PER_TAB
            if (
                UserScriptGrant.RegisterMenuCommand !in registration.script.grants ||
                scriptLimitReached ||
                tabLimitReached
            ) {
                false
            } else {
                registration.menuCommands[request.commandId] = request.caption
                publishCommands(registration.tabId)
                true
            }
        }
        is UserScriptBridgeRequest.UnregisterMenu -> {
            UserScriptGrant.UnregisterMenuCommand in registration.script.grants &&
                (registration.menuCommands.remove(request.commandId) != null).also {
                    publishCommands(registration.tabId)
                }
        }
        is UserScriptBridgeRequest.OpenTab -> {
            if (
                UserScriptGrant.OpenInTab !in registration.script.grants ||
                !registration.openTabRateWindow.accept(System.currentTimeMillis())
            ) {
                false
            } else {
                onOpenTab(
                    UserScriptOpenTabRequest(
                        tabId = registration.tabId,
                        scriptId = registration.script.id,
                        url = request.url,
                        active = request.active,
                    ),
                )
                true
            }
        }
    }

    private fun bindDocument(
        registration: InstalledRegistration,
        replyProxy: JavaScriptReplyProxy,
        sourceUrl: String,
    ) {
        registration.replyProxy = replyProxy
        if (registration.documentUrl == sourceUrl) return
        registration.documentUrl = sourceUrl
        if (registration.menuCommands.isNotEmpty()) {
            registration.menuCommands.clear()
            publishCommands(registration.tabId)
        }
    }

    private fun publishCommands(tabId: String) {
        val commands = installed.values.flatten()
            .filter { registration -> registration.tabId == tabId }
            .flatMap { registration ->
                registration.menuCommands.map { (commandId, caption) ->
                    UserScriptMenuCommand(
                        tabId = tabId,
                        scriptId = registration.script.id,
                        scriptName = registration.script.name,
                        commandId = commandId,
                        caption = caption,
                    )
                }
            }
        onMenuCommandsChanged(tabId, commands)
    }

    private fun reply(
        replyProxy: JavaScriptReplyProxy,
        requestId: Long,
        succeeded: Boolean,
        encodedSnapshot: String? = null,
    ) {
        runCatching {
            replyProxy.postMessage(
                JSONObject()
                    .put("id", requestId)
                    .put("ok", succeeded)
                    .apply {
                        if (encodedSnapshot != null) put("snapshot", encodedSnapshot)
                    }
                    .toString(),
            )
        }
    }

    private fun UserScriptBridgeRequest.requestId(): Long? = when (this) {
        is UserScriptBridgeRequest.SetValue -> requestId
        is UserScriptBridgeRequest.DeleteValue -> requestId
        is UserScriptBridgeRequest.RegisterMenu,
        is UserScriptBridgeRequest.UnregisterMenu,
        is UserScriptBridgeRequest.OpenTab,
        -> null
    }

    private fun remove(webView: WebView, registration: InstalledRegistration) {
        registration.handlers.forEach { handler -> runCatching(handler::remove) }
        registration.handlers.clear()
        if (registration.bridgeInstalled) {
            runCatching {
                WebViewCompat.removeWebMessageListener(
                    webView,
                    registration.executionWorld,
                    UserScriptBridgeContract.BRIDGE_NAME,
                )
            }
            registration.bridgeInstalled = false
        }
    }

    private fun sameOrigin(url: String, origin: Uri): Boolean {
        val current = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = current.scheme?.lowercase() ?: return false
        val host = current.host?.lowercase() ?: return false
        val port = current.port.takeIf { value -> value >= 0 } ?: defaultPort(scheme)
        val originPort = origin.port.takeIf { value -> value >= 0 } ?: defaultPort(
            origin.scheme?.lowercase(),
        )
        return scheme == origin.scheme?.lowercase() &&
            host == origin.host?.lowercase() &&
            port == originPort
    }

    private fun defaultPort(scheme: String?): Int = when (scheme) {
        "http" -> 80
        "https" -> 443
        else -> -1
    }

    private class InstalledRegistration(
        val tabId: String,
        val script: UserScript,
        val allowedOrigins: Set<String>,
        val sources: UserScriptInjectionSources,
        val executionWorld: JavaScriptExecutionWorld,
        val needsBridge: Boolean,
        val handlers: MutableList<ScriptHandler> = mutableListOf(),
        val rateWindow: MessageRateWindow = MessageRateWindow(),
        val openTabRateWindow: MessageRateWindow = MessageRateWindow(
            maxMessages = MAX_OPEN_TABS_PER_WINDOW,
            windowMillis = OPEN_TAB_RATE_WINDOW_MILLIS,
        ),
        val menuCommands: LinkedHashMap<String, String> = linkedMapOf(),
        var replyProxy: JavaScriptReplyProxy? = null,
        var documentUrl: String? = null,
        var bridgeInstalled: Boolean = false,
    )

    private class MessageRateWindow(
        private val maxMessages: Int = MAX_MESSAGES_PER_WINDOW,
        private val windowMillis: Long = RATE_WINDOW_MILLIS,
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

    private companion object {
        const val RATE_WINDOW_MILLIS = 1_000L
        const val MAX_MESSAGES_PER_WINDOW = 128
        const val MAX_MENU_COMMANDS_PER_SCRIPT = 32
        const val MAX_MENU_COMMANDS_PER_TAB = 128
        const val MAX_OPEN_TABS_PER_WINDOW = 4
        const val OPEN_TAB_RATE_WINDOW_MILLIS = 10_000L
        const val MAX_RUNTIME_WRAPPER_BYTES =
            UserScriptRules.MAX_REGISTERED_WRAPPER_BYTES +
                UserScriptParser.MAX_SCRIPTS * UserScriptValueStore.MAX_SCRIPT_BYTES * 2L
        val WEB_SCHEMES = setOf("http", "https")
        val MUTATING_VALUE_GRANTS = setOf(
            UserScriptGrant.DeleteValue,
            UserScriptGrant.SetValue,
        )
        val BRIDGE_GRANTS = MUTATING_VALUE_GRANTS + setOf(
            UserScriptGrant.OpenInTab,
            UserScriptGrant.RegisterMenuCommand,
            UserScriptGrant.UnregisterMenuCommand,
        )
    }
}
