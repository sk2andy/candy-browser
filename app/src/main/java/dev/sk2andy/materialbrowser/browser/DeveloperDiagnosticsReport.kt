package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.data.DeveloperSettings

internal data class DeveloperDiagnosticsSnapshot(
    val appVersion: String,
    val versionCode: Long,
    val buildType: String,
    val sdkInt: Int,
    val engine: AndroidBrowserEngineKind,
    val engineVersion: String?,
    val activeTabCount: Int,
    val rendererSessionCount: Int,
    val fullscreenActive: Boolean,
    val externalPreviewActive: Boolean,
    val inputDiagnosticsEnabled: Boolean,
    val developerSettings: DeveloperSettings,
)

internal object DeveloperDiagnosticsReport {
    fun render(snapshot: DeveloperDiagnosticsSnapshot): String = listOf(
        "Candy developer diagnostics",
        "App version: ${snapshot.appVersion}",
        "Version code: ${snapshot.versionCode}",
        "Build type: ${snapshot.buildType}",
        "Android SDK: ${snapshot.sdkInt}",
        "Browser engine: ${snapshot.engine.displayName}",
        "Engine version: ${snapshot.engineVersion ?: "Unavailable"}",
        "Active tabs: ${snapshot.activeTabCount}",
        "Renderer sessions: ${snapshot.rendererSessionCount}",
        "Fullscreen active: ${snapshot.fullscreenActive.yesOrNo}",
        "External preview active: ${snapshot.externalPreviewActive.yesOrNo}",
        "Input diagnostics enabled: ${snapshot.inputDiagnosticsEnabled.yesOrNo}",
        "Browser chrome scroll dispatch: " +
            snapshot.developerSettings.browserChromeScrollDispatchMode.stableId,
        "Safe-area layout quiet period: " +
            "${snapshot.developerSettings.safeAreaLayoutQuietPeriodMillis} ms",
        "Safe-area required failures: " +
            snapshot.developerSettings.safeAreaRequiredFailureCount,
        "Native safe-area fallback forced: " +
            snapshot.developerSettings.forceSafeAreaFallback.yesOrNo,
    ).joinToString(separator = "\n")

    private val AndroidBrowserEngineKind.displayName: String
        get() = when (this) {
            AndroidBrowserEngineKind.GeckoView -> "GeckoView"
            AndroidBrowserEngineKind.SystemWebView -> "System WebView"
        }

    private val Boolean.yesOrNo: String
        get() = if (this) "Yes" else "No"
}
