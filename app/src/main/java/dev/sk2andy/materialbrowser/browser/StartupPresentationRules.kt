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
        isOnboardingRequired: Boolean,
    ): StartupPresentation {
        val isRegularLauncherStart = isColdStart && isLauncherLaunch
        return StartupPresentation(
            showSplash = isRegularLauncherStart && isStartupAnimationEnabled,
            openAddressEditor = isColdStart && shouldOpenAddressEditor(
                isLauncherLaunch = isLauncherLaunch,
                isStartupAnimationEnabled = isStartupAnimationEnabled,
                isOnboardingRequired = isOnboardingRequired,
            ),
        )
    }

    fun shouldOpenAddressEditor(
        isLauncherLaunch: Boolean,
        isStartupAnimationEnabled: Boolean,
        isOnboardingRequired: Boolean,
    ): Boolean = isLauncherLaunch &&
        !isStartupAnimationEnabled &&
        !isOnboardingRequired
}
