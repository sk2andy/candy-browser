package dev.sk2andy.materialbrowser.browser

object DesktopNavigationRules {
    fun shouldReplayForUserAgentChange(
        isForMainFrame: Boolean,
        method: String?,
        isTargetUserAgentApplied: Boolean,
    ): Boolean =
        isForMainFrame &&
            method.equals("GET", ignoreCase = true) &&
            !isTargetUserAgentApplied
}
