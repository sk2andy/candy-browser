package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
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
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMainFrameNavigationRequest
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaCommand
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaSessionState
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaSessionStateListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoNavigationRequestDecision
import dev.sk2andy.materialbrowser.browser.gecko.GeckoNavigationRequestListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPrivacyEvent
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPrivacyPolicy
import dev.sk2andy.materialbrowser.browser.integration.ExternalAppLauncher
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommand
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommandType
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEvent
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.DeveloperSettings
import dev.sk2andy.materialbrowser.data.GeckoSafeAreaSettings
import dev.sk2andy.materialbrowser.data.HistoryEntry
import dev.sk2andy.materialbrowser.data.HistoryRecordingMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class BrowserControllerGeckoViewBindingInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null
    private var originalEngineKind: AndroidBrowserEngineKind? = null
    private var originalHistory: List<HistoryEntry>? = null
    private var originalHistoryRecordingMode: HistoryRecordingMode? = null
    private var originalExternalAppLinkHandling: ExternalAppLinkHandling? = null

    @Test
    fun webpageImeOpeningReprobesAndParksOccludingAddressBar() {
        lateinit var session: ReentrantAttachSession
        composeRule.runOnIdle {
            val browserController = BrowserController(composeRule.activity)
            controller = browserController
            val tabId = browserController.selectedTabId
            session = ReentrantAttachSession(
                tabId = tabId,
                onFirstAttach = {},
                textInputOcclusionProbeResults = listOf(
                    TextInputOcclusionProbeResult.Occluded,
                ),
            )
            browserController.installGeckoEngineSessionForTesting(session)
            browserController.dispatchGeckoEngineEventForTesting(
                BrowserEngineEvent(
                    tabId = tabId,
                    type = BrowserEngineEventType.NavigationStarted,
                    address = "https://chat.test/",
                    title = null,
                    canGoBack = false,
                    canGoForward = false,
                    failureDescription = null,
                ),
            )
            browserController.setAddressBarBoundsInViewport(
                leftPx = 50f,
                topPx = 800f,
                rightPx = 950f,
                bottomPx = 900f,
                viewportWidthPx = 1_000f,
                viewportHeightPx = 1_000f,
            )
            browserController.onWindowInsetsChanged(
                WindowInsetsCompat.Builder()
                    .setVisible(WindowInsetsCompat.Type.ime(), false)
                    .build(),
            )
            browserController.setBrowserChromeOwnsIme(true)

            assertFalse(browserController.isAddressBarDocked)

            browserController.onWindowInsetsChanged(
                WindowInsetsCompat.Builder()
                    .setInsets(
                        WindowInsetsCompat.Type.ime(),
                        Insets.of(0, 0, 0, 600),
                    )
                    .setVisible(WindowInsetsCompat.Type.ime(), true)
                    .build(),
            )
            browserController.setBrowserChromeOwnsIme(false)
            browserController.setAddressBarBoundsInViewport(
                leftPx = 50f,
                topPx = 700f,
                rightPx = 950f,
                bottomPx = 800f,
                viewportWidthPx = 1_000f,
                viewportHeightPx = 1_000f,
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            session.textInputOcclusionProbeCount == 1
        }
        composeRule.runOnIdle {
            assertEquals(
                BrowserViewportRect(
                    leftFraction = 0.05f,
                    topFraction = 0.7f,
                    rightFraction = 0.95f,
                    bottomFraction = 0.8f,
                ),
                session.lastTextInputOcclusionViewportRect,
            )
            assertEquals(
                TextInputOcclusionProbeMode.FocusedTextInput,
                session.lastTextInputOcclusionProbeMode,
            )
            assertTrue(requireNotNull(controller).isAddressBarDocked)
            requireNotNull(controller).updateAddressBarDocked(false)
        }
    }

    @Test
    fun webpageImeProbeBurstCatchesFocusedInputMovement() {
        lateinit var session: ReentrantAttachSession
        composeRule.runOnIdle {
            val browserController = BrowserController(composeRule.activity)
            controller = browserController
            val tabId = browserController.selectedTabId
            session = ReentrantAttachSession(
                tabId = tabId,
                onFirstAttach = {},
                textInputOcclusionProbeResults = listOf(
                    TextInputOcclusionProbeResult.FocusedTextInputClear,
                    TextInputOcclusionProbeResult.FocusedTextInputClear,
                    TextInputOcclusionProbeResult.Occluded,
                ),
            )
            browserController.installGeckoEngineSessionForTesting(session)
            browserController.dispatchGeckoEngineEventForTesting(
                BrowserEngineEvent(
                    tabId = tabId,
                    type = BrowserEngineEventType.NavigationStarted,
                    address = "https://animated-input.test/",
                    title = null,
                    canGoBack = false,
                    canGoForward = false,
                    failureDescription = null,
                ),
            )
            browserController.setAddressBarBoundsInViewport(
                leftPx = 50f,
                topPx = 700f,
                rightPx = 950f,
                bottomPx = 800f,
                viewportWidthPx = 1_000f,
                viewportHeightPx = 1_000f,
            )
            browserController.onWindowInsetsChanged(
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 600))
                    .setVisible(WindowInsetsCompat.Type.ime(), true)
                    .build(),
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            session.textInputOcclusionProbeCount == 3 &&
                requireNotNull(controller).isAddressBarDocked
        }
        composeRule.runOnIdle {
            assertEquals(
                TextInputOcclusionProbeMode.FocusedTextInput,
                session.lastTextInputOcclusionProbeMode,
            )
            requireNotNull(controller).updateAddressBarDocked(false)
        }
    }

    @Test
    fun webpageImeProbeBurstStopsWhenAppPauses() {
        lateinit var session: ReentrantAttachSession
        composeRule.runOnIdle {
            val browserController = BrowserController(composeRule.activity)
            controller = browserController
            val tabId = browserController.selectedTabId
            session = ReentrantAttachSession(
                tabId = tabId,
                onFirstAttach = {},
                textInputOcclusionProbeResults = List(5) {
                    TextInputOcclusionProbeResult.FocusedTextInputClear
                },
            )
            browserController.installGeckoEngineSessionForTesting(session)
            browserController.dispatchGeckoEngineEventForTesting(
                BrowserEngineEvent(
                    tabId = tabId,
                    type = BrowserEngineEventType.NavigationStarted,
                    address = "https://blurred-input.test/",
                    title = null,
                    canGoBack = false,
                    canGoForward = false,
                    failureDescription = null,
                ),
            )
            browserController.setAddressBarBoundsInViewport(
                leftPx = 50f,
                topPx = 700f,
                rightPx = 950f,
                bottomPx = 800f,
                viewportWidthPx = 1_000f,
                viewportHeightPx = 1_000f,
            )
            browserController.onWindowInsetsChanged(
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 600))
                    .setVisible(WindowInsetsCompat.Type.ime(), true)
                    .build(),
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            session.textInputOcclusionProbeCount == 1
        }
        composeRule.runOnIdle {
            requireNotNull(controller).onPause()
        }

        Thread.sleep(700L)
        composeRule.runOnIdle {
            assertEquals(1, session.textInputOcclusionProbeCount)
            assertFalse(requireNotNull(controller).isAddressBarDocked)
        }
    }

    @Test
    fun developerSafeAreaFallbackForcesNativePreviewInset() {
        composeRule.runOnIdle {
            val browserController = BrowserController(composeRule.activity)
            controller = browserController
            browserController.onWindowInsetsChanged(
                WindowInsetsCompat.Builder()
                    .setInsets(
                        WindowInsetsCompat.Type.statusBars(),
                        Insets.of(0, 72, 0, 0),
                    )
                    .build(),
            )

            assertEquals(0, browserController.previewTopInsetPx(browserController.selectedTabId))

            browserController.updateDeveloperSettings(
                DeveloperSettings(forceSafeAreaFallback = true),
            )

            assertEquals(72, browserController.previewTopInsetPx(browserController.selectedTabId))
            browserController.updateDeveloperSettings(DeveloperSettings())
        }
    }

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            try {
                controller?.destroy()
            } finally {
                val store = BrowserSessionStore(composeRule.activity)
                originalEngineKind?.let { kind ->
                    assertTrue(store.saveAndroidBrowserEngineKind(kind))
                }
                originalHistoryRecordingMode?.let { mode ->
                    assertTrue(store.saveHistoryRecordingMode(mode))
                }
                originalHistory?.let { history ->
                    assertTrue(store.commitHistory(history))
                }
                originalExternalAppLinkHandling?.let(store::saveExternalAppLinkHandling)
            }
        }
    }

    @Test
    fun committedGeckoNavigationRecordsHistoryWhenSavingIsEnabled() {
        composeRule.runOnIdle {
            val store = BrowserSessionStore(composeRule.activity)
            originalHistory = store.loadHistory()
            originalHistoryRecordingMode = store.loadHistoryRecordingMode()
            assertTrue(store.commitHistory(emptyList()))
            assertTrue(store.saveHistoryRecordingMode(HistoryRecordingMode.Enabled))
            val browserController = BrowserController(composeRule.activity)
            controller = browserController
            val tabId = browserController.selectedTabId
            browserController.installGeckoEngineSessionForTesting(
                ReentrantAttachSession(tabId = tabId, onFirstAttach = {}),
            )

            browserController.dispatchGeckoEngineEventForTesting(
                BrowserEngineEvent(
                    tabId = tabId,
                    type = BrowserEngineEventType.NavigationCommitted,
                    address = "https://history.example/article",
                    title = "Recorded article",
                    canGoBack = false,
                    canGoForward = false,
                    failureDescription = null,
                ),
            )

            val entry = store.loadHistory().single()
            assertEquals("https://history.example/article", entry.url)
            assertEquals("Recorded article", entry.title)
            assertEquals(browserController.selectedTabForTesting().profileId, entry.profileId)
        }
    }

    @Test
    fun cloudflareChallengeOfferTemporarilyAllowsCookiesThenReloads() {
        composeRule.runOnIdle {
            val store = BrowserSessionStore(composeRule.activity)
            originalEngineKind = store.loadAndroidBrowserEngineKind()
            assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
            val browserController = BrowserController(composeRule.activity)
            controller = browserController
            val tabId = browserController.selectedTabId
            val session = ReentrantAttachSession(tabId = tabId, onFirstAttach = {})
            browserController.installGeckoEngineSessionForTesting(session)
            browserController.dispatchGeckoEngineEventForTesting(
                BrowserEngineEvent(
                    tabId = tabId,
                    type = BrowserEngineEventType.NavigationStarted,
                    address = CLOUDFLARE_CHALLENGE_PAGE_URL,
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
                    requestUrl = "https://stale.example/",
                    pageUrl = "https://stale.example/",
                    ruleId = null,
                    wasBlocked = false,
                    isBuiltIn = false,
                    isCompatibilityObservation = false,
                    isCloudflareChallengeResponse = true,
                ),
            )
            assertTrue(session.commands.isEmpty())
            assertTrue(session.privacyPolicies.isEmpty())

            browserController.dispatchSelectedGeckoPrivacyEventForTesting(
                GeckoPrivacyEvent(
                    requestUrl = CLOUDFLARE_CHALLENGE_PAGE_URL,
                    pageUrl = CLOUDFLARE_CHALLENGE_PAGE_URL,
                    ruleId = null,
                    wasBlocked = false,
                    isBuiltIn = false,
                    isCompatibilityObservation = false,
                    isCloudflareChallengeResponse = true,
                ),
            )

            val offer = requireNotNull(browserController.captchaCompatibilityOffer)
            assertEquals(CaptchaProvider.Cloudflare, offer.provider)
            assertTrue(session.commands.isEmpty())
            assertTrue(session.privacyPolicies.isEmpty())
            browserController.respondToCaptchaCompatibilityOffer(
                token = offer.token,
                choice = CaptchaCompatibilityPromptChoice.AllowForTab,
            )

            assertEquals(true, session.privacyPolicies.single().allowThirdPartyCookiesForSite)
            assertEquals(
                listOf(BrowserEngineCommandType.Reload),
                session.commands.map(BrowserEngineCommand::type),
            )
            assertTrue(browserController.acceptsThirdPartyCookiesForTesting())
            assertNull(browserController.captchaCompatibilityOffer)
            assertTrue(
                store.loadSitePrivacyOverrides().values.none { overrides ->
                    CLOUDFLARE_CHALLENGE_PAGE_HOST in overrides
                },
            )
        }
    }

    @Test
    fun externalPreviewChallengeMovesToTabForConsentFlow() {
        composeRule.runOnIdle {
            val store = BrowserSessionStore(composeRule.activity)
            originalEngineKind = store.loadAndroidBrowserEngineKind()
            assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
            val browserController = BrowserController(composeRule.activity)
            controller = browserController
            assertTrue(browserController.openExternalLinkPreview(CLOUDFLARE_CHALLENGE_PAGE_URL))
            val preview = requireNotNull(browserController.externalLinkPreviewState)
            assertTrue(browserController.prepareExternalLinkPreview(preview.sessionId))

            browserController.dispatchExternalLinkPreviewPrivacyEventForTesting(
                GeckoPrivacyEvent(
                    requestUrl = CLOUDFLARE_CHALLENGE_PAGE_URL,
                    pageUrl = CLOUDFLARE_CHALLENGE_PAGE_URL,
                    ruleId = null,
                    wasBlocked = false,
                    isBuiltIn = false,
                    isCompatibilityObservation = false,
                    isCloudflareChallengeResponse = true,
                ),
            )

            assertNull(browserController.externalLinkPreviewState)
            assertEquals(CLOUDFLARE_CHALLENGE_PAGE_URL, browserController.selectedTabForTesting().url)
        }
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
    fun existingGeckoViewReceivesUpdatedBackdropCaptureRequirement() {
        composeRule.runOnIdle {
            val activity = composeRule.activity
            val browserController = BrowserController(activity)
            controller = browserController
            val host = FrameLayout(activity)
            activity.addContentView(host, matchParentLayoutParams())
            val session = ReentrantAttachSession(
                tabId = browserController.selectedTabId,
                onFirstAttach = {},
            )
            browserController.installGeckoEngineSessionForTesting(session)

            browserController.attachSelectedBrowserEngineView(
                container = host,
                backdropCaptureEnabled = false,
            )
            browserController.attachSelectedBrowserEngineView(
                container = host,
                backdropCaptureEnabled = true,
            )

            assertEquals(listOf(false, true), session.backdropCaptureRequirements)
            assertEquals(1, session.createCount)
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
                    browserController.attachSelectedBrowserEngineView(
                        container = destinationHost,
                        onContentPresented = { staleContentCallbackCount++ },
                    )
                },
                onFirstDetach = {
                    browserController.attachSelectedBrowserEngineView(
                        container = destinationHost,
                        onContentPresented = { currentContentCallbackCount++ },
                    )
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
            val store = BrowserSessionStore(composeRule.activity)
            originalEngineKind = store.loadAndroidBrowserEngineKind()
            assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
            val browserController = BrowserController(composeRule.activity)
            assertTrue(browserController.usesGeckoEngine)
            controller = browserController
            val tabId = browserController.selectedTabId
            val session = ReentrantAttachSession(
                tabId = tabId,
                onFirstAttach = {},
            )
            browserController.installGeckoEngineSessionForTesting(session)
            browserController.updateDeveloperSettings(DeveloperSettings())
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
            assertEquals(0, session.privacyPolicies.last().topInsetPx)
            assertEquals(96, session.privacyPolicies.last().cssSafeAreaTopInsetPx)
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
            assertEquals(0, session.privacyPolicies.single().cssSafeAreaTopInsetPx)

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
                    geckoSafeAreaSettings = GeckoSafeAreaSettings(
                        recheckChangedElements = false,
                        mutationDebounceMillis = 250,
                    ),
                ),
            )
            assertEquals(
                emptyList<BrowserEngineCommandType>(),
                session.commands.map(BrowserEngineCommand::type),
            )
            assertEquals(1, session.privacyPolicies.size)
            assertEquals(0, session.privacyPolicies.single().topInsetPx)
            assertEquals(
                250,
                session.privacyPolicies.single().safeAreaLayoutQuietPeriodMillis,
            )
            assertEquals(4, session.privacyPolicies.single().safeAreaRequiredFailureCount)
            assertEquals(250, session.privacyPolicies.single().geckoSafeAreaSettings.mutationDebounceMillis)
            assertEquals(false, session.privacyPolicies.single().geckoSafeAreaSettings.recheckChangedElements)
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
            assertEquals(0, session.privacyPolicies.last().topInsetPx)
            assertEquals(2, session.privacyPolicies.size)
            assertEquals(0, session.privacyPolicies.first().cssSafeAreaTopInsetPx)
            assertEquals(96, session.privacyPolicies.last().cssSafeAreaTopInsetPx)
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
    fun staleFallbackRestorationCannotClearNewerNavigationFallback() {
        composeRule.runOnIdle {
            val store = BrowserSessionStore(composeRule.activity)
            originalEngineKind = store.loadAndroidBrowserEngineKind()
            assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
            val browserController = BrowserController(composeRule.activity)
            controller = browserController
            val tabId = browserController.selectedTabId
            val session = ReentrantAttachSession(tabId = tabId, onFirstAttach = {})
            browserController.installGeckoEngineSessionForTesting(session)
            browserController.updateDeveloperSettings(DeveloperSettings())
            browserController.onWindowInsetsChanged(
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 96, 0, 0))
                    .build(),
            )
            fun navigate(path: String) {
                browserController.dispatchGeckoEngineEventForTesting(
                    BrowserEngineEvent(
                        tabId = tabId,
                        type = BrowserEngineEventType.NavigationStarted,
                        address = "https://www.google.com/$path",
                        title = null,
                        canGoBack = false,
                        canGoForward = false,
                        failureDescription = null,
                    ),
                )
            }
            fun fallback(generation: Int) {
                browserController.dispatchSelectedGeckoPrivacyEventForTesting(
                    GeckoPrivacyEvent(
                        requestUrl = "",
                        pageUrl = "https://www.google.com/",
                        ruleId = null,
                        wasBlocked = false,
                        isBuiltIn = false,
                        isCompatibilityObservation = false,
                        safeAreaFallbackNavigationGeneration = generation,
                    ),
                )
            }

            navigate("first")
            fallback(1)
            session.deferPolicyReadyCallbacks = true
            navigate("second")
            val obsoleteReady = session.policyReadyCallbacks.single()
            navigate("third")
            session.privacyPolicies.clear()
            obsoleteReady()
            assertTrue(session.privacyPolicies.isEmpty())

            session.policyReadyCallbacks.last()()
            assertEquals(96, session.privacyPolicies.single().cssSafeAreaTopInsetPx)
            session.deferPolicyReadyCallbacks = false
            session.privacyPolicies.clear()
            fallback(3)
            assertEquals(0, session.privacyPolicies.single().cssSafeAreaTopInsetPx)
            session.privacyPolicies.clear()
            obsoleteReady()
            assertTrue(session.privacyPolicies.isEmpty())
        }
    }

    @Test
    fun HTTP404CommitKeepsNavigationDataAndExposesNotFoundStatus() {
        composeRule.runOnIdle {
            val browserController = BrowserController(composeRule.activity)
            controller = browserController
            val tabId = browserController.selectedTabId
            browserController.installGeckoEngineSessionForTesting(
                ReentrantAttachSession(tabId = tabId, onFirstAttach = {}),
            )
            val missingUrl = "https://example.com/missing"

            browserController.dispatchGeckoEngineEventForTesting(
                BrowserEngineEvent(
                    tabId = tabId,
                    type = BrowserEngineEventType.NavigationStarted,
                    address = missingUrl,
                    title = null,
                    canGoBack = false,
                    canGoForward = false,
                    failureDescription = null,
                ),
            )
            browserController.dispatchGeckoEngineEventForTesting(
                BrowserEngineEvent(
                    tabId = tabId,
                    type = BrowserEngineEventType.NavigationCommitted,
                    address = missingUrl,
                    title = "Missing",
                    canGoBack = true,
                    canGoForward = false,
                    failureDescription = null,
                    httpStatusCode = 404,
                ),
            )

            assertEquals(missingUrl, browserController.selectedTab.url)
            assertEquals("Missing", browserController.selectedTab.title)
            assertEquals(404, browserController.selectedTab.httpStatusCode)
            assertNull(browserController.selectedTab.error)
            assertEquals(true, browserController.selectedTab.canGoBack)
        }
    }

    @Test
    fun redirectedAppHandoffRestoresSourceAndReturnedLinkSkipsPreview() {
        lateinit var browserController: BrowserController
        lateinit var session: ReentrantAttachSession
        lateinit var recordingContext: RecordingContext
        composeRule.runOnIdle {
            val activity = composeRule.activity
            val store = BrowserSessionStore(activity)
            originalEngineKind = store.loadAndroidBrowserEngineKind()
            assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
            recordingContext = RecordingContext(activity)
            browserController = BrowserController(
                activity = activity,
                externalApps = ExternalAppLauncher(
                    context = recordingContext,
                    canResolveExternalActivity = { true },
                ),
            )
            controller = browserController
            val tabId = browserController.selectedTabId
            session = ReentrantAttachSession(
                tabId = tabId,
                onFirstAttach = {},
                historyUrls = mapOf(-1 to APP_HANDOFF_EARLIER_URL),
            )
            browserController.installGeckoEngineSessionForTesting(session)
            browserController.dispatchGeckoEngineEventForTesting(
                BrowserEngineEvent(
                    tabId = tabId,
                    type = BrowserEngineEventType.NavigationCommitted,
                    address = APP_HANDOFF_SOURCE_URL,
                    title = "Search",
                    canGoBack = false,
                    canGoForward = false,
                    failureDescription = null,
                ),
            )

            assertEquals(
                GeckoNavigationRequestDecision.Allow,
                browserController.dispatchSelectedGeckoNavigationRequestForTesting(
                    GeckoMainFrameNavigationRequest(
                        url = APP_HANDOFF_REDIRECT_URL,
                        isRedirect = false,
                        hasUserGesture = true,
                        isDirectNavigation = false,
                    ),
                ),
            )
            browserController.dispatchGeckoEngineEventForTesting(
                BrowserEngineEvent(
                    tabId = tabId,
                    type = BrowserEngineEventType.NavigationStarted,
                    address = APP_HANDOFF_REDIRECT_URL,
                    title = null,
                    canGoBack = true,
                    canGoForward = false,
                    failureDescription = null,
                ),
            )
            assertEquals(
                GeckoNavigationRequestDecision.Deny,
                browserController.dispatchSelectedGeckoNavigationRequestForTesting(
                    GeckoMainFrameNavigationRequest(
                        url = APP_HANDOFF_TARGET_URL,
                        isRedirect = true,
                        hasUserGesture = false,
                        isDirectNavigation = false,
                    ),
                ),
            )
            assertNull(recordingContext.lastIntent)
            browserController.dispatchGeckoEngineEventForTesting(
                BrowserEngineEvent(
                    tabId = tabId,
                    type = BrowserEngineEventType.NavigationCommitted,
                    address = APP_HANDOFF_REDIRECT_URL,
                    title = "302 Moved",
                    canGoBack = true,
                    canGoForward = false,
                    failureDescription = null,
                    httpStatusCode = 302,
                ),
            )
            assertEquals(
                listOf(BrowserEngineCommandType.Stop),
                session.commands.map(BrowserEngineCommand::type),
            )
            session.setHistoryUrl(-1, APP_HANDOFF_SOURCE_URL)
            browserController.onResume()
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(APP_HANDOFF_TARGET_URL, recordingContext.lastIntent?.dataString)
            assertEquals(
                listOf(BrowserEngineCommandType.Stop, BrowserEngineCommandType.Back),
                session.commands.map(BrowserEngineCommand::type),
            )

            session.setHistoryUrl(0, APP_HANDOFF_SOURCE_URL)
            browserController.dispatchGeckoEngineEventForTesting(
                BrowserEngineEvent(
                    tabId = browserController.selectedTabId,
                    type = BrowserEngineEventType.NavigationCommitted,
                    address = APP_HANDOFF_SOURCE_URL,
                    title = "Search",
                    canGoBack = true,
                    canGoForward = true,
                    failureDescription = null,
                ),
            )
            assertEquals(
                listOf(
                    BrowserEngineCommandType.Stop,
                    BrowserEngineCommandType.Back,
                    BrowserEngineCommandType.ReplaceHistory,
                ),
                session.commands.map(BrowserEngineCommand::type),
            )

            session.commands.clear()
            assertTrue(browserController.openReturnedExternalAppLink(APP_HANDOFF_RETURN_URL))
            assertNull(browserController.externalLinkPreviewState)
            assertEquals(APP_HANDOFF_RETURN_URL, browserController.selectedTab.url)
            assertEquals(
                listOf(BrowserEngineCommandType.Load),
                session.commands.map(BrowserEngineCommand::type),
            )
        }
    }

    @Test
    fun askEveryTimeDefersNewWindowAppLinkUntilConfirmation() {
        lateinit var browserController: BrowserController
        lateinit var recordingContext: RecordingContext
        composeRule.runOnIdle {
            val activity = composeRule.activity
            val store = BrowserSessionStore(activity)
            originalEngineKind = store.loadAndroidBrowserEngineKind()
            originalExternalAppLinkHandling = store.loadExternalAppLinkHandling()
            assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
            recordingContext = RecordingContext(activity)
            browserController = BrowserController(
                activity = activity,
                externalApps = ExternalAppLauncher(
                    context = recordingContext,
                    canResolveExternalActivity = { true },
                ),
            )
            controller = browserController
            val tabId = browserController.selectedTabId
            val session = ReentrantAttachSession(tabId = tabId, onFirstAttach = {})
            browserController.installGeckoEngineSessionForTesting(session)
            browserController.updateExternalAppLinkHandling(ExternalAppLinkHandling.AskEveryTime)
            browserController.dispatchGeckoEngineEventForTesting(
                BrowserEngineEvent(
                    tabId = tabId,
                    type = BrowserEngineEventType.NavigationCommitted,
                    address = APP_HANDOFF_SOURCE_URL,
                    title = "Search",
                    canGoBack = false,
                    canGoForward = false,
                    failureDescription = null,
                ),
            )

            assertEquals(
                GeckoNavigationRequestDecision.Deny,
                browserController.dispatchSelectedGeckoNavigationRequestForTesting(
                    GeckoMainFrameNavigationRequest(
                        url = APP_HANDOFF_TARGET_URL,
                        isRedirect = false,
                        hasUserGesture = true,
                        isDirectNavigation = false,
                        target = BrowserEngineNavigationTarget.New,
                    ),
                ),
            )
            assertNull(recordingContext.lastIntent)
            val prompt = requireNotNull(browserController.externalAppPrompt)
            assertEquals("chatgpt.com", prompt.destination)

            browserController.confirmExternalAppPrompt(prompt.id)

            assertEquals(APP_HANDOFF_TARGET_URL, recordingContext.lastIntent?.dataString)
            assertNull(browserController.externalAppPrompt)
        }
    }

    @Test
    fun askEveryTimeAllowsWebLinkWhenNoExternalAppCanHandleIt() {
        composeRule.runOnIdle {
            val activity = composeRule.activity
            val store = BrowserSessionStore(activity)
            originalEngineKind = store.loadAndroidBrowserEngineKind()
            originalExternalAppLinkHandling = store.loadExternalAppLinkHandling()
            assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
            val recordingContext = RecordingContext(activity)
            val browserController = BrowserController(
                activity = activity,
                externalApps = ExternalAppLauncher(
                    context = recordingContext,
                    canResolveExternalActivity = { false },
                ),
            )
            controller = browserController
            val tabId = browserController.selectedTabId
            browserController.installGeckoEngineSessionForTesting(
                ReentrantAttachSession(tabId = tabId, onFirstAttach = {}),
            )
            browserController.updateExternalAppLinkHandling(ExternalAppLinkHandling.AskEveryTime)
            browserController.dispatchGeckoEngineEventForTesting(
                BrowserEngineEvent(
                    tabId = tabId,
                    type = BrowserEngineEventType.NavigationCommitted,
                    address = APP_HANDOFF_SOURCE_URL,
                    title = "Search",
                    canGoBack = false,
                    canGoForward = false,
                    failureDescription = null,
                ),
            )

            assertEquals(
                GeckoNavigationRequestDecision.Allow,
                browserController.dispatchSelectedGeckoNavigationRequestForTesting(
                    GeckoMainFrameNavigationRequest(
                        url = APP_HANDOFF_TARGET_URL,
                        isRedirect = false,
                        hasUserGesture = true,
                        isDirectNavigation = false,
                    ),
                ),
            )
            assertNull(recordingContext.lastIntent)
            assertNull(browserController.externalAppPrompt)
        }
    }

    @Test
    fun askEveryTimeDefersSpecialSchemeSubmittedFromAddressBar() {
        composeRule.runOnIdle {
            val activity = composeRule.activity
            val store = BrowserSessionStore(activity)
            originalEngineKind = store.loadAndroidBrowserEngineKind()
            originalExternalAppLinkHandling = store.loadExternalAppLinkHandling()
            assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
            val recordingContext = RecordingContext(activity)
            val browserController = BrowserController(
                activity = activity,
                externalApps = ExternalAppLauncher(recordingContext),
            )
            controller = browserController
            val tabId = browserController.selectedTabId
            browserController.installGeckoEngineSessionForTesting(
                ReentrantAttachSession(tabId = tabId, onFirstAttach = {}),
            )
            browserController.updateExternalAppLinkHandling(ExternalAppLinkHandling.AskEveryTime)

            browserController.submitAddress("mailto:candy@example.com")

            assertNull(recordingContext.lastIntent)
            val prompt = requireNotNull(browserController.externalAppPrompt)
            assertEquals("mailto", prompt.destination)

            browserController.confirmExternalAppPrompt(prompt.id)

            assertEquals("mailto:candy@example.com", recordingContext.lastIntent?.dataString)
            assertNull(browserController.externalAppPrompt)
        }
    }

    @Test
    fun newNavigationInvalidatesPendingExternalAppPrompt() {
        composeRule.runOnIdle {
            val activity = composeRule.activity
            val store = BrowserSessionStore(activity)
            originalEngineKind = store.loadAndroidBrowserEngineKind()
            originalExternalAppLinkHandling = store.loadExternalAppLinkHandling()
            assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
            val recordingContext = RecordingContext(activity)
            val browserController = BrowserController(
                activity = activity,
                externalApps = ExternalAppLauncher(
                    context = recordingContext,
                    canResolveExternalActivity = { true },
                ),
            )
            controller = browserController
            val tabId = browserController.selectedTabId
            browserController.installGeckoEngineSessionForTesting(
                ReentrantAttachSession(tabId = tabId, onFirstAttach = {}),
            )
            browserController.updateExternalAppLinkHandling(ExternalAppLinkHandling.AskEveryTime)
            browserController.dispatchGeckoEngineEventForTesting(
                BrowserEngineEvent(
                    tabId = tabId,
                    type = BrowserEngineEventType.NavigationCommitted,
                    address = APP_HANDOFF_SOURCE_URL,
                    title = "Search",
                    canGoBack = false,
                    canGoForward = false,
                    failureDescription = null,
                ),
            )
            assertEquals(
                GeckoNavigationRequestDecision.Deny,
                browserController.dispatchSelectedGeckoNavigationRequestForTesting(
                    GeckoMainFrameNavigationRequest(
                        url = APP_HANDOFF_TARGET_URL,
                        isRedirect = false,
                        hasUserGesture = true,
                        isDirectNavigation = false,
                    ),
                ),
            )
            val promptId = requireNotNull(browserController.externalAppPrompt).id

            browserController.dispatchGeckoEngineEventForTesting(
                BrowserEngineEvent(
                    tabId = tabId,
                    type = BrowserEngineEventType.NavigationStarted,
                    address = "https://example.com/new",
                    title = null,
                    canGoBack = true,
                    canGoForward = false,
                    failureDescription = null,
                ),
            )
            browserController.confirmExternalAppPrompt(promptId)

            assertNull(browserController.externalAppPrompt)
            assertNull(recordingContext.lastIntent)
        }
    }

    @Test
    fun cancellingRedirectedAppPromptRestoresSourceWithoutLaunchingApp() {
        lateinit var browserController: BrowserController
        lateinit var session: ReentrantAttachSession
        lateinit var recordingContext: RecordingContext
        composeRule.runOnIdle {
            val activity = composeRule.activity
            val store = BrowserSessionStore(activity)
            originalEngineKind = store.loadAndroidBrowserEngineKind()
            originalExternalAppLinkHandling = store.loadExternalAppLinkHandling()
            assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
            recordingContext = RecordingContext(activity)
            browserController = BrowserController(
                activity = activity,
                externalApps = ExternalAppLauncher(
                    context = recordingContext,
                    canResolveExternalActivity = { true },
                ),
            )
            controller = browserController
            val tabId = browserController.selectedTabId
            session = ReentrantAttachSession(
                tabId = tabId,
                onFirstAttach = {},
                historyUrls = mapOf(-1 to APP_HANDOFF_EARLIER_URL),
            )
            browserController.installGeckoEngineSessionForTesting(session)
            browserController.updateExternalAppLinkHandling(ExternalAppLinkHandling.AskEveryTime)
            browserController.dispatchGeckoEngineEventForTesting(
                BrowserEngineEvent(
                    tabId = tabId,
                    type = BrowserEngineEventType.NavigationCommitted,
                    address = APP_HANDOFF_SOURCE_URL,
                    title = "Search",
                    canGoBack = false,
                    canGoForward = false,
                    failureDescription = null,
                ),
            )
            assertEquals(
                GeckoNavigationRequestDecision.Allow,
                browserController.dispatchSelectedGeckoNavigationRequestForTesting(
                    GeckoMainFrameNavigationRequest(
                        url = APP_HANDOFF_REDIRECT_URL,
                        isRedirect = false,
                        hasUserGesture = true,
                        isDirectNavigation = false,
                    ),
                ),
            )
            browserController.dispatchGeckoEngineEventForTesting(
                BrowserEngineEvent(
                    tabId = tabId,
                    type = BrowserEngineEventType.NavigationStarted,
                    address = APP_HANDOFF_REDIRECT_URL,
                    title = null,
                    canGoBack = true,
                    canGoForward = false,
                    failureDescription = null,
                ),
            )
            assertEquals(
                GeckoNavigationRequestDecision.Deny,
                browserController.dispatchSelectedGeckoNavigationRequestForTesting(
                    GeckoMainFrameNavigationRequest(
                        url = APP_HANDOFF_TARGET_URL,
                        isRedirect = true,
                        hasUserGesture = false,
                        isDirectNavigation = false,
                    ),
                ),
            )
            assertNull(recordingContext.lastIntent)
            val prompt = requireNotNull(browserController.externalAppPrompt)
            session.setHistoryUrl(-1, APP_HANDOFF_SOURCE_URL)

            browserController.dismissExternalAppPrompt(prompt.id)
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertNull(recordingContext.lastIntent)
            assertNull(browserController.externalAppPrompt)
            assertEquals(
                listOf(BrowserEngineCommandType.Stop, BrowserEngineCommandType.Back),
                session.commands.map(BrowserEngineCommand::type),
            )
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
        textInputOcclusionProbeResults: List<TextInputOcclusionProbeResult> = emptyList(),
        historyUrls: Map<Int, String> = emptyMap(),
    ) : AndroidBrowserEngineSessionPort {
        private val historyUrls = historyUrls.toMutableMap()
        private val textInputOcclusionProbeResults =
            textInputOcclusionProbeResults.toMutableList()
        val commands = mutableListOf<BrowserEngineCommand>()
        val privacyPolicies = mutableListOf<GeckoPrivacyPolicy>()
        var deferPolicyReadyCallbacks = false
        val policyReadyCallbacks = mutableListOf<() -> Unit>()
        val backdropCaptureRequirements = mutableListOf<Boolean>()
        var createCount = 0
            private set
        var createdView: View? = null
            private set
        @Volatile
        var textInputOcclusionProbeCount = 0
            private set
        @Volatile
        var lastTextInputOcclusionViewportRect: BrowserViewportRect? = null
            private set
        @Volatile
        var lastTextInputOcclusionProbeMode: TextInputOcclusionProbeMode? = null
            private set
        private var attachDispatched = false
        private var detachDispatched = false
        private var releaseDispatched = false

        override fun setBackdropCaptureEnabled(enabled: Boolean) {
            backdropCaptureRequirements += enabled
        }

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

        override fun historyUrlAtOffset(offset: Int): String? = historyUrls[offset]

        fun setHistoryUrl(offset: Int, url: String) {
            historyUrls[offset] = url
        }

        override fun extractPageForReader(onComplete: (String?) -> Unit) = onComplete(null)

        override fun probeTextInputOcclusion(
            viewportRect: BrowserViewportRect,
            mode: TextInputOcclusionProbeMode,
            onComplete: (TextInputOcclusionProbeResult) -> Unit,
        ) {
            textInputOcclusionProbeCount++
            lastTextInputOcclusionViewportRect = viewportRect
            lastTextInputOcclusionProbeMode = mode
            onComplete(
                if (textInputOcclusionProbeResults.isEmpty()) {
                    TextInputOcclusionProbeResult.NoFocusedTextInput
                } else {
                    textInputOcclusionProbeResults.removeAt(0)
                },
            )
        }

        override fun updatePrivacyPolicy(
            policy: GeckoPrivacyPolicy,
            reloadOnCookiePermissionChange: Boolean,
            onReady: () -> Unit,
        ) {
            privacyPolicies += policy
            if (deferPolicyReadyCallbacks) policyReadyCallbacks += onReady else onReady()
        }
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        var lastIntent: Intent? = null

        override fun startActivity(intent: Intent) {
            lastIntent = Intent(intent)
        }
    }

    private companion object {
        const val CLOUDFLARE_CHALLENGE_PAGE_HOST = "managed-challenge.example"
        const val CLOUDFLARE_CHALLENGE_PAGE_URL =
            "https://$CLOUDFLARE_CHALLENGE_PAGE_HOST/verify"
        const val APP_HANDOFF_SOURCE_URL = "https://www.google.com/search?q=chatgpt"
        const val APP_HANDOFF_EARLIER_URL = "https://www.google.com/"
        const val APP_HANDOFF_REDIRECT_URL = "https://www.google.com/url?q=chatgpt"
        const val APP_HANDOFF_TARGET_URL = "https://chatgpt.com/"
        const val APP_HANDOFF_RETURN_URL = "https://chatgpt.com/auth/callback"
    }
}
