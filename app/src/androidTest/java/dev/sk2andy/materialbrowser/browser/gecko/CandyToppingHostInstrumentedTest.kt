package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.userscript.UserScript
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParseResult
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParser
import java.net.InetAddress
import java.net.ServerSocket
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
class CandyToppingHostInstrumentedTest {
    @Test
    fun bundledHostInjectsRegularMatchAndRejectsExcludeAndPrivateSession() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = FixtureServer()
        val manifest = context.assets.open("candy_toppings/manifest.json")
            .bufferedReader()
            .use { reader -> JSONObject(reader.readText()) }
        assertEquals(3, manifest.getInt("manifest_version"))
        assertEquals(
            CandyToppingHostContract.EXTENSION_ID,
            manifest.getJSONObject("browser_specific_settings")
                .getJSONObject("gecko")
                .getString("id"),
        )
        assertTrue(manifest.getJSONArray("optional_permissions").contains("userScripts"))
        assertFalse(manifest.getJSONArray("permissions").contains("userScripts"))

        lateinit var runtime: GeckoRuntimeHandle
        val ready = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            runtime = GeckoRuntimeOwner.getOrCreate(context)
            runtime.toppings.reconcile(listOf(topping(server.url)))
            runtime.toppings.setStateListener { state ->
                if (state != GeckoToppingHostState.Initializing) ready.countDown()
            }
        }

        assertTrue("Candy Topping host did not initialize", ready.await(20, TimeUnit.SECONDS))
        assertEquals(GeckoToppingHostState.Ready, runtime.toppings.state)

        val regularInjected = CountDownLatch(1)
        val excludedStopped = CountDownLatch(1)
        val privateStopped = CountDownLatch(1)
        val excludedTitle = AtomicReference<String?>()
        val privateTitle = AtomicReference<String?>()
        lateinit var regularSession: GeckoBrowserSession
        lateinit var excludedSession: GeckoBrowserSession
        lateinit var privateSession: GeckoBrowserSession
        lateinit var regularView: View
        lateinit var excludedView: View
        lateinit var privateView: View
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            regularSession = runtime.createSession(profileId = "regular", isPrivate = false)
            regularView = regularSession.createView(context)
            regularSession.setStateListener { state ->
                if (state.title == INJECTED_TITLE) regularInjected.countDown()
            }
            excludedSession = runtime.createSession(profileId = "excluded", isPrivate = false)
            excludedView = excludedSession.createView(context)
            excludedSession.setStateListener { state ->
                excludedTitle.set(state.title)
                if (state.lastNavigationSucceeded == true) excludedStopped.countDown()
            }
            privateSession = runtime.createSession(profileId = "private", isPrivate = true)
            privateView = privateSession.createView(context)
            privateSession.setStateListener { state ->
                privateTitle.set(state.title)
                if (state.lastNavigationSucceeded == true) privateStopped.countDown()
            }
            assertTrue(regularSession.loadUrl(server.url))
            assertTrue(excludedSession.loadUrl("${server.url}excluded/page"))
            assertTrue(privateSession.loadUrl(server.url))
        }

        try {
            assertTrue(
                "Registered Gecko user script did not execute",
                regularInjected.await(20, TimeUnit.SECONDS),
            )
            assertTrue("Excluded fixture did not finish", excludedStopped.await(20, TimeUnit.SECONDS))
            assertTrue("Private fixture did not finish", privateStopped.await(20, TimeUnit.SECONDS))
            assertFalse(excludedTitle.get() == INJECTED_TITLE)
            assertFalse(privateTitle.get() == INJECTED_TITLE)

        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                regularSession.releaseView(regularView)
                excludedSession.releaseView(excludedView)
                privateSession.releaseView(privateView)
                regularSession.close()
                excludedSession.close()
                privateSession.close()
            }
            server.close()
        }
    }

    private fun topping(url: String, title: String = INJECTED_TITLE): UserScript {
        val source = """
            // ==UserScript==
            // @name Gecko host fixture
            // @include $url*
            // @exclude ${url}excluded/*
            // @run-at document-start
            // @grant none
            // ==/UserScript==
            document.title = "$title";
        """.trimIndent()
        return (UserScriptParser.parse(id = "gecko-host-fixture", source = source)
            as UserScriptParseResult.Accepted).script
    }

    private class FixtureServer : AutoCloseable {
        private val socket = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${socket.localPort}/"
        private val thread = Thread({ serve() }, "gecko-topping-fixture").apply {
            isDaemon = true
            start()
        }

        private fun serve() {
            repeat(4) {
                runCatching {
                    socket.accept().use { connection ->
                        val reader = connection.getInputStream().bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) {
                            // Consume request headers.
                        }
                        val body = "<html><head><title>Original</title></head><body>fixture</body></html>"
                            .toByteArray()
                        connection.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\n".toByteArray())
                            write("Content-Type: text/html; charset=utf-8\r\n".toByteArray())
                            write("Content-Length: ${body.size}\r\n".toByteArray())
                            write("Connection: close\r\n\r\n".toByteArray())
                            write(body)
                            flush()
                        }
                    }
                }
            }
        }

        override fun close() {
            socket.close()
            thread.join(2_000)
        }
    }

    private companion object {
        const val INJECTED_TITLE = "Candy Topping Injected"
    }
}

private fun org.json.JSONArray.contains(expected: String): Boolean =
    (0 until length()).any { index -> optString(index) == expected }
