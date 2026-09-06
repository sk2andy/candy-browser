package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoBrowsingDataInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun runtimeCookieClearCoversIsolatedContextsAndCacheClearCompletes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = FixtureServer()
        lateinit var runtime: GeckoRuntimeHandle
        lateinit var firstSession: GeckoBrowserSession
        lateinit var secondSession: GeckoBrowserSession
        lateinit var firstView: View
        lateinit var secondView: View
        instrumentation.runOnMainSync {
            runtime = GeckoRuntimeOwner.getOrCreate(context)
            firstSession = runtime.createSession(
                profileId = "clear-first-${UUID.randomUUID()}",
                isolationEnabled = true,
                isPrivate = false,
            )
            secondSession = runtime.createSession(
                profileId = "clear-second-${UUID.randomUUID()}",
                isolationEnabled = true,
                isPrivate = false,
            )
            firstView = firstSession.createView(context)
            secondView = secondSession.createView(context)
        }

        try {
            awaitTitle(firstSession, COOKIE_MISSING) { firstSession.loadUrl(server.url) }
            awaitTitle(secondSession, COOKIE_MISSING) { secondSession.loadUrl(server.url) }
            awaitTitle(firstSession, COOKIE_PRESENT, firstSession::reload)
            awaitTitle(secondSession, COOKIE_PRESENT, secondSession::reload)

            assertTrue(clear(runtime, GeckoBrowsingData.Cookies))

            awaitTitle(firstSession, COOKIE_MISSING, firstSession::reload)
            awaitTitle(secondSession, COOKIE_MISSING, secondSession::reload)
            assertTrue(clear(runtime, GeckoBrowsingData.AllCaches))
        } finally {
            runCatching { clear(runtime, GeckoBrowsingData.Cookies) }
            instrumentation.runOnMainSync {
                firstSession.releaseView(firstView)
                secondSession.releaseView(secondView)
                firstSession.close()
                secondSession.close()
            }
            server.close()
        }
    }

    private fun awaitTitle(
        session: GeckoBrowserSession,
        expected: String,
        action: () -> Unit,
    ) {
        val reached = CountDownLatch(1)
        instrumentation.runOnMainSync {
            session.setStateListener { state ->
                if (state.title == expected) reached.countDown()
            }
            action()
        }
        assertTrue(
            "Gecko page did not reach title $expected",
            reached.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
    }

    private fun clear(runtime: GeckoRuntimeHandle, data: GeckoBrowsingData): Boolean {
        val completed = CountDownLatch(1)
        val succeeded = AtomicBoolean(false)
        instrumentation.runOnMainSync {
            runtime.clearBrowsingData(data) { result ->
                succeeded.set(result)
                completed.countDown()
            }
        }
        assertTrue(
            "Gecko browsing-data clear did not complete",
            completed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
        return succeeded.get()
    }

    private class FixtureServer : AutoCloseable {
        private val socket = ServerSocket(0, REQUEST_BACKLOG, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${socket.localPort}/"
        private val thread = Thread({ serve() }, "gecko-clear-data-fixture").apply {
            isDaemon = true
            start()
        }

        private fun serve() {
            while (!socket.isClosed) {
                runCatching {
                    socket.accept().use { connection ->
                        val reader = connection.getInputStream().bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) {
                            // Consume request headers.
                        }
                        val body = """
                            <html><head><script>
                            const present = document.cookie.includes('candy_clear=present');
                            if (!present) document.cookie = 'candy_clear=present; path=/';
                            document.title = present ? '$COOKIE_PRESENT' : '$COOKIE_MISSING';
                            </script></head><body>fixture</body></html>
                        """.trimIndent().toByteArray()
                        connection.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\n".toByteArray())
                            write("Content-Type: text/html; charset=utf-8\r\n".toByteArray())
                            write("Cache-Control: public, max-age=3600\r\n".toByteArray())
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
        const val COOKIE_MISSING = "Cookie missing"
        const val COOKIE_PRESENT = "Cookie present"
        const val REQUEST_BACKLOG = 4
        const val TIMEOUT_SECONDS = 20L
    }
}
