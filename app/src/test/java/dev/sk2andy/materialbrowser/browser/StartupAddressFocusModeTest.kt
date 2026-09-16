package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupAddressFocusModeTest {
    @Test
    fun `stable ids round trip for every mode`() {
        StartupAddressFocusMode.entries.forEach { mode ->
            assertEquals(mode, StartupAddressFocusMode.fromStableId(mode.stableId))
        }
    }

    @Test
    fun `missing and unknown ids preserve previous behavior`() {
        listOf(null, "unknown").forEach { value ->
            assertEquals(
                StartupAddressFocusMode.WhenStartupAnimationDisabled,
                StartupAddressFocusMode.fromStableId(value),
            )
        }
    }
}
