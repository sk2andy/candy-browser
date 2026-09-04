package dev.sk2andy.materialbrowser.browser

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserControllerCaptchaCompatibilityInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        activityRule.scenario.onActivity { activity ->
            controller?.destroy()
            controller = null
            activity.getSharedPreferences(
                BrowserSessionStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
        }
    }

    @Test
    fun tabGrantChangesOnlyCookiePolicyWithoutPersistence() {
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)
            browserController.selectedWebViewForTesting().loadDataWithBaseURL(
                "https://checkout.example/",
                "<html><body>Checkout</body></html>",
                "text/html",
                "utf-8",
                null,
            )
        }
        awaitDocumentHost("checkout.example")
        await { controller?.isBundledBlockingReadyForTesting() == true }

        var originalUserAgent = ""
        activityRule.scenario.onActivity {
            val browserController = requireNotNull(controller)
            originalUserAgent = browserController.selectedWebViewForTesting()
                .settings.userAgentString
            browserController.detectCaptchaForTesting(
                "https://challenges.cloudflare.com/turnstile/v0/api.js",
            )
        }
        await { controller?.captchaCompatibilityOffer != null }

        activityRule.scenario.onActivity { activity ->
            val browserController = requireNotNull(controller)
            val offer = requireNotNull(browserController.captchaCompatibilityOffer)
            browserController.respondToCaptchaCompatibilityOffer(
                offer.token,
                CaptchaCompatibilityPromptChoice.AllowForTab,
            )

            assertTrue(browserController.siteProtectionState(offer.tabId).captchaCompatibilityAllowed)
            assertTrue(browserController.acceptsThirdPartyCookiesForTesting(offer.tabId))
            assertEquals(
                originalUserAgent,
                browserController.selectedWebViewForTesting().settings.userAgentString,
            )
            assertTrue(BrowserSessionStore(activity).loadSitePrivacyOverrides().isEmpty())

            assertTrue(browserController.revokeThirdPartyCookieCompatibility(offer.tabId))
            assertFalse(
                browserController.siteProtectionState(offer.tabId).captchaCompatibilityAllowed,
            )
            assertFalse(browserController.acceptsThirdPartyCookiesForTesting(offer.tabId))
        }
    }

    @Test
    fun profileGrantPersistsForExactSiteHost() {
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)
            browserController.selectedWebViewForTesting().loadDataWithBaseURL(
                "https://checkout.example/",
                "<html><body>Checkout</body></html>",
                "text/html",
                "utf-8",
                null,
            )
        }
        awaitDocumentHost("checkout.example")
        await { controller?.isBundledBlockingReadyForTesting() == true }

        activityRule.scenario.onActivity {
            requireNotNull(controller).detectCaptchaForTesting(
                "https://www.google.com/recaptcha/api.js",
            )
        }
        await { controller?.captchaCompatibilityOffer != null }

        activityRule.scenario.onActivity { activity ->
            val browserController = requireNotNull(controller)
            val offer = requireNotNull(browserController.captchaCompatibilityOffer)
            browserController.respondToCaptchaCompatibilityOffer(
                offer.token,
                CaptchaCompatibilityPromptChoice.AllowForProfile,
            )

            val stored = BrowserSessionStore(activity).loadSitePrivacyOverrides()
            assertTrue(
                stored.getValue(offer.profileId)
                    .getValue("checkout.example")
                    .captchaCompatibilityAllowed == true,
            )
        }
    }

    private fun freshController(activity: ComponentActivity): BrowserController {
        activity.getSharedPreferences(
            BrowserSessionStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
        return BrowserController(activity).also { controller = it }
    }

    private fun await(condition: () -> Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        repeat(100) {
            instrumentation.waitForIdleSync()
            var matched = false
            activityRule.scenario.onActivity { matched = condition() }
            if (matched) return
            Thread.sleep(100)
        }
        var matched = false
        activityRule.scenario.onActivity { matched = condition() }
        assertTrue("Timed out waiting for CAPTCHA compatibility state", matched)
    }

    private fun awaitDocumentHost(expectedHost: String) {
        val result = AtomicReference("pending")
        repeat(50) {
            activityRule.scenario.onActivity {
                requireNotNull(controller).selectedWebViewForTesting().evaluateJavascript(
                    "location.hostname",
                    result::set,
                )
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            if (result.get().contains(expectedHost)) return
            Thread.sleep(100)
        }
        assertTrue("Timed out waiting for document host", result.get().contains(expectedHost))
    }
}
