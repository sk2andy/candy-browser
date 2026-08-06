package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteRowOrderRulesTest {
    @Test
    fun `removed row keeps its position while promoted row enters in target order`() {
        val current = listOf("A", "B", "C", "D", "E", "F")
        val target = listOf("A", "B", "D", "E", "F", "G")

        assertEquals(
            listOf("A", "B", "C", "D", "E", "F", "G"),
            FavoriteRowOrderRules.mergeForExit(current, target),
        )
    }

    @Test
    fun `bounded outgoing row does not corrupt final add order`() {
        val current = listOf("A", "B", "C", "D", "E", "F")
        val target = listOf("X", "A", "B", "C", "D", "E")

        val merged = FavoriteRowOrderRules.mergeForExit(current, target)

        assertEquals(listOf("X", "A", "B", "C", "D", "F", "E"), merged)
        assertEquals(target, merged.filter { it != "F" })
    }
}
