package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebContentStatusBarAppearanceRulesTest {
    @Test
    fun `opaque light theme color uses dark status icons`() {
        val appearance = WebContentStatusBarAppearanceRules.fromReportedColor("#fefefe")

        assertEquals(0xFFFEFEFE.toInt(), appearance?.colorArgb)
        assertTrue(requireNotNull(appearance).useDarkIcons)
    }

    @Test
    fun `opaque dark theme color uses light status icons`() {
        val appearance = WebContentStatusBarAppearanceRules.fromReportedColor("#121212")

        assertEquals(0xFF121212.toInt(), appearance?.colorArgb)
        assertFalse(requireNotNull(appearance).useDarkIcons)
    }

    @Test
    fun `untrusted colors outside normalized hex contract are rejected`() {
        listOf(
            null,
            "",
            "#fff",
            "#80112233",
            "rgb(1, 2, 3)",
            "#xyzxyz",
            "#123456".repeat(1_000),
        ).forEach { value ->
            assertNull(WebContentStatusBarAppearanceRules.fromReportedColor(value))
        }
    }
}
