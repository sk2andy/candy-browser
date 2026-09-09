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
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPrivacyEvent
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPrivacyPolicy
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommand
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommandType
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEvent
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import dev.sk2andy.materialbrowser.data.DeveloperSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class BrowserControllerGeckoViewBindingInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        composeRule.runOnIdle { controller?.destroy() }
    }

    @Test
    fun synchronousAttachReentryReusesRecordedGeckoView() {
        composeRule.runOnIdle {
            val activity = composeRule.activity
            val browserController = BrowserController(activity)
            controller = browserController
            val host = FrameLayout(activity)
            activity.addContentView(
                host,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            val session = ReentrantAttachSession(
                tabId = browserController.selectedTabId,
                onFirstAttach = { browserController.attachSelectedBrowserEngineView(host) },
            )
            browserController.installGeckoEngineSessionForTesting(session)

            val attached = browserController.attachSelectedBrowserEngineView(host)

            assertEquals(1, session.createCount)
            assertSame(attached, session.createdView)
            assertSame(host, attached?.parent)
        }
    }

    @Test
    fun fullscreenPresentationPreventsSecondGeckoViewForAnotherHost() {
        composeRule.runOnIdle {
            val activity = composeRule.activity
            val browserController = BrowserController(activity)
            controller = browserController
            val browserHost = FrameLayout(activity)
            val fullscreenHost = FrameLayout(activity)
            val replacementBrowserHost = FrameLayout(activity)
            val root = FrameLayout(activity).apply {
                addView(browserHost)
                addView(fullscreenHost)
                addView(replacementBrowserHost)
            }
            activity.addContentView(
                root,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            val session = ReentrantAttachSession(
                tabId = browserController.selectedTabId,
                onFirstAttach = {},
            )
            browserController.installGeckoEngineSessionForTesting(session)
            val attached = browserController.attachSelectedBrowserEngineView(browserHost)
            browserController.reportSelectedGeckoMediaStateForTesting(eligibleMediaState())
            browserController.prepareForPictureInPicture()
            browserController.attachFullscreenVideoView(fullscreenHost)
            browserController.detachBrowserEngineView(browserHost)

            val replacement =
                browserController.attachSelectedBrowserEngineView(replacementBrowserHost)

            assertNull(replacement)
            assertEquals(1, session.createCount)
            assertSame(attached, session.createdView)
            assertSame(fullscreenHost, attached?.parent)
        }
    }

    @Test
    fun differentHostReentryRetriesAfterCurrentTransferCompletes() {
        lateinit var browserController: BrowserController
        lateinit var destinationHost: FrameLayout
        lateinit var session: ReentrantAttachSession
        composeRule.runOnIdle {
            val activity = composeRule.activity
            browserController = BrowserController(activity)
            controller = browserController
            val sourceHost = FrameLayout(activity)
            destinationHost = FrameLayout(activity)
            val root = FrameLayout(activity).apply {
                addView(sourceHost)
            }
            activity.addContentView(root, matchParentLayoutParams())
            session = ReentrantAttachSession(
                tabId = browserController.selectedTabId,
                onFirstAttach = {
                    assertNull(
                        browserController.attachSelectedBrowserEngineView(destinationHost),
                    )
                },
            )
            browserController.installGeckoEngineSessionForTesting(session)

            browserController.attachSelectedBrowserEngineView(sourceHost)
            root.addView(destinationHost)
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, session.createCount)
            assertSame(destinationHost, session.createdView?.parent)
        }
    }

    @Test
    fun differentHostRetryIsCancelledWhenDestinationIsDisposedBeforeDispatch() {
        lateinit var browserController: BrowserController
        lateinit var sourceHost: FrameLayout
        lateinit var destinationHost: FrameLayout
        lateinit var session: ReentrantAttachSession
        composeRule.runOnIdle {
            val activity = composeRule.activity
            browserController = BrowserController(activity)
            controller = browserController
            sourceHost = FrameLayout(activity)
            destinationHost = FrameLayout(activity)
            val root = FrameLayout(activity).apply {
                addView(sourceHost)
            }
            activity.addContentView(root, matchParentLayoutParams())
            session = ReentrantAttachSession(
                tabId = browserController.selectedTabId,
                onFirstAttach = {
                    assertNull(
                        browserController.attachSelectedBrowserEngineView(destinationHost),
                    )
                },
            )
            browserController.installGeckoEngineSessionForTesting(session)

            browserController.attachSelectedBrowserEngineView(sourceHost)
            root.addView(destinationHost)
            browserController.detachBrowserEngineView(destinationHost)
            root.removeView(destinationHost)
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, session.createCount)
            assertSame(sourceHost, session.createdView?.parent)
            assertEquals(0, destinationHost.childCount)
        }
    }

    @Test
    fun reusedHostRunsOnlyItsNewestAttachRetry() {
        lateinit var browserController: BrowserController
        lateinit var destinationHost: FrameLayout
        lateinit var transferHost: FrameLayout
        var staleContentCallbackCount = 0
        var currentContentCallbackCount = 0
        composeRule.runOnIdle {
            val activity = composeRule.activity
            browserController = BrowserController(activity)
            controller = browserController
            val sourceHost = FrameLayout(activity)
            destinationHost = FrameLayout(activity)
            transferHost = FrameLayout(activity)
            val root = FrameLayout(activity).apply {
                addView(sourceHost)
                addView(transferHost)
            }
            activity.addContentView(root, matchParentLayoutParams())
            val session = ReentrantAttachSession(
                tabId = browserController.selectedTabId,
                onFirstAttach = {
                    browserController.attachSelectedBrowserEngineView(destinationHost) {
                        staleContentCallbackCount++
                    }
                },
                onFirstDetach = {
                    browserController.attachSelectedBrowserEngineView(destinationHost) {
                        currentContentCallbackCount++
                    }
                },
                presentContentImmediately = true,
            )
            browserController.installGeckoEngineSessionForTesting(session)

            browserController.attachSelectedBrowserEngineView(sourceHost)
            root.addView(destinationHost)
            browserController.detachBrowserEngineView(destinationHost)
            root.removeView(destinationHost)
            root.addView(destinationHost)
            browserController.attachSelectedBrowserEngineView(transferHost)
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertSame(destinationHost, destinationHost.getChildAt(0)?.parent)
            assertEquals(0, staleContentCallbackCount)
            assertEquals(1, currentContentCallbackCount)
        }
    }

    @Test
    fun safeAreaFallbackUpdatesPolicyWithoutReloadingCurrentNavigation() {
        composeRule.runOnIdle {
            val browserController = BrowserController(composeRule.activity)
            controller = browserController
            val tabId = browserController.selectedTabId
            val session = ReentrantAttachSession(
                tabId = tabId,
                onFirstAttach = {},
            )
            browserController.installGeckoEngineSessionForTesting(session)
            browserController.onWindowInsetsChanged(
                WindowInsetsCompat.Builder()
                    .setInsets(
                        WindowInsetsCompat.Type.statusBars(),
                        Insets.of(0, 96, 0, 0),
                    )
                    .build(),
            )
            browserController.dispatchGeckoEngineEventForTesting(
                BrowserEngineEvent(
                    tabId = tabId,
                    type = BrowserEngineEventType.NavigationStarted,
                    address = "https://www.google.com/search?q=test",
                    title = null,
                    canGoBack = false,
                    canGoForward = false,
                    failureDescription = null,
                ),
            )
            session.commands.clear()
            session.privacyPolicies.clear()

            browserController.dispatchSelectedGeckoPrivacyEventForTesting(
                GeckoPrivacyEvent(
                    requestUrl = "",
                    pageUrl = "https://www.google.com/",
                    ruleId = null,
                    wasBlocked = false,
                    isBuiltIn = false,
                    isCompatibilityObservation = false,
                    safeAreaFallbackNavigationGeneration = 1,
                ),
            )

            assertEquals(
                emptyList<BrowserEngineCommandType>(),
                session.commands.map(BrowserEngineCommand::type),
            )
            assertEquals(1, session.privacyPolicies.size)
            assertEquals(0, session.privacyPolicies.single().topInsetPx)

            browserController.dispatchSelectedGeckoPrivacyEventForTesting(
                GeckoPrivacyEvent(
                    requestUrl = "",
                    pageUrl = "https://www.google.com/",
                    ruleId = null,
                    wasBlocked = false,
                    isBuiltIn = false,
                    isCompatibilityObservation = false,
                    safeAreaFallbackNavigationGeneration = 1,
                ),
            )
            assertEquals(1, session.privacyPolicies.size)

            session.privacyPolicies.clear()
            browserController.updateDeveloperSettings(
                DeveloperSettings(
                    safeAreaLayoutQuietPeriodMillis = 250,
                    safeAreaRequiredFailureCount = 4,
                ),
            )
            assertEquals(
                emptyList<BrowserEngineCommandType>(),
                session.commands.map(BrowserEngineCommand::type),
            )
            assertEquals(1, session.privacyPolicies.size)
            assertEquals(96, session.privacyPolicies.single().topInsetPx)
            assertEquals(
                250,
                session.privacyPolicies.single().safeAreaLayoutQuietPeriodMillis,
            )
            assertEquals(4, session.privacyPolicies.single().safeAreaRequiredFailureCount)
            session.privacyPolicies.clear()

            browserController.dispatchGeckoEngineEventForTesting(
                BrowserEngineEvent(
                    tabId = tabId,
                    type = BrowserEngineEventType.NavigationStarted,
                    address = "https://www.google.com/search?q=next",
                    title = null,
                    canGoBack = false,
                    canGoForward = false,
                    failureDescription = null,
                ),
            )
            assertEquals(96, session.privacyPolicies.last().topInsetPx)
            session.privacyPolicies.clear()
            browserController.dispatchSelectedGeckoPrivacyEventForTesting(
                GeckoPrivacyEvent(
                    requestUrl = "",
                    pageUrl = "https://www.google.com/",
                    ruleId = null,
                    wasBlocked = false,
                    isBuiltIn = false,
                    isCompatibilityObservation = false,
                    safeAreaFallbackNavigationGeneration = 1,
                ),
            )
            assertEquals(emptyList<GeckoPrivacyPolicy>(), session.privacyPolicies)
        }
    }

    @Test
    fun synchronousReleaseReentryDoesNotDiscardReplacementBinding() {
        composeRule.runOnIdle {
            val activity = composeRule.activity
            val browserController = BrowserController(activity)
            controller = browserController
            val host = FrameLayout(activity)
            activity.addContentView(host, matchParentLayoutParams())
            val firstSession = ReentrantAttachSession(
                tabId = browserController.selectedTabId,
                onFirstAttach = {},
                onFirstRelease = { browserController.attachSelectedBrowserEngineView(host) },
            )
            browserController.installGeckoEngineSessionForTesting(firstSession)
            browserController.attachSelectedBrowserEngineView(host)
            val replacementTabId = browserController.createTab()
            val replacementSession = ReentrantAttachSession(
                tabId = replacementTabId,
                onFirstAttach = {},
            )
            browserController.installGeckoEngineSessionForTesting(replacementSession)

            val replacement = browserController.attachSelectedBrowserEngineView(host)

            assertEquals(1, replacementSession.createCount)
            assertSame(replacement, replacementSession.createdView)
            assertSame(host, replacement?.parent)
        }
    }

    @Test
    fun synchronousDetachReentryDoesNotUndoRendererTransfer() {
        composeRule.runOnIdle {
            val activity = composeRule.activity
            val browserController = BrowserController(activity)
            controller = browserController
            val sourceHost = FrameLayout(activity)
            val destinationHost = FrameLayout(activity)
            val root = FrameLayout(activity).apply {
                addView(sourceHost)
                addView(destinationHost)
            }
            activity.addContentView(root, matchParentLayoutParams())
            val session = ReentrantAttachSession(
                tabId = browserController.selectedTabId,
                onFirstAttach = {},
                onFirstDetach = {
                    browserController.attachSelectedBrowserEngineView(sourceHost)
                },
            )
            browserController.installGeckoEngineSessionForTesting(session)
            val attached = browserController.attachSelectedBrowserEngineView(sourceHost)

            val transferred = browserController.attachSelectedBrowserEngineView(destinationHost)

            assertEquals(1, session.createCount)
            assertSame(attached, transferred)
            assertSame(destinationHost, transferred?.parent)
        }
    }

    private fun eligibleMediaState() =
        GeckoMediaSessionState(
            isActive = true,
            isPlaying = true,
            isFullscreen = true,
            durationMillis = 60_000,
            videoWidth = 1_280,
            videoHeight = 720,
            videoTrackCount = 1,
        )

    private fun matchParentLayoutParams() = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT,
    )

    private class ReentrantAttachSession(
        override val tabId: String,
        private val onFirstAttach: () -> Unit,
        private val onFirstDetach: (() -> Unit)? = null,
        private val onFirstRelease: (() -> Unit)? = null,
        private val presentContentImmediately: Boolean = false,
    ) : AndroidBrowserEngineSessionPort {
        val commands = mutableListOf<BrowserEngineCommand>()
        val privacyPolicies = mutableListOf<GeckoPrivacyPolicy>()
        var createCount = 0
            private set
        var createdView: View? = null
            private set
        private var attachDispatched = false
        private var detachDispatched = false
        private var releaseDispatched = false

        override fun createView(context: Context): View {
            check(createdView == null) { "Gecko session already has a bound View" }
            createCount++
            return object : View(context) {
                override fun onAttachedToWindow() {
                    super.onAttachedToWindow()
                    if (attachDispatched) return
                    attachDispatched = true
                    onFirstAttach()
                }

                override fun onDetachedFromWindow() {
                    super.onDetachedFromWindow()
                    if (detachDispatched) return
                    detachDispatched = true
                    onFirstDetach?.invoke()
                }
            }.also { createdView = it }
        }

        override fun releaseView(view: View) {
            if (createdView === view) createdView = null
            if (releaseDispatched) return
            releaseDispatched = true
            onFirstRelease?.invoke()
        }

        override fun execute(command: BrowserEngineCommand) {
            commands += command
        }

        override fun awaitContentPresented(listener: () -> Unit) {
            if (presentContentImmediately) listener()
        }

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
            privacyPolicies += policy
            onReady()
        }
    }
}
