package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.recall.RecallMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRecallRulesTest {
    @Test
    fun `content matches join metadata and remain profile isolated`() {
        val personal = HistoryEntry("https://example.com/page", "Unrelated", 10L, "personal")
        val work = HistoryEntry("https://work.example/page", "Work", 20L, "work")
        val match = RecallMatch(
            profileId = "personal",
            url = "https://example.com/page#section",
            title = "Stored title",
            excerpt = "[candy] browser text",
            visitedAt = 9L,
            score = 4.0,
        )

        val result = HistoryRecallRules.merge(
            history = listOf(work, personal),
            selectedProfileIds = setOf("personal"),
            query = "candy browser",
            recallMatches = listOf(match, match.copy(profileId = "work", url = work.url)),
        )

        assertEquals(listOf(personal), result.entries)
        assertEquals("[candy] browser text", result.excerptsByEntryKey.values.single())
    }

    @Test
    fun `recall-only document becomes actionable history row and blank query removes it`() {
        val match = RecallMatch(
            profileId = "personal",
            url = "https://example.com/remembered",
            title = "Remembered",
            excerpt = "matching excerpt",
            visitedAt = 30L,
            score = 1.0,
        )

        val found = HistoryRecallRules.merge(emptyList(), setOf("personal"), "matching", listOf(match))
        val cleared = HistoryRecallRules.merge(emptyList(), setOf("personal"), "", listOf(match))

        assertEquals(match.url, found.entries.single().url)
        assertTrue(cleared.entries.isEmpty())
        assertTrue(cleared.excerptsByEntryKey.isEmpty())
    }
}
