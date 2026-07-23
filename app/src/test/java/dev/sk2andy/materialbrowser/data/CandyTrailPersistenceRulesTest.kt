package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BrowserTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandyTrailPersistenceRulesTest {
    @Test
    fun `only normal tab ids cross persistence boundary`() {
        val normal = BrowserTab(id = "normal", lastAccessedAt = 1L)
        val private = BrowserTab(id = "private", lastAccessedAt = 2L, isIncognito = true)

        assertTrue(CandyTrailPersistenceRules.canPersist(normal))
        assertFalse(CandyTrailPersistenceRules.canPersist(private))
        assertEquals(setOf("normal"), CandyTrailPersistenceRules.persistentTabIds(listOf(normal, private)))
    }
}
