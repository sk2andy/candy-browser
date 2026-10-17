package dev.sk2andy.materialbrowser.browser.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InlineTranslationRulesTest {
    @Test
    fun `selection preserves exact whitespace for matching the original document`() {
        assertEquals("  First line\nNext line  ", InlineTranslationRules.source("  First line\nNext line  "))
    }

    @Test
    fun `invalid selection and excessive response are rejected`() {
        assertNull(InlineTranslationRules.source(" "))
        assertNull(InlineTranslationRules.source("hidden\u0000text"))
        assertNull(InlineTranslationRules.source("a".repeat(8_001)))
        assertNull(InlineTranslationRules.result("b".repeat(24_001)))
        assertNull(InlineTranslationRules.result(null))
    }

    @Test
    fun `language pairs remove duplicates invalid pairs and identity translations`() {
        assertEquals(
            listOf(InlineTranslationPair("de", "en"), InlineTranslationPair("en", "de")),
            InlineTranslationRules.pairs(listOf(
                InlineTranslationPair("en", "de"), InlineTranslationPair("de", "en"),
                InlineTranslationPair("en", "de"), InlineTranslationPair("en", "en"),
                InlineTranslationPair("", "de"),
            )),
        )
    }
}
