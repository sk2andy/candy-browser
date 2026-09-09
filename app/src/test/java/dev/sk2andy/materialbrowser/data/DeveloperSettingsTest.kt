package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DeveloperSettingsTest {
    @Test
    fun `defaults preserve the stabilized safe area fallback`() {
        val settings = DeveloperSettings()

        assertEquals(400, settings.safeAreaLayoutQuietPeriodMillis)
        assertEquals(3, settings.safeAreaRequiredFailureCount)
    }

    @Test
    fun `unsafe values are bounded independently`() {
        assertEquals(
            DeveloperSettings(
                safeAreaLayoutQuietPeriodMillis = 100,
                safeAreaRequiredFailureCount = 5,
            ),
            DeveloperSettings(
                safeAreaLayoutQuietPeriodMillis = 0,
                safeAreaRequiredFailureCount = 99,
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
