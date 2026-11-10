package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import dev.sk2andy.materialbrowser.browser.actions.BrowserContentTargetListener
import dev.sk2andy.materialbrowser.browser.gecko.AndroidBrowserEngineSessionPort
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEnginePreviewCapture
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEnginePreparedSession
import dev.sk2andy.materialbrowser.browser.gecko.GeckoFindResult
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMainFrameNavigationRequest
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaCommand
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaSessionStateListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoNavigationRequestDecision
import dev.sk2andy.materialbrowser.browser.gecko.GeckoNavigationRequestListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoNewSessionRequest
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPrivacyPolicy
import dev.sk2andy.materialbrowser.browser.userscript.GreasyForkInstallStage
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParseResult
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParser
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GreasyForkDownloadResult
import dev.sk2andy.materialbrowser.data.UserScriptStore
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommand
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class BrowserControllerGreasyForkGeckoInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private lateinit var controller: BrowserController
    private var downloads = 0
    private val script =
        (UserScriptParser.parse("preview", SCRIPT) as UserScriptParseResult.Accepted).script

    @Before
    fun setUp() {
        activityRule.scenario.onActivity { activity ->
            activity.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit()
            assertTrue(
                BrowserSessionStore(activity).saveAndroidBrowserEngineKind(
                    AndroidBrowserEngineKind.GeckoView,
                ),
            )
            UserScriptStore(activity).clear()
            controller = BrowserController(activity)
            controller.onStart()
            controller.onResume()
            controller.greasyForkDownloadForTesting = { _, complete ->
                downloads++
                complete(GreasyForkDownloadResult.Success(script))
            }
        }
    }

    @After
    fun tearDown() {
        activityRule.scenario.onActivity { activity ->
            controller.destroy()
            UserScriptStore(activity).clear()
            activity.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
    }

    @Test
    fun geckoNavigationOffersReviewForUserLinksWithoutSaving() {
        activityRule.scenario.onActivity { activity ->
            controller.dispatchSelectedGeckoNavigationRequestForTesting(
                request(hasGesture = false, target = BrowserEngineNavigationTarget.Current),
            )
            assertEquals(0, downloads)
            assertNull(controller.greasyForkInstallPrompt)
            assertTrue(UserScriptStore(activity).load().isEmpty())

            assertEquals(
                GeckoNavigationRequestDecision.Deny,
                controller.dispatchSelectedGeckoNavigationRequestForTesting(
                    request(hasGesture = true, target = BrowserEngineNavigationTarget.New),
                ),
            )
            assertEquals(1, downloads)
        }
        waitForReview()
        activityRule.scenario.onActivity { activity ->
            val review = controller.greasyForkInstallPrompt?.stage as GreasyForkInstallStage.Review
            assertEquals(script.source, review.script.source)
            assertTrue(controller.userScripts.isEmpty())
            assertTrue(UserScriptStore(activity).load().isEmpty())
            controller.dismissGreasyForkInstall()
            assertEquals(
                GeckoNavigationRequestDecision.Deny,
                controller.dispatchSelectedGeckoNavigationRequestForTesting(
                    request(hasGesture = true, target = BrowserEngineNavigationTarget.Current),
                ),
            )
            assertEquals(2, downloads)
        }
        waitForReview()
        activityRule.scenario.onActivity { activity ->
            assertTrue(controller.userScripts.isEmpty())
            assertTrue(UserScriptStore(activity).load().isEmpty())
        }
    }

    @Test
    fun delayedNewWindowFromGreasyForkOffersReviewWithoutGesture() {
        lateinit var session: HistorySession
        activityRule.scenario.onActivity {
            session = HistorySession(controller.selectedTabId)
            controller.installGeckoEngineSessionForTesting(session)
            assertEquals(
                GeckoNavigationRequestDecision.Deny,
                controller.dispatchSelectedGeckoNavigationRequestForTesting(
                    request(hasGesture = false, target = BrowserEngineNavigationTarget.New),
                ),
            )
            assertEquals(1, downloads)
        }
        waitForReview()
        activityRule.scenario.onActivity { activity ->
            assertTrue(controller.userScripts.isEmpty())
            assertTrue(UserScriptStore(activity).load().isEmpty())
            controller.dismissGreasyForkInstall()
            assertFalse(
                controller.dispatchSelectedGeckoNewSessionForTesting(
                    GeckoNewSessionRequest(INSTALL_URL, object : BrowserEnginePreparedSession {}),
                ),
            )
            assertEquals(2, downloads)
        }
        waitForReview()
        activityRule.scenario.onActivity {
            controller.dismissGreasyForkInstall()
            session.historyUrl = "https://example.invalid/"
            controller.dispatchSelectedGeckoNavigationRequestForTesting(
                request(hasGesture = false, target = BrowserEngineNavigationTarget.New),
            )
            assertEquals(2, downloads)
            assertNull(controller.greasyForkInstallPrompt)
        }
    }

    private fun request(
        hasGesture: Boolean,
        target: BrowserEngineNavigationTarget,
    ) = GeckoMainFrameNavigationRequest(
        url = INSTALL_URL,
        isRedirect = false,
        hasUserGesture = hasGesture,
        isDirectNavigation = target == BrowserEngineNavigationTarget.Current,
        target = target,
    )

    private fun waitForReview() {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            var reviewReady = false
            activityRule.scenario.onActivity {
                reviewReady = controller.greasyForkInstallPrompt?.stage is GreasyForkInstallStage.Review
            }
            if (reviewReady) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Greasy Fork review did not open")
    }

    private class HistorySession(override val tabId: String) : AndroidBrowserEngineSessionPort {
        var historyUrl = "https://greasyfork.org/en/scripts/476331-insert-code"

        override fun execute(command: BrowserEngineCommand) = Unit
        override fun createView(context: Context): View = View(context)
        override fun awaitContentPresented(listener: () -> Unit) = listener()
        override fun releaseView(view: View) = Unit
        override fun capturePreview(
            targetWidthPx: Int,
            visibleViewHeightPx: Int,
            maximumTargetHeightPx: Int,
            onComplete: (Bitmap?) -> Unit,
        ): BrowserEnginePreviewCapture? = null
        override fun findInPage(query: String, forward: Boolean, onComplete: (GeckoFindResult?) -> Unit) =
            onComplete(null)
        override fun clearFindInPage() = Unit
        override fun printPage() = false
        override fun setDesktopMode(enabled: Boolean) = Unit
        override fun setActive(active: Boolean) = Unit
        override fun setMediaStateListener(listener: GeckoMediaSessionStateListener?) = Unit
        override fun setScrollListener(listener: BrowserEngineScrollListener?) = Unit
        override fun setContentTargetListener(listener: BrowserContentTargetListener?) = Unit
        override fun setNavigationRequestListener(listener: GeckoNavigationRequestListener?) = Unit
        override fun setVideoAutoplayBlocked(blocked: Boolean) = Unit
        override fun setAudioMuted(muted: Boolean) = Unit
        override fun executeMediaCommand(command: GeckoMediaCommand) = Unit
        override fun seekMedia(positionMillis: Long) = Unit
        override fun goToHistoryIndex(index: Int) = Unit
        override fun historyUrlAtOffset(offset: Int): String? = historyUrl.takeIf { offset == 0 }
        override fun extractPageForReader(onComplete: (String?) -> Unit) = onComplete(null)
        override fun updatePrivacyPolicy(
            policy: GeckoPrivacyPolicy,
            reloadOnCookiePermissionChange: Boolean,
            onReady: () -> Unit,
        ) = onReady()
    }

    private companion object {
        const val INSTALL_URL = "https://update.greasyfork.org/scripts/476331/Insert%20code.user.js"
        val SCRIPT = """
            // ==UserScript==
            // @name Example
            // @match https://example.com/*
            // @grant none
            // ==/UserScript==
            document.title = 'Example';
        """.trimIndent()
    }
}
