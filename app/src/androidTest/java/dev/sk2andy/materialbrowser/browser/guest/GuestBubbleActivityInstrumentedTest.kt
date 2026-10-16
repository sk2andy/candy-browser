package dev.sk2andy.materialbrowser.browser.guest

import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.sk2andy.materialbrowser.GuestBubbleActivity
import dev.sk2andy.materialbrowser.GuestBubbleCleanupActivity
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses accessibility across the guest process, so no same-process activity harness is involved. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GuestBubbleActivityInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val store = GuestBubbleSessionStore(context)
    private var ownedSessionId: String? = null
    private var previousEnabled: Boolean? = null

    @Before
    fun setUp() {
        assertNull("Never replace an existing guest session", store.state())
        previousEnabled = store.isEnabled()
        assertTrue(store.setEnabled(true))
        assertTrue(store.start())
        ownedSessionId = requireNotNull(store.state()).id
    }

    @After
    fun tearDown() {
        val sessionId = ownedSessionId
        if (sessionId != null) {
            val current = store.state()
            if (current != null) {
                assertEquals("Only erase this test's session", sessionId, current.id)
            }
            stopGuestProcess()
            if (current != null) {
                val directory = File(context.noBackupFilesDir, GuestBubbleRules.SESSION_DIRECTORY_NAME)
                listOf("session", "session.bak", "session.new").forEach { name ->
                    val marker = File(directory, name)
                    if (marker.exists()) assertTrue(marker.delete())
                }
            }
            guestWebViewDirectory().deleteRecursively()
            GuestBubblePublicSurfaces.restore(context)
        }
        previousEnabled?.let { assertTrue(store.setEnabled(it)) }
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            (monitor.getActivitiesInStage(Stage.RESUMED) +
                monitor.getActivitiesInStage(Stage.STARTED) +
                monitor.getActivitiesInStage(Stage.STOPPED))
                .filterIsInstance<MainActivity>()
                .distinct()
                .forEach { it.finish() }
        }
    }

    @Test
    fun guestSessionExposesBrowsingControlsWithoutOwnerControls() {
        launchGuest()
        assertGuestSurface()
        assertEquals(GuestBubblePhase.Active, store.state()?.phase)
    }

    @Test
    fun guestProcessDeathKeepsOwnerLockedAndStartsWithFreshGuestData() {
        launchGuest()
        val first = requireNotNull(store.state())
        val oldData = File(guestWebViewDirectory(), "guest-before-process-death")
        oldData.writeText("Discard after guest process death")
        stopGuestProcess()
        assertTrue(store.isLocked())

        launchOwnerIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
        assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.EditText").enabled(true)), TIMEOUT_MILLIS))
        assertGuestSurface()
        val resumed = requireNotNull(store.state())
        assertEquals(first.id, resumed.id)
        assertFalse(first.guestProcessId == resumed.guestProcessId)
        assertFalse(oldData.exists())
    }

    @Test
    fun launcherIntentWhileGuestActiveReturnsToGuest() {
        launchGuest()
        launchOwnerIntent(
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER),
        )
        assertGuestSurface()
        assertEquals(ownedSessionId, store.state()?.id)
    }

    @Test
    fun externalLinkWhileGuestActiveCannotExposeOwnerBrowser() {
        launchGuest()
        launchOwnerIntent(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.invalid/guest-owner-boundary"))
                .setClass(context, MainActivity::class.java),
        )
        assertGuestSurface()
        assertEquals(ownedSessionId, store.state()?.id)
    }

    @Test
    fun guestNavigationKeepsCookiesAndBrowsingPersistenceSeparateFromOwner() {
        val testId = UUID.randomUUID().toString().replace("-", "")
        val path = "/guest-isolation-$testId"
        val ownerCookie = "owner_$testId=owner_only"
        val guestCookie = "guest_$testId=guest_only"
        val pageText = "Guest cookie isolation $testId"
        CookieFixtureServer(path, guestCookie, pageText).use { server ->
            try {
                setOwnerCookie(server.url, "$ownerCookie; Path=$path; SameSite=Lax")
                launchGuest()
                device.findObject(By.clazz("android.widget.EditText").enabled(true)).apply {
                    text = server.url
                    click()
                }
                device.pressEnter()
                assertTrue("Guest document was not requested", server.firstRequest.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                assertTrue("Guest document did not finish loading", device.wait(Until.hasObject(By.text(pageText)), TIMEOUT_MILLIS))
                assertFalse(server.firstRequestCookies.get().orEmpty().contains(ownerCookie))
                assertFalse(server.confirmationCookies.get().orEmpty().contains(ownerCookie))
                assertTrue("Guest response cookie was not stored in its own jar", server.confirmationCookies.get().orEmpty().contains(guestCookie))

                val ownerCookies = AtomicReference<String>()
                instrumentation.runOnMainSync {
                    ownerCookies.set(CookieManager.getInstance().getCookie(server.url).orEmpty())
                }
                assertTrue(ownerCookies.get().contains(ownerCookie))
                assertFalse(ownerCookies.get().contains(guestCookie))
                val ownerSession = BrowserSessionStore(context)
                assertTrue(ownerSession.loadTabs().first.none { it.url.startsWith(server.url) })
                assertTrue(ownerSession.loadHistory().none { it.url.startsWith(server.url) })
            } finally {
                setOwnerCookie(server.url, "${ownerCookie.substringBefore('=')}=; Max-Age=0; Path=$path")
            }
        }
    }

    @Test
    fun closingSessionStopsGuestProcessAndErasesItsWebViewDataBeforeOwnerReturns() {
        launchGuest()
        val active = requireNotNull(store.state())
        assertNotNull(active.guestProcessId)
        val guestData = guestWebViewDirectory()
        assertTrue(guestData.isDirectory)
        val testFile = File(guestData, "guest-bubble-cleanup-test")
        testFile.writeText("Only guest-session data")
        val guestCache = File(context.cacheDir, "webview_${GuestBubbleRules.WEBVIEW_DATA_SUFFIX}")
        guestCache.mkdirs()
        File(guestCache, "guest-bubble-cache-test").writeText("Only guest cached content")

        // Seed the authenticated transition in this test-owned marker, never a production bypass.
        File(
            File(context.noBackupFilesDir, GuestBubbleRules.SESSION_DIRECTORY_NAME),
            "session",
        ).writeText(GuestBubbleRules.encodeMarker(active.copy(phase = GuestBubblePhase.Closing)))
        context.startActivity(GuestBubbleCleanupActivity.createIntent(context))

        waitUntil("Guest cleanup did not clear its owner lock") { !store.isLocked() }
        assertFalse("Guest WebView data survived cleanup", guestData.exists())
        assertFalse("Guest WebView cache survived cleanup", guestCache.exists())
        waitUntil("Guest process survived cleanup") { guestProcessIds().isEmpty() }
        waitUntil("Owner browser did not resume after cleanup") {
            device.executeShellCommand("dumpsys activity activities").lineSequence().any {
                (it.contains("topResumedActivity") || it.contains("mResumedActivity")) &&
                    it.contains("MainActivity") && it.contains(context.packageName)
            }
        }
    }

    private fun setOwnerCookie(url: String, value: String) {
        val completed = CountDownLatch(1)
        val accepted = AtomicReference<Boolean>()
        instrumentation.runOnMainSync {
            CookieManager.getInstance().setCookie(url, value) {
                accepted.set(it)
                completed.countDown()
            }
        }
        assertTrue("Owner cookie callback timed out", completed.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        assertEquals(true, accepted.get())
    }

    private fun launchGuest() {
        context.startActivity(GuestBubbleActivity.createIntent(context))
        val ready = device.wait(
            Until.hasObject(By.clazz("android.widget.EditText").enabled(true)),
            TIMEOUT_MILLIS,
        )
        if (!ready) device.dumpWindowHierarchy(File(context.cacheDir, "guest-startup-failure.xml"))
        assertTrue(
            "Guest browser did not become ready",
            ready,
        )
        assertGuestSurface()
    }

    private fun launchOwnerIntent(intent: Intent) {
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            assertNotNull("Owner intent was not delivered", monitor.waitForActivityWithTimeout(TIMEOUT_MILLIS))
            instrumentation.waitForIdleSync()
            device.waitForIdle()
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun assertGuestSurface() {
        assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.guest_bubble_title))), TIMEOUT_MILLIS))
        assertTrue(device.hasObject(By.text(context.getString(R.string.guest_bubble_end))))
        assertTrue(device.hasObject(By.text(context.getString(R.string.guest_bubble_address))))
        assertFalse(device.hasObject(By.text(context.getString(R.string.settings_title))))
        assertFalse(device.hasObject(By.desc(context.getString(R.string.cd_add_profile))))
    }

    private fun stopGuestProcess() {
        guestProcessIds().forEach { processId ->
            assertTrue("Never stop the instrumentation process", processId != Process.myPid())
            Process.killProcess(processId)
        }
        waitUntil("Guest process did not stop") { guestProcessIds().isEmpty() }
    }

    private fun guestProcessIds(): List<Int> = context.getSystemService(ActivityManager::class.java)
        .runningAppProcesses.orEmpty()
        .filter { GuestBubbleRules.isGuestProcess(context.packageName, it.processName) }
        .map { it.pid }

    private fun guestWebViewDirectory(): File = File(
        context.applicationInfo.dataDir,
        "app_webview_${GuestBubbleRules.WEBVIEW_DATA_SUFFIX}",
    )

    private fun waitUntil(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (!condition()) {
            assertTrue(message, SystemClock.uptimeMillis() < deadline)
            SystemClock.sleep(50L)
        }
    }

    private class CookieFixtureServer(
        private val path: String,
        private val guestCookie: String,
        private val pageText: String,
    ) : AutoCloseable {
        private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${server.localPort}$path"
        val firstRequest = CountDownLatch(1)
        val firstRequestCookies = AtomicReference<String>()
        val confirmationCookies = AtomicReference<String>()
        private val thread = Thread(::serve, "guest-bubble-cookie-fixture").apply {
            isDaemon = true
            start()
        }

        private fun serve() {
            while (!server.isClosed) {
                try {
                    server.accept().use { connection ->
                        connection.soTimeout = 5_000
                        val reader = connection.getInputStream().bufferedReader()
                        val target = reader.readLine().orEmpty().split(' ').getOrNull(1).orEmpty()
                        var cookieHeader = ""
                        while (true) {
                            val header = reader.readLine() ?: break
                            if (header.isEmpty()) break
                            if (header.substringBefore(':').equals("Cookie", ignoreCase = true)) {
                                cookieHeader = header.substringAfter(':').trim()
                            }
                        }
                        val isDocument = target == path
                        if (isDocument) {
                            firstRequestCookies.compareAndSet(null, cookieHeader)
                            firstRequest.countDown()
                        } else if (target == "$path?confirm") {
                            confirmationCookies.set(cookieHeader)
                        }
                        val body = if (isDocument) {
                            """<!doctype html><html><body><main id="result">Loading</main>
                                <script>fetch('$path?confirm', {credentials:'include'})
                                .then(() => document.getElementById('result').textContent = '$pageText');</script>
                                </body></html>""".trimIndent().toByteArray(Charsets.UTF_8)
                        } else {
                            "OK".toByteArray(Charsets.UTF_8)
                        }
                        connection.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\n".toByteArray())
                            write("Content-Type: text/html; charset=utf-8\r\n".toByteArray())
                            write("Cache-Control: no-store\r\n".toByteArray())
                            if (isDocument) write("Set-Cookie: $guestCookie; Path=$path; SameSite=Lax\r\n".toByteArray())
                            write("Content-Length: ${body.size}\r\n".toByteArray())
                            write("Connection: close\r\n\r\n".toByteArray())
                            write(body)
                            flush()
                        }
                    }
                } catch (_: SocketException) {
                    if (server.isClosed) return
                }
            }
        }

        override fun close() {
            server.close()
            thread.join(2_000L)
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
    }
}
