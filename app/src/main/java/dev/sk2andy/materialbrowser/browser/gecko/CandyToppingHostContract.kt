package dev.sk2andy.materialbrowser.browser.gecko

import dev.sk2andy.materialbrowser.browser.userscript.UserScript
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptGrant
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptInjection
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptInjectionSources
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptRules
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptRunAt

internal object CandyToppingHostContract {
    const val EXTENSION_ID = "candy-topping-host@sk2andy.dev"
    const val EXTENSION_LOCATION = "resource://android/assets/candy_toppings/"
    const val NATIVE_APP = "dev.sk2andy.materialbrowser.toppings"
    const val OPTIONAL_PERMISSION = "userScripts"
    const val PROTOCOL_VERSION = 2
}

internal data class GeckoToppingRegistration(
    val id: String,
    val scriptId: String,
    val worldId: String,
    val javascriptSources: List<String>,
    val matchPatterns: List<String>,
    val includeGlobs: List<String>,
    val excludeGlobs: List<String>,
    val runAt: String,
)

internal enum class GeckoToppingUnsupportedReason {
    InvalidSnapshot,
    InvalidInjection,
}

internal data class GeckoToppingPlan(
    val registrations: List<GeckoToppingRegistration>,
    val unsupportedScripts: Map<String, GeckoToppingUnsupportedReason>,
)

/** Builds GeckoView-140 browser.userScripts registrations from Candy's persisted model. */
internal object CandyToppingHostCompiler {
    fun compile(
        scripts: List<UserScript>,
        encodedValues: (String) -> Map<String, String> = { emptyMap() },
    ): GeckoToppingPlan {
        if (!UserScriptRules.isWithinCollectionBounds(scripts)) {
            return GeckoToppingPlan(
                registrations = emptyList(),
                unsupportedScripts = scripts.associate { script ->
                    script.id to GeckoToppingUnsupportedReason.InvalidSnapshot
                },
            )
        }
        val unsupported = linkedMapOf<String, GeckoToppingUnsupportedReason>()
        val registrations = UserScriptRules.selectForRegistration(
            scripts = scripts,
            isPrivate = false,
        ).mapNotNull { script ->
            val values = encodedValues(script.id)
            val sources = UserScriptInjection.sources(
                script = script,
                encodedValues = values,
            )
            if (sources == null) {
                unsupported[script.id] = GeckoToppingUnsupportedReason.InvalidInjection
                return@mapNotNull null
            }
            registration(script, sources, values)
        }
        return GeckoToppingPlan(
            registrations = registrations.sortedBy(GeckoToppingRegistration::id),
            unsupportedScripts = unsupported.toSortedMap(),
        )
    }

    private fun registration(
        script: UserScript,
        sources: UserScriptInjectionSources,
        encodedValues: Map<String, String>,
    ): GeckoToppingRegistration {
        val worldSeed = buildString {
            append(script.id)
            append('\u0000')
            append(script.source)
            append('\u0000')
            append(script.matchPatterns.joinToString("\u0000"))
            append('\u0000')
            append(script.includePatterns.joinToString("\u0000"))
            append('\u0000')
            append(script.excludePatterns.joinToString("\u0000"))
            append('\u0000')
            append(script.runAt.name)
            append('\u0000')
            append(script.requires.joinToString("\u0000") { require -> require.source.orEmpty() })
            append('\u0000')
            append(script.resources.joinToString("\u0000") { resource ->
                "${resource.name}:${resource.mimeType}:${resource.encodedContent}"
            })
        }
        val worldId = UserScriptInjection.executionWorldName(worldSeed)
        val registrationRevision = UserScriptInjection.executionWorldName(
            "$worldSeed\u0000${encodedValues.toSortedMap().entries.joinToString("\u0000")}",
        ).substringAfterLast('.').take(REGISTRATION_REVISION_CHARS)
        return GeckoToppingRegistration(
            // Firefox can retain the previous source when a dynamic registration is updated in
            // place. A content-addressed ID makes each catalog revision an atomic remove/add.
            id = "candy-$registrationRevision",
            scriptId = script.id,
            worldId = worldId,
            javascriptSources = buildList {
                add(sources.guardSource)
                if (script.grants.any(BRIDGE_GRANTS::contains)) {
                    add(bridgeSource(script))
                }
                add(
                    buildString {
                        append("(async () => {\n")
                        append("const __candyRuntime = globalThis.browser?.runtime;\n")
                        append("if (!__candyRuntime) return;\n")
                        append("const __candyAccess = await __candyRuntime.sendMessage({")
                        append("type: 'private-check', protocolVersion: ")
                        append(CandyToppingHostContract.PROTOCOL_VERSION)
                        append(", scriptId: '")
                        append(script.id.javascriptSingleQuoted())
                        append("'});\n")
                        append("if (__candyAccess?.allowed !== true) return;\n")
                        append("{ const browser = undefined; const chrome = undefined;\n")
                        append(sources.userSource)
                        append("\n}\n})();")
                    },
                )
            },
            matchPatterns = script.matchPatterns.flatMap { pattern ->
                if (pattern == "<all_urls>") {
                    listOf("http://*/*", "https://*/*")
                } else {
                    listOf(pattern)
                }
            }.ifEmpty {
                // Gecko's userScripts matcher needs a positive base scope. @include then narrows
                // these web-only bases with the userscript's complete glob.
                listOf("http://*/*", "https://*/*")
            },
            includeGlobs = script.includePatterns,
            excludeGlobs = script.excludePatterns,
            runAt = when (script.runAt) {
                UserScriptRunAt.DocumentStart -> "document_start"
                UserScriptRunAt.DocumentEnd -> "document_end"
            },
        )
    }

