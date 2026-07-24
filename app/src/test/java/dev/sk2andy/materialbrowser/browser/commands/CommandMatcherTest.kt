package dev.sk2andy.materialbrowser.browser.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandMatcherTest {
    private val commands = listOf(
        suggestion("reload", "Reload"),
        suggestion("clear-cache-and-reload", "Clear cache & reload"),
        suggestion("clear-cookies-and-reload", "Delete cookies & reload"),
        suggestion("open-settings", "Settings"),
    )

    @Test
    fun `empty explicit query returns registry order`() {
        assertEquals(
            commands.map { it.command.executionId },
            CommandMatcher.match(">", commands, 10).map { it.command.executionId },
        )
    }

    @Test
    fun `empty non command query returns no commands`() {
        assertTrue(CommandMatcher.match("", commands, 10).isEmpty())
    }

    @Test
    fun `explicit localized fuzzy search ignores accents and ranks exact prefix first`() {
        val localized = listOf(
            suggestion("open-settings", "Configurações"),
            suggestion("reload", "Recarregar"),
        )

        val matches = CommandMatcher.match("> configuracoes", localized, 10)

        assertEquals(listOf("open-settings"), matches.map { it.command.executionId })
    }

    @Test
    fun `explicit fuzzy subsequence finds localized command name`() {
        val matches = CommandMatcher.match("> clr cch", commands, 10)

        assertEquals("clear-cache-and-reload", matches.single().command.executionId)
    }

    @Test
    fun `exact and prefix matches rank before contains and fuzzy`() {
        val ranked = listOf(
            suggestion("contains", "Open reload tools"),
            suggestion("prefix", "Reload page"),
            suggestion("exact", "Reload"),
            suggestion("fuzzy", "Read local data"),
        )

        assertEquals(
            listOf("exact", "prefix", "contains", "fuzzy"),
            CommandMatcher.match("> reload", ranked, 10).map { it.command.executionId },
        )
    }

    @Test
    fun `implicit command match is conservative and capped at one`() {
        assertEquals(
            listOf("reload"),
            CommandMatcher.match("relo", commands, 10).map { it.command.executionId },
        )
        assertTrue(CommandMatcher.match("rel", commands, 10).isEmpty())
        assertTrue(CommandMatcher.match("reload.example", commands, 10).isEmpty())
        assertTrue(CommandMatcher.match("https://reload", commands, 10).isEmpty())
    }

    @Test
    fun `ambiguous implicit prefix returns no command`() {
        val ambiguous = listOf(
            suggestion("clear-cache", "Clear cache"),
            suggestion("clear-cookies", "Clear cookies"),
        )

        assertTrue(CommandMatcher.match("clear", ambiguous, 10).isEmpty())
    }

    @Test
    fun `explicit search matches unique profile target and effect`() {
        val targeted = (2..12).map { index ->
            CommandSuggestion(
                BrowserCommand(
                    "switch-profile:$index",
                    BrowserCommandKind.SwitchProfile,
                    targetProfileId = "$index",
                    targetProfileLabel = "$index · 🍬",
                ),
                "Switch to profile",
                "Opens profile $index · 🍬",
            )
        }

        assertEquals(
            listOf("switch-profile:12"),
            CommandMatcher.match("> 12", targeted, targeted.size)
                .map { it.command.executionId },
        )
    }

    private fun suggestion(id: String, name: String) = CommandSuggestion(
        command = BrowserCommand(
            executionId = id,
            kind = BrowserCommandKind.Reload,
        ),
        name = name,
        effect = "Effect",
    )
}
