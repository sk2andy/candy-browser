package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BlankTabModeMorphRulesTest {
    @Test
    fun `mode interpolation is bounded at both endpoints`() {
        assertEquals(0f, BlankTabModeMorphRules.bounded(-0.4f), 0.001f)
        assertEquals(1f, BlankTabModeMorphRules.bounded(1.4f), 0.001f)
        assertEquals(0f, BlankTabModeMorphRules.bounded(Float.NaN), 0.001f)
        assertEquals(14f, BlankTabModeMorphRules.controlCornerRadiusDp(0f), 0.001f)
        assertEquals(20f, BlankTabModeMorphRules.controlCornerRadiusDp(1f), 0.001f)
        assertEquals(48f, BlankTabModeMorphRules.heroCornerRadiusDp(0f), 0.001f)
        assertEquals(30f, BlankTabModeMorphRules.heroCornerRadiusDp(1f), 0.001f)
    }

    @Test
    fun `icon treatments crossfade without exceeding unit alpha`() {
        assertEquals(1f, BlankTabModeMorphRules.regularIconAlpha(-1f), 0.001f)
        assertEquals(0f, BlankTabModeMorphRules.incognitoIconAlpha(-1f), 0.001f)
        assertEquals(0.5f, BlankTabModeMorphRules.regularIconAlpha(0.5f), 0.001f)
        assertEquals(0.5f, BlankTabModeMorphRules.incognitoIconAlpha(0.5f), 0.001f)
        assertEquals(0f, BlankTabModeMorphRules.regularIconAlpha(2f), 0.001f)
        assertEquals(1f, BlankTabModeMorphRules.incognitoIconAlpha(2f), 0.001f)
    }

    @Test
    fun `reveal radius covers viewport from control origin`() {
        assertEquals(
            500f,
            BlankTabModeMorphRules.maxRevealRadius(
                originX = 300f,
                originY = 400f,
                width = 300f,
                height = 400f,
            ),
            0.001f,
        )
        assertEquals(
            250f,
            BlankTabModeMorphRules.maxRevealRadius(
                originX = 150f,
                originY = 200f,
                width = 300f,
                height = 400f,
            ),
            0.001f,
        )
        assertEquals(
            125f,
            BlankTabModeMorphRules.revealRadius(
                progress = 0.5f,
                originX = 150f,
                originY = 200f,
                width = 300f,
                height = 400f,
            ),
            0.001f,
        )
    }
}