    private fun bridgeSource(script: UserScript): String = """
        (() => {
            "use strict";
            const runtime = globalThis.browser?.runtime;
            if (!runtime || typeof runtime.sendMessage !== "function" ||
                typeof runtime.connect !== "function") return;
            const scriptPort = runtime.connect({ name: "candy-topping-user-script" });
            const bridge = {
                onmessage: null,
                postMessage(raw) {
                    Promise.resolve(runtime.sendMessage({
                        type: "bridge",
                        protocolVersion: ${CandyToppingHostContract.PROTOCOL_VERSION},
                        scriptId: "${script.id.javascriptDoubleQuoted()}",
                        payload: String(raw),
                    })).then((response) => {
                        if (response !== undefined && typeof bridge.onmessage === "function") {
                            bridge.onmessage({ data: JSON.stringify(response) });
                        }
                    }).catch(() => {});
                },
            };
            Object.defineProperty(globalThis, "${dev.sk2andy.materialbrowser.browser.userscript.UserScriptBridgeContract.BRIDGE_NAME}", {
                value: bridge,
                writable: false,
                configurable: false,
            });
            scriptPort.onMessage.addListener((message) => {
                if (message?.type !== "menu-invoke" ||
                    message?.scriptId !== "${script.id.javascriptDoubleQuoted()}") return;
                if (typeof bridge.onmessage === "function") {
                    bridge.onmessage({ data: JSON.stringify(message) });
                }
            });
        })();
    """.trimIndent()

    private val BRIDGE_GRANTS = setOf(
        UserScriptGrant.DeleteValue,
        UserScriptGrant.OpenInTab,
        UserScriptGrant.RegisterMenuCommand,
        UserScriptGrant.SetValue,
        UserScriptGrant.UnregisterMenuCommand,
    )

    private const val REGISTRATION_REVISION_CHARS = 32
}

private fun String.javascriptSingleQuoted(): String = replace("\\", "\\\\")
    .replace("'", "\\'")
    .replace("\n", "\\n")
    .replace("\r", "\\r")

private fun String.javascriptDoubleQuoted(): String = replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")

internal enum class GeckoToppingHostState {
    Initializing,
    Ready,
    Unavailable,
}

/** Revision gate; failure is terminal, while each later catalog revision is acknowledged. */
internal class GeckoToppingInitializationGate {
    var state = GeckoToppingHostState.Initializing
        private set

    private val pending = mutableListOf<() -> Unit>()
    private var stateListener: (GeckoToppingHostState) -> Unit = {}

    fun setStateListener(listener: (GeckoToppingHostState) -> Unit) {
        stateListener = listener
        listener(state)
    }

    fun runAfterInitialization(action: () -> Unit) {
        if (state == GeckoToppingHostState.Initializing) {
            pending += action
        } else {
            action()
        }
    }

    fun beginRevision(): Boolean {
        if (state == GeckoToppingHostState.Unavailable) return false
        if (state == GeckoToppingHostState.Initializing) return true
        state = GeckoToppingHostState.Initializing
        stateListener(state)
        return true
    }

    fun complete(nextState: GeckoToppingHostState) {
        require(nextState != GeckoToppingHostState.Initializing)
        // A failure is terminal. In particular, a late extension ACK must never revive a host
        // after its timeout already released navigation as unavailable.
        if (state != GeckoToppingHostState.Initializing) return
        state = nextState
        stateListener(nextState)
        val actions = pending.toList()
        pending.clear()
        actions.forEach { action -> action() }
    }
}

internal interface GeckoToppingHostRuntime {
    val state: GeckoToppingHostState

    fun reconcile(scripts: List<UserScript>)

    fun runAfterInitialization(action: () -> Unit)

    fun setStateListener(listener: (GeckoToppingHostState) -> Unit)

    fun setInteractionDelegate(delegate: GeckoToppingInteractionDelegate) = Unit

    fun setActiveTab(tabId: String?) = Unit

    fun invokeMenuCommand(command: dev.sk2andy.materialbrowser.browser.userscript.UserScriptMenuCommand) = Unit

    fun clearValues(scriptId: String) = Unit
}

internal interface GeckoToppingInteractionDelegate {
    fun onMenuCommandsChanged(
        tabId: String,
        commands: List<dev.sk2andy.materialbrowser.browser.userscript.UserScriptMenuCommand>,
    )

    fun onOpenTab(request: dev.sk2andy.materialbrowser.browser.userscript.UserScriptOpenTabRequest)

    companion object {
        val None = object : GeckoToppingInteractionDelegate {
            override fun onMenuCommandsChanged(
                tabId: String,
                commands: List<dev.sk2andy.materialbrowser.browser.userscript.UserScriptMenuCommand>,
            ) = Unit

            override fun onOpenTab(
                request: dev.sk2andy.materialbrowser.browser.userscript.UserScriptOpenTabRequest,
            ) = Unit
        }
    }
}
