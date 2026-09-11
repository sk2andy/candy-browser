package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.data.DeveloperSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeveloperDiagnosticsReportTest {
    @Test
    fun `report renders a stable privacy-safe runtime snapshot`() {
        val report = DeveloperDiagnosticsReport.render(
            DeveloperDiagnosticsSnapshot(
                appVersion = "0.38-debug",
                versionCode = 38,
                buildType = "debug",
                sdkInt = 36,
                engine = AndroidBrowserEngineKind.GeckoView,
                engineVersion = "155.0",
                activeTabCount = 3,
                rendererSessionCount = 2,
                fullscreenActive = true,
                externalPreviewActive = false,
                inputDiagnosticsEnabled = true,
                developerSettings = DeveloperSettings(
                    safeAreaLayoutQuietPeriodMillis = 250,
                    safeAreaRequiredFailureCount = 4,
                    forceSafeAreaFallback = true,
                ),
            ),
        )

        assertEquals(
            """
            Candy developer diagnostics
            App version: 0.38-debug
            Version code: 38
            Build type: debug
            Android SDK: 36
            Browser engine: GeckoView
            Engine version: 155.0
            Active tabs: 3
            Renderer sessions: 2
            Fullscreen active: Yes
            External preview active: No
            Input diagnostics enabled: Yes
            Safe-area layout quiet period: 250 ms
            Safe-area required failures: 4
            Native safe-area fallback forced: Yes
            """.trimIndent(),
            report,
        )
        listOf("http://", "https://", "tab id", "profile name").forEach { privateField ->
            assertFalse(report.contains(privateField, ignoreCase = true))
        }
    }

    @Test
    fun `report keeps unavailable engine version explicit`() {
        val report = DeveloperDiagnosticsReport.render(
            DeveloperDiagnosticsSnapshot(
                appVersion = "0.38",
                versionCode = 38,
                buildType = "release",
                sdkInt = 35,
                engine = AndroidBrowserEngineKind.SystemWebView,
                engineVersion = null,
                activeTabCount = 1,
                rendererSessionCount = 1,
                fullscreenActive = false,
                externalPreviewActive = true,
                inputDiagnosticsEnabled = false,
                developerSettings = DeveloperSettings(),
            ),
        )

        assertEquals(
            """
            Candy developer diagnostics
            App version: 0.38
            Version code: 38
            Build type: release
            Android SDK: 35
            Browser engine: System WebView
            Engine version: Unavailable
            Active tabs: 1
            Renderer sessions: 1
            Fullscreen active: No
            External preview active: Yes
            Input diagnostics enabled: No
            Safe-area layout quiet period: 400 ms
            Safe-area required failures: 3
            Native safe-area fallback forced: No
            """.trimIndent(),
            report,
        )
    }
}
