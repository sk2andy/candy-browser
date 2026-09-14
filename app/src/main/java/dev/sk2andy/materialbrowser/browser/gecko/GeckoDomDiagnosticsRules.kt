package dev.sk2andy.materialbrowser.browser.gecko

internal object GeckoDomDiagnosticsRules {
    const val MAX_PAYLOAD_CHARS = 24 * 1_024

    fun requestRejection(enabled: Boolean, hasPrivateSession: Boolean, activeTargets: Int, busy: Boolean): String? = when {
        !enabled -> "disabled"
        hasPrivateSession -> "private_session"
        activeTargets == 0 -> "target_unavailable"
        activeTargets != 1 -> "ambiguous_target"
        busy -> "busy"
        else -> null
    }

    fun canPublish(requestGeneration: Long, currentGeneration: Long, hasPrivateSession: Boolean, payloadChars: Int): Boolean =
        requestGeneration == currentGeneration && !hasPrivateSession && payloadChars in 2..MAX_PAYLOAD_CHARS
}
