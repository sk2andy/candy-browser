package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTranslationRecoveryRulesTest {
    @Test
    fun `provider HTTP and transport failures request recovery`() {
        listOf(400, 404, 429, 503, 599).forEach { statusCode ->
            assertTrue(
                PageTranslationRecoveryRules.isProviderFailure(
                    provider = PageTranslationProvider.Yandex,
                    url = "https://translated.turbopages.org/proxy_u/en/https/mt.cc/download/",
                    navigationFailed = false,
                    httpStatusCode = statusCode,
                ),
            )
        }
        assertTrue(
            PageTranslationRecoveryRules.isProviderFailure(
                provider = PageTranslationProvider.Google,
                url = "https://mt-cc.translate.goog/download/",
                navigationFailed = true,
                httpStatusCode = null,
            ),
        )
    }

    @Test
    fun `successful and unrelated pages do not request recovery`() {
        assertFalse(
            PageTranslationRecoveryRules.isProviderFailure(
                provider = PageTranslationProvider.Yandex,
                url = "https://translated.turbopages.org/proxy_u/en/https/mt.cc/download/",
                navigationFailed = false,
                httpStatusCode = 200,
            ),
        )
        assertFalse(
            PageTranslationRecoveryRules.isProviderFailure(
                provider = PageTranslationProvider.Yandex,
                url = "https://example.com/",
                navigationFailed = true,
                httpStatusCode = 503,
            ),
        )
    }

    @Test
    fun `committed provider entry page requests recovery`() {
        assertTrue(
            PageTranslationRecoveryRules.isRejectedEntryPage(
                provider = PageTranslationProvider.Google,
                url = "https://translate.google.com/translate?u=https%3A%2F%2Fmt.cc%2Fdownload%2F",
                navigationCommitted = true,
            ),
        )
        assertFalse(
            PageTranslationRecoveryRules.isRejectedEntryPage(
                provider = PageTranslationProvider.Google,
                url = "https://mt-cc.translate.goog/download/",
                navigationCommitted = true,
            ),
        )
        assertFalse(
            PageTranslationRecoveryRules.isRejectedEntryPage(
                provider = PageTranslationProvider.Google,
                url = "https://translate.google.com/translate",
                navigationCommitted = false,
            ),
        )
    }

    @Test
    fun `every provider result page needs content check`() {
        assertTrue(
            PageTranslationRecoveryRules.shouldCheckResultContent(
                provider = PageTranslationProvider.Google,
                url = "https://mt-cc.translate.goog/download/",
            ),
        )
        assertFalse(
            PageTranslationRecoveryRules.shouldCheckResultContent(
                provider = PageTranslationProvider.Google,
                url = "https://translate.google.com/translate",
            ),
        )
        assertTrue(
            PageTranslationRecoveryRules.shouldCheckResultContent(
                provider = PageTranslationProvider.Yandex,
                url = "https://translated.turbopages.org/proxy_u/en/https/mt.cc/download/",
            ),
        )
        assertTrue(
            PageTranslationRecoveryRules.shouldCheckResultContent(
                provider = PageTranslationProvider.Kagi,
                url = "https://translate.kagi.com/mt.cc/download?to=en",
            ),
        )
    }

    @Test
    fun `content visibility accepts Gecko and System WebView result formats`() {
        val visiblePayload = """{"hasVisibleContent":true,"blocks":[]}"""
        assertEquals(
            PageTranslationContentOutcome.Visible,
            PageTranslationRecoveryRules.contentOutcome(visiblePayload),
        )
        assertEquals(
            PageTranslationContentOutcome.Visible,
            PageTranslationRecoveryRules.contentOutcome(
                "\"{\\\"hasVisibleContent\\\":true,\\\"blocks\\\":[]}\"",
            ),
        )
    }

    @Test
    fun `only explicit empty visibility confirms blank result`() {
        assertEquals(
            PageTranslationContentOutcome.Empty,
            PageTranslationRecoveryRules.contentOutcome(
                """{"hasVisibleContent":false,"blocks":[]}""",
            ),
        )
        listOf(
            null,
            "null",
            """{"blocks":[]}""",
            """{"hasVisibleContent":"false"}""",
            "invalid",
        ).forEach { rawResult ->
            assertEquals(
                PageTranslationContentOutcome.Unknown,
                PageTranslationRecoveryRules.contentOutcome(rawResult),
            )
        }
    }

    @Test
    fun `short or non-reader content remains visible`() {
        assertEquals(
            PageTranslationContentOutcome.Visible,
            PageTranslationRecoveryRules.contentOutcome(
                """{"hasVisibleContent":true,"blocks":[],"title":"Canvas app"}""",
            ),
        )
    }

    @Test
    fun `visible provider rejection requests recovery with or without semantic blocks`() {
        val providerErrorPayload =
            """{"hasVisibleContent":true,"visibleText":"Unable to translate this page.","blocks":[]}"""
        val semanticProviderErrorPayload =
            """{"hasVisibleContent":true,"visibleText":"Unable to translate this page.","blocks":[{"text":"Article explains the provider error."}]}"""
        val longArticlePayload =
            """{"hasVisibleContent":true,"visibleText":"Unable to translate this page. ${"Article context. ".repeat(80)}","blocks":[{"text":"Article context."}]}"""
        assertEquals(
            PageTranslationContentOutcome.ProviderError,
            PageTranslationRecoveryRules.contentOutcome(providerErrorPayload),
        )
        assertEquals(
            PageTranslationContentOutcome.ProviderError,
            PageTranslationRecoveryRules.contentOutcome(semanticProviderErrorPayload),
        )
        assertEquals(
            PageTranslationContentOutcome.Visible,
            PageTranslationRecoveryRules.contentOutcome(longArticlePayload),
        )
    }

    @Test
    fun `empty result must remain empty across two checks`() {
        assertEquals(
            PageTranslationContentAction.Retry,
            PageTranslationRecoveryRules.contentAction(
                outcome = PageTranslationContentOutcome.Empty,
                emptyChecksRemaining = 2,
            ),
        )
        assertEquals(
            PageTranslationContentAction.ReportFailure,
            PageTranslationRecoveryRules.contentAction(
                outcome = PageTranslationContentOutcome.Empty,
                emptyChecksRemaining = 1,
            ),
        )
        assertEquals(
            PageTranslationContentAction.ReportFailure,
            PageTranslationRecoveryRules.contentAction(
                outcome = PageTranslationContentOutcome.ProviderError,
                emptyChecksRemaining = 2,
            ),
        )
        listOf(
            PageTranslationContentOutcome.Visible,
            PageTranslationContentOutcome.Unknown,
        ).forEach { outcome ->
            assertEquals(
                PageTranslationContentAction.Complete,
                PageTranslationRecoveryRules.contentAction(
                    outcome = outcome,
                    emptyChecksRemaining = 2,
                ),
            )
        }
    }

    @Test
    fun `Yandex failure returns to source and other providers offer Yandex`() {
        assertEquals(
            PageTranslationRecoveryAction.OpenOriginal,
            PageTranslationRecoveryRules.actionFor(PageTranslationProvider.Yandex),
        )
        assertEquals(
            PageTranslationRecoveryAction.TryYandex,
            PageTranslationRecoveryRules.actionFor(PageTranslationProvider.Google),
        )
        assertEquals(
            PageTranslationRecoveryAction.TryYandex,
            PageTranslationRecoveryRules.actionFor(PageTranslationProvider.Kagi),
        )
    }
}
