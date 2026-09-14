package dev.sk2andy.materialbrowser.browser.gecko

internal object GeckoPerformanceDiagnosticsRules {
    const val MAX_CAPTURE_DURATION_MS = 120_000L
    const val MAX_PROFILE_BYTES = 64L * 1024L * 1024L

    fun startRejection(
        enabled: Boolean,
        hasRuntime: Boolean,
        hasPrivateSession: Boolean,
        isBusy: Boolean,
    ): String? = when {
        !enabled -> "disabled"
        hasPrivateSession -> "private_session"
        !hasRuntime -> "runtime_unavailable"
        isBusy -> "busy"
        else -> null
    }

    fun canPublish(
        captureGeneration: Long,
        currentGeneration: Long,
        hasPrivateSession: Boolean,
        compressedBytes: Long,
    ): Boolean = captureGeneration == currentGeneration &&
        !hasPrivateSession && compressedBytes in 2..MAX_PROFILE_BYTES

    fun hasGzipHeader(bytes: ByteArray): Boolean = bytes.size >= 2 &&
        bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()

    fun profileRejection(compressedBytes: Long, hasGzipHeader: Boolean): String? = when {
        compressedBytes > MAX_PROFILE_BYTES -> "profile_too_large"
        compressedBytes < 2 || !hasGzipHeader -> "error"
        else -> null
    }
}
