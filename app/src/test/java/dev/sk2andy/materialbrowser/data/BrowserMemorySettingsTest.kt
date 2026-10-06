package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserMemorySettingsTest {
    @Test
    fun `defaults unload idle tabs after three minutes and retain no extra background tab`() {
        val settings = BrowserMemorySettings()

        assertEquals(3, settings.foregroundTabIdleTimeoutMinutes)
        assertEquals(0, settings.backgroundWarmTabCount)
        assertEquals(5, settings.historyCacheLifetimeMinutes)
    }

    @Test
    fun `bounds keep idle timeout positive and background warm quota nonnegative`() {
        assertEquals(
            BrowserMemorySettings(
                foregroundTabIdleTimeoutMinutes = 1,
                backgroundWarmTabCount = 0,
                historyCacheLifetimeMinutes = 1,
            ),
            BrowserMemorySettings(
                foregroundTabIdleTimeoutMinutes = Int.MIN_VALUE,
                backgroundWarmTabCount = Int.MIN_VALUE,
                historyCacheLifetimeMinutes = Int.MIN_VALUE,
            ).normalized(),
        )
        assertEquals(
            BrowserMemorySettings(
                foregroundTabIdleTimeoutMinutes = 60,
                backgroundWarmTabCount = 20,
                historyCacheLifetimeMinutes = 60,
            ),
            BrowserMemorySettings(
                foregroundTabIdleTimeoutMinutes = Int.MAX_VALUE,
                backgroundWarmTabCount = Int.MAX_VALUE,
                historyCacheLifetimeMinutes = Int.MAX_VALUE,
            ).normalized(),
        )
    }

    @Test
    fun `developer settings normalize memory controls without changing logging or fallback`() {
        val settings = DeveloperSettings(
            appLoggingEnabled = true,
            forceSafeAreaFallback = true,
            browserMemorySettings = BrowserMemorySettings(
                foregroundTabIdleTimeoutMinutes = 0,
                backgroundWarmTabCount = 4,
                historyCacheLifetimeMinutes = 0,
            ),
        )
        val normalized = settings.normalized()

        assertEquals(
            settings.copy(
                browserMemorySettings = BrowserMemorySettings(
                    foregroundTabIdleTimeoutMinutes = 1,
                    backgroundWarmTabCount = 4,
                    historyCacheLifetimeMinutes = 1,
                ),
            ),
            normalized,
        )
        assertEquals(normalized, normalized.normalized())
    }
}
