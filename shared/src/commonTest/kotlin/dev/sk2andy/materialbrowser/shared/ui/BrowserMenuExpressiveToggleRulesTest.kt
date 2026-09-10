package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserMenuExpressiveToggleRulesTest {
    @Test
    fun uncheckedButtonUsesFullCornerRadius() {
        assertEquals(
            24.dp,
            BrowserMenuExpressiveToggleRules.cornerRadius(
                checked = false,
                pressed = false,
                buttonHeight = 48.dp,
            ),
        )
    }

    @Test
    fun checkedButtonUsesExpressiveSquareRadius() {
        assertEquals(
            12.dp,
            BrowserMenuExpressiveToggleRules.cornerRadius(
                checked = true,
                pressed = false,
                buttonHeight = 48.dp,
            ),
        )
    }

    @Test
    fun pressedButtonUsesSameRadiusForBothToggleStates() {
        listOf(false, true).forEach { checked ->
            assertEquals(
                8.dp,
                BrowserMenuExpressiveToggleRules.cornerRadius(
                    checked = checked,
                    pressed = true,
                    buttonHeight = 48.dp,
                ),
            )
        }
    }
}
