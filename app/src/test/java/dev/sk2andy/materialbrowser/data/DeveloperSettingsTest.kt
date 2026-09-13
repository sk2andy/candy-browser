package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DeveloperSettingsTest {
    @Test
    fun `defaults preserve the stabilized safe area fallback`() {
        val settings = DeveloperSettings()

        assertEquals(
            BrowserChromeScrollDispatchMode.Optimized,
            settings.browserChromeScrollDispatchMode,
        )
        assertEquals(400, settings.safeAreaLayoutQuietPeriodMillis)
        assertEquals(3, settings.safeAreaRequiredFailureCount)
        assertEquals(false, settings.forceSafeAreaFallback)
        assertEquals(GeckoSafeAreaSettings(), settings.geckoSafeAreaSettings)
    }

    @Test
    fun `gecko budgets normalize without changing chrome or fallback settings`() {
        val settings = DeveloperSettings(
            browserChromeScrollDispatchMode = BrowserChromeScrollDispatchMode.Fixed15Hz,
            safeAreaLayoutQuietPeriodMillis = 250,
            safeAreaRequiredFailureCount = 4,
            forceSafeAreaFallback = true,
            geckoSafeAreaSettings = GeckoSafeAreaSettings(
                enabled = false,
                interactionWindowMillis = 0,
                maxElementsPerBatch = 99,
            ),
        )

        assertEquals(
            settings.copy(
                geckoSafeAreaSettings = GeckoSafeAreaSettings(
                    enabled = false,
                    interactionWindowMillis = 100,
                    maxElementsPerBatch = 64,
                ),
            ),
            settings.normalized(),
        )
    }

    @Test
    fun `scroll dispatch mode uses stable ids and rejects unknown values`() {
        assertEquals(
            BrowserChromeScrollDispatchMode.Fixed120Hz,
            BrowserChromeScrollDispatchMode.fromStableId("fixed_120_hz"),
        )
        assertEquals(
            BrowserChromeScrollDispatchMode.Optimized,
            BrowserChromeScrollDispatchMode.fromStableId("unknown"),
        )
        assertEquals(
            BrowserChromeScrollDispatchMode.Optimized,
            BrowserChromeScrollDispatchMode.fromStableId(null),
        )
    }

    @Test
    fun `unsafe values are bounded independently`() {
        assertEquals(
            DeveloperSettings(
                safeAreaLayoutQuietPeriodMillis = 100,
                safeAreaRequiredFailureCount = 5,
                forceSafeAreaFallback = true,
            ),
            DeveloperSettings(
                safeAreaLayoutQuietPeriodMillis = 0,
                safeAreaRequiredFailureCount = 99,
                forceSafeAreaFallback = true,
            ).normalized(),
        )
    }

    @Test
    fun `layout quiet period snaps to its documented step`() {
        assertEquals(
            100,
            DeveloperSettings(safeAreaLayoutQuietPeriodMillis = 123)
                .normalized()
                .safeAreaLayoutQuietPeriodMillis,
        )
        assertEquals(
            150,
            DeveloperSettings(safeAreaLayoutQuietPeriodMillis = 126)
                .normalized()
                .safeAreaLayoutQuietPeriodMillis,
        )
    }
}
