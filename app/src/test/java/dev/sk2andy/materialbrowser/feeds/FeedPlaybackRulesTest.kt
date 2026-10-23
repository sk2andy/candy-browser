package dev.sk2andy.materialbrowser.feeds

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedPlaybackRulesTest {
    @Test
    fun `notification metadata removes control and direction override characters`() {
        assertEquals("Article title", FeedPlaybackRules.metadata(" \u0000Article\u202E title\n "))
    }

    @Test
    fun `notification metadata is bounded`() {
        assertEquals("a".repeat(160), FeedPlaybackRules.metadata("a".repeat(400)))
    }
}
