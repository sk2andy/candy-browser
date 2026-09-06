package dev.sk2andy.materialbrowser.browser.gecko

import dev.sk2andy.materialbrowser.browser.userscript.UserScript
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptApi
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptGrant
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptInjection
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptRules
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptRunAt

internal object CandyToppingHostContract {
    const val EXTENSION_ID = "candy-topping-host@sk2andy.dev"
    const val EXTENSION_LOCATION = "resource://android/assets/candy_toppings/"
    const val NATIVE_APP = "dev.sk2andy.materialbrowser.toppings"
    const val OPTIONAL_PERMISSION = "userScripts"
    const val PROTOCOL_VERSION = 1
}

internal data class GeckoToppingRegistration(
    val id: String,
    val worldId: String,
    val javascript: String,
    val matchPatterns: List<String>,
    val includeGlobs: List<String>,
    val excludeGlobs: List<String>,
    val runAt: String,
)

internal enum class GeckoToppingUnsupportedReason {
    InvalidSnapshot,
    Dependencies,
    PrivilegedGrant,
}

internal data class GeckoToppingPlan(
    val registrations: List<GeckoToppingRegistration>,
    val unsupportedScripts: Map<String, GeckoToppingUnsupportedReason>,
)

/** Builds GeckoView-140 browser.userScripts registrations from Candy's persisted model. */
internal object CandyToppingHostCompiler {
    fun compile(scripts: List<UserScript>): GeckoToppingPlan {
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
            val reason = unsupportedReason(script)
            if (reason != null) {
                unsupported[script.id] = reason
                return@mapNotNull null
            }
            registration(script)
        }
        return GeckoToppingPlan(
            registrations = registrations.sortedBy(GeckoToppingRegistration::id),
            unsupportedScripts = unsupported.toSortedMap(),
        )
    }

    private fun unsupportedReason(script: UserScript): GeckoToppingUnsupportedReason? = when {
        script.requires.isNotEmpty() || script.resources.isNotEmpty() ->
            GeckoToppingUnsupportedReason.Dependencies

        script.grants.any { grant -> grant !in LOCAL_GRANTS } ->
            GeckoToppingUnsupportedReason.PrivilegedGrant

        else -> null
    }

    private fun registration(script: UserScript): GeckoToppingRegistration {
        val worldId = UserScriptInjection.executionWorldName(
            buildString {
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
            },
        )
        val registrationRevision = worldId.substringAfterLast('.').take(REGISTRATION_REVISION_CHARS)
        return GeckoToppingRegistration(
            // Firefox can retain the previous source when a dynamic registration is updated in
            // place. A content-addressed ID makes each catalog revision an atomic remove/add.
            id = "candy-$registrationRevision",
            worldId = worldId,
            javascript = buildString {
                append("(async () => {\n")
                append("const __candyRuntime = globalThis.browser?.runtime;\n")
                append("if (!__candyRuntime) return;\n")
                append("const __candyAccess = await __candyRuntime.sendMessage({")
                append("type: 'private-check', protocolVersion: ")
                append(CandyToppingHostContract.PROTOCOL_VERSION)
                append("});\n")
                append("if (__candyAccess?.allowed !== true) return;\n")
                append("{ const browser = undefined; const chrome = undefined;\n")
                append(UserScriptApi.bootstrap(script, encodedValues = emptyMap()))
                append('\n')
                append(script.source)
                append("\n}\n})();")
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

    private val LOCAL_GRANTS = setOf(
        UserScriptGrant.AddStyle,
        UserScriptGrant.Info,
    )

    private const val REGISTRATION_REVISION_CHARS = 32
}

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
}
