package dev.sk2andy.materialbrowser.browser

internal object ReleaseNotesPresentationRules {
    fun shouldPresent(
        isNewLaunch: Boolean,
        isLauncherLaunch: Boolean,
        isAppUpdate: Boolean,
        isInitialOnboardingRequired: Boolean,
        contentAvailable: Boolean,
        currentVersionCode: Long,
        lastHandledVersionCode: Long?,
    ): Boolean = isNewLaunch &&
        isLauncherLaunch &&
        isAppUpdate &&
        !isInitialOnboardingRequired &&
        contentAvailable &&
        currentVersionCode > 0L &&
        (lastHandledVersionCode == null || currentVersionCode > lastHandledVersionCode)
}
