package dev.sk2andy.materialbrowser.data

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguageRulesTest {
    private val simplified = Locale.forLanguageTag("zh-Hans")
    private val traditional = Locale.forLanguageTag("zh-Hant")
    private val english = Locale.forLanguageTag("en")
    private val supported = listOf(simplified, traditional, english)

    @Test
    fun `explicit Chinese script selects its own language option`() {
        assertEquals(traditional, AppLanguageRules.selectedLocale("zh-Hant", supported))
        assertEquals(simplified, AppLanguageRules.selectedLocale("zh-Hans", supported.reversed()))
        assertEquals(traditional, AppLanguageRules.selectedLocale("zh-Hant-TW", supported))
        assertEquals(simplified, AppLanguageRules.selectedLocale("zh-Hans-TW", supported))
    }

    @Test
    fun `Chinese region fallback selects matching script independent of list order`() {
        listOf("zh-TW", "zh-HK", "zh-MO").forEach { tag ->
            assertEquals(traditional, AppLanguageRules.selectedLocale(tag, supported))
        }
        listOf("zh", "zh-CN", "zh-SG").forEach { tag ->
            assertEquals(simplified, AppLanguageRules.selectedLocale(tag, supported.reversed()))
        }
    }

    @Test
    fun `exact locale wins over another region for the same language`() {
        val british = Locale.forLanguageTag("en-GB")
        assertEquals(
            british,
            AppLanguageRules.selectedLocale("en-GB", listOf(english, british)),
        )
        assertEquals(english, AppLanguageRules.selectedLocale("en-US", supported))
    }

    @Test
    fun `device default and unsupported languages have no explicit selection`() {
        assertNull(AppLanguageRules.selectedLocale("", supported))
        assertNull(AppLanguageRules.selectedLocale("ja", supported))
    }
}
