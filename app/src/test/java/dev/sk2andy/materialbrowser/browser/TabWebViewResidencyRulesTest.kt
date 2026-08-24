package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class TabWebViewResidencyRulesTest {
    @Test
    fun `oldest unprotected residents are evicted first`() {
        val evictions = TabWebViewResidencyRules.evictionOrder(
            residentTabIds = setOf("first", "second", "third", "selected"),
            accessOrder = mapOf(
                "first" to 1L,
                "second" to 2L,
                "third" to 3L,
                "selected" to 4L,
            ),
            protectedTabIds = setOf("selected"),
            limit = 2,
        )

        assertEquals(listOf("first", "second"), evictions)
    }

    @Test
    fun `protected residents may temporarily exceed limit`() {
        val evictions = TabWebViewResidencyRules.evictionOrder(
            residentTabIds = setOf("selected", "audio", "preview"),
            accessOrder = mapOf("selected" to 1L, "audio" to 2L, "preview" to 3L),
            protectedTabIds = setOf("selected", "audio", "preview"),
            limit = 1,
        )

        assertEquals(emptyList<String>(), evictions)
    }

    @Test
    fun `missing access order is oldest with deterministic tie break`() {
        val evictions = TabWebViewResidencyRules.evictionOrder(
            residentTabIds = setOf("z", "a", "recent"),
            accessOrder = mapOf("recent" to 1L),
            protectedTabIds = emptySet(),
            limit = 1,
        )

        assertEquals(listOf("a", "z"), evictions)
    }

    @Test
    fun `limit is normalized to supported bounds`() {
        assertEquals(TabWebViewResidencyRules.MIN_LIMIT, TabWebViewResidencyRules.normalizedLimit(-1))
        assertEquals(
            TabWebViewResidencyRules.MAX_LIMIT,
            TabWebViewResidencyRules.normalizedLimit(Int.MAX_VALUE),
        )
    }
}
