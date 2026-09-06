package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CopyOnWriteArrayList
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension

@RunWith(AndroidJUnit4::class)
class GeckoExtensionChromeInstrumentedTest {
    @Test
    fun builtInFixtureExercisesActionsPopupOptionsTabsAndPublicApis() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = FixtureServer()
        val installedLatch = CountDownLatch(1)
        val actionLatch = CountDownLatch(1)
        val popupLatch = CountDownLatch(1)
        val optionsLatch = CountDownLatch(1)
        val createdLatch = CountDownLatch(1)
        val updatedLatch = CountDownLatch(1)
        val closedLatch = CountDownLatch(1)
        val conformanceLatch = CountDownLatch(1)
        val conformance = AtomicReference<JSONObject>()
        val phases = CopyOnWriteArrayList<String>()
        val actionKey = AtomicReference<GeckoExtensionActionKey>()
        lateinit var runtime: GeckoViewRuntimeHandle
        lateinit var extension: WebExtension
        lateinit var host: FixtureChromeHost
        lateinit var primary: GeckoBrowserSession
        lateinit var primaryView: android.view.View

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            runtime = GeckoRuntimeOwner.getOrCreate(context) as GeckoViewRuntimeHandle
            runtime.ensureBuiltInExtensionFixture()
                .withHandler(Handler(Looper.getMainLooper()))
                .accept(
                    { installed ->
                        extension = checkNotNull(installed)
                        installedLatch.countDown()
                    },
                    { error -> throw AssertionError("Fixture installation failed", error) },
                )
        }
        assertTrue(installedLatch.await(20, TimeUnit.SECONDS))

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            extension.setMessageDelegate(
                object : WebExtension.MessageDelegate {
                    override fun onMessage(
                        nativeApp: String,
                        message: Any,
                        sender: WebExtension.MessageSender,
                    ): GeckoResult<Any> {
                        val payload = message as? JSONObject
                        if (nativeApp == NATIVE_APP && payload?.optString("type") == "conformance") {
                            conformance.set(payload.optJSONObject("results"))
                            conformanceLatch.countDown()
                        }
                        if (nativeApp == NATIVE_APP && payload?.optString("type") == "phase") {
                            phases += payload.optString("phase")
                        }
                        return GeckoResult.fromValue(JSONObject().put("accepted", true))
                    }
                },
                NATIVE_APP,
            )
            host = FixtureChromeHost(
                context = context,
                runtime = runtime,
                actionLatch = actionLatch,
                popupLatch = popupLatch,
                optionsLatch = optionsLatch,
                createdLatch = createdLatch,
                updatedLatch = updatedLatch,
                closedLatch = closedLatch,
                actionKey = actionKey,
            )
            runtime.extensions.setChromeHost(host)
            primary = runtime.createSession(
                profileId = PROFILE_ID,
                isolationEnabled = false,
                isPrivate = false,
            )
            host.bind(PRIMARY_TAB_ID, primary, generation = 1, selected = true)
            primaryView = primary.createView(context)
            primary.setActive(true)
            assertTrue(primary.loadUrl(server.pageUrl()))
        }

        try {
            assertTrue("Browser action not published", actionLatch.await(20, TimeUnit.SECONDS))
            assertTrue("Browser action did not become clickable", awaitActionClick(runtime, actionKey))
            assertTrue("Popup not routed", popupLatch.await(20, TimeUnit.SECONDS))
            assertTrue("Options page not routed", optionsLatch.await(20, TimeUnit.SECONDS))
            assertTrue(
                "API conformance message missing; phases=$phases",
                conformanceLatch.await(20, TimeUnit.SECONDS),
            )
            val results = checkNotNull(conformance.get())
            assertFalse("Fixture conformance failed before delegate assertions: $results", results.has("error"))
            assertTrue("tabs.create not delegated", createdLatch.await(20, TimeUnit.SECONDS))
            assertTrue("tabs.update not delegated", updatedLatch.await(20, TimeUnit.SECONDS))
            assertTrue("tabs.remove not delegated", closedLatch.await(20, TimeUnit.SECONDS))
            assertEquals(0, host.createdIndex)
            assertEquals(null, host.updatedMuted)
            listOf(
                "tabs",
                "webNavigation",
                "scripting",
                "css",
                "storage",
                "runtimeMessaging",
                "tabLifecycle",
                "downloads",
            ).forEach { api -> assertTrue("Fixture API failed: $api; $results", results.optBoolean(api)) }
            assertFalse(results.has("error"))
            assertNull("Popup session survived owner-tab switch", host.popupView)
            assertEquals("moz-extension", host.optionsUrl?.substringBefore("://"))
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                runtime.extensions.setChromeHost(null)
                extension.setMessageDelegate(null, NATIVE_APP)
                host.close()
                primary.releaseView(primaryView)
                primary.close()
            }
            server.close()
        }
    }

    private fun awaitActionClick(
        runtime: GeckoViewRuntimeHandle,
        actionKey: AtomicReference<GeckoExtensionActionKey>,
    ): Boolean {
        repeat(50) {
            var clicked = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                clicked = actionKey.get()?.let(runtime.extensions::clickChromeAction) == true
            }
            if (clicked) return true
            Thread.sleep(100)
        }
        return false
    }

    private class FixtureChromeHost(
        private val context: Context,
        private val runtime: GeckoViewRuntimeHandle,
        private val actionLatch: CountDownLatch,
        private val popupLatch: CountDownLatch,
        private val optionsLatch: CountDownLatch,
        private val createdLatch: CountDownLatch,
        private val updatedLatch: CountDownLatch,
        private val closedLatch: CountDownLatch,
        private val actionKey: AtomicReference<GeckoExtensionActionKey>,
    ) : GeckoExtensionChromeHost, AutoCloseable {
        private data class Bound(
            val identity: GeckoExtensionSessionIdentity,
            val session: GeckoBrowserSession,
        )

        private val sessions = linkedMapOf<String, Bound>()
        private val tabOrder = mutableListOf<String>()
        private var selectedTabId: String? = null
        private var nextTab = 0
        var popupView: GeckoView? = null
            private set
        var optionsUrl: String? = null
            private set
        var createdIndex: Int? = null
            private set
        var updatedMuted: Boolean? = null
            private set
        private var optionsView: android.view.View? = null
        private var optionsSession: GeckoBrowserSession? = null

        fun bind(
            tabId: String,
            session: GeckoBrowserSession,
            generation: Long,
            selected: Boolean,
        ) {
            val identity = GeckoExtensionSessionIdentity(
                tabId = tabId,
                profileId = PROFILE_ID,
                isPrivate = false,
                generation = generation,
            )
            sessions[tabId] = Bound(identity, session)
            if (tabId !in tabOrder) tabOrder += tabId
            session.bindExtensionTab(tabId, generation)
            if (selected) {
                selectedTabId = tabId
                runtime.extensions.onSelectedChromeSessionChanged()
            }
        }

        override fun currentSessionIdentity(): GeckoExtensionSessionIdentity? =
            selectedTabId?.let(sessions::get)?.identity

        override fun isCurrentSession(identity: GeckoExtensionSessionIdentity): Boolean =
            sessions[identity.tabId]?.identity == identity

        override fun createTab(
            request: GeckoExtensionCreateTabRequest,
            session: GeckoSession,
        ): String {
            val tabId = "fixture-created-${++nextTab}"
            val adopted = checkNotNull(
                runtime.adoptExtensionSession(
                    session = session,
                    profileId = PROFILE_ID,
                    isPrivate = request.source?.isPrivate == true,
                ),
            )
            bind(tabId, adopted, generation = 1, selected = request.active)
            request.index?.let { requestedIndex ->
                tabOrder.remove(tabId)
                tabOrder.add(requestedIndex.coerceIn(0, tabOrder.size), tabId)
            }
            createdIndex = tabOrder.indexOf(tabId)
            createdLatch.countDown()
            return tabId
        }

        override fun updateTab(request: GeckoExtensionUpdateTabRequest): Boolean {
            if (!isCurrentSession(request.target)) return false
            updatedMuted = request.muted
            updatedLatch.countDown()
            return true
        }

        override fun closeTab(target: GeckoExtensionSessionIdentity): Boolean {
            val bound = sessions.remove(target.tabId) ?: return false
            tabOrder.remove(target.tabId)
            bound.session.close()
            if (selectedTabId == target.tabId) selectedTabId = PRIMARY_TAB_ID
            closedLatch.countDown()
            return true
        }

        override fun openPopup(
            popup: GeckoExtensionPopupIdentity,
            session: GeckoSession,
            toggle: Boolean,
        ): Boolean {
            popupView = GeckoView(context).also { view -> view.setSession(session) }
            popupLatch.countDown()
            return true
        }

        override fun closePopup(popup: GeckoExtensionPopupIdentity) {
            popupView?.releaseSession()
            popupView = null
        }

        override fun openOptionsPage(
            extensionId: String,
            owner: GeckoExtensionSessionIdentity,
            url: String,
            openInTab: Boolean,
        ): String {
            optionsUrl = url
            val tabId = "fixture-options-${++nextTab}"
            val createdOptionsSession = runtime.createSession(
                profileId = PROFILE_ID,
                isolationEnabled = false,
                isPrivate = false,
            )
            optionsSession = createdOptionsSession
            bind(tabId, createdOptionsSession, 1, selected = true)
            optionsView = createdOptionsSession.createView(context)
            check(createdOptionsSession.loadExtensionUrl(url))
            optionsLatch.countDown()
            return tabId
        }

        override fun onActionsChanged(actions: List<GeckoExtensionActionState>) {
            actions.firstOrNull { action ->
                action.key.extensionId == FIXTURE_ID &&
                    action.key.kind == GeckoExtensionActionKind.Browser &&
                    action.key.tabId == PRIMARY_TAB_ID
            }?.let { action ->
                actionKey.set(action.key)
                actionLatch.countDown()
            }
        }

        override fun close() {
            popupView?.releaseSession()
            popupView = null
            optionsView?.let { view -> optionsSession?.releaseView(view) }
            optionsView = null
            optionsSession = null
            sessions.values.toList().forEach { bound -> bound.session.close() }
            sessions.clear()
            tabOrder.clear()
        }
    }

    private class FixtureServer : AutoCloseable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val thread = Thread({ serve() }, "extension-chrome-fixture").apply {
            isDaemon = true
            start()
        }

        fun pageUrl(): String = "http://google.candy.localhost:${socket.localPort}/page"

        private fun serve() {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { connection ->
                        val reader = connection.getInputStream().bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) Unit
                        val body = "<html><head><title>fixture</title></head><body>fixture</body></html>"
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
                } catch (error: SocketException) {
                    if (socket.isClosed) return
                }
            }
        }

        override fun close() {
            socket.close()
            thread.join(2_000)
        }
    }

    private companion object {
        const val FIXTURE_ID = "candy-firefox-fixture@sk2andy.dev"
        const val NATIVE_APP = "browser"
        const val PROFILE_ID = "extension-chrome"
        const val PRIMARY_TAB_ID = "primary"
    }
}
