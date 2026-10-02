package dev.sk2andy.materialbrowser.browser.gecko

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.data.DeveloperSettings
import dev.sk2andy.materialbrowser.browser.HttpsOnlyMode
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoPreferenceController

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(markerClass = [org.mozilla.geckoview.ExperimentalGeckoViewApi::class])
class GeckoLoggingInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun capturesNativeHttpAndPausesBeforePrivateNavigationUntilEveryOwnerReleases() {
        FixtureServer().use { server ->
            val uri = createExportUri()
            val otherOwner = Any()
            lateinit var runtime: GeckoRuntimeHandle
            lateinit var regular: GeckoBrowserSession
            lateinit var regularView: View
            var runtimeReady = false
            var regularReady = false
            var privateSession: GeckoBrowserSession? = null
            var privateView: View? = null
            try {
                instrumentation.runOnMainSync {
                    runtime = GeckoRuntimeOwner.getOrCreate(context)
                    runtimeReady = true
                    runtime.setHttpsOnlyMode(HttpsOnlyMode.Off)
                    GeckoLogging.configure(DeveloperSettings())
                    regular = runtime.createSession("logging-${UUID.randomUUID()}", isPrivate = false)
                    regularView = regular.createView(context)
                    regularReady = true
                }
                assertTrue(runBlocking { GeckoLogging.clear() })
                val racedPause = CountDownLatch(1)
                var racedPauseSucceeded = false
                instrumentation.runOnMainSync {
                    GeckoLogging.configure(DeveloperSettings(geckoLoggingEnabled = true))
                    GeckoLogging.beforePrivateSession(otherOwner) { stopped ->
                        racedPauseSucceeded = stopped
                        racedPause.countDown()
                    }
                }
                assertTrue(racedPause.await(20, TimeUnit.SECONDS))
                assertTrue(racedPauseSucceeded)
                awaitStatus(GeckoLoggingStatus.PausedForPrivateBrowsing)
                instrumentation.runOnMainSync { GeckoLogging.setPrivateBrowsingActive(false, otherOwner) }
                awaitStatus(GeckoLoggingStatus.Recording)
                awaitTitle(regular, "public-capture") { regular.loadUrl(server.url("public-capture")) }
                val publicLogs = exportText(uri)
                assertTrue("Native HTTP logging did not capture public URL", publicLogs.contains("public-capture"))
                assertTrue(publicLogs.contains("nsHttp"))
                awaitStatus(GeckoLoggingStatus.Recording)

                // A user-branch override must not make a successful DEFAULT write falsely acknowledge pause.
                val overrideApplied = CountDownLatch(1)
                var overrideVerified = false
                instrumentation.runOnMainSync {
                    GeckoPreferenceController.setGeckoPref("logging.nsHttp", 4, GeckoPreferenceController.PREF_BRANCH_USER)
                        .accept({
                            GeckoPreferenceController.getGeckoPref("logging.nsHttp").accept({ pref ->
                                overrideVerified = pref?.userValue == 4
                                overrideApplied.countDown()
                            }, { overrideApplied.countDown() })
                        }, { overrideApplied.countDown() })
                }
                assertTrue(overrideApplied.await(20, TimeUnit.SECONDS))
                assertTrue("Gecko USER override was not installed", overrideVerified)
                instrumentation.runOnMainSync {
                    privateSession = runtime.createSession("logging-private-${UUID.randomUUID()}", isPrivate = true)
                    privateView = requireNotNull(privateSession).createView(context)
                    GeckoLogging.setPrivateBrowsingActive(true, otherOwner)
                }
                awaitTitle(requireNotNull(privateSession), "private-secret") {
                    requireNotNull(privateSession).loadUrl(server.url("private-secret"))
                }
                awaitStatus(GeckoLoggingStatus.PausedForPrivateBrowsing)
                assertFalse(exportText(uri).contains("private-secret"))
                instrumentation.runOnMainSync {
                    requireNotNull(privateSession).releaseView(requireNotNull(privateView))
                    requireNotNull(privateSession).close()
                    privateSession = null
                    privateView = null
                }
                awaitStatus(GeckoLoggingStatus.PausedForPrivateBrowsing)
                awaitTitle(regular, "paused-public") { regular.loadUrl(server.url("paused-public")) }
                assertFalse(exportText(uri).contains("paused-public"))
                instrumentation.runOnMainSync { GeckoLogging.setPrivateBrowsingActive(false, otherOwner) }
                awaitStatus(GeckoLoggingStatus.Recording)
                awaitTitle(regular, "resumed-public") { regular.loadUrl(server.url("resumed-public")) }
                assertTrue(exportText(uri).contains("resumed-public"))
                assertTrue(runBlocking { GeckoLogging.clear() })
                assertFalse(exportText(uri).contains("public-capture"))
            } finally {
                instrumentation.runOnMainSync {
                    GeckoLogging.configure(DeveloperSettings())
                    privateView?.let { privateSession?.releaseView(it) }
                    privateSession?.close()
                    if (regularReady) {
                        regular.releaseView(regularView)
                        regular.close()
                    }
                    GeckoLogging.setPrivateBrowsingActive(false, otherOwner)
                    if (runtimeReady) runtime.setHttpsOnlyMode(HttpsOnlyMode.Default)
                }
                runBlocking { GeckoLogging.clear() }
                context.contentResolver.delete(uri, null, null)
            }
        }
    }

    private fun awaitStatus(expected: GeckoLoggingStatus) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < deadline) {
            var matches = false
            instrumentation.runOnMainSync { matches = GeckoLogging.status == expected }
            if (matches) return
            Thread.sleep(50)
        }
        error("Gecko logging did not reach $expected")
    }

    private fun awaitTitle(session: GeckoBrowserSession, title: String, action: () -> Unit) {
        val reached = CountDownLatch(1)
        instrumentation.runOnMainSync {
            session.setStateListener { state -> if (state.title == title) reached.countDown() }
            action()
        }
        assertTrue("Gecko page did not reach $title", reached.await(20, TimeUnit.SECONDS))
    }

    private fun createExportUri(): Uri = requireNotNull(context.contentResolver.insert(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "candy-gecko-logs-${UUID.randomUUID()}.txt")
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        },
    ))

    private fun exportText(uri: Uri): String {
        assertTrue(runBlocking { GeckoLogging.export(context, uri, "Test diagnostics") })
        return requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private class FixtureServer : AutoCloseable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        fun url(path: String) = "http://127.0.0.1:${socket.localPort}/$path"
        private val thread = Thread({
            while (!socket.isClosed) runCatching {
                socket.accept().use { connection ->
                    val reader = connection.getInputStream().bufferedReader()
                    val path = reader.readLine().split(' ')[1].removePrefix("/")
                    while (!reader.readLine().isNullOrEmpty()) { /* Consume headers. */ }
                    val body = "<html><head><title>$path</title></head><body>fixture</body></html>".toByteArray()
                    connection.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nCache-Control: no-store\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(body)
                        flush()
                    }
                }
            }
        }, "gecko-logging-fixture").apply { isDaemon = true; start() }

        override fun close() {
            socket.close()
            thread.join(2_000)
        }
    }
}
