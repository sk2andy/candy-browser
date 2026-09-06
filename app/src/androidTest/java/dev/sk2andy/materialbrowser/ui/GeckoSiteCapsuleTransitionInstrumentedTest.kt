package dev.sk2andy.materialbrowser.ui

import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.gecko.GeckoRuntimeOwner
import dev.sk2andy.materialbrowser.browser.gecko.GeckoToppingHostState
import dev.sk2andy.materialbrowser.capsule.SiteCapsule
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoSiteCapsuleTransitionInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun setUp() {
        clearPreferences()
        GestureOnboardingStore(context).markCompleted()
        BrowserSessionStore(context).saveStartupAnimationEnabled(false)
    }

    @After
    fun tearDown() {
        clearPreferences()
    }

    @Test
    fun openFullCandyKeepsSelectedGeckoPageAndBrowserState() {
        FixtureServer().use { server ->
            awaitGeckoRuntimeReadiness()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var capsule: SiteCapsule
                lateinit var tabId: String
                lateinit var profileId: String
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assertTrue(controller.usesGeckoEngine)
                    profileId = controller.activeProfileId
                    capsule = SiteCapsule(
                        id = "b2082ab1-b0cb-4a2a-92ab-d3487a6d4af1",
                        name = "Gecko Capsule",
                        startUrl = server.url("capsule"),
                        profileId = profileId,
                        createdAtMillis = 1L,
                        updatedAtMillis = 1L,
                    )
                    controller.siteCapsules += capsule
                    assertTrue(controller.openSiteCapsule(capsule.id))
                    tabId = controller.selectedTabId
                }
                awaitCondition(scenario) { controller ->
                    controller.selectedTab.url == capsule.startUrl &&
                        !controller.selectedTab.isLoading
                }
                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().submitAddress(server.url("inside"))
                }
                awaitCondition(scenario) { controller ->
                    controller.selectedTab.url == server.url("inside") &&
                        !controller.selectedTab.isLoading
                }
                val capsuleView = scenario.readActivity { activity ->
                    requireNotNull(
                        activity.browserControllerForTesting().selectedGeckoViewForTesting(),
                    )
                }

                val openFullCandy = scenario.readActivity {
                    it.getString(R.string.capsule_open_full_candy)
                }
                assertTrue("Full Candy action was not clickable", clickNode(openFullCandy))

                awaitCondition(scenario) { controller ->
                    controller.activeCapsuleId == null &&
                        controller.selectedGeckoViewForTesting()?.isAttachedToWindow == true
                }
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assertEquals(tabId, controller.selectedTabId)
                    assertEquals(profileId, controller.selectedTab.profileId)
                    assertEquals(server.url("inside"), controller.selectedTab.url)
                    assertFalse(controller.selectedTab.isIncognito)
                    assertSame(capsuleView, controller.selectedGeckoViewForTesting())
                    controller.goBack()
                }
                awaitCondition(scenario) { controller ->
                    controller.selectedTab.url == capsule.startUrl &&
                        !controller.selectedTab.isLoading
                }
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assertNotNull(controller.selectedGeckoViewForTesting())
                    controller.submitAddress(server.url("full"))
                }
                awaitCondition(scenario) { controller ->
                    controller.selectedTab.url == server.url("full") &&
                        controller.selectedTab.title == "Full Candy" &&
                        !controller.selectedTab.isLoading
                }
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assertEquals(tabId, controller.selectedTabId)
                    assertTrue(controller.selectedTab.canGoBack)
                }
            }
        }
    }

    private fun awaitGeckoRuntimeReadiness() {
        val toppingHostReady = CountDownLatch(1)
        instrumentation.runOnMainSync {
            GeckoRuntimeOwner.getOrCreate(context)
                .toppings
                .setStateListener { state ->
                    if (state == GeckoToppingHostState.Ready) toppingHostReady.countDown()
                }
        }
        assertTrue(
            "Gecko Topping host did not initialize",
            toppingHostReady.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
        )
        instrumentation.waitForIdleSync()
        Thread.sleep(PRIVACY_HOST_SETTLE_MILLIS)
    }

    private fun awaitCondition(
        scenario: ActivityScenario<MainActivity>,
        predicate: (BrowserController) -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            val matched = scenario.readActivity { activity ->
                predicate(activity.browserControllerForTesting())
            }
            if (matched) return
            SystemClock.sleep(POLL_MILLIS)
        }
        throw AssertionError("Timed out waiting for Gecko capsule transition")
    }

    private fun clickNode(text: String): Boolean {
        repeat(NODE_ATTEMPTS) {
            val pending = ArrayDeque<AccessibilityNodeInfo>()
            instrumentation.uiAutomation.rootInActiveWindow?.let(pending::addLast)
            while (pending.isNotEmpty()) {
                val node = pending.removeFirst()
                if (node.text?.toString() == text) {
                    var clickable: AccessibilityNodeInfo? = node
                    while (clickable != null && !clickable.isClickable) clickable = clickable.parent
                    if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                        instrumentation.waitForIdleSync()
                        return true
                    }
                }
                repeat(node.childCount) { index -> node.getChild(index)?.let(pending::addLast) }
            }
            SystemClock.sleep(POLL_MILLIS)
        }
        return false
    }

    private fun clearPreferences() {
        listOf(
            BrowserSessionStore.PREFERENCES_NAME,
            GestureOnboardingStore.PREFERENCES_NAME,
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun <T> ActivityScenario<MainActivity>.readActivity(block: (MainActivity) -> T): T {
        val result = AtomicReference<T>()
        onActivity { activity -> result.set(block(activity)) }
        return result.get()
    }

    private class FixtureServer : AutoCloseable {
        private val socket = ServerSocket(0, REQUEST_BACKLOG, InetAddress.getByName("127.0.0.1"))
        private val baseUrl = "http://127.0.0.1:${socket.localPort}/"
        private val thread = Thread({ serve() }, "gecko-capsule-fixture").apply {
            isDaemon = true
            start()
        }

        fun url(path: String): String = "$baseUrl$path"

        private fun serve() {
            while (!socket.isClosed) {
                runCatching {
                    socket.accept().use { connection ->
                        val reader = connection.getInputStream().bufferedReader()
                        val path = reader.readLine()?.split(' ')?.getOrNull(1).orEmpty()
                        while (!reader.readLine().isNullOrEmpty()) {
                            // Consume request headers.
                        }
                        val title = if (path == "/full") "Full Candy" else "Capsule Page"
                        val body = "<html><head><title>$title</title></head><body>$title</body></html>"
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
            thread.join(1_000L)
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 20_000L
        const val POLL_MILLIS = 50L
        const val NODE_ATTEMPTS = 100
        const val REQUEST_BACKLOG = 4
        const val PRIVACY_HOST_SETTLE_MILLIS = 2_000L
    }
}
