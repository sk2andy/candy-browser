package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressEditorCompletionRulesTest {
    @Test
    fun `submit accepts valid ghost domain completion`() {
        assertEquals(
            "github.com",
            AddressEditorCompletionRules.submissionText("Git", "github.com"),
        )
    }

    @Test
    fun `submit keeps input when ghost is missing or unrelated`() {
        assertEquals("candy", AddressEditorCompletionRules.submissionText("candy", null))
        assertEquals(
            "candy",
            AddressEditorCompletionRules.submissionText("candy", "github.com"),
        )
    }
}
