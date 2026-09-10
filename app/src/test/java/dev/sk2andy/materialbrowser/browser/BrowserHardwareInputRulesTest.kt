package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserHardwareInputRulesTest {
    @Test
    fun `maps primary modifier browser shortcuts`() {
        val expectedActions = mapOf(
            BrowserHardwareKey.L to BrowserHardwareInputAction.FocusAddress,
            BrowserHardwareKey.T to BrowserHardwareInputAction.NewTab,
            BrowserHardwareKey.W to BrowserHardwareInputAction.CloseTab,
            BrowserHardwareKey.R to BrowserHardwareInputAction.Reload,
            BrowserHardwareKey.F to BrowserHardwareInputAction.FindInPage,
            BrowserHardwareKey.Tab to BrowserHardwareInputAction.NextTab,
        )

        expectedActions.forEach { (key, action) ->
            assertEquals(
                action,
                BrowserHardwareInputRules.keyboardAction(
                    BrowserHardwareKeyStroke(key = key, ctrlPressed = true),
                ),
            )
            assertEquals(
                action,
                BrowserHardwareInputRules.keyboardAction(
                    BrowserHardwareKeyStroke(key = key, metaPressed = true),
                ),
            )
        }
    }

    @Test
    fun `maps reverse tab history and function key shortcuts`() {
        assertEquals(
            BrowserHardwareInputAction.PreviousTab,
            BrowserHardwareInputRules.keyboardAction(
                BrowserHardwareKeyStroke(
                    key = BrowserHardwareKey.Tab,
                    ctrlPressed = true,
                    shiftPressed = true,
                ),
            ),
        )
        assertEquals(
            BrowserHardwareInputAction.GoBack,
            BrowserHardwareInputRules.keyboardAction(
                BrowserHardwareKeyStroke(key = BrowserHardwareKey.Left, altPressed = true),
            ),
        )
        assertEquals(
            BrowserHardwareInputAction.GoForward,
            BrowserHardwareInputRules.keyboardAction(
                BrowserHardwareKeyStroke(key = BrowserHardwareKey.Right, altPressed = true),
            ),
        )
        assertEquals(
            BrowserHardwareInputAction.Reload,
            BrowserHardwareInputRules.keyboardAction(
                BrowserHardwareKeyStroke(key = BrowserHardwareKey.F5),
            ),
        )
    }

    @Test
    fun `rejects repeats and unsupported modifier combinations`() {
        assertNull(
            BrowserHardwareInputRules.keyboardAction(
                BrowserHardwareKeyStroke(
                    key = BrowserHardwareKey.L,
                    ctrlPressed = true,
                    repeatCount = 1,
                ),
            ),
        )
        assertNull(
            BrowserHardwareInputRules.keyboardAction(
                BrowserHardwareKeyStroke(
                    key = BrowserHardwareKey.T,
                    ctrlPressed = true,
                    shiftPressed = true,
                ),
            ),
        )
        assertNull(
            BrowserHardwareInputRules.keyboardAction(
                BrowserHardwareKeyStroke(
                    key = BrowserHardwareKey.L,
                    ctrlPressed = true,
                    metaPressed = true,
                ),
            ),
        )
        assertNull(
            BrowserHardwareInputRules.keyboardAction(
                BrowserHardwareKeyStroke(
                    key = BrowserHardwareKey.Left,
                    ctrlPressed = true,
                    metaPressed = true,
                    altPressed = true,
                ),
            ),
        )
        assertNull(
            BrowserHardwareInputRules.keyboardAction(
                BrowserHardwareKeyStroke(
                    key = BrowserHardwareKey.F5,
                    ctrlPressed = true,
                    metaPressed = true,
                ),
            ),
        )
        assertNull(
            BrowserHardwareInputRules.keyboardAction(
                BrowserHardwareKeyStroke(key = BrowserHardwareKey.Other, ctrlPressed = true),
            ),
        )
    }

    @Test
    fun `maps only auxiliary mouse navigation buttons`() {
        assertEquals(
            BrowserHardwareInputAction.GoBack,
            BrowserHardwareInputRules.mouseAction(BrowserMouseButton.Back),
        )
        assertEquals(
            BrowserHardwareInputAction.GoForward,
            BrowserHardwareInputRules.mouseAction(BrowserMouseButton.Forward),
        )
        assertNull(BrowserHardwareInputRules.mouseAction(BrowserMouseButton.Other))
    }

    @Test
    fun `cycles adjacent tabs in both directions`() {
        val tabIds = listOf("one", "two", "three")

        assertEquals(
            "three",
            BrowserHardwareInputRules.adjacentTabId(tabIds, "two", forward = true),
        )
        assertEquals(
            "one",
            BrowserHardwareInputRules.adjacentTabId(tabIds, "three", forward = true),
        )
        assertEquals(
            "three",
            BrowserHardwareInputRules.adjacentTabId(tabIds, "one", forward = false),
        )
    }

    @Test
    fun `does not select adjacent tab without valid choice`() {
        assertNull(
            BrowserHardwareInputRules.adjacentTabId(
                tabIds = listOf("only"),
                selectedTabId = "only",
                forward = true,
            ),
        )
        assertNull(
            BrowserHardwareInputRules.adjacentTabId(
                tabIds = listOf("one", "two"),
                selectedTabId = "missing",
                forward = false,
            ),
        )
    }
}
