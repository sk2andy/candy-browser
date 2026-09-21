package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import dev.sk2andy.materialbrowser.browser.actions.BrowserContentTargetListener
import dev.sk2andy.materialbrowser.browser.gecko.AndroidBrowserEngineSessionPort
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEnginePreviewCapture
import dev.sk2andy.materialbrowser.browser.gecko.GeckoFindResult
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaCommand
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaSessionState
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaSessionStateListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoNavigationRequestListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPrivacyPolicy
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.DeveloperSettings
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommand
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEvent
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class BrowserControllerFullscreenReturnInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null
    private var originalEngineKind: AndroidBrowserEngineKind? = null

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            try {
                controller?.destroy()
            } finally {
                originalEngineKind?.let { kind ->
                    assertTrue(BrowserSessionStore(composeRule.activity).saveAndroidBrowserEngineKind(kind))
                }
            }
        }
    }

    @Test
    fun directFullscreenReturnRetriesWhenInsetPolicySupersedesDispatchedRestore() {
        lateinit var session: RestoringSession
        composeRule.runOnIdle {
            val store = BrowserSessionStore(composeRule.activity)
            originalEngineKind = store.loadAndroidBrowserEngineKind()
            assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
            val browserController = BrowserController(composeRule.activity)
            controller = browserController
            browserController.updateDeveloperSettings(DeveloperSettings())
            val tabId = browserController.selectedTabId
            session = RestoringSession(tabId)
            browserController.installGeckoEngineSessionForTesting(session)
            browserController.attachSelectedBrowserEngineView(
                FrameLayout(composeRule.activity).also { host ->
                    composeRule.activity.addContentView(
                        host,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        ),
                    )
                },
            )
            browserController.dispatchGeckoEngineEventForTesting(
                BrowserEngineEvent(
                    tabId = tabId,
                    type = BrowserEngineEventType.NavigationStarted,
                    address = "https://media.example/",
                    title = null,
                    canGoBack = false,
                    canGoForward = false,
                    failureDescription = null,
                ),
            )
            browserController.reportSelectedGeckoMediaStateForTesting(
                GeckoMediaSessionState(
                    isActive = true,
                    isPlaying = true,
                    hasInlineVideo = true,
                    isInlineVideoPlaying = true,
                    isInlineVideoPresented = true,
                    inlineVideoWidth = 1_280,
                    inlineVideoHeight = 720,
                    inlineVideoDocumentNonce = "document-nonce",
                    inlineVideoElementNonce = "element-nonce",
                ),
            )
            browserController.reportSelectedGeckoFullscreenStateForTesting(true)
            browserController.onWindowInsetsChanged(
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 96, 0, 0))
                    .setVisible(WindowInsetsCompat.Type.statusBars(), true)
                    .build(),
            )
            browserController.reportSelectedGeckoFullscreenStateForTesting(false)
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) { session.restoreCount == 1 }
        composeRule.runOnUiThread {
            assertTrue(requireNotNull(controller).isMediaLayoutRestorationPending)
            requireNotNull(controller).onWindowInsetsChanged(
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 128, 0, 0))
                    .setVisible(WindowInsetsCompat.Type.statusBars(), true)
                    .build(),
            )
            assertEquals(128, session.policies.last().cssSafeAreaTopInsetPx)
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) { session.restoreCount == 2 }
        composeRule.runOnUiThread {
            assertTrue(requireNotNull(controller).isMediaLayoutRestorationPending)
            session.completeRestore(true)
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            !requireNotNull(controller).isMediaLayoutRestorationPending
        }
    }

    private class RestoringSession(
        override val tabId: String,
    ) : AndroidBrowserEngineSessionPort {
        val policies = mutableListOf<GeckoPrivacyPolicy>()
        @Volatile var restoreCount = 0
            private set
        private var restoreCallback: ((Boolean) -> Unit)? = null

        override fun createView(context: Context): View = View(context)

        override fun releaseView(view: View) = Unit

        override fun execute(command: BrowserEngineCommand) = Unit

        override fun awaitContentPresented(listener: () -> Unit) = Unit

        override fun capturePreview(
            targetWidthPx: Int,
            visibleViewHeightPx: Int,
            maximumTargetHeightPx: Int,
            onComplete: (Bitmap?) -> Unit,
        ): BrowserEnginePreviewCapture? = null

        override fun findInPage(
            query: String,
            forward: Boolean,
            onComplete: (GeckoFindResult?) -> Unit,
        ) = onComplete(null)

        override fun clearFindInPage() = Unit

        override fun printPage(): Boolean = false

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

        override fun historyUrlAtOffset(offset: Int): String? = null

        override fun extractPageForReader(onComplete: (String?) -> Unit) = onComplete(null)

        override fun updatePrivacyPolicy(
            policy: GeckoPrivacyPolicy,
            reloadOnCookiePermissionChange: Boolean,
            onReady: () -> Unit,
        ) {
            policies += policy
            restoreCallback?.also { callback ->
                restoreCallback = null
                callback(false)
            }
            onReady()
        }

        override fun restorePictureInPicturePresentation(onResult: (Boolean) -> Unit) {
            restoreCount++
            restoreCallback = onResult
        }

        fun completeRestore(restored: Boolean) {
            val callback = restoreCallback
            restoreCallback = null
            callback?.invoke(restored)
        }
    }
}
