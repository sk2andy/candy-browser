package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.os.SystemClock
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.data.GeckoSafeAreaSettings
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoSession

@RunWith(AndroidJUnit4::class)
class CandyPrivacyHostInstrumentedTest {
    @Test
    fun stalePrivacyBootstrapCallbacksDoNotReplaceRestoredPageAddress() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = FixtureServer()
        val pageUrl = server.pageUrl(PAGE_HOST)
        val pageLoaded = CountDownLatch(1)
        val latestState = AtomicReference<GeckoBrowserSessionState>()
        lateinit var session: GeckoBrowserSession
        lateinit var view: View

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            session = GeckoRuntimeOwner.getOrCreate(context).createSession(
                profileId = "privacy-stale-bootstrap",
                isPrivate = false,
                privacyPolicy = GeckoPrivacyPolicy.Disabled.copy(pageHost = PAGE_HOST),
            )
            view = session.createView(context)
            session.setStateListener { state ->
                latestState.set(state)
                if (state.url == pageUrl && !state.isLoading) pageLoaded.countDown()
            }
            assertTrue(session.loadUrl(pageUrl))
        }

        try {
            assertTrue("Web page did not load", pageLoaded.await(20, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val nativeSession = session.javaClass.getDeclaredField("session")
                    .apply { isAccessible = true }
                    .get(session) as GeckoSession
                val staleBootstrap =
                    "moz-extension://5e6344e7-68a0-4a80-863c-0123456789ab/" +
                        "bootstrap.html?token=11111111-2222-4333-8444-555555555555"
                nativeSession.progressDelegate?.onPageStart(nativeSession, staleBootstrap)
                nativeSession.navigationDelegate?.onLocationChange(
                    nativeSession,
                    staleBootstrap,
                    emptyList(),
                    false,
                )
                assertEquals(pageUrl, latestState.get().url)
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                session.releaseView(view)
                session.close()
            }
            server.close()
        }
    }

    @Test
    fun rapidPolicyRevisionsKeepOneBootstrapAndGateOnTheCurrentRevision() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = FixtureServer()
        val policiesReady = CountDownLatch(3)
        val readyCount = AtomicInteger()
        val loadedPage = CountDownLatch(1)
        lateinit var runtime: GeckoRuntimeHandle
        lateinit var session: GeckoBrowserSession
        lateinit var view: View

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            runtime = GeckoRuntimeOwner.getOrCreate(context)
            session = runtime.createSession(
                profileId = "privacy-rapid-policy",
                isPrivate = false,
                privacyPolicy = GeckoPrivacyPolicy.Disabled.copy(pageHost = PAGE_HOST),
            )
            view = session.createView(context)
            session.setStateListener { state ->
                if (state.title == ALLOWED_TITLE) loadedPage.countDown()
            }
            listOf(
                GeckoPrivacyPolicy.Disabled.copy(pageHost = PAGE_HOST),
                GeckoPrivacyPolicy.Disabled.copy(
                    pageHost = PAGE_HOST,
                    hideCookieConsent = true,
                    cookieBannerRemovalDisabled = false,
                ),
                GeckoPrivacyPolicy.Disabled.copy(
                    pageHost = PAGE_HOST,
                    hideCookieConsent = false,
                    cookieBannerRemovalDisabled = true,
                ),
            ).forEach { policy ->
                session.updatePrivacyPolicy(policy) {
                    readyCount.incrementAndGet()
                    policiesReady.countDown()
                }
            }
            assertTrue(session.loadUrl(server.pageUrl(PAGE_HOST)))
        }

        try {
            assertTrue("Current Privacy policy was not acknowledged", policiesReady.await(20, TimeUnit.SECONDS))
            assertEquals(3, readyCount.get())
            assertTrue("Final policy was not applied", loadedPage.await(20, TimeUnit.SECONDS))
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                session.releaseView(view)
                session.close()
            }
            server.close()
        }
    }

    @Test
    fun thirdPartyCookieSettingBlocksGloballyAndAllowsConfirmedSiteException() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = FixtureServer()
        lateinit var runtime: GeckoRuntimeHandle
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            runtime = GeckoRuntimeOwner.getOrCreate(context)
        }

        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                runtime.setBlockThirdPartyCookies(true)
            }
            assertCookieScenario(
                context = context,
                runtime = runtime,
                server = server,
                profileId = "privacy-cookie-blocked",
                policy = GeckoPrivacyPolicy.Disabled.copy(
                    pageHost = COOKIE_PAGE_HOST,
                    blockThirdPartyCookies = true,
                ),
                expectedTitle = COOKIE_BLOCKED_TITLE,
            )
            assertCookieScenario(
                context = context,
                runtime = runtime,
                server = server,
                profileId = "privacy-cookie-site-allowed",
                policy = GeckoPrivacyPolicy.Disabled.copy(
                    pageHost = COOKIE_PAGE_HOST,
                    blockThirdPartyCookies = true,
                    allowThirdPartyCookiesForSite = true,
                ),
                expectedTitle = COOKIE_ALLOWED_TITLE,
            )
            assertCookieScenario(
                context = context,
                runtime = runtime,
                server = server,
                profileId = "privacy-cookie-site-allowed",
                policy = GeckoPrivacyPolicy.Disabled.copy(
                    pageHost = COOKIE_PAGE_HOST,
                    blockThirdPartyCookies = true,
                ),
                expectedTitle = COOKIE_BLOCKED_TITLE,
            )
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                runtime.setBlockThirdPartyCookies(false)
            }
            assertCookieScenario(
                context = context,
                runtime = runtime,
                server = server,
                profileId = "privacy-cookie-global-allowed",
                policy = GeckoPrivacyPolicy.Disabled.copy(
                    pageHost = COOKIE_PAGE_HOST,
                    blockThirdPartyCookies = false,
                ),
                expectedTitle = COOKIE_ALLOWED_TITLE,
            )
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                runtime.setBlockThirdPartyCookies(true)
            }
            server.close()
        }
    }

    @Test
    fun allowedSiteCookieBehaviorIsAppliedBeforeInitialNavigation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = FixtureServer()
        val loadingStarted = CountDownLatch(1)
        val navigationFinished = CountDownLatch(1)
        val loadedPage = CountDownLatch(1)
        lateinit var runtime: GeckoViewRuntimeHandle
        lateinit var session: GeckoBrowserSession
        lateinit var view: View

        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                runtime = GeckoRuntimeOwner.getOrCreate(context) as GeckoViewRuntimeHandle
                runtime.setBlockThirdPartyCookies(true)
                session = runtime.createSession(
                    profileId = "privacy-cookie-initial-navigation",
                    isPrivate = false,
                    privacyPolicy = GeckoPrivacyPolicy.Disabled.copy(
                        pageHost = COOKIE_PAGE_HOST,
                        blockThirdPartyCookies = true,
                        allowThirdPartyCookiesForSite = true,
                    ),
                )
                view = session.createView(context)
                session.setStateListener { state ->
                    if (state.url == server.slowCookiePageUrl() && state.isLoading) {
                        loadingStarted.countDown()
                    }
                    if (
                        state.url == server.slowCookiePageUrl() &&
                        state.title == COOKIE_ALLOWED_TITLE &&
                        !state.isLoading &&
                        state.lastNavigationSucceeded != null
                    ) {
                        navigationFinished.countDown()
                    }
                    if (state.title == COOKIE_ALLOWED_TITLE) loadedPage.countDown()
                }

                assertEquals(
                    ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY,
                    runtime.normalCookieBehaviorForTesting(),
                )
                assertTrue(session.loadUrl(server.cookiePageUrl()))
                assertEquals(
                    ContentBlocking.CookieBehavior.ACCEPT_ALL,
                    runtime.normalCookieBehaviorForTesting(),
                )
                session.stop()
                assertEquals(
                    ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY,
                    runtime.normalCookieBehaviorForTesting(),
                )
                session.releaseView(view)
                session.close()

                session = runtime.createSession(
                    profileId = "privacy-cookie-inactive-navigation",
                    isPrivate = false,
                    privacyPolicy = GeckoPrivacyPolicy.Disabled.copy(
                        pageHost = COOKIE_PAGE_HOST,
                        blockThirdPartyCookies = true,
                        allowThirdPartyCookiesForSite = true,
                    ),
                )
                view = session.createView(context)
                session.setStateListener { state ->
                    if (state.url == server.slowCookiePageUrl() && state.isLoading) {
                        loadingStarted.countDown()
                    }
                    if (
                        state.url == server.slowCookiePageUrl() &&
                        state.title == COOKIE_ALLOWED_TITLE &&
                        !state.isLoading &&
                        state.lastNavigationSucceeded != null
                    ) {
                        navigationFinished.countDown()
                    }
                    if (state.title == COOKIE_ALLOWED_TITLE) loadedPage.countDown()
                }
                assertTrue(session.loadUrl(server.slowCookiePageUrl()))
                assertEquals(
                    ContentBlocking.CookieBehavior.ACCEPT_ALL,
                    runtime.normalCookieBehaviorForTesting(),
                )
                session.setActive(true)
            }
            assertTrue(
                "Inactive cookie navigation did not start",
                loadingStarted.await(20, TimeUnit.SECONDS),
            )
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                session.setActive(false)
                assertEquals(
                    ContentBlocking.CookieBehavior.ACCEPT_ALL,
                    runtime.normalCookieBehaviorForTesting(),
                )
            }
            server.releaseSlowCookiePage()

            assertTrue(
                "Allowed cookie page did not load after inactive navigation",
                loadedPage.await(20, TimeUnit.SECONDS),
            )
            assertTrue(
                "Inactive cookie navigation did not finish",
                navigationFinished.await(20, TimeUnit.SECONDS),
            )
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertEquals(
                    ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY,
                    runtime.normalCookieBehaviorForTesting(),
                )
            }
        } finally {
            server.releaseSlowCookiePage()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                session.releaseView(view)
                session.setActive(false)
                session.close()
            }
            server.close()
        }
    }

    @Test
    fun allowedSiteCookieBehaviorIsAppliedBeforeSessionRestore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = FixtureServer()
        val initialPageLoaded = CountDownLatch(1)
        val restoreLoadingStarted = CountDownLatch(1)
        val restoreNavigationFinished = CountDownLatch(1)
        val restoredPageLoaded = CountDownLatch(1)
        val policy = GeckoPrivacyPolicy.Disabled.copy(
            pageHost = COOKIE_PAGE_HOST,
            blockThirdPartyCookies = true,
            allowThirdPartyCookiesForSite = true,
        )
        lateinit var runtime: GeckoViewRuntimeHandle
        lateinit var session: GeckoBrowserSession
        lateinit var view: View
        var restoreGate: FixtureServer.ResponseGate? = null

        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                runtime = GeckoRuntimeOwner.getOrCreate(context) as GeckoViewRuntimeHandle
                runtime.setBlockThirdPartyCookies(true)
                session = runtime.createSession(
                    profileId = "privacy-cookie-session-restore",
                    isPrivate = false,
                    privacyPolicy = policy,
                )
                view = session.createView(context)
                session.setStateListener { state ->
                    if (state.title == COOKIE_ALLOWED_TITLE) initialPageLoaded.countDown()
                }
                session.setActive(true)
                assertTrue(session.loadUrl(server.cookiePageUrl()))
            }
            assertTrue(
                "Initial allowed cookie page did not load",
                initialPageLoaded.await(20, TimeUnit.SECONDS),
            )
            val historyDeadline = SystemClock.elapsedRealtime() + 20_000L
            while (
                session.historyUrlAtOffset(0) != server.cookiePageUrl() &&
                SystemClock.elapsedRealtime() < historyDeadline
            ) {
                SystemClock.sleep(25L)
            }
            assertEquals(server.cookiePageUrl(), session.historyUrlAtOffset(0))

            lateinit var snapshot: String
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                snapshot = requireNotNull(session.sessionStateSnapshot())
                session.releaseView(view)
                session.setActive(false)
                session.close()
                assertEquals(
                    ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY,
                    runtime.normalCookieBehaviorForTesting(),
                )

                session = runtime.createSession(
                    profileId = "privacy-cookie-session-restore",
                    isPrivate = false,
                    privacyPolicy = policy,
                )
                view = session.createView(context)
                session.setStateListener { state ->
                    if (state.url == server.cookiePageUrl() && state.isLoading) {
                        restoreLoadingStarted.countDown()
                    }
                    if (
                        state.url == server.cookiePageUrl() &&
                        state.title == COOKIE_ALLOWED_TITLE &&
                        !state.isLoading &&
                        state.lastNavigationSucceeded != null
                    ) {
                        restoreNavigationFinished.countDown()
                    }
                    if (state.title == COOKIE_ALLOWED_TITLE) restoredPageLoaded.countDown()
                }
                restoreGate = server.blockNextCookiePage()
                assertTrue(session.restoreSessionState(snapshot))
                assertEquals(
                    ContentBlocking.CookieBehavior.ACCEPT_ALL,
                    runtime.normalCookieBehaviorForTesting(),
                )
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertEquals(
                    ContentBlocking.CookieBehavior.ACCEPT_ALL,
                    runtime.normalCookieBehaviorForTesting(),
                )
                session.setActive(true)
            }
            assertTrue(
                "Restored cookie navigation did not start",
                restoreLoadingStarted.await(20, TimeUnit.SECONDS),
            )
            assertTrue(
                "Restored cookie fixture was not requested",
                requireNotNull(restoreGate).started.await(20, TimeUnit.SECONDS),
            )
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                session.setActive(false)
                assertEquals(
                    ContentBlocking.CookieBehavior.ACCEPT_ALL,
                    runtime.normalCookieBehaviorForTesting(),
                )
            }
            requireNotNull(restoreGate).release.countDown()

            assertTrue(
                "Allowed cookie page did not load after session restore",
                restoredPageLoaded.await(20, TimeUnit.SECONDS),
            )
            assertTrue(
                "Restored cookie navigation did not finish",
                restoreNavigationFinished.await(20, TimeUnit.SECONDS),
            )
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertEquals(
                    ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY,
                    runtime.normalCookieBehaviorForTesting(),
                )
            }
        } finally {
            restoreGate?.release?.countDown()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                session.releaseView(view)
                session.setActive(false)
                session.close()
            }
            server.close()
        }
    }

    @Test
    fun recognizedCompatibilityHostIsObservedWithoutBlockingTheRequest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = FixtureServer()
        val observed = CountDownLatch(1)
        val compatibilityEvent = AtomicReference<GeckoPrivacyEvent>()
        lateinit var runtime: GeckoRuntimeHandle
        lateinit var session: GeckoBrowserSession
        lateinit var view: View
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            runtime = GeckoRuntimeOwner.getOrCreate(context)
            session = runtime.createSession(
                profileId = "privacy-compatibility-observation",
                isPrivate = false,
                privacyPolicy = GeckoPrivacyPolicy(
                    pageHost = PAGE_HOST,
                    blockAdsAndTrackers = false,
                    hideCookieConsent = false,
                    cookieBannerRemovalDisabled = true,
                    pausedHosts = emptySet(),
                    candyRules = emptyList(),
                    compatibilityRequestHosts = setOf(TRACKER_HOST),
                ),
                privacyEventSink = GeckoPrivacyEventSink { event ->
                    if (event.isCompatibilityObservation) {
                        compatibilityEvent.set(event)
                        observed.countDown()
                    }
                },
            )
            view = session.createView(context)
            assertTrue(session.loadUrl(server.pageUrl(PAGE_HOST)))
        }

        try {
            assertTrue(
                "Compatibility request was not observed",
                observed.await(20, TimeUnit.SECONDS),
            )
            assertEquals(server.scriptUrl, compatibilityEvent.get().requestUrl)
            assertFalse(compatibilityEvent.get().wasBlocked)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                session.releaseView(view)
                session.close()
            }
            server.close()
        }
    }

    @Test
    fun documentStartCssSafeAreaKeepsFixedAndStickyHeadersBelowStatusBar() {
        assertDocumentStartSafeArea(isPrivate = false)
    }

    @Test
    fun privateDocumentStartCssSafeAreaKeepsFixedAndStickyHeadersBelowStatusBar() {
        assertDocumentStartSafeArea(isPrivate = true)
    }

    @Test
    fun dynamicLayoutProtectsAddedHeaderWithoutNativeFallback() {
        val ready = CountDownLatch(1)
        val protectionReady = CountDownLatch(1)
        val fallback = CountDownLatch(1)
        val policy = safeAreaPolicy().copy(
            geckoSafeAreaSettings = GeckoSafeAreaSettings(requireInteractionForUpdates = false),
        )
        withSafeAreaFixture(
            profileId = "privacy-dynamic-safe-area",
            policy = policy,
            pageUrl = FixtureServer::dynamicSafeAreaPageUrl,
            onState = { state ->
                if (state.title == DYNAMIC_READY_TITLE) ready.countDown()
                if (state.title == DYNAMIC_PROTECTED_TITLE) protectionReady.countDown()
            },
            onPrivacyEvent = { event ->
                if (event.safeAreaFallbackNavigationGeneration != null) fallback.countDown()
            },
        ) { scenario, session, server ->
            assertTrue("Dynamic fixture never received CSS protection", ready.await(20, TimeUnit.SECONDS))
            scenario.onActivity {
                assertTrue(session.loadUrl("${server.dynamicSafeAreaPageUrl()}#trigger"))
            }
            assertTrue("Added header never received CSS protection", protectionReady.await(20, TimeUnit.SECONDS))
            assertFalse("Supported dynamic layout requested native fallback", fallback.await(1, TimeUnit.SECONDS))
        }
    }

    @Test
    fun developerCssSafeAreaSettingsReachRunningDocumentWithoutReload() {
        val protectionReady = CountDownLatch(1)
        val restored = CountDownLatch(1)
        val protectedAgain = CountDownLatch(1)
        val disabledSeen = AtomicBoolean(false)
        val initialTitle = AtomicReference<String>()
        val restoredTitle = AtomicReference<String>()
        val protectedAgainTitle = AtomicReference<String>()
        val fallback = AtomicReference<GeckoPrivacyEvent>()
        val policy = safeAreaPolicy()
        withSafeAreaFixture(
            profileId = "privacy-live-developer-safe-area",
            policy = policy,
            pageUrl = FixtureServer::developerSafeAreaPageUrl,
            onState = { state ->
                val title = state.title.orEmpty()
                when {
                    title.startsWith(DEVELOPER_READY_TITLE_PREFIX) -> {
                        initialTitle.compareAndSet(null, title)
                        if (disabledSeen.get()) {
                            protectedAgainTitle.set(title)
                            protectedAgain.countDown()
                        } else {
                            protectionReady.countDown()
                        }
                    }
                    title.startsWith(DEVELOPER_DISABLED_TITLE_PREFIX) -> {
                        restoredTitle.set(title)
                        disabledSeen.set(true)
                        restored.countDown()
                    }
                }
            },
            onPrivacyEvent = { event ->
                if (event.safeAreaFallbackNavigationGeneration != null) fallback.set(event)
            },
        ) { scenario, session, _ ->
            assertTrue("Developer fixture never received CSS protection", protectionReady.await(20, TimeUnit.SECONDS))
            val disabledPolicyReady = CountDownLatch(1)
            scenario.onActivity {
                session.updatePrivacyPolicy(
                    policy.copy(geckoSafeAreaSettings = GeckoSafeAreaSettings(enabled = false)),
                    onReady = disabledPolicyReady::countDown,
                )
            }
            assertTrue("Disabled CSS policy was not acknowledged", disabledPolicyReady.await(20, TimeUnit.SECONDS))
            assertTrue("Disabling CSS protection did not restore author layout", restored.await(20, TimeUnit.SECONDS))
            val enabledPolicyReady = CountDownLatch(1)
            scenario.onActivity {
                session.updatePrivacyPolicy(policy, onReady = enabledPolicyReady::countDown)
            }
            assertTrue("Enabled CSS policy was not acknowledged", enabledPolicyReady.await(20, TimeUnit.SECONDS))
            assertTrue("Re-enabling CSS protection did not protect the document", protectedAgain.await(20, TimeUnit.SECONDS))
            assertEquals(
                "Updating developer settings reloaded the Gecko document",
                initialTitle.get().substringAfter(':'),
                restoredTitle.get().substringAfter(':'),
            )
            assertEquals(
                "Re-enabling CSS protection reloaded the Gecko document",
                initialTitle.get().substringAfter(':'),
                protectedAgainTitle.get().substringAfter(':'),
            )
            assertNull("Live CSS policy requested native fallback", fallback.get())
        }
    }

    private fun assertDocumentStartSafeArea(isPrivate: Boolean) {
        val protectionReady = CountDownLatch(1)
        val scrolled = CountDownLatch(1)
        val fallback = AtomicReference<GeckoPrivacyEvent>()
        val latestState = AtomicReference<GeckoBrowserSessionState>()
        withSafeAreaFixture(
            profileId = "privacy-safe-area-$isPrivate",
            isPrivate = isPrivate,
            policy = safeAreaPolicy(),
            pageUrl = FixtureServer::safeAreaPageUrl,
            onState = { state ->
                latestState.set(state)
                if (state.title == SAFE_AREA_TITLE) protectionReady.countDown()
                if (state.title == SCROLLED_SAFE_AREA_TITLE) scrolled.countDown()
            },
            onPrivacyEvent = { event ->
                if (event.safeAreaFallbackNavigationGeneration != null) fallback.set(event)
            },
        ) { scenario, session, _ ->
            assertTrue(
                "Fixed header and flow never received CSS protection; state=${latestState.get()}",
                protectionReady.await(20, TimeUnit.SECONDS),
            )
            scenario.onActivity { session.scrollToVerticalOffset(SCROLL_OFFSET_PX) }
            assertTrue(
                "Sticky header entered the status bar after scrolling; state=${latestState.get()}",
                scrolled.await(20, TimeUnit.SECONDS),
            )
            assertNull("Supported fixed/sticky layout requested native fallback", fallback.get())
        }
    }

    private fun safeAreaPolicy(): GeckoPrivacyPolicy = GeckoPrivacyPolicy.Disabled.copy(
        pageHost = COOKIE_PAGE_HOST,
        cssSafeAreaTopInsetPx = SAFE_AREA_INSET_PX,
        navigationGeneration = 1,
    )

    private fun withSafeAreaFixture(
        profileId: String,
        isPrivate: Boolean = false,
        policy: GeckoPrivacyPolicy,
        pageUrl: (FixtureServer) -> String,
        onState: (GeckoBrowserSessionState) -> Unit,
        onPrivacyEvent: (GeckoPrivacyEvent) -> Unit,
        block: (ActivityScenario<GeckoScrollTestActivity>, GeckoBrowserSession, FixtureServer) -> Unit,
    ) {
        FixtureServer().use { server ->
            ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                lateinit var session: GeckoBrowserSession
                lateinit var view: View
                scenario.onActivity { activity ->
                    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                    session = GeckoRuntimeOwner.getOrCreate(activity).createSession(
                        profileId = profileId,
                        isPrivate = isPrivate,
                        privacyPolicy = policy,
                        privacyEventSink = GeckoPrivacyEventSink(onPrivacyEvent),
                    )
                    session.bindExtensionTab(profileId, 1)
                    session.setStateListener(GeckoBrowserSessionStateListener(onState))
                    view = session.createView(activity)
                    activity.setContentView(view)
                    session.setActive(true)
                    (view as GeckoViewInsetHost).updateInsets(
                        GeckoViewInsetRules.resolve(
                            safeArea = GeckoViewInsets(left = 0, top = SAFE_AREA_INSET_PX, right = 0, bottom = 0),
                            forceNativeSafeArea = false,
                            forceNativeTopSafeArea = false,
                            isFullscreenContent = false,
                            isInsideSafeDrawingHost = false,
                        ),
                        WindowInsetsCompat.Builder()
                            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, SAFE_AREA_INSET_PX, 0, 0))
                            .build(),
                    )
                    assertTrue(session.loadUrl(pageUrl(server)))
                }
                try {
                    block(scenario, session, server)
                } finally {
                    scenario.onActivity {
                        session.releaseView(view)
                        session.setActive(false)
                        session.close()
                    }
                }
            }
        }
    }

    private fun assertCookieScenario(
        context: Context,
        runtime: GeckoRuntimeHandle,
        server: FixtureServer,
        profileId: String,
        policy: GeckoPrivacyPolicy,
        expectedTitle: String,
    ) {
        val titleReached = CountDownLatch(1)
        val finalState = AtomicReference<GeckoBrowserSessionState>()
        lateinit var session: GeckoBrowserSession
        lateinit var view: View
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            session = runtime.createSession(
                profileId = profileId,
                isPrivate = false,
                privacyPolicy = policy,
            )
            view = session.createView(context)
            session.setActive(true)
            session.setStateListener { state ->
                finalState.set(state)
                if (state.title == expectedTitle) titleReached.countDown()
            }
            assertTrue(session.loadUrl(server.cookiePageUrl()))
        }

        try {
            assertTrue(
                "Third-party cookie fixture did not reach $expectedTitle; state=${finalState.get()}",
                titleReached.await(20, TimeUnit.SECONDS),
            )
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                session.releaseView(view)
                session.setActive(false)
                session.close()
            }
        }
    }

    private class FixtureServer : AutoCloseable {
        data class ResponseGate(
            val started: CountDownLatch = CountDownLatch(1),
            val release: CountDownLatch = CountDownLatch(1),
        )

        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val developerDocumentCount = AtomicInteger()
        private val slowCookiePageRelease = CountDownLatch(1)
        private val nextCookiePageGate = AtomicReference<ResponseGate?>()
        val scriptUrl = "http://$TRACKER_HOST:${socket.localPort}/probe.js"
        private val thread = Thread({ serve() }, "gecko-privacy-fixture").apply {
            isDaemon = true
            start()
        }

        fun pageUrl(host: String) = "http://$host:${socket.localPort}/"

        fun cookiePageUrl() = "http://$COOKIE_PAGE_HOST:${socket.localPort}/cookie-page"

        fun slowCookiePageUrl() = "http://$COOKIE_PAGE_HOST:${socket.localPort}/slow-cookie-page"

        fun releaseSlowCookiePage() = slowCookiePageRelease.countDown()

        fun blockNextCookiePage(): ResponseGate = ResponseGate().also { gate ->
            check(nextCookiePageGate.compareAndSet(null, gate))
        }

        fun safeAreaPageUrl() = "http://$COOKIE_PAGE_HOST:${socket.localPort}/safe-area"

        fun dynamicSafeAreaPageUrl() =
            "http://$COOKIE_PAGE_HOST:${socket.localPort}/dynamic-safe-area"

        fun developerSafeAreaPageUrl() =
            "http://$COOKIE_PAGE_HOST:${socket.localPort}/developer-safe-area"

        private fun serve() {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { connection ->
                        val reader = connection.getInputStream().bufferedReader()
                        val requestLine = reader.readLine().orEmpty()
                        var host = ""
                        while (true) {
                            val header = reader.readLine() ?: break
                            if (header.isEmpty()) break
                            if (header.startsWith("Host:", ignoreCase = true)) {
                                host = header.substringAfter(':').substringBefore(':').trim()
                            }
                        }
                        val isScript = requestLine.contains(" /probe.js ")
                        val isCookieFrame = requestLine.contains(" /cookie-frame ")
                        val isSlowCookiePage = requestLine.contains(" /slow-cookie-page ")
                        val isCookiePage = requestLine.contains(" /cookie-page ")
                        val responseGate = if (isCookiePage) nextCookiePageGate.getAndSet(null) else null
                        val body = when {
                            isScript -> "document.title='$ALLOWED_TITLE';"
                            isCookieFrame -> cookieFrame()
                            isSlowCookiePage -> cookiePage()
                            isCookiePage -> cookiePage()
                            requestLine.contains(" /developer-safe-area") ->
                                developerSafeAreaPage(developerDocumentCount.incrementAndGet())
                            requestLine.contains(" /dynamic-safe-area ") -> dynamicSafeAreaPage()
                            requestLine.contains(" /safe-area ") -> safeAreaPage()
                            else -> page(host)
                        }.toByteArray()
                        connection.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\n".toByteArray())
                            write(
                                "Content-Type: ${if (isScript) "text/javascript" else "text/html"}; charset=utf-8\r\n"
                                    .toByteArray(),
                            )
                            write("Content-Length: ${body.size}\r\n".toByteArray())
                            write("Connection: close\r\n\r\n".toByteArray())
                            flush()
                            responseGate?.started?.countDown()
                            responseGate?.release?.await(20, TimeUnit.SECONDS)
                            if (isSlowCookiePage) {
                                slowCookiePageRelease.await(20, TimeUnit.SECONDS)
                            }
                            write(body)
                            flush()
                        }
                    }
                } catch (error: SocketException) {
                    if (socket.isClosed) return
                    // Gecko may cancel an in-flight fixture response while applying a policy
                    // reload. A closed peer is expected and must not crash the instrumentation
                    // process that is validating the next session binding.
                }
            }
        }

        private fun page(host: String): String = """
            <!doctype html>
            <html><head><title>$BLOCKED_TITLE</title></head>
            <body><script src="$scriptUrl"></script></body></html>
        """.trimIndent()

        private fun cookiePage(): String = """
            <!doctype html>
            <html><head><title>Checking third-party cookie</title></head><body>
            <script>
            addEventListener('message', function(event) {
              if (event.origin !== 'http://$COOKIE_FRAME_HOST:${socket.localPort}') return;
              document.title = event.data === 'cookie-present' ?
                '$COOKIE_ALLOWED_TITLE' : '$COOKIE_BLOCKED_TITLE';
            });
            </script>
            <iframe src="http://$COOKIE_FRAME_HOST:${socket.localPort}/cookie-frame"></iframe>
            </body></html>
        """.trimIndent()

        private fun cookieFrame(): String = """
            <!doctype html>
            <html><body><script>
            var cookieVisible = false;
            try {
              document.cookie = 'candyThirdPartyCookie=present; path=/';
              cookieVisible = document.cookie.indexOf('candyThirdPartyCookie=present') >= 0;
            } catch (error) {}
            parent.postMessage(
              cookieVisible ? 'cookie-present' : 'cookie-blocked',
              'http://$COOKIE_PAGE_HOST:${socket.localPort}'
            );
            </script></body></html>
        """.trimIndent()

        private fun safeAreaPage(): String = """
            <!doctype html>
            <html><head><title>Checking CSS safe area</title>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
              html, body { margin: 0; min-height: 200vh; }
              #header { position: fixed; inset: 0 0 auto; height: 24px; background: red; }
              #lead { height: 200px; }
              #sticky { position: sticky; top: 0; height: 24px; background: blue; }
              #content { height: 4000px; }
              #env { position: absolute; visibility: hidden; padding-top: env(safe-area-inset-top); }
            </style></head><body><header id="header">Header</header>
            <div id="lead"></div><nav id="sticky">Sticky</nav><main id="content"></main>
            <div id="env"></div><script>
              let protectedBeforeScroll = false;
              const timer = setInterval(() => {
                const expected = $SAFE_AREA_INSET_PX / devicePixelRatio;
                const env = Number.parseFloat(getComputedStyle(document.querySelector('#env')).paddingTop);
                const bodyPadding = Number.parseFloat(getComputedStyle(document.body).paddingTop);
                // Gecko protects flow with its CSS layer, not the legacy html::before spacer.
                const legacyBefore = Number.parseFloat(getComputedStyle(document.documentElement, '::before').height);
                if (Math.abs(env - expected) > 0.5 || legacyBefore > 0.5) return;
                if (scrollY <= 100) {
                  const top = document.querySelector('#header').getBoundingClientRect().top;
                  const flowTop = document.querySelector('#lead').getBoundingClientRect().top;
                  if (Math.abs(top - expected) <= 0.5 && Math.abs(bodyPadding - expected) <= 0.5 &&
                      Math.abs(flowTop - expected) <= 0.5) {
                    protectedBeforeScroll = true;
                    document.title = '$SAFE_AREA_TITLE';
                  }
                  return;
                }
                if (!protectedBeforeScroll) return;
                document.querySelector('#header').style.display = 'none';
                const stickyTop = document.querySelector('#sticky').getBoundingClientRect().top;
                if (Math.abs(stickyTop - expected) <= 0.5) {
                  clearInterval(timer);
                  document.title = '$SCROLLED_SAFE_AREA_TITLE';
                }
              }, 25);
            </script></body></html>
        """.trimIndent()

        private fun dynamicSafeAreaPage(): String = """
            <!doctype html>
            <html><head><title>Preparing dynamic CSS layout</title>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
              html, body { margin: 0; min-height: 200vh; }
              #header { position: fixed; top: 0; left: 0; right: 0; height: 24px; background: red; }
              #env { position: absolute; visibility: hidden; padding-top: env(safe-area-inset-top); }
            </style></head><body><header id="header">Header</header><main>Content</main>
            <div id="env"></div><script>
              let addedHeader = null;
              addEventListener('hashchange', () => {
                if (location.hash !== '#trigger' || addedHeader) return;
                addedHeader = document.createElement('nav');
                addedHeader.style.cssText = 'position:fixed;top:0;left:0;right:0;height:24px;background:blue';
                addedHeader.textContent = 'Added header';
                document.body.appendChild(addedHeader);
              });
              setInterval(() => {
                const expected = $SAFE_AREA_INSET_PX / devicePixelRatio;
                const env = Number.parseFloat(getComputedStyle(document.querySelector('#env')).paddingTop);
                if (Math.abs(env - expected) > 0.5) return;
                const top = (addedHeader || document.querySelector('#header')).getBoundingClientRect().top;
                if (Math.abs(top - expected) <= 0.5) {
                  document.title = addedHeader ? '$DYNAMIC_PROTECTED_TITLE' : '$DYNAMIC_READY_TITLE';
                }
              }, 25);
            </script></body></html>
        """.trimIndent()

        private fun developerSafeAreaPage(documentId: Int): String = """
            <!doctype html>
            <html><head><title>Preparing developer CSS policy:$documentId</title>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
              html, body { margin: 0; min-height: 200vh; }
              #header { position: fixed; top: 0; left: 0; right: 0; height: 24px; background: red; }
              #env { position: absolute; visibility: hidden; padding-top: env(safe-area-inset-top); }
            </style></head><body><header id="header">Header</header><main>Developer safe-area fixture</main>
            <div id="env"></div><script>
              let wasProtected = false;
              setInterval(() => {
                const expected = $SAFE_AREA_INSET_PX / devicePixelRatio;
                const env = Number.parseFloat(getComputedStyle(document.querySelector('#env')).paddingTop);
                if (Math.abs(env - expected) > 0.5) return;
                const top = document.querySelector('#header').getBoundingClientRect().top;
                const padding = Number.parseFloat(getComputedStyle(document.body).paddingTop);
                if (Math.abs(top - expected) <= 0.5 && Math.abs(padding - expected) <= 0.5) {
                  wasProtected = true;
                  document.title = '$DEVELOPER_READY_TITLE_PREFIX:$documentId';
                } else if (wasProtected && Math.abs(top) <= 0.5 && Math.abs(padding) <= 0.5) {
                  document.title = '$DEVELOPER_DISABLED_TITLE_PREFIX:$documentId';
                }
              }, 25);
            </script></body></html>
        """.trimIndent()

        override fun close() {
            socket.close()
            thread.join(2_000)
        }
    }

    private companion object {
        const val PAGE_HOST = "page.candy.localhost"
        const val TRACKER_HOST = "tracker.ads.localhost"
        const val COOKIE_PAGE_HOST = "localhost"
        const val COOKIE_FRAME_HOST = "127.0.0.1"
        const val BLOCKED_TITLE = "Candy Privacy Blocked"
        const val ALLOWED_TITLE = "Candy Privacy Allowed"
        const val COOKIE_BLOCKED_TITLE = "Third-party cookie blocked"
        const val COOKIE_ALLOWED_TITLE = "Third-party cookie allowed"
        const val SAFE_AREA_INSET_PX = 96
        const val SCROLL_OFFSET_PX = 600
        const val SAFE_AREA_TITLE = "Candy safe area applied"
        const val SCROLLED_SAFE_AREA_TITLE = "Candy sticky safe area applied"
        const val DYNAMIC_READY_TITLE = "Dynamic CSS safe area ready"
        const val DYNAMIC_PROTECTED_TITLE = "Dynamic CSS header protected"
        const val DEVELOPER_READY_TITLE_PREFIX = "Developer CSS safe area ready"
        const val DEVELOPER_DISABLED_TITLE_PREFIX = "Developer CSS safe area disabled"
    }
}
