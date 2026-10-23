package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.sk2andy.materialbrowser.browser.SearchShortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressSearchShortcutRulesTest {
    private val shortcuts = listOf(SearchShortcut("gh", "https://github.com/search?q=%s"))

    @Test
    fun `software keyboard backspace removes empty chip instead of exposing partial alias`() {
        val result = AddressSearchShortcutRules.edit(value("!gh "), value("!gh"), shortcuts)
        assertEquals(TextFieldValue(), result)
    }

    @Test
    fun `deleting last query character preserves chip until next backspace`() {
        val result = AddressSearchShortcutRules.edit(value("!gh x"), value("!gh "), shortcuts)
        assertEquals("!gh ", result.text)
        assertEquals(TextRange(4), result.selection)
        assertEquals(TextFieldValue(), AddressSearchShortcutRules.edit(result, value("!gh"), shortcuts))
    }

    @Test
    fun `backspace at query start cannot delete hidden prefix while query remains`() {
        val previous = TextFieldValue("!gh compose", TextRange(4))
        val next = TextFieldValue("!ghcompose", TextRange(3))
        assertEquals(previous, AddressSearchShortcutRules.edit(previous, next, shortcuts))
    }

    @Test
    fun `selection and composition cannot enter the hidden prefix`() {
        val result = AddressSearchShortcutRules.edit(
            value("!gh compose"),
            TextFieldValue("!gh compose", selection = TextRange(0, 11), composition = TextRange(0, 4)),
            shortcuts,
        )
        assertEquals(TextRange(4, 11), result.selection)
        assertNull(result.composition)
        val composing = TextFieldValue("!gh compose", TextRange(11), TextRange(4, 11))
        assertEquals(composing, AddressSearchShortcutRules.edit(value("!gh "), composing, shortcuts))
    }

    @Test
    fun `completion preserves selected shortcut and plain input stays plain`() {
        assertEquals("!gh compose android", AddressSearchShortcutRules.fill("!gh comp", "compose android", shortcuts))
        assertEquals("compose android", AddressSearchShortcutRules.fill("comp", "compose android", shortcuts))
        val plain = value("!unknown compose")
        assertEquals(plain, AddressSearchShortcutRules.edit(value(""), plain, shortcuts))
    }

    private fun value(text: String) = TextFieldValue(text, TextRange(text.length))
}
