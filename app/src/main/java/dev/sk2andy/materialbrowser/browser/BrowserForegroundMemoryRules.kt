package dev.sk2andy.materialbrowser.browser

internal object BrowserForegroundMemoryRules {
    const val SWEEP_INTERVAL_MILLIS = 30_000L

    fun evictionOrder(
        residentTabIds: Set<String>,
        lastAccessElapsedRealtime: Map<String, Long>,
        protectedTabIds: Set<String>,
        nowElapsedRealtime: Long,
        idleTimeoutMillis: Long,
    ): List<String> = residentTabIds.asSequence()
        .filterNot(protectedTabIds::contains)
        .filter { tabId ->
            val accessedAt = lastAccessElapsedRealtime[tabId] ?: return@filter false
            nowElapsedRealtime - accessedAt >= idleTimeoutMillis.coerceAtLeast(1L)
        }
        .sortedWith(
            compareBy<String> { tabId -> lastAccessElapsedRealtime[tabId] }
                .thenBy { tabId -> tabId },
        )
        .toList()
}
