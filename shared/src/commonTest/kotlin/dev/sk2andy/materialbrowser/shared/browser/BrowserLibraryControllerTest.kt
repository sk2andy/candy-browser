package dev.sk2andy.materialbrowser.shared.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserLibraryControllerTest {
    @Test
    fun `favorites match canonical page identity and retain bounded titles`() {
        val controller = BrowserLibraryController()
        controller.toggleFavorite(tab("HTTPS://EXAMPLE.COM:443#chapter"), 10)
        assertTrue(controller.isFavorite("https://example.com/"))
        assertEquals("EXAMPLE", controller.state.favorites.single().title)
        controller.toggleFavorite(tab("https://example.com/"), 20)
        assertTrue(controller.state.favorites.isEmpty())
    }

    @Test
    fun `private and unsupported pages never enter durable library`() {
        val controller = BrowserLibraryController()
        controller.toggleFavorite(tab("https://secret.example/", isPrivate = true), 10)
        controller.recordVisit(tab("https://secret.example/", isPrivate = true), 10)
        controller.recordVisit(tab("javascript:alert(1)"), 10)
        controller.recordVisit(tab(""), 10)
        assertEquals(BrowserLibraryState(), controller.state)
    }

    @Test
    fun `restore rejects malformed entries deduplicates favorites and bounds history`() {
        val controller = BrowserLibraryController()
        val valid = BrowserLibraryEntry("https://example.com", "Example", 100)
        controller.restore(
            favorites = listOf(
                valid,
                valid.copy(address = "https://EXAMPLE.com:443/#fragment"),
                valid.copy(address = "file:///private"),
                valid.copy(updatedAtMillis = -1),
            ),
            history = List(300) { valid.copy(updatedAtMillis = it.toLong()) },
        )
        assertEquals(1, controller.state.favorites.size)
        assertEquals(250, controller.state.history.size)
        assertEquals(299L, controller.state.history.first().updatedAtMillis)
        assertEquals(50L, controller.state.history.last().updatedAtMillis)
    }

    @Test
    fun `history removal and clear stay inside profile boundary`() {
        val controller = BrowserLibraryController()
        controller.recordVisit(tab("https://example.com/"), 1)
        controller.recordVisit(tab("https://example.com/", profileId = "work"), 2)
        controller.recordVisit(tab("https://work.example/", profileId = "work"), 3)
        controller.removeHistory("https://example.com/#part", "work")
        assertEquals(listOf("work", "candy"), controller.state.history.map { it.profileId })
        controller.clearHistory("work")
        assertEquals("candy", controller.state.history.single().profileId)
        controller.clearHistory(null)
        assertTrue(controller.state.history.isEmpty())
    }

    @Test
    fun `invalid and stale favorite mutations preserve state`() {
        val controller = BrowserLibraryController()
        controller.addFavorite(BrowserLibraryEntry("https://example.com/", "Example", 1))
        val before = controller.state
        controller.addFavorite(BrowserLibraryEntry("https://user:password@example.com/", "Unsafe", 1))
        controller.removeFavorite("https://missing.example/")
        assertEquals(before, controller.state)
        assertFalse(controller.isFavorite("https://missing.example/"))
    }

    @Test
    fun `favorite insertion evicts beyond shared cap and bounds title`() {
        val controller = BrowserLibraryController()
        repeat(101) { index ->
            controller.addFavorite(BrowserLibraryEntry("https://host$index.example/", "x".repeat(5_000), index.toLong()))
        }
        assertEquals(100, controller.state.favorites.size)
        assertEquals("https://host100.example/", controller.state.favorites.first().address)
        assertEquals(4_096, controller.state.favorites.first().title.length)
        assertFalse(controller.isFavorite("https://host0.example/"))
    }

    @Test
    fun `shared mutations retain repeat visits and favorite toggle removal semantics`() {
        val visit = BrowserLibraryEntry("https://example.com/", "Example", 1)
        assertEquals(listOf(visit, visit), BrowserLibraryRules.newestHistory(listOf(visit, visit), 250) { it.updatedAtMillis })
        assertTrue(BrowserLibraryRules.newestHistory(listOf(visit), -1) { it.updatedAtMillis }.isEmpty())
        assertTrue(BrowserLibraryRules.toggleFavorite(listOf(visit, visit), visit, 0) { it.address }.isEmpty())
    }

    @Test
    fun `library search matches title and address without changing order`() {
        val entries = listOf(
            BrowserLibraryEntry("https://example.com/", "Candy", 1),
            BrowserLibraryEntry("https://candy.example/", "Other", 2),
            BrowserLibraryEntry("https://other.example/", "Unrelated", 3),
        )
        assertEquals(entries.take(2), BrowserLibraryRules.matchingEntries(entries, " CANDY "))
    }

    private fun tab(
        address: String,
        isPrivate: Boolean = false,
        profileId: String = "candy",
    ): BrowserTabState = BrowserTabState(
        id = "tab-1",
        address = address,
        title = " EXAMPLE ",
        isPrivate = isPrivate,
        profileId = profileId,
    )
}
