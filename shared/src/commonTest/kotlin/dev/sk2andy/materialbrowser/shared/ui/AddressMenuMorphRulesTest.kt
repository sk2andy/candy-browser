package dev.sk2andy.materialbrowser.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class AddressMenuMorphRulesTest {
    @Test
    fun `collapsed chrome keeps address content fully visible`() {
        assertEquals(
            AddressMenuMorphFrame(
                menuExpansionProgress = 0f,
                addressContentAlpha = 1f,
                addressSurfaceScale = 1f,
                addressCornerMorphProgress = 0f,
            ),
            AddressMenuMorphRules.frame(
                animatedProgress = 0f,
                expanded = false,
                reduceMotion = false,
            ),
        )
    }

    @Test
    fun `midpoint shares one timeline between menu and address`() {
        val frame = AddressMenuMorphRules.frame(
            animatedProgress = 0.5f,
            expanded = true,
            reduceMotion = false,
        )

        assertEquals(0.5f, frame.menuExpansionProgress)
        assertEquals(0.5f, frame.addressContentAlpha)
        assertEquals(0.97f, frame.addressSurfaceScale, 0.001f)
        assertEquals(0.5f, frame.addressCornerMorphProgress)
    }

    @Test
    fun `expanded chrome leaves menu as only visible surface`() {
        val frame = AddressMenuMorphRules.frame(
            animatedProgress = 1f,
            expanded = true,
            reduceMotion = false,
        )

        assertEquals(1f, frame.menuExpansionProgress)
        assertEquals(0f, frame.addressContentAlpha)
        assertEquals(0.94f, frame.addressSurfaceScale, 0.001f)
        assertEquals(1f, frame.addressCornerMorphProgress)
    }

    @Test
    fun `reduce motion snaps to requested end state`() {
        assertEquals(
            1f,
            AddressMenuMorphRules.frame(
                animatedProgress = 0f,
                expanded = true,
                reduceMotion = true,
            ).menuExpansionProgress,
        )
        assertEquals(
            0f,
            AddressMenuMorphRules.frame(
                animatedProgress = 1f,
                expanded = false,
                reduceMotion = true,
            ).menuExpansionProgress,
        )
    }
}
