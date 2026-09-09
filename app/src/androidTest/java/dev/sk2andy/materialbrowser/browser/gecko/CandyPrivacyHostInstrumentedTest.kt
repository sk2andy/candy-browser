package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CandyPrivacyHostInstrumentedTest {
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
    fun documentStartInsetKeepsFixedHeaderBelowStatusBar() {
        assertDocumentStartInset(isPrivate = false)
    }

    @Test
    fun privateDocumentStartInsetKeepsFixedHeaderBelowStatusBar() {
        assertDocumentStartInset(isPrivate = true)
    }

    private fun assertDocumentStartInset(isPrivate: Boolean) {
        val server = FixtureServer()
        val safeTitle = CountDownLatch(1)
        val scrolledSafeTitle = CountDownLatch(1)
        val fallback = AtomicReference<GeckoPrivacyEvent>()
        val finalState = AtomicReference<GeckoBrowserSessionState>()
        lateinit var session: GeckoBrowserSession
        lateinit var view: View
        ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val policy = GeckoPrivacyPolicy.Disabled.copy(
                    pageHost = COOKIE_PAGE_HOST,
                    topInsetPx = SAFE_AREA_INSET_PX,
                    navigationGeneration = 1,
                )
                session = GeckoRuntimeOwner.getOrCreate(activity).createSession(
                    profileId = "privacy-safe-area-$isPrivate",
                    isPrivate = isPrivate,
                    privacyPolicy = policy,
                    privacyEventSink = GeckoPrivacyEventSink { event ->
                        if (event.safeAreaFallbackNavigationGeneration != null) fallback.set(event)
                    },
                )
                view = session.createView(activity)
                activity.setContentView(view)
                session.setStateListener { state ->
                    finalState.set(state)
                    if (state.title == SAFE_AREA_TITLE) safeTitle.countDown()
                    if (state.title == SCROLLED_SAFE_AREA_TITLE) scrolledSafeTitle.countDown()
                }
                session.setActive(true)
                assertTrue(session.loadUrl(server.safeAreaPageUrl()))
            }

            try {
                assertTrue(
                    "Fixed header never reached the document inset; " +
                        "state=${finalState.get()}, fallback=${fallback.get()}",
                    safeTitle.await(45, TimeUnit.SECONDS),
                )
                scenario.onActivity { session.scrollToVerticalOffset(SCROLL_OFFSET_PX) }
                assertTrue(
                    "Sticky header entered the status bar after scrolling; " +
                        "state=${finalState.get()}, fallback=${fallback.get()}",
                    scrolledSafeTitle.await(20, TimeUnit.SECONDS),
                )
            } finally {
                scenario.onActivity {
                    session.releaseView(view)
                    session.setActive(false)
                    session.close()
                }
                server.close()
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
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val scriptUrl = "http://$TRACKER_HOST:${socket.localPort}/probe.js"
        private val thread = Thread({ serve() }, "gecko-privacy-fixture").apply {
            isDaemon = true
            start()
        }

        fun pageUrl(host: String) = "http://$host:${socket.localPort}/"

        fun cookiePageUrl() = "http://$COOKIE_PAGE_HOST:${socket.localPort}/cookie-page"

        fun safeAreaPageUrl() = "http://$COOKIE_PAGE_HOST:${socket.localPort}/safe-area"

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
                        val body = when {
                            isScript -> "document.title='$ALLOWED_TITLE';"
                            isCookieFrame -> cookieFrame()
                            requestLine.contains(" /cookie-page ") -> cookiePage()
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
            <html><head><title>Checking safe area</title>
            <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
            <style>
              html, body { margin: 0; min-height: 200vh; }
              #header { position: fixed; inset: 0 0 auto; height: 24px; background: red; }
              #lead { height: 200px; }
              #sticky { position: sticky; top: 0; height: 24px; background: blue; }
              #content { height: 4000px; }
            </style></head><body><header id="header">Header</header>
            <div id="lead"></div><nav id="sticky">Sticky</nav><main id="content"></main><script>
              const timer = setInterval(() => {
                const expected = $SAFE_AREA_INSET_PX / devicePixelRatio;
                const top = document.querySelector('#header').getBoundingClientRect().top;
                const before = Number.parseFloat(
                  getComputedStyle(document.documentElement, '::before').height
                );
                if (Math.abs(top - expected) <= 0.5 && Math.abs(before - expected) <= 0.5) {
                  clearInterval(timer);
                  document.title = '$SAFE_AREA_TITLE';
                }
              }, 25);
              addEventListener('scroll', () => {
                const expected = $SAFE_AREA_INSET_PX / devicePixelRatio;
                document.querySelector('#header').style.display = 'none';
                const stickyTop = document.querySelector('#sticky').getBoundingClientRect().top;
                if (
                  scrollY > 100 &&
                  Math.abs(stickyTop - expected) <= 0.5
                ) {
                  document.title = '$SCROLLED_SAFE_AREA_TITLE';
                }
              }, { passive: true });
              setTimeout(() => {
                clearInterval(timer);
                const root = document.documentElement;
                const top = document.querySelector('#header').getBoundingClientRect().top;
                const before = getComputedStyle(root, '::before').height;
                document.title = 'unsafe:' + top + ':' + before + ':' +
                  root.style.getPropertyValue('--candy-browser-content-top-inset');
              }, 5000);
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
    }
}
