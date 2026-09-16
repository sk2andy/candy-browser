package dev.sk2andy.materialbrowser.browser.integration

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalAppLauncherInstrumentedTest {
    private val context = RecordingContext(
        InstrumentationRegistry.getInstrumentation().targetContext,
    )
    private val launcher = ExternalAppLauncher(context)

    @Test
    fun webLinksRequireDirectNonBrowserDefaultHandler() {
        assertEquals(
            ExternalLaunchResult.Launched,
            launcher.openWebUrlExternally("https://example.com/article"),
        )

        val launchedIntent = requireNotNull(context.lastIntent)
        assertEquals(Intent.ACTION_VIEW, launchedIntent.action)
        assertEquals("https://example.com/article", launchedIntent.dataString)
        assertTrue(launchedIntent.categories.orEmpty().contains(Intent.CATEGORY_BROWSABLE))
        assertTrue(launchedIntent.flags and Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER != 0)
        assertTrue(launchedIntent.flags and Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT != 0)
    }

    @Test
    fun webLinkWithoutDirectAppHandlerFallsBackToCurrentWebView() {
        context.launchFailure = ActivityNotFoundException()

        assertEquals(
            ExternalLaunchResult.Unsupported,
            launcher.openWebUrlExternally("https://example.com/article"),
        )
    }

    @Test
    fun webLinkAvailabilityUsesTheSameHardenedIntentWithoutLaunchingIt() {
        var resolvedIntent: Intent? = null
        val resolvingLauncher = ExternalAppLauncher(context) { target ->
            resolvedIntent = Intent(target)
            true
        }

        assertTrue(resolvingLauncher.canOpenWebUrlExternally("https://example.com/article"))
        assertNull(context.lastIntent)
        val target = requireNotNull(resolvedIntent)
        assertEquals("https://example.com/article", target.dataString)
        assertTrue(target.flags and Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER != 0)
        assertTrue(target.flags and Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT != 0)
    }

    @Test
    fun googlePlayLinksTargetPlayStoreWithoutGenericResolutionFlags() {
        assertEquals(
            ExternalLaunchResult.Launched,
            launcher.openWebUrlExternally(PLAY_STORE_URL),
        )

        val launchedIntent = requireNotNull(context.lastIntent)
        assertEquals(Intent.ACTION_VIEW, launchedIntent.action)
        assertEquals(PLAY_STORE_URL, launchedIntent.dataString)
        assertEquals("com.android.vending", launchedIntent.`package`)
        assertTrue(launchedIntent.categories.orEmpty().contains(Intent.CATEGORY_BROWSABLE))
        assertEquals(0, launchedIntent.flags and Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
        assertEquals(0, launchedIntent.flags and Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT)
    }

    @Test
    fun googlePlayLinkWithoutPlayStoreFallsBackToCurrentWebView() {
        context.launchFailure = ActivityNotFoundException()

        assertEquals(
            ExternalLaunchResult.Unsupported,
            launcher.openWebUrlExternally(PLAY_STORE_URL),
        )
    }

    @Test
    fun retriesAgainstCurrentInstalledAppsWithoutCachingHandlers() {
        context.launchFailure = ActivityNotFoundException()
        assertEquals(
            ExternalLaunchResult.Unsupported,
            launcher.open(Uri.parse("candy-app://callback?code=redacted")),
        )

        context.launchFailure = null
        assertEquals(
            ExternalLaunchResult.Launched,
            launcher.open(Uri.parse("candy-app://callback?code=redacted")),
        )

        val launchedIntent = requireNotNull(context.lastIntent)
        assertEquals(Intent.ACTION_VIEW, launchedIntent.action)
        assertNotEquals(Intent.ACTION_CHOOSER, launchedIntent.action)
        assertEquals("candy-app://callback?code=redacted", launchedIntent.dataString)
        assertTrue(launchedIntent.categories.orEmpty().contains(Intent.CATEGORY_BROWSABLE))
        assertTrue(launchedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun securityFailureUsesSafeBrowserFallback() {
        context.launchFailure = SecurityException()

        assertEquals(
            ExternalLaunchResult.OpenInBrowser("https://example.com/fallback"),
            launcher.open(
                uri = Uri.parse("candy-app://callback"),
                browserFallbackUrl = "https://example.com/fallback",
            ),
        )
    }

    @Test
    fun activityHandoffsUseExternalTasksForWebSpecialSchemeAndIntentLinks() {
        lateinit var activity: RecordingActivity
        InstrumentationRegistry.getInstrumentation().runOnMainSync { activity = RecordingActivity() }
        val activityLauncher = ExternalAppLauncher(activity)
        val requests = listOf(
            "https://example.com/article",
            PLAY_STORE_URL,
            "candy-app://callback",
            "intent://callback#Intent;scheme=candy-app;package=example.app;end",
        )
        for (request in requests) {
            val result = if (request.startsWith("https:")) {
                activityLauncher.openWebUrlExternally(request)
            } else {
                activityLauncher.open(Uri.parse(request))
            }
            assertEquals(request, ExternalLaunchResult.Launched, result)
            assertTrue(
                request,
                requireNotNull(activity.lastIntent).flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0,
            )
        }
    }

    @Test
    fun namedHttpsIntentTriesInstalledAppBeforePlayStoreFallback() {
        assertEquals(
            "https://www.twitch.tv/candy",
            launcher.webTargetUrl(Uri.parse(TWITCH_INTENT)),
        )
        assertEquals(ExternalLaunchResult.Launched, launcher.open(Uri.parse(TWITCH_INTENT)))

        val launchedIntent = requireNotNull(context.lastIntent)
        assertEquals("https://www.twitch.tv/candy", launchedIntent.dataString)
        assertEquals("tv.twitch.android.app", launchedIntent.`package`)
        assertEquals(Intent.ACTION_VIEW, launchedIntent.action)
        assertNull(launchedIntent.component)
        assertNull(launchedIntent.selector)
        assertNull(launchedIntent.extras)
        assertTrue(launchedIntent.flags and Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER != 0)
        assertTrue(launchedIntent.flags and Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT != 0)
        assertEquals(0, launchedIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    @Test
    fun missingNamedHttpsHandlerUsesValidatedStoreFallback() {
        context.launchFailure = ActivityNotFoundException()
        assertEquals(
            ExternalLaunchResult.OpenInBrowser(PLAY_STORE_URL),
            launcher.open(Uri.parse(TWITCH_INTENT)),
        )
        assertEquals("tv.twitch.android.app", requireNotNull(context.lastIntent).`package`)
    }

    @Test
    fun unscopedHttpIntentStillRequiresNonBrowserDefault() {
        assertEquals(
            ExternalLaunchResult.Launched,
            launcher.open(Uri.parse("intent://example.com/article#Intent;scheme=http;end")),
        )
        val launchedIntent = requireNotNull(context.lastIntent)
        assertNull(launchedIntent.`package`)
        assertTrue(launchedIntent.flags and Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER != 0)
        assertTrue(launchedIntent.flags and Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT != 0)
    }

    @Test
    fun malformedWebInternalSchemeAndOwnPackageIntentsNeverLaunch() {
        val rejected = listOf(
            "intent://user@example.com/article#Intent;scheme=https;package=example.app;end",
            "intent://example.com/article#Intent;scheme=file;package=example.app;end",
            "intent://example.com/article#Intent;scheme=https;package=${context.packageName};end",
        )
        for (request in rejected) {
            assertEquals(request, ExternalLaunchResult.Unsupported, launcher.open(Uri.parse(request)))
            assertNull(request, context.lastIntent)
        }
    }

    private class RecordingActivity : Activity() {
        var lastIntent: Intent? = null

        override fun getPackageName(): String =
            InstrumentationRegistry.getInstrumentation().targetContext.packageName

        override fun startActivity(intent: Intent) {
            lastIntent = Intent(intent)
        }
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        var launchFailure: RuntimeException? = null
        var lastIntent: Intent? = null

        override fun startActivity(intent: Intent) {
            lastIntent = Intent(intent)
            launchFailure?.let { throw it }
        }
    }

    private companion object {
        const val PLAY_STORE_URL =
            "https://play.google.com/store/apps/details?id=com.outtiefive.phantomshell"
        const val TWITCH_INTENT =
            "intent://www.twitch.tv/candy#Intent;scheme=https;package=tv.twitch.android.app;" +
                "action=android.intent.action.SEND;component=tv.twitch.android.app/.Ignored;" +
                "launchFlags=0x1;S.untrusted=value;" +
                "S.browser_fallback_url=https%3A%2F%2Fplay.google.com%2Fstore%2Fapps%2Fdetails" +
                "%3Fid%3Dcom.outtiefive.phantomshell;end"
    }
}
