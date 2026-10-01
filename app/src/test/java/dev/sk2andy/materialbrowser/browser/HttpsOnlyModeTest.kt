package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpsOnlyModeTest {
    @Test
    fun `missing or unknown persisted mode protects all tabs`() {
        assertEquals(HttpsOnlyMode.AllTabs, HttpsOnlyMode.Default)
        listOf(null, "", "future-mode", "ALL_TABS").forEach { value ->
            assertEquals(HttpsOnlyMode.AllTabs, HttpsOnlyMode.fromStableId(value))
        }
    }

    @Test
    fun `stable IDs preserve explicit mode choices`() {
        assertEquals(HttpsOnlyMode.Off, HttpsOnlyMode.fromStableId("off"))
        assertEquals(HttpsOnlyMode.PrivateOnly, HttpsOnlyMode.fromStableId("private_only"))
        assertEquals(HttpsOnlyMode.AllTabs, HttpsOnlyMode.fromStableId("all_tabs"))
    }

    @Test
    fun `private only protects private tabs while all tabs also protects regular tabs`() {
        assertFalse(HttpsOnlyMode.Off.protects(isPrivate = false))
        assertFalse(HttpsOnlyMode.Off.protects(isPrivate = true))
        assertFalse(HttpsOnlyMode.PrivateOnly.protects(isPrivate = false))
        assertTrue(HttpsOnlyMode.PrivateOnly.protects(isPrivate = true))
        assertTrue(HttpsOnlyMode.AllTabs.protects(isPrivate = false))
        assertTrue(HttpsOnlyMode.AllTabs.protects(isPrivate = true))
    }
}
