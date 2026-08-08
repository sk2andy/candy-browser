package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressBarPresentationRulesTest {
    @Test
    fun `docked presentation overrides scroll state`() {
        listOf(false, true).forEach { compact ->
            assertEquals(
                AddressBarPresentation.Docked,
                AddressBarPresentationRules.resolve(
                    docked = true,
                    compact = compact,
                    editing = false,
                    showingCommandFeedback = false,
                ),
            )
        }
    }

    @Test
    fun `editing temporarily expands docked address bar`() {
        assertEquals(
            AddressBarPresentation.Expanded,
            AddressBarPresentationRules.resolve(
                docked = true,
                compact = true,
                editing = true,
                showingCommandFeedback = false,
            ),
        )
    }

    @Test
    fun `command feedback stays visible while docked`() {
        assertEquals(
            AddressBarPresentation.CommandFeedback,
            AddressBarPresentationRules.resolve(
                docked = true,
                compact = true,
                editing = false,
                showingCommandFeedback = true,
            ),
        )
    }

    @Test
    fun `centered presentation follows compact scroll state`() {
        assertEquals(
            AddressBarPresentation.Compact,
            AddressBarPresentationRules.resolve(
                docked = false,
                compact = true,
                editing = false,
                showingCommandFeedback = false,
            ),
        )
        assertEquals(
            AddressBarPresentation.Expanded,
            AddressBarPresentationRules.resolve(
                docked = false,
                compact = false,
                editing = false,
                showingCommandFeedback = false,
            ),
        )
    }
}

