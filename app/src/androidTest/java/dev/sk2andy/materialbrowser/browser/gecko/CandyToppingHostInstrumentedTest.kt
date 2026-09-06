package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.userscript.UserScript
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptMenuCommand
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptOpenTabRequest
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParseResult
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParser
import dev.sk2andy.materialbrowser.data.UserScriptValueStore
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
        val menuPublished = CountDownLatch(1)
        val openedTab = CountDownLatch(1)
        val menuCommand = AtomicReference<UserScriptMenuCommand?>()
        val openTabRequest = AtomicReference<UserScriptOpenTabRequest?>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            runtime = GeckoRuntimeOwner.getOrCreate(context)
            runtime.toppings.clearValues(SCRIPT_ID)
            runtime.toppings.setInteractionDelegate(
                object : GeckoToppingInteractionDelegate {
                    override fun onMenuCommandsChanged(
                        tabId: String,
                        commands: List<UserScriptMenuCommand>,
                    ) {
                        commands.singleOrNull()?.let { command ->
                            menuCommand.set(command)
                            menuPublished.countDown()
                        }
                    }

                    override fun onOpenTab(request: UserScriptOpenTabRequest) {
                        openTabRequest.set(request)
                        openedTab.countDown()
                    }
                },
            )
            runtime.toppings.reconcile(listOf(topping(server.url)))
            runtime.toppings.setStateListener { state ->
                if (state != GeckoToppingHostState.Initializing) ready.countDown()
            }
        }

        assertTrue("Candy Topping host did not initialize", ready.await(20, TimeUnit.SECONDS))
        assertEquals(GeckoToppingHostState.Ready, runtime.toppings.state)

        val regularInjected = CountDownLatch(1)
        val menuInvoked = CountDownLatch(1)
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
                if (state.title == MENU_INVOKED_TITLE) menuInvoked.countDown()
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
            regularSession.setActive(true)
            excludedSession.setActive(false)
            privateSession.setActive(false)
            runtime.toppings.setActiveTab(CANDY_TAB_ID)
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
            assertEquals(
                "\"$INJECTED_TITLE\"",
                UserScriptValueStore(context).snapshot(SCRIPT_ID)[VALUE_KEY],
            )
            assertTrue(
                "Active Gecko tab did not publish its Topping menu command",
                menuPublished.await(20, TimeUnit.SECONDS),
            )
            assertEquals(
                UserScriptMenuCommand(
                    tabId = CANDY_TAB_ID,
                    scriptId = SCRIPT_ID,
                    scriptName = SCRIPT_NAME,
                    commandId = "1",
                    caption = MENU_CAPTION,
                ),
                menuCommand.get(),
            )
            assertTrue(
                "GM_openInTab did not reach the active Candy tab delegate",
                openedTab.await(20, TimeUnit.SECONDS),
            )
            assertEquals(
                UserScriptOpenTabRequest(
                    tabId = CANDY_TAB_ID,
                    scriptId = SCRIPT_ID,
                    url = "${server.url}opened",
                    active = false,
                ),
                openTabRequest.get(),
            )

            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                runtime.toppings.invokeMenuCommand(checkNotNull(menuCommand.get()))
            }
            assertTrue(
                "Topping menu callback did not execute in its Gecko user-script world",
                menuInvoked.await(20, TimeUnit.SECONDS),
            )

        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                runtime.toppings.setActiveTab(null)
                runtime.toppings.setInteractionDelegate(GeckoToppingInteractionDelegate.None)
                runtime.toppings.clearValues(SCRIPT_ID)
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
            // @name $SCRIPT_NAME
            // @include $url*
            // @exclude ${url}excluded/*
            // @run-at document-start
            // @grant GM_getValue
            // @grant GM_setValue
            // @grant GM_registerMenuCommand
            // @grant GM_openInTab
            // @grant GM_getResourceText
            // @require https://cdn.jsdelivr.net/npm/candy-fixture@1/require.js
            // @resource fixture https://cdn.jsdelivr.net/npm/candy-fixture@1/resource.txt
            // ==/UserScript==
            const dependencyReady = globalThis.candyRequiredFixture === "ready";
            const resourceReady = GM_getResourceText("fixture") === "$RESOURCE_TEXT";
            const injectedTitle = dependencyReady && resourceReady ? "$title" : "$DEPENDENCY_FAILED_TITLE";
            GM.setValue("$VALUE_KEY", injectedTitle).then(() => {
              document.title = GM_getValue("$VALUE_KEY", "missing");
              GM_registerMenuCommand("$MENU_CAPTION", () => {
                document.title = "$MENU_INVOKED_TITLE";
              });
              GM_openInTab("${url}opened", { active: false });
            });
        """.trimIndent()
        val parsed = (UserScriptParser.parse(id = SCRIPT_ID, source = source)
            as UserScriptParseResult.Accepted).script
        return parsed.copy(
            requires = parsed.requires.map { dependency ->
                dependency.copy(source = "globalThis.candyRequiredFixture = 'ready';")
            },
            resources = parsed.resources.map { resource ->
                resource.copy(
                    encodedContent = java.util.Base64.getEncoder()
                        .encodeToString(RESOURCE_TEXT.toByteArray()),
                    mimeType = "text/plain",
                )
            },
        )
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
        const val CANDY_TAB_ID = "candy-fixture-tab"
        const val INJECTED_TITLE = "Candy Topping Injected"
        const val MENU_CAPTION = "Candy fixture menu"
        const val MENU_INVOKED_TITLE = "Candy Topping Menu Invoked"
        const val DEPENDENCY_FAILED_TITLE = "Candy Topping Dependency Failed"
        const val RESOURCE_TEXT = "Candy resource"
        const val SCRIPT_ID = "gecko-host-fixture"
        const val SCRIPT_NAME = "Gecko host fixture"
        const val VALUE_KEY = "bridge-probe"
    }
}

private fun org.json.JSONArray.contains(expected: String): Boolean =
    (0 until length()).any { index -> optString(index) == expected }
