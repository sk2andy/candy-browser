package dev.sk2andy.materialbrowser.browser.commands

import dev.sk2andy.materialbrowser.data.AddressSuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressSuggestionComposerTest {
    private val navigation = listOf(AddressSuggestion("https://reload.example", "Reload docs"))
    private val command = CommandSuggestion(
        BrowserCommand("reload", BrowserCommandKind.Reload),
        "Reload",
        "Reload current tab",
    )

    @Test
    fun `explicit command query excludes URL navigation`() {
        val result = AddressSuggestionComposer.compose("> reload", navigation, listOf(command), 6)

        assertEquals(listOf(AddressSuggestionItem.Command(command)), result)
    }

    @Test
    fun `implicit command follows URL suggestions to avoid collision`() {
        val result = AddressSuggestionComposer.compose("reload", navigation, listOf(command), 6)

        assertTrue(result.first() is AddressSuggestionItem.Navigation)
        assertTrue(result.last() is AddressSuggestionItem.Command)
    }

    @Test
    fun `limit applies to combined list`() {
        val result = AddressSuggestionComposer.compose("reload", navigation, listOf(command), 1)

        assertEquals(1, result.size)
        assertTrue(result.single() is AddressSuggestionItem.Navigation)
    }

    @Test
    fun `implicit command reserves final slot after dominant navigation matches`() {
        val manyNavigation = (1..6).map {
            AddressSuggestion("https://reload$it.example", "Reload $it")
        }

        val result = AddressSuggestionComposer.compose("reload", manyNavigation, listOf(command), 6)

        assertEquals(6, result.size)
        assertEquals(5, result.count { it is AddressSuggestionItem.Navigation })
        assertTrue(result.last() is AddressSuggestionItem.Command)
    }

    @Test
    fun `remote searches follow local navigation and precede command`() {
        val result = AddressSuggestionComposer.compose(
            query = "reload",
            navigation = navigation,
            commands = listOf(command),
            searchQueries = listOf("reload page", "reload browser"),
            limit = 6,
        )

        assertTrue(result.first() is AddressSuggestionItem.Navigation)
        assertEquals(
            listOf("reload page", "reload browser"),
            result.filterIsInstance<AddressSuggestionItem.Search>().map { it.query },
        )
        assertTrue(result.last() is AddressSuggestionItem.Command)
    }

    @Test
    fun `local navigation keeps one slot when remote searches fill small limit`() {
        val result = AddressSuggestionComposer.compose(
            query = "reload",
            navigation = navigation,
            commands = emptyList(),
            searchQueries = listOf("one", "two", "three", "four"),
            limit = 3,
        )

        assertTrue(result.first() is AddressSuggestionItem.Navigation)
        assertEquals(2, result.count { it is AddressSuggestionItem.Search })
    }
}
