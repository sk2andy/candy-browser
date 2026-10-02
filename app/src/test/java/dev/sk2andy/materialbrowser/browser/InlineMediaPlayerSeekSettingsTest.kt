package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class InlineMediaPlayerSeekSettingsTest {
    @Test
    fun `defaults retain ten seconds in both directions`() {
        assertEquals(InlineMediaPlayerSeekSettings(10, 10), InlineMediaPlayerSeekSettings())
        assertEquals(listOf(5, 10, 15, 20, 30, 60), InlineMediaPlayerSeekSettingsRules.SupportedSeconds)
    }

    @Test
    fun `supported choices preserve independent directions`() {
        for (backward in listOf(5, 10, 15, 20, 30, 60)) {
            for (forward in listOf(5, 10, 15, 20, 30, 60)) {
                val settings = InlineMediaPlayerSeekSettings(backward, forward)
                assertEquals(settings, InlineMediaPlayerSeekSettingsRules.normalize(settings))
            }
        }
    }

    @Test
    fun `invalid direction falls back without changing valid peer`() {
        for (invalid in listOf(Int.MIN_VALUE, -1, 0, 1, 9, 11, 59, 61, Int.MAX_VALUE)) {
            assertEquals(10, InlineMediaPlayerSeekSettingsRules.normalizeSeconds(invalid))
            assertEquals(
                InlineMediaPlayerSeekSettings(10, 30),
                InlineMediaPlayerSeekSettingsRules.normalize(InlineMediaPlayerSeekSettings(invalid, 30)),
            )
            assertEquals(
                InlineMediaPlayerSeekSettings(15, 10),
                InlineMediaPlayerSeekSettingsRules.normalize(InlineMediaPlayerSeekSettings(15, invalid)),
            )
        }
    }
}
