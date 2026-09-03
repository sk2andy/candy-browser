package dev.sk2andy.materialbrowser.browser

internal object ReleaseNotesPresentationRules {
    fun shouldPresent(
        isNewLaunch: Boolean,
        isLauncherLaunch: Boolean,
        isAppUpdate: Boolean,
        contentAvailable: Boolean,
        currentVersionCode: Long,
        lastPresentedVersionCode: Long?,
    ): Boolean = isNewLaunch &&
        isLauncherLaunch &&
        isAppUpdate &&
        contentAvailable &&
        currentVersionCode > 0L &&
        (lastPresentedVersionCode == null || currentVersionCode > lastPresentedVersionCode)
}
