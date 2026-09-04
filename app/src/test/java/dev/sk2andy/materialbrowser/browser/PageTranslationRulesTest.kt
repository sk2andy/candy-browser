package dev.sk2andy.materialbrowser.browser

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTranslationRulesTest {
    @Test
    fun `google translation encodes source url and detects language`() {
        assertEquals(
            "https://translate.google.com/translate?sl=auto&tl=de&u=" +
                "https%3A%2F%2Fexample.com%2Fchapter%3Fid%3D7%26mode%3Dread",
            PageTranslationRules.buildTranslationUrl(
                provider = PageTranslationProvider.Google,
                sourceUrl = "https://example.com/chapter?id=7&mode=read",
                targetLanguage = "DE",
            ),
        )
    }

    @Test
    fun `yandex translation encodes source url and target language`() {
        assertEquals(
            "https://translate.yandex.com/translate?url=" +
                "https%3A%2F%2Fexample.com%2Fnovel%2520chapter&lang=fr",
            PageTranslationRules.buildTranslationUrl(
                provider = PageTranslationProvider.Yandex,
                sourceUrl = "https://example.com/novel%20chapter",
                targetLanguage = "fr",
            ),
        )
    }

    @Test
    fun `default provider routes reported page through yandex`() {
        assertEquals(
            "https://translate.yandex.com/translate?url=" +
                "https%3A%2F%2Fmt.cc%2Fdownload&lang=en",
            PageTranslationRules.buildTranslationUrl(
                provider = PageTranslationProvider.fromStableId(null),
                sourceUrl = "https://mt.cc/download",
                targetLanguage = "en",
            ),
        )
    }

    @Test
    fun `kagi translation uses website route and preserves source location`() {
        assertEquals(
            "https://translate.kagi.com/example.com/chapter?id=7&to=pt#part-2",
            PageTranslationRules.buildTranslationUrl(
                provider = PageTranslationProvider.Kagi,
                sourceUrl = "https://example.com/chapter?id=7#part-2",
                targetLanguage = "pt",
            ),
        )
        assertEquals(
            "https://translate.kagi.com/example.com/?to=de",
            PageTranslationRules.buildTranslationUrl(
                provider = PageTranslationProvider.Kagi,
                sourceUrl = "https://example.com/",
                targetLanguage = "de",
            ),
        )
    }

    @Test
    fun `invalid sources and translation result pages are rejected`() {
        assertNull(
            PageTranslationRules.buildTranslationUrl(
                provider = PageTranslationProvider.Google,
                sourceUrl = "javascript:alert(1)",
                targetLanguage = "en",
            ),
        )
        assertFalse(
            PageTranslationRules.canTranslate(
                provider = PageTranslationProvider.Google,
                sourceUrl = "https://translate.google.com/",
            ),
        )
        assertFalse(
            PageTranslationRules.canTranslate(
                provider = PageTranslationProvider.Kagi,
                sourceUrl = "https://translate.kagi.com/example.com",
            ),
        )
        assertFalse(
            PageTranslationRules.canTranslate(
                provider = PageTranslationProvider.Google,
                sourceUrl = "https://example-com.translate.goog/",
            ),
        )
        assertFalse(
            PageTranslationRules.canTranslate(
                provider = PageTranslationProvider.Yandex,
                sourceUrl = "https://translated.turbopages.org/proxy",
            ),
        )
        assertTrue(
            PageTranslationRules.canTranslate(
                provider = PageTranslationProvider.Google,
                sourceUrl = "https://小说.example/chapter",
            ),
        )
    }

    @Test
    fun `translation result hosts with dns root dot are rejected`() {
        assertFalse(
            PageTranslationRules.canTranslate(
                provider = PageTranslationProvider.Google,
                sourceUrl = "https://translate.google.com./",
            ),
        )
        assertFalse(
            PageTranslationRules.canTranslate(
                provider = PageTranslationProvider.Google,
                sourceUrl = "https://example-com.translate.goog./",
            ),
        )
        assertFalse(
            PageTranslationRules.canTranslate(
                provider = PageTranslationProvider.Yandex,
                sourceUrl = "https://translated.turbopages.org./proxy",
            ),
        )
        assertFalse(
            PageTranslationRules.canTranslate(
                provider = PageTranslationProvider.Kagi,
                sourceUrl = "https://translate.kagi.com./example.com",
            ),
        )
    }

    @Test
    fun `kagi rejects source queries that collide with provider parameters`() {
        listOf("to", "kt_quality", "kt_view", "%74o").forEach { parameter ->
            val sourceUrl = "https://example.com/chapter?$parameter=source-value"
            assertFalse(
                PageTranslationRules.canTranslate(
                    provider = PageTranslationProvider.Kagi,
                    sourceUrl = sourceUrl,
                ),
            )
            assertNull(
                PageTranslationRules.buildTranslationUrl(
                    provider = PageTranslationProvider.Kagi,
                    sourceUrl = sourceUrl,
                    targetLanguage = "de",
                ),
            )
        }
        assertTrue(
            PageTranslationRules.canTranslate(
                provider = PageTranslationProvider.Kagi,
                sourceUrl = "https://example.com/chapter?id=7",
            ),
        )
        assertTrue(
            PageTranslationRules.canTranslate(
                provider = PageTranslationProvider.Google,
                sourceUrl = "https://example.com/chapter?to=source-value",
            ),
        )
    }

    @Test
    fun `invalid locale language falls back to english`() {
        assertEquals("de", PageTranslationRules.targetLanguage(Locale.GERMAN))
        assertEquals("en", PageTranslationRules.targetLanguage(Locale.ROOT))
    }

    @Test
    fun `unknown provider id uses compatible default`() {
        assertEquals(PageTranslationProvider.Yandex, PageTranslationProvider.fromStableId(null))
        assertEquals(PageTranslationProvider.Yandex, PageTranslationProvider.fromStableId("other"))
        assertEquals(PageTranslationProvider.Google, PageTranslationProvider.fromStableId("google"))
        assertEquals(PageTranslationProvider.Yandex, PageTranslationProvider.fromStableId("yandex"))
        assertEquals(PageTranslationProvider.Kagi, PageTranslationProvider.fromStableId("kagi"))
    }
}
