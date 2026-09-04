package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptchaCompatibilityRulesTest {
    @Test
    fun `detects supported cross site captcha providers`() {
        assertProvider(
            CaptchaProvider.Cloudflare,
            "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit",
        )
        assertProvider(
            CaptchaProvider.Cloudflare,
            "https://abc.challenges.cloudflare.com/cdn-cgi/challenge-platform/h/g/orchestrate/chl_page/v1",
        )
        assertProvider(
            CaptchaProvider.GoogleRecaptcha,
            "https://www.google.com/recaptcha/api.js?render=site-key",
        )
        assertProvider(
            CaptchaProvider.GoogleRecaptcha,
            "https://www.recaptcha.net/recaptcha/enterprise.js?render=site-key",
        )
        assertProvider(
            CaptchaProvider.HCaptcha,
            "https://js.hcaptcha.com/1/api.js?render=explicit",
        )
    }

    @Test
    fun `rejects insecure first party malformed and lookalike requests`() {
        val rejected = listOf(
            "http://challenges.cloudflare.com/turnstile/v0/api.js",
            "https://challenges.cloudflare.com.example/turnstile/v0/api.js",
            "https://www.google.com/recaptcha/unrelated.js",
            "https://www.google.com.example/recaptcha/api.js",
            "https://js.hcaptcha.com/1/other.js",
            "not a url",
        )

        rejected.forEach { requestUrl ->
            assertNull(
                requestUrl,
                CaptchaCompatibilityRules.providerForSubresource(
                    requestUrl = requestUrl,
                    pageUrl = "https://shop.example/checkout",
                ),
            )
        }
        assertNull(
            CaptchaCompatibilityRules.providerForSubresource(
                requestUrl = "https://www.google.com/recaptcha/api.js",
                pageUrl = "https://maps.google.com/",
            ),
        )
    }

    private fun assertProvider(expected: CaptchaProvider, requestUrl: String) {
        assertEquals(
            expected,
            CaptchaCompatibilityRules.providerForSubresource(
                requestUrl = requestUrl,
                pageUrl = "https://shop.example/checkout",
            ),
        )
    }
}
