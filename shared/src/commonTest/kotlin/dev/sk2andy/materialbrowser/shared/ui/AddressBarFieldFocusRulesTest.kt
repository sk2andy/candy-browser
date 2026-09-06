package dev.sk2andy.materialbrowser.shared.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddressBarFieldFocusRulesTest {
    @Test
    fun initialUnfocusedEventDoesNotDismissNewEditor() {
        assertFalse(
            AddressBarFieldFocusRules.shouldDismissEditor(
                hasReceivedFocus = false,
                isFocused = false,
            ),
        )
    }

    @Test
    fun focusLossDismissesEditorAfterItWasFocused() {
        assertTrue(
            AddressBarFieldFocusRules.shouldDismissEditor(
                hasReceivedFocus = true,
                isFocused = false,
            ),
        )
    }

    @Test
    fun activeFocusNeverDismissesEditor() {
        assertFalse(
            AddressBarFieldFocusRules.shouldDismissEditor(
                hasReceivedFocus = true,
                isFocused = true,
            ),
        )
    }

    @Test
    fun staleFocusRequestDoesNotReopenEditorForLoadedPage() {
        assertFalse(
            AddressBarFieldFocusRules.shouldApplyFocusRequest(
                request = 1,
                address = "https://example.com/",
            ),
        )
        assertTrue(
            AddressBarFieldFocusRules.shouldApplyFocusRequest(
                request = 1,
                address = "",
            ),
        )
    }
}
