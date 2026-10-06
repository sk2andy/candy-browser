package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserMainMenuPopupRulesTest {
    @Test
    fun `short window leaves room for DropdownMenu margins and padding`() {
        assertEquals(188.dp, BrowserMainMenuPopupRules.maxContentHeight(300.dp, 0.8f))
    }

    @Test
    fun `tall window keeps preferred menu height`() {
        assertEquals(640.dp, BrowserMainMenuPopupRules.maxContentHeight(800.dp, 0.8f))
    }

    @Test
    fun `height never becomes negative`() {
        assertEquals(0.dp, BrowserMainMenuPopupRules.maxContentHeight(100.dp, 0.8f))
    }
}
