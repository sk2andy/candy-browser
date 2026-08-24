package dev.sk2andy.materialbrowser.browser

internal object TabWebViewResidencyRules {
    const val DEFAULT_LIMIT = 10
    const val MIN_LIMIT = 1
    const val MAX_LIMIT = 20

    fun normalizedLimit(limit: Int): Int = limit.coerceIn(MIN_LIMIT, MAX_LIMIT)

    fun evictionOrder(
        residentTabIds: Set<String>,
        accessOrder: Map<String, Long>,
        protectedTabIds: Set<String>,
        limit: Int,
    ): List<String> {
        val excessCount = (residentTabIds.size - normalizedLimit(limit)).coerceAtLeast(0)
        if (excessCount == 0) return emptyList()
        return residentTabIds.asSequence()
            .filterNot(protectedTabIds::contains)
            .sortedWith(
                compareBy<String> { tabId -> accessOrder[tabId] ?: Long.MIN_VALUE }
                    .thenBy { tabId -> tabId },
            )
            .take(excessCount)
            .toList()
    }
}
