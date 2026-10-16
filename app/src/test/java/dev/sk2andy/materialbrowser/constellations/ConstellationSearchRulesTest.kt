package dev.sk2andy.materialbrowser.constellations

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstellationSearchRulesTest {
    @Test
    fun `keywords match title url and excerpt with AND across fields`() {
        val pages = listOf(
            node("one", title = "Café culture", excerpt = "Fresh coffee in München"),
            node("two", title = "Coffee guide", excerpt = "Paris bakeries"),
        )
        val index = ConstellationSearchRules.index("personal", pages)

        assertEquals(listOf("https://one.example"), matches(index, "CAFE munchen one.example"))
        assertEquals(listOf("https://two.example"), matches(index, "coffee PARIS"))
        assertTrue(matches(index, "cafe paris").isEmpty())
        assertTrue(matches(index, "espresso").isEmpty())
    }

    @Test
    fun `foreign profiles never enter index or match even when url and text coincide`() {
        val local = node("shared", title = "Local privacy")
        val foreign = node("shared", title = "Secret work", profileId = "work")
        val index = ConstellationSearchRules.index("personal", listOf(foreign, local, local))

        assertEquals(1, index.size)
        assertEquals(listOf("https://shared.example"), matches(index, "local"))
        assertTrue(matches(index, "secret").isEmpty())
        val foreignIndex = ConstellationSearchRules.index("work", listOf(foreign))
        assertTrue(matches(foreignIndex, "secret").isEmpty())
        assertTrue(ConstellationSearchRules.index("", listOf(local)).isEmpty())
    }

    @Test
    fun `normalization handles decomposed accents unicode spaces and root case rules`() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val index = ConstellationSearchRules.index(
                "personal", listOf(node("one", title = "CAFÉ ISTANBUL")),
            )

            assertEquals(listOf("https://one.example"), matches(index, "cafe\u0301\u00a0istanbul"))
            assertFalse(ConstellationSearchRules.isSearching(" \n\t\u00a0\u0301"))
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun `query length is bounded before matching and blank query restores every eligible node`() {
        val query = "a".repeat(160) + " absent"
        val index = ConstellationSearchRules.index(
            "personal", listOf(node("one", title = "a".repeat(160)), node("two", title = "Other page")),
        )

        assertEquals(160, ConstellationSearchRules.boundQuery(query).length)
        assertEquals(listOf("https://one.example"), matches(index, query))
        assertEquals(listOf("https://one.example", "https://two.example"), matches(index, " \n "))
    }

    @Test
    fun `search uses literal normalized text without semantic aliases`() {
        val index = ConstellationSearchRules.index("personal", listOf(node("one", title = "Local AI")))

        assertTrue(matches(index, "offline").isEmpty())
        assertTrue(matches(index, "artificial intelligence").isEmpty())
        assertEquals(listOf("https://one.example"), matches(index, "local ai"))
    }

    @Test
    fun `match navigation wraps both ways and tolerates removed focus`() {
        val matches = listOf("one", "two", "three")

        assertEquals("one", ConstellationSearchRules.step(matches, "three", 1))
        assertEquals("three", ConstellationSearchRules.step(matches, "one", -1))
        assertEquals("one", ConstellationSearchRules.step(matches, "removed", 1))
        assertEquals("three", ConstellationSearchRules.step(matches, null, -1))
        assertNull(ConstellationSearchRules.step(emptyList(), null, 1))
    }

    @Test
    fun `focused match centers at readable zoom with finite bounded viewport`() {
        val page = node("one", title = "Local AI").copy(x = 0.5f, y = -0.5f)
        val viewport = ConstellationSearchRules.focusViewport(page, ConstellationGraphViewport(), 300f, 200f)

        assertEquals(2f, viewport.scale, 0f)
        assertEquals(-84f, viewport.panX, 0.001f)
        assertEquals(84f, viewport.panY, 0.001f)
        assertEquals(
            ConstellationGraphViewport(),
            ConstellationSearchRules.focusViewport(page.copy(x = Float.NaN), viewport, 300f, 200f),
        )
    }

    private fun matches(index: List<ConstellationSearchEntry>, query: String) =
        ConstellationSearchRules.matchingUrls("personal", index, query)

    private fun node(name: String, title: String, excerpt: String = "", profileId: String = "personal") =
        ConstellationGraphNode(
            document = ConstellationDocument(profileId, "https://$name.example", title, excerpt, 1L, listOf(1f, 0f)),
            x = 0f,
            y = 0f,
        )
}
