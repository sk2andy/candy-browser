package dev.sk2andy.materialbrowser.browser

internal data class StartupPresentation(
    val showSplash: Boolean,
    val openAddressEditor: Boolean,
)

internal object StartupPresentationRules {
    fun resolve(
        isColdStart: Boolean,
        isLauncherLaunch: Boolean,
        isStartupAnimationEnabled: Boolean,
        startupAddressFocusMode: StartupAddressFocusMode = StartupAddressFocusMode.Default,
        isOnboardingRequired: Boolean,
        isReleaseNotesRequired: Boolean = false,
    ): StartupPresentation {
        val isRegularLauncherStart = isColdStart && isLauncherLaunch
        return StartupPresentation(
            showSplash = isRegularLauncherStart && isStartupAnimationEnabled,
            openAddressEditor = isColdStart && shouldOpenAddressEditor(
                isLauncherLaunch = isLauncherLaunch,
                isStartupAnimationEnabled = isStartupAnimationEnabled,
                startupAddressFocusMode = startupAddressFocusMode,
                isOnboardingRequired = isOnboardingRequired,
                isReleaseNotesRequired = isReleaseNotesRequired,
            ),
        )
    }

    fun shouldOpenAddressEditor(
        isLauncherLaunch: Boolean,
        isStartupAnimationEnabled: Boolean,
        startupAddressFocusMode: StartupAddressFocusMode = StartupAddressFocusMode.Default,
        isOnboardingRequired: Boolean,
        isReleaseNotesRequired: Boolean = false,
    ): Boolean {
        if (!isLauncherLaunch || isOnboardingRequired || isReleaseNotesRequired) return false
        return when (startupAddressFocusMode) {
            StartupAddressFocusMode.WhenStartupAnimationDisabled ->
                !isStartupAnimationEnabled
            StartupAddressFocusMode.Always -> true
            StartupAddressFocusMode.Never -> false
        }
    }

    fun shouldOpenHomePage(
        isLauncherLaunch: Boolean,
        isOpenHomeOnStartupEnabled: Boolean,
    ): Boolean = isLauncherLaunch && isOpenHomeOnStartupEnabled
}
