package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.blocking.CandyRule
import dev.sk2andy.materialbrowser.blocking.CandyRuleAction
import dev.sk2andy.materialbrowser.blocking.CandyRuleKind
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CandyPrivacyHostInstrumentedTest {
    @Test
    fun firstNavigationBlocksAllowsAndProtectsPrivateSubresources() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = FixtureServer()
        val manifest = context.assets.open("candy_privacy/manifest.json")
            .bufferedReader()
            .use { reader -> JSONObject(reader.readText()) }
        assertEquals(3, manifest.getInt("manifest_version"))
        assertEquals(
            CandyPrivacyHostContract.EXTENSION_ID,
            manifest.getJSONObject("browser_specific_settings")
                .getJSONObject("gecko")
                .getString("id"),
        )
        assertTrue(manifest.getJSONArray("permissions").contains("webRequestBlocking"))

        lateinit var runtime: GeckoRuntimeHandle
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            runtime = GeckoRuntimeOwner.getOrCreate(context)
        }

        try {
            assertScenario(
                context = context,
                runtime = runtime,
                server = server,
                profileId = "privacy-block",
                isPrivate = false,
                rules = listOf(blockRule()),
                expectedTitle = BLOCKED_TITLE,
                expectedBlocked = true,
            )
            assertScenario(
                context = context,
                runtime = runtime,
                server = server,
                profileId = "privacy-allow",
                isPrivate = false,
                rules = listOf(blockRule(), allowRule()),
                expectedTitle = ALLOWED_TITLE,
                expectedBlocked = false,
            )
            assertScenario(
                context = context,
                runtime = runtime,
                server = server,
                profileId = "privacy-private",
                isPrivate = true,
                rules = listOf(blockRule()),
                expectedTitle = BLOCKED_TITLE,
                expectedBlocked = true,
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun advancedAndHostScopedCosmeticsApplyBeforeTheFirstPageSettles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = FixtureServer()
        lateinit var runtime: GeckoRuntimeHandle
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            runtime = GeckoRuntimeOwner.getOrCreate(context)
        }

        try {
            assertBuiltInAdvancedBlock(context, runtime, server)
            assertCosmeticScenario(
                context = context,
                runtime = runtime,
                server = server,
                host = STATIC_COSMETIC_HOST,
                profileId = "privacy-static-cosmetic",
                isPrivate = false,
                rules = emptyList(),
                pausedHosts = emptySet(),
                expectedTitle = COSMETIC_HIDDEN_TITLE,
            )
            assertCosmeticScenario(
                context = context,
                runtime = runtime,
                server = server,
                host = PROCEDURAL_COSMETIC_HOST,
                profileId = "privacy-procedural-cosmetic",
                isPrivate = false,
                rules = emptyList(),
                pausedHosts = emptySet(),
                expectedTitle = COSMETIC_HIDDEN_TITLE,
            )
            assertCosmeticScenario(
                context = context,
                runtime = runtime,
                server = server,
                host = DYNAMIC_COSMETIC_HOST,
                profileId = "privacy-private-cosmetic",
                isPrivate = true,
                rules = listOf(cosmeticRule()),
                pausedHosts = emptySet(),
                expectedTitle = COSMETIC_HIDDEN_TITLE,
            )
            assertCosmeticScenario(
                context = context,
                runtime = runtime,
                server = server,
                host = DYNAMIC_COSMETIC_HOST,
                profileId = "privacy-paused-cosmetic",
                isPrivate = false,
                rules = listOf(cosmeticRule()),
                pausedHosts = setOf(DYNAMIC_COSMETIC_HOST),
                expectedTitle = COSMETIC_VISIBLE_TITLE,
            )
        } finally {
            server.close()
        }
    }

    private fun assertScenario(
        context: Context,
        runtime: GeckoRuntimeHandle,
        server: FixtureServer,
        profileId: String,
        isPrivate: Boolean,
        rules: List<CandyRule>,
        expectedTitle: String,
        expectedBlocked: Boolean,
    ) {
        val stopped = CountDownLatch(1)
        val decision = CountDownLatch(1)
        val finalState = AtomicReference<GeckoBrowserSessionState>()
        val privacyEvent = AtomicReference<GeckoPrivacyEvent>()
        lateinit var session: GeckoBrowserSession
        lateinit var view: View
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            session = runtime.createSession(
                profileId = profileId,
                isPrivate = isPrivate,
                privacyPolicy = GeckoPrivacyPolicy(
                    pageHost = PAGE_HOST,
                    blockAdsAndTrackers = true,
                    hideCookieConsent = false,
                    cookieBannerRemovalDisabled = true,
                    pausedHosts = emptySet(),
                    candyRules = rules,
                ),
                privacyEventSink = GeckoPrivacyEventSink { event ->
                    if (event.ruleId in rules.map(CandyRule::id)) {
                        privacyEvent.set(event)
                        decision.countDown()
                    }
                },
            )
            view = session.createView(context)
            session.setStateListener { state ->
                finalState.set(state)
                if (state.lastNavigationSucceeded != null) stopped.countDown()
            }
            assertTrue(session.loadUrl(server.pageUrl(PAGE_HOST)))
        }

        try {
            assertTrue("Gecko fixture navigation did not finish", stopped.await(20, TimeUnit.SECONDS))
            assertTrue("Candy Privacy decision was not reported", decision.await(5, TimeUnit.SECONDS))
            assertEquals(true, finalState.get().lastNavigationSucceeded)
            assertEquals(expectedTitle, finalState.get().title)
            assertEquals(expectedBlocked, privacyEvent.get().wasBlocked)
            assertFalse(privacyEvent.get().isBuiltIn)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                session.releaseView(view)
                session.close()
            }
        }
    }

    private fun assertBuiltInAdvancedBlock(
        context: Context,
        runtime: GeckoRuntimeHandle,
        server: FixtureServer,
    ) {
        val titleReached = CountDownLatch(1)
        val decision = CountDownLatch(1)
        val privacyEvent = AtomicReference<GeckoPrivacyEvent>()
        lateinit var session: GeckoBrowserSession
        lateinit var view: View
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            session = runtime.createSession(
                profileId = "privacy-advanced",
                isPrivate = false,
                privacyPolicy = GeckoPrivacyPolicy(
                    pageHost = ADVANCED_PAGE_HOST,
                    blockAdsAndTrackers = true,
                    hideCookieConsent = false,
                    cookieBannerRemovalDisabled = true,
                    pausedHosts = emptySet(),
                    candyRules = emptyList(),
                ),
                privacyEventSink = GeckoPrivacyEventSink { event ->
                    if (event.isBuiltIn && event.wasBlocked) {
                        privacyEvent.set(event)
                        decision.countDown()
                    }
                },
            )
            view = session.createView(context)
            session.setStateListener { state ->
                if (state.title == BLOCKED_TITLE) titleReached.countDown()
            }
            assertTrue(session.loadUrl(server.pageUrl(ADVANCED_PAGE_HOST)))
        }

        try {
            assertTrue("Advanced fixture did not settle", titleReached.await(20, TimeUnit.SECONDS))
            assertTrue("Advanced block was not reported", decision.await(5, TimeUnit.SECONDS))
            assertTrue(privacyEvent.get().isBuiltIn)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                session.releaseView(view)
                session.close()
            }
        }
    }

    private fun assertCosmeticScenario(
        context: Context,
        runtime: GeckoRuntimeHandle,
        server: FixtureServer,
        host: String,
        profileId: String,
        isPrivate: Boolean,
        rules: List<CandyRule>,
        pausedHosts: Set<String>,
        expectedTitle: String,
    ) {
        val titleReached = CountDownLatch(1)
        lateinit var session: GeckoBrowserSession
        lateinit var view: View
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            session = runtime.createSession(
                profileId = profileId,
                isPrivate = isPrivate,
                privacyPolicy = GeckoPrivacyPolicy(
                    pageHost = host,
                    blockAdsAndTrackers = true,
                    hideCookieConsent = false,
                    cookieBannerRemovalDisabled = true,
                    pausedHosts = pausedHosts,
                    candyRules = rules,
                ),
            )
            view = session.createView(context)
            session.setStateListener { state ->
                if (state.title == expectedTitle) titleReached.countDown()
            }
            assertTrue(session.loadUrl(server.pageUrl(host)))
        }

        try {
            assertTrue("Cosmetic fixture did not reach $expectedTitle", titleReached.await(20, TimeUnit.SECONDS))
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                session.releaseView(view)
                session.close()
            }
        }
    }

    private fun blockRule() = CandyRule(
        id = BLOCK_RULE_ID,
        action = CandyRuleAction.Block,
        kind = CandyRuleKind.RequestHost,
        requestHost = TRACKER_HOST,
    )

    private fun allowRule() = CandyRule(
        id = ALLOW_RULE_ID,
        action = CandyRuleAction.Allow,
        kind = CandyRuleKind.HostPair,
        requestHost = TRACKER_HOST,
        firstPartyHost = PAGE_HOST,
    )

    private fun cosmeticRule() = CandyRule(
        id = "instrumentation-cosmetic",
        action = CandyRuleAction.Cosmetic,
        kind = CandyRuleKind.CosmeticCss,
        firstPartyHost = DYNAMIC_COSMETIC_HOST,
        cosmeticSelector = ".user-ad",
    )

    private class FixtureServer : AutoCloseable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val scriptUrl = "http://$TRACKER_HOST:${socket.localPort}/probe.js"
        private val thread = Thread({ serve() }, "gecko-privacy-fixture").apply {
            isDaemon = true
            start()
        }

        fun pageUrl(host: String) = "http://$host:${socket.localPort}/"

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
                        val body = if (isScript) {
                            "document.title='$ALLOWED_TITLE';"
                        } else {
                            page(host)
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
                    if (!socket.isClosed) throw error
                }
            }
        }

        private fun page(host: String): String = when (host) {
            STATIC_COSMETIC_HOST -> cosmeticPage("desktop-ad-btf", "")
            PROCEDURAL_COSMETIC_HOST -> cosmeticPage("", "les-title", "HD")
            DYNAMIC_COSMETIC_HOST -> cosmeticPage("", "user-ad")
            else -> """
                <!doctype html>
                <html><head><title>$BLOCKED_TITLE</title></head>
                <body><script src="$scriptUrl"></script></body></html>
            """.trimIndent()
        }

        private fun cosmeticPage(id: String, cssClass: String, text: String = "Ad"): String = """
            <!doctype html>
            <html><head><title>Checking Candy cosmetics</title></head>
            <body><div id="$id" class="$cssClass">$text</div><script>
            setTimeout(function(){
              var node=document.querySelector('div');
              document.title=getComputedStyle(node).display==='none'?
                '$COSMETIC_HIDDEN_TITLE':'$COSMETIC_VISIBLE_TITLE';
            },750);
            </script></body></html>
        """.trimIndent()

        override fun close() {
            socket.close()
            thread.join(2_000)
        }
    }

    private companion object {
        const val PAGE_HOST = "page.candy.localhost"
        const val ADVANCED_PAGE_HOST = "direct-cloud.localhost"
        const val STATIC_COSMETIC_HOST = "amazon.localhost"
        const val PROCEDURAL_COSMETIC_HOST = "0123movies.localhost"
        const val DYNAMIC_COSMETIC_HOST = "cosmetic.candy.localhost"
        const val TRACKER_HOST = "tracker.ads.localhost"
        const val BLOCK_RULE_ID = "instrumentation-host-block"
        const val ALLOW_RULE_ID = "instrumentation-pair-allow"
        const val BLOCKED_TITLE = "Candy Privacy Blocked"
        const val ALLOWED_TITLE = "Candy Privacy Allowed"
        const val COSMETIC_HIDDEN_TITLE = "Candy Cosmetic Hidden"
        const val COSMETIC_VISIBLE_TITLE = "Candy Cosmetic Visible"
    }
}

private fun org.json.JSONArray.contains(expected: String): Boolean =
    (0 until length()).any { index -> optString(index) == expected }
