package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.engine.BrowserWebContentColorScheme
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoWebContentThemeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun preferredColorSchemeChangesReachExistingWebsitesAfterReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = ThemeFixtureServer()
        val title = AtomicReference<String>()
        lateinit var runtime: GeckoRuntimeHandle
        lateinit var session: GeckoBrowserSession
        lateinit var view: View
        instrumentation.runOnMainSync {
            runtime = GeckoRuntimeOwner.getOrCreate(context)
            runtime.setWebContentColorScheme(BrowserWebContentColorScheme.Light)
            session = runtime.createSession(
                profileId = "theme-${UUID.randomUUID()}",
                isPrivate = false,
            )
            view = session.createView(context)
            session.setStateListener { state -> title.set(state.title) }
            session.setActive(true)
            assertTrue(session.loadUrl(server.url))
        }

        try {
            assertTrue(awaitTitle(title, LIGHT_TITLE))

            instrumentation.runOnMainSync {
                runtime.setWebContentColorScheme(BrowserWebContentColorScheme.Dark)
                session.reload()
            }

            assertTrue(awaitTitle(title, DARK_TITLE))
            assertEquals(DARK_TITLE, title.get())
        } finally {
            instrumentation.runOnMainSync {
                runtime.setWebContentColorScheme(BrowserWebContentColorScheme.System)
                session.releaseView(view)
                session.setActive(false)
                session.close()
            }
            server.close()
        }
    }

    @Test
    fun systemPreferredColorSchemeFollowsRuntimeConfigurationChanges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalConfiguration = Configuration(context.resources.configuration)
        val server = ThemeFixtureServer()
        val title = AtomicReference<String>()
        lateinit var runtime: GeckoRuntimeHandle
        lateinit var session: GeckoBrowserSession
        lateinit var view: View
        instrumentation.runOnMainSync {
            runtime = GeckoRuntimeOwner.getOrCreate(context)
            runtime.setWebContentColorScheme(BrowserWebContentColorScheme.System)
            runtime.onConfigurationChanged(
                originalConfiguration.withNightMode(Configuration.UI_MODE_NIGHT_NO),
            )
            session = runtime.createSession(
                profileId = "theme-system-${UUID.randomUUID()}",
                isPrivate = false,
            )
            view = session.createView(context)
            session.setStateListener { state -> title.set(state.title) }
            session.setActive(true)
            assertTrue(session.loadUrl(server.url))
        }

        try {
            assertTrue(awaitTitle(title, LIGHT_TITLE))

            instrumentation.runOnMainSync {
                runtime.onConfigurationChanged(
                    originalConfiguration.withNightMode(Configuration.UI_MODE_NIGHT_YES),
                )
                session.reload()
            }

            assertTrue(awaitTitle(title, DARK_TITLE))
            assertEquals(DARK_TITLE, title.get())
        } finally {
            instrumentation.runOnMainSync {
                runtime.onConfigurationChanged(originalConfiguration)
                runtime.setWebContentColorScheme(BrowserWebContentColorScheme.System)
                session.releaseView(view)
                session.setActive(false)
                session.close()
            }
            server.close()
        }
    }

    private fun awaitTitle(title: AtomicReference<String>, expected: String): Boolean {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (title.get() == expected) return true
            SystemClock.sleep(POLL_MILLIS)
        }
        return title.get() == expected
    }

    private fun Configuration.withNightMode(nightMode: Int): Configuration =
        Configuration(this).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }

    private class ThemeFixtureServer : AutoCloseable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val thread = Thread({ serve() }, "gecko-theme-fixture").apply {
            isDaemon = true
            start()
        }

        val url = "http://127.0.0.1:${socket.localPort}/"

        private fun serve() {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { connection ->
                        val reader = connection.getInputStream().bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) {
                            // Consume request headers.
                        }
                        val body = """
                            <!doctype html><html><head><script>
                            const scheme = matchMedia('(prefers-color-scheme: dark)');
                            const publishScheme = () => {
                                document.title = scheme.matches ? '$DARK_TITLE' : '$LIGHT_TITLE';
                            };
                            scheme.addEventListener('change', publishScheme);
                            publishScheme();
                            </script></head><body>theme probe</body></html>
                        """.trimIndent().toByteArray()
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
            thread.join(1_000L)
        }
    }

    private companion object {
        const val LIGHT_TITLE = "theme-light"
        const val DARK_TITLE = "theme-dark"
        const val TIMEOUT_MILLIS = 20_000L
        const val POLL_MILLIS = 50L
    }
}
