package dev.sk2andy.materialbrowser.browser.integration

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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
    }
}
