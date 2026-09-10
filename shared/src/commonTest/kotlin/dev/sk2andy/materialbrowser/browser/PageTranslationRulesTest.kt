package dev.sk2andy.materialbrowser.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PageTranslationRulesTest {
    @Test
    fun googleTranslationEncodesSourceUrlAndLanguage() {
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
    fun compatibleDefaultRoutesThroughYandex() {
        assertEquals(PageTranslationProvider.Yandex, PageTranslationProvider.fromStableId(null))
        assertEquals(PageTranslationProvider.Yandex, PageTranslationProvider.fromStableId("unknown"))
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
    fun kagiPreservesSourceLocationAndRejectsProviderParameters() {
        assertEquals(
            "https://translate.kagi.com/example.com/chapter?id=7&to=pt#part-2",
            PageTranslationRules.buildTranslationUrl(
                provider = PageTranslationProvider.Kagi,
                sourceUrl = "https://example.com/chapter?id=7#part-2",
                targetLanguage = "pt",
            ),
        )
        listOf("to", "kt_quality", "kt_view", "%74o").forEach { parameter ->
            assertFalse(
                PageTranslationRules.canTranslate(
                    provider = PageTranslationProvider.Kagi,
                    sourceUrl = "https://example.com/chapter?$parameter=source-value",
                ),
            )
        }
    }

    @Test
    fun unsafeSourcesAndProviderResultPagesAreRejected() {
        assertNull(
            PageTranslationRules.buildTranslationUrl(
                provider = PageTranslationProvider.Google,
                sourceUrl = "javascript:alert(1)",
                targetLanguage = "en",
            ),
        )
        listOf(
            "https://translate.google.com./",
            "https://example-com.translate.goog./",
            "https://translated.turbopages.org./proxy",
            "https://translate.kagi.com./example.com",
        ).forEach { sourceUrl ->
            assertFalse(
                PageTranslationRules.canTranslate(
                    provider = PageTranslationProvider.Google,
                    sourceUrl = sourceUrl,
                ),
            )
        }
        assertTrue(
            PageTranslationRules.canTranslate(
                provider = PageTranslationProvider.Google,
                sourceUrl = "https://小说.example/chapter",
            ),
        )
    }

    @Test
    fun invalidLanguageFallsBackToEnglish() {
        assertEquals("de", PageTranslationRules.targetLanguage("DE"))
        assertEquals("en", PageTranslationRules.targetLanguage(""))
        assertEquals("en", PageTranslationRules.targetLanguage("de-DE"))
    }

    @Test
    fun providerPagesAcceptOnlyExactEntryAndResultHosts() {
        val accepted = mapOf(
            PageTranslationProvider.Google to listOf(
                "https://translate.google.com/translate?u=https%3A%2F%2Fexample.com",
                "https://example-com.translate.goog/",
            ),
            PageTranslationProvider.Yandex to listOf(
                "https://translate.yandex.com/translate?url=https%3A%2F%2Fexample.com",
                "https://translated.turbopages.org/proxy_u/en/https/example.com/",
            ),
            PageTranslationProvider.Kagi to listOf(
                "https://translate.kagi.com/example.com?to=en",
            ),
        )
        accepted.forEach { (provider, urls) ->
            urls.forEach { url -> assertTrue(PageTranslationRules.isProviderPage(provider, url)) }
        }

        listOf(
            "https://translate.google.com.evil.example/",
            "https://translate.goog.evil.example/",
            "https://translated.turbopages.org.evil.example/",
            "https://translate.kagi.com.evil.example/",
        ).forEach { url ->
            PageTranslationProvider.entries.forEach { provider ->
                assertFalse(PageTranslationRules.isProviderPage(provider, url))
            }
        }
    }

    @Test
    fun resultPagesExcludeProviderEntryRoutes() {
        assertTrue(
            PageTranslationRules.isProviderResultPage(
                PageTranslationProvider.Google,
                "https://mt-cc.translate.goog/download/",
            ),
        )
        assertTrue(
            PageTranslationRules.isProviderResultPage(
                PageTranslationProvider.Yandex,
                "https://translated.turbopages.org/proxy_u/en/https/mt.cc/download/",
            ),
        )
        assertFalse(
            PageTranslationRules.isProviderResultPage(
                PageTranslationProvider.Google,
                "https://translate.google.com/translate",
            ),
        )
        assertFalse(
            PageTranslationRules.isProviderResultPage(
                PageTranslationProvider.Yandex,
                "https://translate.yandex.com/translate",
            ),
        )
    }
}
