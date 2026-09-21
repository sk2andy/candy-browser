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
import dev.sk2andy.materialbrowser.shared.topping.ToppingFrameScope
import java.net.URI
import java.util.IdentityHashMap
import java.util.UUID
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
            registration.generation++
            registration.documents.clear()
            registration.proxyGenerations.clear()
        }
        registrations.map(InstalledRegistration::tabId).distinct().forEach(::publishCommands)
    }

    fun invokeMenuCommand(command: UserScriptMenuCommand) {
        val target = installed.values.asSequence()
            .flatten()
            .filter { registration ->
                registration.tabId == command.tabId &&
                    registration.script.id == command.scriptId
            }
            .firstNotNullOfOrNull { registration ->
                registration.documents.values.firstOrNull { document ->
                    document.documentId == command.documentId &&
                        document.menuCommands[command.commandId] == command.caption &&
                        UserScriptRules.matches(registration.script, document.url)
                }?.let { document -> registration to document }
            } ?: return
        val (registration, document) = target
        val delivered = runCatching {
            document.replyProxy.postMessage(
                JSONObject()
                    .put("type", "menu-invoke")
                    .put("commandId", command.commandId)
                    .toString(),
            )
        }.isSuccess
        if (!delivered) {
            registration.documents.remove(document.replyProxy)
            publishCommands(registration.tabId)
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
            rawMessage == null ||
            installed[sourceView]?.any { candidate -> candidate === registration } != true ||
            (registration.script.effectiveFrameScope == ToppingFrameScope.Top && !isMainFrame)
        ) return
        if (sourceOrigin.scheme?.lowercase() !in WEB_SCHEMES) return
        val request = UserScriptBridgeContract.parse(rawMessage) ?: return
        if (
            request !is UserScriptBridgeRequest.DisposeDocument &&
            !registration.rateWindow.accept(System.currentTimeMillis())
        ) {
            request.requestId()?.let { requestId ->
                reply(replyProxy, requestId, succeeded = false)
            }
            return
        }
        val registrationGeneration = registration.generation
        val proxyGeneration = registration.proxyGenerations[replyProxy] ?: 0L
        runCatching {
            replyProxy.executeJavaScript(
                UserScriptInjection.frameValidationSource(registration.script.id),
                object : WebViewOutcomeReceiver<String, JavaScriptExecutionException> {
                    override fun onResult(result: String) {
                        val frame = parseFrameValidation(result) ?: return
                        if (
                            installed[sourceView]?.any { candidate ->
                                candidate === registration
                            } != true ||
                            registration.generation != registrationGeneration ||
                            (registration.proxyGenerations[replyProxy] ?: 0L) != proxyGeneration ||
                            !frame.allowed ||
                            !sameOrigin(frame.url, sourceOrigin) ||
                            !UserScriptRules.matches(registration.script, frame.url)
                        ) return
                        val document = bindDocument(registration, replyProxy, frame.url)
                        val succeeded = applyMessage(registration, document, request)
                        if (document.menuCommands.isEmpty()) {
                            registration.documents.remove(replyProxy)
                        }
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
        document: InstalledDocument,
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
                request.commandId !in document.menuCommands &&
                registration.documents.values.sumOf { candidate ->
                    candidate.menuCommands.size
                } >= MAX_MENU_COMMANDS_PER_SCRIPT
            val tabCommandCount = installed.values.flatten()
                .filter { candidate -> candidate.tabId == registration.tabId }
                .sumOf { candidate ->
                    candidate.documents.values.sumOf { document ->
                        document.menuCommands.size
                    }
                }
            val tabLimitReached = request.commandId !in document.menuCommands &&
                tabCommandCount >= MAX_MENU_COMMANDS_PER_TAB
            if (
                UserScriptGrant.RegisterMenuCommand !in registration.script.grants ||
                scriptLimitReached ||
                tabLimitReached
            ) {
                false
            } else {
                document.menuCommands[request.commandId] = request.caption
                publishCommands(registration.tabId)
                true
            }
        }
        UserScriptBridgeRequest.DisposeDocument -> {
            registration.documents.remove(document.replyProxy)
            registration.proxyGenerations[document.replyProxy] =
                (registration.proxyGenerations[document.replyProxy] ?: 0L) + 1L
            publishCommands(registration.tabId)
            true
        }
        is UserScriptBridgeRequest.UnregisterMenu -> {
            UserScriptGrant.UnregisterMenuCommand in registration.script.grants &&
                (document.menuCommands.remove(request.commandId) != null).also {
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
    ): InstalledDocument {
        val existing = registration.documents[replyProxy]
        if (existing == null) {
            return InstalledDocument(
                documentId = UUID.randomUUID().toString(),
                replyProxy = replyProxy,
                url = sourceUrl,
            ).also { document -> registration.documents[replyProxy] = document }
        }
        if (existing.url != sourceUrl) {
            existing.url = sourceUrl
            existing.menuCommands.clear()
            publishCommands(registration.tabId)
        }
        return existing
    }

    private fun publishCommands(tabId: String) {
        val commands = installed.values.flatten()
            .filter { registration -> registration.tabId == tabId }
            .flatMap { registration ->
                registration.documents.values.flatMap { document ->
                    document.menuCommands.map { (commandId, caption) ->
                        UserScriptMenuCommand(
                            tabId = tabId,
                            scriptId = registration.script.id,
                            scriptName = registration.script.name,
                            commandId = commandId,
                            caption = caption,
                            documentId = document.documentId,
                        )
                    }
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
        UserScriptBridgeRequest.DisposeDocument,
        -> null
    }

    private fun remove(webView: WebView, registration: InstalledRegistration) {
        registration.documents.clear()
        registration.proxyGenerations.clear()
        registration.generation++
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

    private fun parseFrameValidation(result: String): FrameValidation? {
        val decoded = runCatching {
            JSONTokener(result).nextValue() as? String
        }.getOrNull() ?: result
        val value = runCatching { JSONObject(decoded) }.getOrNull() ?: return null
        if (value.length() != 2) return null
        val url = value.opt("url") as? String ?: return null
        val allowed = value.opt("allowed") as? Boolean ?: return null
        return FrameValidation(url = url, allowed = allowed)
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
        val documents: MutableMap<JavaScriptReplyProxy, InstalledDocument> = IdentityHashMap(),
        val proxyGenerations: MutableMap<JavaScriptReplyProxy, Long> = IdentityHashMap(),
        var generation: Long = 0L,
        var bridgeInstalled: Boolean = false,
    )

    private class InstalledDocument(
        val documentId: String,
        val replyProxy: JavaScriptReplyProxy,
        var url: String,
        val menuCommands: LinkedHashMap<String, String> = linkedMapOf(),
    )

    private data class FrameValidation(
        val url: String,
        val allowed: Boolean,
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
