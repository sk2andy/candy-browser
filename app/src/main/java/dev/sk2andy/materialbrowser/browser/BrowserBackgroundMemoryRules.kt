package dev.sk2andy.materialbrowser.browser

internal enum class BrowserBackgroundMemoryTrimAction {
    None,
    UiResources,
    UiResourcesAndSessions,
}

internal object BrowserBackgroundMemoryRules {
    const val INPUT_CHECK_TIMEOUT_MILLIS = 2_000L
    const val INPUT_CHECK_RETRY_MILLIS = 250L
    const val MAX_INPUT_CHECK_ATTEMPTS = 3

    fun shouldRetryInputCheck(attempt: Int): Boolean = attempt in 1 until MAX_INPUT_CHECK_ATTEMPTS

    fun trimAction(
        level: Int,
        isProcessInForeground: Boolean,
    ): BrowserBackgroundMemoryTrimAction {
        if (isProcessInForeground) return BrowserBackgroundMemoryTrimAction.None
        return when {
            level >= TRIM_MEMORY_BACKGROUND -> BrowserBackgroundMemoryTrimAction.UiResourcesAndSessions
            level == TRIM_MEMORY_UI_HIDDEN -> BrowserBackgroundMemoryTrimAction.UiResources
            else -> BrowserBackgroundMemoryTrimAction.None
        }
    }

    fun evictionOrder(
        residentTabIds: Set<String>,
        accessOrder: Map<String, Long>,
        selectedTabId: String?,
        protectedTabIds: Set<String>,
        isProcessInForeground: Boolean,
        warmTabCount: Int = 0,
    ): List<String> {
        if (isProcessInForeground) return emptyList()
        val retainedTabIds = if (selectedTabId == null) {
            protectedTabIds
        } else {
            protectedTabIds + selectedTabId
        }
        val unselectedTabIds = residentTabIds - setOfNotNull(selectedTabId)
        val excessCount = (unselectedTabIds.size - warmTabCount.coerceAtLeast(0)).coerceAtLeast(0)
        return unselectedTabIds.asSequence()
            .filterNot(retainedTabIds::contains)
            .sortedWith(
                compareBy<String> { tabId -> accessOrder[tabId] ?: Long.MIN_VALUE }
                    .thenBy { tabId -> tabId },
            )
            .take(excessCount)
            .toList()
    }

    private const val TRIM_MEMORY_UI_HIDDEN = 20
    private const val TRIM_MEMORY_BACKGROUND = 40
}
