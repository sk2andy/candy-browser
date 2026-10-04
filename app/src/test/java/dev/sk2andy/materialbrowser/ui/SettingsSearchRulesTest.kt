package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchRulesTest {
    private val entries = listOf(
        entry("font", "Website font size", "Adjust text size", "Appearance"),
        entry("history", "History suggestions", "Find visited websites", "Search"),
        entry("language", "App-Sprache", "Gerätesprache oder eigene Sprache", "Browser"),
    )

    @Test
    fun `blank query leaves normal settings to the caller`() {
        assertTrue(SettingsSearchRules.results(" \n ", entries).isEmpty())
    }

    @Test
    fun `matches localized titles descriptions and destination context`() {
        assertEquals(listOf("font"), ids("APPEARANCE text"))
        assertEquals(listOf("language"), ids("GERATESPRACHE"))
        assertEquals(listOf("history"), ids("visited suggestions"))
    }

    @Test
    fun `every word must match the same entry`() {
        assertTrue(ids("font visited").isEmpty())
        assertTrue(ids("missing setting").isEmpty())
    }

    @Test
    fun `title matches rank before descriptions with stable ties`() {
        val items = listOf(
            entry("description", "Other", "Website font size", "Browser"),
            entry("partial", "Website font size limit", "", "Appearance"),
            entry("exact", "Website font size", "", "Appearance"),
            entry("tie", "Website font size factor", "", "Appearance"),
        )
        assertEquals(
            listOf("exact", "partial", "tie", "description"),
            SettingsSearchRules.results("website font size", items).map { it.id },
        )
    }

    @Test
    fun `only supplied available entries can appear`() {
        assertTrue(SettingsSearchRules.results("font", entries.drop(1)).isEmpty())
    }

    @Test
    fun `query ignores input beyond the shared length bound`() {
        val boundedQuery = "font" + " ".repeat(SettingsSearchRules.MAX_QUERY_LENGTH - 4)
        assertEquals(listOf("font"), ids(boundedQuery))
        assertEquals(listOf("font"), ids(boundedQuery + "visited"))
    }

    private fun ids(query: String): List<String> =
        SettingsSearchRules.results(query, entries).map { it.id }

    private fun entry(id: String, title: String, description: String, context: String) =
        SettingsSearchEntry(id, title, description, context, SettingsDestination.Browser)
}
