package dev.sk2andy.materialbrowser.browser.commands

import dev.sk2andy.materialbrowser.data.AddressSuggestion
import dev.sk2andy.materialbrowser.recall.RecallMatch
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
    private val recall = RecallMatch(
        profileId = "personal",
        url = "https://recall.example",
        title = "Recall docs",
        excerpt = "matching local text",
        visitedAt = 2L,
        score = 3.0,
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

    @Test
    fun `recall matches precede remote searches and remain capped`() {
        val result = AddressSuggestionComposer.compose(
            query = "candy browser",
            navigation = navigation,
            commands = listOf(command),
            searchQueries = listOf("candy browser remote"),
            recallMatches = listOf(recall, recall.copy(url = "https://two.example"), recall.copy(url = "https://three.example")),
            limit = 8,
        )

        assertTrue(result.first() is AddressSuggestionItem.Navigation)
        assertEquals(2, result.count { it is AddressSuggestionItem.Recall })
        assertTrue(
            result.indexOfFirst { it is AddressSuggestionItem.Recall } <
                result.indexOfFirst { it is AddressSuggestionItem.Search },
        )
    }

    @Test
    fun `explicit recall excludes navigation remote and normal commands`() {
        val result = AddressSuggestionComposer.compose(
            query = ">recall candy browser",
            navigation = navigation,
            commands = listOf(command),
            searchQueries = listOf("remote"),
            recallMatches = listOf(recall),
            limit = 8,
        )

        assertEquals(listOf(AddressSuggestionItem.Recall(recall)), result)
    }

    @Test
    fun `recall replaces duplicate canonical navigation row`() {
        val overlappingRecall = recall.copy(url = "https://reload.example/#section")

        val result = AddressSuggestionComposer.compose(
            query = "reload docs",
            navigation = navigation,
            commands = emptyList(),
            recallMatches = listOf(overlappingRecall),
            limit = 6,
        )

        assertEquals(listOf(AddressSuggestionItem.Recall(overlappingRecall)), result)
    }
}
