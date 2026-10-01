package dev.sk2andy.materialbrowser.data

internal data class NativeCrashCaptureCheckpoint(
    val captureSinceMillis: Long? = null,
    val collectedThroughMillis: Long = 0L,
)

internal object NativeCrashCaptureRules {
    private val GECKO_PROCESS = Regex(
        "(?:tab|isolatedTab)_disable_art_image_(?:[0-9]|[1-3][0-9])|" +
            "(?:gmplugin|socket|gpu|rdd|utility|zygoteTab)_disable_art_image_|media",
    )

    fun isBrowserProcess(packageName: String, processName: String): Boolean =
        processName == packageName ||
            processName.startsWith("$packageName:") &&
            GECKO_PROCESS.matches(processName.removePrefix("$packageName:"))

    fun allows(
        checkpoint: NativeCrashCaptureCheckpoint,
        timestampMillis: Long,
        beforeMillis: Long,
    ): Boolean {
        val since = checkpoint.captureSinceMillis ?: return false
        return timestampMillis > since &&
            timestampMillis > checkpoint.collectedThroughMillis &&
            timestampMillis < beforeMillis
    }

    fun reset(
        checkpoint: NativeCrashCaptureCheckpoint,
        allowed: Boolean,
        nowMillis: Long,
    ): NativeCrashCaptureCheckpoint {
        val cutoff = maxOf(
            nowMillis.coerceAtLeast(0L),
            checkpoint.captureSinceMillis ?: 0L,
            checkpoint.collectedThroughMillis,
        )
        return NativeCrashCaptureCheckpoint(
            captureSinceMillis = cutoff.takeIf { allowed && nowMillis >= cutoff },
            collectedThroughMillis = cutoff,
        )
    }
}
