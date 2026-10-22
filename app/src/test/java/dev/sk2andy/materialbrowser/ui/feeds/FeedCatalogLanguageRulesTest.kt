package dev.sk2andy.materialbrowser.ui.feeds

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedCatalogLanguageRulesTest {
    @Test
    fun `native language names remain searchable in a different app language`() {
        val japanese = FeedCatalogLanguage("ja", "Japanisch", "日本語")
        val arabic = FeedCatalogLanguage("ar", "Arabisch", "العربية")
        assertTrue(FeedCatalogLanguageRules.matches(japanese, "日本語"))
        assertTrue(FeedCatalogLanguageRules.matches(japanese, "Japanese"))
        assertTrue(FeedCatalogLanguageRules.matches(arabic, "العربية"))
        assertFalse(FeedCatalogLanguageRules.matches(japanese, "English"))
    }

    @Test
    fun `language matching ignores region script and case`() {
        assertEquals("pt", FeedCatalogLanguageRules.baseLanguage("pt-BR"))
        assertEquals("zh", FeedCatalogLanguageRules.baseLanguage("zh-Hant-TW"))
        assertEquals("en", FeedCatalogLanguageRules.baseLanguage("EN_us"))
        assertEquals("", FeedCatalogLanguageRules.baseLanguage(""))
        assertEquals("he", FeedCatalogLanguageRules.baseLanguage("iw-IL"))
        assertEquals("id", FeedCatalogLanguageRules.baseLanguage("in"))
        assertEquals("yi", FeedCatalogLanguageRules.baseLanguage("ji"))
    }

    @Test
    fun `international choices include independent language families`() {
        val codes = FeedCatalogLanguageRules.choices(Locale.ENGLISH).map { it.code }
        assertTrue(
            codes.containsAll(
                listOf("ar", "bn", "de", "en", "es", "fa", "hi", "ja", "ko", "pt", "sw", "zh")
            )
        )
        assertEquals(codes.size, codes.distinct().size)
        assertFalse(codes.any { it in listOf("iw", "in", "ji") })
    }
}
