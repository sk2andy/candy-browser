package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserBackgroundMemoryRulesTest {
    @Test
    fun `unknown input checks retry at most three times`() {
        assertFalse(BrowserBackgroundMemoryRules.shouldRetryInputCheck(0))
        assertTrue(BrowserBackgroundMemoryRules.shouldRetryInputCheck(1))
        assertTrue(BrowserBackgroundMemoryRules.shouldRetryInputCheck(2))
        assertFalse(BrowserBackgroundMemoryRules.shouldRetryInputCheck(3))
        assertFalse(BrowserBackgroundMemoryRules.shouldRetryInputCheck(Int.MAX_VALUE))
    }

    @Test
    fun `configured warm count retains newest unselected sessions beside selected`() {
        assertEquals(
            listOf("oldest"),
            BrowserBackgroundMemoryRules.evictionOrder(
                residentTabIds = setOf("selected", "oldest", "middle", "newest"),
                accessOrder = mapOf("selected" to 0L, "oldest" to 1L, "middle" to 2L, "newest" to 3L),
                selectedTabId = "selected",
                protectedTabIds = emptySet(),
                isProcessInForeground = false,
                warmTabCount = 2,
            ),
        )
    }

    @Test
    fun `warm quota never needs an absent selected session`() {
        assertEquals(
            listOf("oldest"),
            BrowserBackgroundMemoryRules.evictionOrder(
                residentTabIds = setOf("oldest", "newest"),
                accessOrder = mapOf("oldest" to 1L, "newest" to 2L),
                selectedTabId = "missing",
                protectedTabIds = emptySet(),
                isProcessInForeground = false,
                warmTabCount = 1,
            ),
        )
    }

    @Test
    fun `foreground process rejects background eviction`() {
        assertEquals(
            emptyList<String>(),
            BrowserBackgroundMemoryRules.evictionOrder(
                residentTabIds = setOf("selected", "older"),
                accessOrder = mapOf("selected" to 2L, "older" to 1L),
                selectedTabId = "selected",
                protectedTabIds = emptySet(),
                isProcessInForeground = true,
            ),
        )
    }

    @Test
    fun `hidden UI releases resources without evicting sessions`() {
        assertEquals(
            BrowserBackgroundMemoryTrimAction.UiResources,
            BrowserBackgroundMemoryRules.trimAction(level = 20, isProcessInForeground = false),
        )
    }

    @Test
    fun `background memory pressure also trims resident sessions`() {
        listOf(40, 60, 80).forEach { level ->
            assertEquals(
                BrowserBackgroundMemoryTrimAction.UiResourcesAndSessions,
                BrowserBackgroundMemoryRules.trimAction(level = level, isProcessInForeground = false),
            )
        }
    }

    @Test
    fun `foreground and unrelated memory signals leave resources unchanged`() {
        listOf(20, 40, 60, 80).forEach { level ->
            assertEquals(
                BrowserBackgroundMemoryTrimAction.None,
                BrowserBackgroundMemoryRules.trimAction(level = level, isProcessInForeground = true),
            )
        }
        listOf(-1, 0, 5, 10, 15).forEach { level ->
            assertEquals(
                BrowserBackgroundMemoryTrimAction.None,
                BrowserBackgroundMemoryRules.trimAction(level = level, isProcessInForeground = false),
            )
        }
    }

    @Test
    fun `background budget retains selected session and evicts oldest first`() {
        assertEquals(
            listOf("older", "recent"),
            BrowserBackgroundMemoryRules.evictionOrder(
                residentTabIds = setOf("selected", "older", "recent"),
                accessOrder = mapOf("selected" to 0L, "older" to 1L, "recent" to 2L),
                selectedTabId = "selected",
                protectedTabIds = emptySet(),
                isProcessInForeground = false,
            ),
        )
    }

    @Test
    fun `protected sessions exceed background budget without eviction`() {
        val protectedTabIds = setOf("audio", "pip", "file", "login", "private", "form")

        assertEquals(
            listOf("eligible"),
            BrowserBackgroundMemoryRules.evictionOrder(
                residentTabIds = protectedTabIds + setOf("selected", "eligible"),
                accessOrder = emptyMap(),
                selectedTabId = "selected",
                protectedTabIds = protectedTabIds,
                isProcessInForeground = false,
            ),
        )
    }

    @Test
    fun `absent selected session does not consume an unselected warm slot`() {
        assertEquals(
            listOf("older", "recent"),
            BrowserBackgroundMemoryRules.evictionOrder(
                residentTabIds = setOf("older", "recent"),
                accessOrder = mapOf("older" to 1L, "recent" to 2L),
                selectedTabId = null,
                protectedTabIds = emptySet(),
                isProcessInForeground = false,
            ),
        )
    }

    @Test
    fun `empty or single resident session stays within background budget`() {
        listOf(emptySet(), setOf("selected")).forEach { residentTabIds ->
            assertEquals(
                emptyList<String>(),
                BrowserBackgroundMemoryRules.evictionOrder(
                    residentTabIds = residentTabIds,
                    accessOrder = emptyMap(),
                    selectedTabId = "selected",
                    protectedTabIds = emptySet(),
                    isProcessInForeground = false,
                ),
            )
        }
    }
}
