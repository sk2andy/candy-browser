package dev.sk2andy.materialbrowser

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMainFrameNavigationRequest
import dev.sk2andy.materialbrowser.browser.gecko.GeckoNavigationRequestDecision
import dev.sk2andy.materialbrowser.browser.integration.LauncherShortcutRules
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class MainActivityIncomingNavigationInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = context.getSharedPreferences(
        BrowserSessionStore.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val handoffs = CopyOnWriteArrayList<Intent>()
    private val monitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != Intent.ACTION_VIEW || intent.component != null) return null
            handoffs += Intent(intent)
            return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        }
    }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        BrowserSessionStore(context).saveStartupAnimationEnabled(false)
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        instrumentation.addMonitor(monitor)
    }

    @After
    fun tearDown() {
        instrumentation.removeMonitor(monitor)
        preferences.edit().clear().commit()
    }

    @Test
    fun incomingViewOpensPreviewWithoutReturningLinkToExternalApp() {
        BrowserSessionStore(context).saveExternalLinkPreviewEnabled(true)
        ActivityScenario.launch<MainActivity>(incomingIntent(INCOMING_URL)).use { scenario ->
            scenario.onActivity { activity ->
                val preview = requireNotNull(activity.browserControllerForTesting().externalLinkPreviewState)
                assertEquals(INCOMING_URL, preview.currentUrl)
                assertNull(preview.appHandoffExpiresAtElapsedRealtime)
            }
            assertEquals(0, handoffs.size)
        }
    }

    @Test
    fun incomingTabRedirectCannotReturnToAppButSubsequentTapCan() {
        BrowserSessionStore(context).saveExternalLinkPreviewEnabled(false)
        ActivityScenario.launch<MainActivity>(incomingIntent(INCOMING_URL)).use { scenario ->
            awaitIncomingTab(scenario)
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertEquals(INCOMING_URL, controller.selectedTab.url)
                assertEquals(0, handoffs.size)
                assertEquals(
                    GeckoNavigationRequestDecision.Allow,
                    controller.dispatchSelectedGeckoNavigationRequestForTesting(
                        GeckoMainFrameNavigationRequest(
                            url = "https://www.youtube.com/watch?v=candy",
                            isRedirect = true,
                            hasUserGesture = false,
                            isDirectNavigation = false,
                        ),
                    ),
                )
                assertEquals(
                    GeckoNavigationRequestDecision.Allow,
                    controller.dispatchSelectedGeckoNavigationRequestForTesting(
                        GeckoMainFrameNavigationRequest(
                            url = APP_URL,
                            isRedirect = true,
                            hasUserGesture = false,
                            isDirectNavigation = false,
                        ),
                    ),
                )
                assertEquals(0, handoffs.size)
                assertEquals(
                    GeckoNavigationRequestDecision.Deny,
                    controller.dispatchSelectedGeckoNavigationRequestForTesting(
                        GeckoMainFrameNavigationRequest(
                            url = APP_URL,
                            isRedirect = false,
                            hasUserGesture = true,
                            isDirectNavigation = false,
                        ),
                    ),
                )
                assertEquals(APP_URL, handoffs.single().dataString)
            }
        }
    }

    @Test
    fun warmIncomingViewKeepsNewPreviewWithoutAppHandoffGrant() {
        BrowserSessionStore(context).saveExternalLinkPreviewEnabled(true)
        ActivityScenario.launch<MainActivity>(incomingIntent(INCOMING_URL)).use { scenario ->
            val nextUrl = "https://youtube.candy.test/redirect?q=https%3A%2F%2Fexample.com"
            lateinit var originalIntent: Intent
            scenario.onActivity { activity ->
                originalIntent = activity.intent
                activity.startActivity(
                    incomingIntent(nextUrl).apply { removeFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK) },
                )
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                try {
                    val preview = requireNotNull(activity.browserControllerForTesting().externalLinkPreviewState)
                    assertEquals(nextUrl, preview.currentUrl)
                    assertNull(preview.appHandoffExpiresAtElapsedRealtime)
                    assertEquals(0, handoffs.size)
                } finally {
                    // ActivityScenario matches lifecycle events against its original launch intent.
                    activity.intent = originalIntent
                }
            }
        }
    }

    @Test
    fun userDepartureKeepsPreviewForPasswordManagerReturn() {
        BrowserSessionStore(context).saveExternalLinkPreviewEnabled(true)
        ActivityScenario.launch<MainActivity>(incomingIntent(INCOMING_URL)).use { scenario ->
            var previewSessionId = -1L
            lateinit var previewEngineView: View
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                previewSessionId = requireNotNull(controller.externalLinkPreviewState).sessionId
                assertTrue(controller.prepareExternalLinkPreview(previewSessionId))
                previewEngineView = requireNotNull(
                    controller.externalLinkPreviewEngineViewForTesting(),
                )
                instrumentation.callActivityOnUserLeaving(activity)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()

                assertEquals(
                    INCOMING_URL,
                    controller.externalLinkPreviewState?.currentUrl,
                )
                assertEquals(previewSessionId, controller.externalLinkPreviewState?.sessionId)
                assertSame(previewEngineView, controller.externalLinkPreviewEngineViewForTesting())
            }
        }
    }

    @Test
    fun warmAppIconLaunchDismissesPreview() {
        assertWarmLaunchDismissesPreview(Intent.ACTION_MAIN)
    }

    @Test
    fun warmWidgetLaunchDismissesPreview() {
        assertWarmLaunchDismissesPreview(LauncherShortcutRules.ACTION_OPEN_APP)
    }

    private fun incomingIntent(url: String): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    private fun assertWarmLaunchDismissesPreview(action: String) {
        BrowserSessionStore(context).saveExternalLinkPreviewEnabled(true)
        ActivityScenario.launch<MainActivity>(incomingIntent(INCOMING_URL)).use { scenario ->
            lateinit var originalIntent: Intent
            scenario.onActivity { activity ->
                originalIntent = activity.intent
                activity.startActivity(
                    Intent(activity, MainActivity::class.java)
                        .setAction(action)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                try {
                    assertNull(activity.browserControllerForTesting().externalLinkPreviewState)
                } finally {
                    // ActivityScenario matches lifecycle events against its original launch intent.
                    activity.intent = originalIntent
                }
            }
        }
    }

    private fun awaitIncomingTab(scenario: ActivityScenario<MainActivity>) {
        val deadline = SystemClock.elapsedRealtime() + 30_000L
        var currentUrl = ""
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity ->
                currentUrl = activity.browserControllerForTesting().selectedTab.url
            }
            if (currentUrl == INCOMING_URL) return
            SystemClock.sleep(50L)
        }
        assertTrue("Incoming tab did not load; current URL: $currentUrl", currentUrl == INCOMING_URL)
    }

    private companion object {
        const val INCOMING_URL = "https://youtube.candy.test/redirect?q=https%3A%2F%2Fgithub.com"
        const val APP_URL = "candy-fixture://return-to-app"
    }
}
