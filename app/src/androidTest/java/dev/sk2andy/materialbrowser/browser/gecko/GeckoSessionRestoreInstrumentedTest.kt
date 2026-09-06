package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommands
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoSessionRestoreInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun regularGeckoSessionRestoresHistoryInSameProfileWhilePrivateStateStaysMemoryOnly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = FixtureServer()
        val profileId = "restore-${UUID.randomUUID()}"
        lateinit var regular: AndroidBrowserEngineSessionPort
        lateinit var regularView: View
        lateinit var privateSession: AndroidBrowserEngineSessionPort
        lateinit var privateView: View

        instrumentation.runOnMainSync {
            regular = session(context, tabId = REGULAR_TAB_ID, profileId = profileId, isPrivate = false)
            regularView = regular.createView(context)
            privateSession = session(
                context,
                tabId = PRIVATE_TAB_ID,
                profileId = profileId,
                isPrivate = true,
            )
            privateView = privateSession.createView(context)
        }
        try {
            execute(regular, BrowserEngineCommands.load(server.url("root")))
            assertTrue(await { regular.historyUrlAtOffset(0) == server.url("root") })
            execute(regular, BrowserEngineCommands.load(server.url("second")))
            assertTrue(await { regular.historyUrlAtOffset(-1) == server.url("root") })
            val snapshot = requireNotNull(regular.sessionStateSnapshot())

            execute(privateSession, BrowserEngineCommands.load(server.url("private")))
            assertTrue(await { privateSession.historyUrlAtOffset(0) == server.url("private") })
            assertEquals(null, privateSession.sessionStateSnapshot())

            instrumentation.runOnMainSync {
                regular.releaseView(regularView)
                regular.execute(BrowserEngineCommands.close())
            }

            lateinit var restored: AndroidBrowserEngineSessionPort
            lateinit var restoredView: View
            instrumentation.runOnMainSync {
                restored = session(
                    context,
                    tabId = REGULAR_TAB_ID,
                    profileId = profileId,
                    isPrivate = false,
                )
                restoredView = restored.createView(context)
                assertTrue(restored.restoreSessionState(snapshot))
            }
            try {
                assertTrue(await { restored.historyUrlAtOffset(-1) == server.url("root") })
                execute(restored, BrowserEngineCommands.back())
                assertTrue(await { restored.historyUrlAtOffset(0) == server.url("root") })
                assertFalse(restored.sessionStateSnapshot().isNullOrBlank())
            } finally {
                instrumentation.runOnMainSync {
                    restored.releaseView(restoredView)
                    restored.execute(BrowserEngineCommands.close())
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                privateSession.releaseView(privateView)
                privateSession.execute(BrowserEngineCommands.close())
            }
            server.close()
        }
    }

    private fun session(
        context: Context,
        tabId: String,
        profileId: String,
        isPrivate: Boolean,
    ): AndroidBrowserEngineSessionPort = GeckoBrowserEngineSessionFactory(context).create(
        tabId = tabId,
        profileId = profileId,
        isPrivate = isPrivate,
        eventSink = BrowserEngineEventSink { },
    )

    private fun execute(
        session: AndroidBrowserEngineSessionPort,
        command: dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommand,
    ) = instrumentation.runOnMainSync { session.execute(command) }

    private fun await(predicate: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return true
            SystemClock.sleep(POLL_MILLIS)
        }
        return predicate()
    }

    private class FixtureServer : AutoCloseable {
        private val socket = ServerSocket(0, REQUEST_BACKLOG, InetAddress.getByName("127.0.0.1"))
        private val baseUrl = "http://127.0.0.1:${socket.localPort}/"
        private val thread = Thread({ serve() }, "gecko-session-restore-fixture").apply {
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
                        while (true) {
                            val header = reader.readLine() ?: break
                            if (header.isEmpty()) break
                        }
                        val body = "<title>${path.trim('/')}</title>".toByteArray()
                        connection.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\n".toByteArray())
                            write("Content-Type: text/html; charset=utf-8\r\n".toByteArray())
                            write("Content-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
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
        const val REGULAR_TAB_ID = "00000000-0000-0000-0000-000000000981"
        const val PRIVATE_TAB_ID = "00000000-0000-0000-0000-000000000982"
        const val POLL_MILLIS = 25L
        const val REQUEST_BACKLOG = 4
        const val TIMEOUT_MILLIS = 20_000L
    }
}
