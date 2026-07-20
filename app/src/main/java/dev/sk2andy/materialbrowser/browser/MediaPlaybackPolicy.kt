package dev.sk2andy.materialbrowser.browser

internal object MediaPlaybackPolicy {
    fun requiresUserGesture(
        tabId: String,
        selectedTabId: String,
        isActivityResumed: Boolean,
    ): Boolean = tabId != selectedTabId || !isActivityResumed
}
