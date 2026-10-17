package dev.sk2andy.materialbrowser.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TabOverviewHeroLayoutRulesTest {
    @Test
    fun `separate title leaves the portrait preview inside its viewport`() {
        val layout = TabOverviewHeroRules.coverflowCardLayout(400f, 600f, reservedHeight = 50f)

        assertEquals(296f, layout.width, 0.001f)
        assertEquals(550f, layout.width / layout.aspectRatio, 0.001f)
        assertTrue(layout.width / layout.aspectRatio + 50f <= 600f)
    }

    @Test
    fun `short landscape viewport reserves the whole title row`() {
        val layout = TabOverviewHeroRules.coverflowCardLayout(800f, 180f, reservedHeight = 90f)

        assertEquals(190.08f, layout.width, 0.001f)
        assertEquals(90f, layout.width / layout.aspectRatio, 0.001f)
    }

    @Test
    fun `overlay title preserves existing Android card geometry`() {
        val layout = TabOverviewHeroRules.coverflowCardLayout(400f, 600f)

        assertEquals(296f, layout.width, 0.001f)
        assertEquals(0.45f, layout.aspectRatio)
    }

    @Test
    fun `invalid reserved height preserves the default layout`() {
        val expected = TabOverviewHeroRules.coverflowCardLayout(400f, 600f)

        for (height in listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(
                expected,
                TabOverviewHeroRules.coverflowCardLayout(400f, 600f, reservedHeight = height),
            )
        }
    }
}
