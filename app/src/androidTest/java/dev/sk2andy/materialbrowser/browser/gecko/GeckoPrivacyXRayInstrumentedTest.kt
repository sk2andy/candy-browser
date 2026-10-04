package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoPrivacyXRayInstrumentedTest {
    @Test
    fun signedUblockBlocksRequestsWithoutInventingCandyPrivacyObservations() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var runtime: GeckoRuntimeHandle
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            runtime = GeckoRuntimeOwner.getOrCreate(context)
        }
        try {
            val uBlock = awaitExtensionOperation(scope) {
                checkNotNull(runtime.extensions.listInstalled().firstOrNull { it.id == U_BLOCK_ID })
            }
            assertTrue("The fixture requires the enabled bundled uBlock", uBlock.enabled)
            assertFalse(uBlock.isBuiltIn)
            assertFalse(uBlock.temporary)
            assertTrue("uBlock must be Mozilla-signed", uBlock.signedState >= 2)

            FixtureServer().use { server ->
                val events = CopyOnWriteArrayList<GeckoPrivacyEvent>()
                val title = AtomicReference<String>()
                lateinit var session: GeckoBrowserSession
                lateinit var view: View
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    session = runtime.createSession(
                        profileId = "privacy-xray-ubo",
                        isPrivate = false,
                        privacyPolicy = GeckoPrivacyPolicy.Disabled.copy(pageHost = PAGE_HOST),
                        privacyEventSink = GeckoPrivacyEventSink { event -> events += event },
                    )
                    session.bindExtensionTab("privacy-xray-ubo", 1)
                    session.setStateListener { state -> title.set(state.title) }
                    view = session.createView(context)
                    session.setActive(true)
                }
                try {
                    // Installation completes before uBlock finishes initializing its filter engine.
                    // Every attempt uses a fresh URL and an observable allowed-resource control.
                    val blockedPhase = (1..5).firstNotNullOfOrNull { attempt ->
                        val phase = "enabled-$attempt"
                        val result = loadProbe(session, server, title, phase)
                        assertEquals(1, server.hits(phase, CONTROL_PATH))
                        phase.takeIf { result == "$phase:blocked:loaded" }
                    }
                    assertTrue("Bundled uBlock never blocked the packaged EasyList fixture", blockedPhase != null)
                    assertEquals(0, server.hits(checkNotNull(blockedPhase), BLOCKED_PATH))
                    assertTrue("uBlock requests must not be claimed as Candy events", events.none { it.wasBlocked })

                    assertFalse(awaitExtensionOperation(scope) { runtime.extensions.disable(U_BLOCK_ID) }.enabled)
                    assertEquals("disabled:loaded:loaded", loadProbe(session, server, title, "disabled"))
                    assertEquals(1, server.hits("disabled", BLOCKED_PATH))
                    assertEquals(1, server.hits("disabled", CONTROL_PATH))

                    assertTrue(awaitExtensionOperation(scope) { runtime.extensions.enable(U_BLOCK_ID) }.enabled)
                    val restoredPhase = (1..5).firstNotNullOfOrNull { attempt ->
                        val phase = "restored-$attempt"
                        val result = loadProbe(session, server, title, phase)
                        assertEquals(1, server.hits(phase, CONTROL_PATH))
                        phase.takeIf { result == "$phase:blocked:loaded" }
                    }
                    assertTrue("Re-enabling uBlock did not restore filtering", restoredPhase != null)
                    assertEquals(0, server.hits(checkNotNull(restoredPhase), BLOCKED_PATH))

                    InstrumentationRegistry.getInstrumentation().runOnMainSync {
                        assertTrue(session.loadUrl(server.cleanPageUrl()))
                    }
                    assertTrue("Clean navigation did not finish", awaitTitle(title, "clean"))
                    assertTrue("No Candy count may be synthesized on navigation", events.none { it.wasBlocked })
                } finally {
                    try {
                        // Restore the process-wide extension even when the causal control fails.
                        assertTrue(awaitExtensionOperation(scope) { runtime.extensions.enable(U_BLOCK_ID) }.enabled)
                    } finally {
                        InstrumentationRegistry.getInstrumentation().runOnMainSync {
                            session.releaseView(view)
                            session.setActive(false)
                            session.close()
                        }
                    }
                }
            }
        } finally {
            scope.cancel()
        }
    }

    private fun loadProbe(
        session: GeckoBrowserSession,
        server: FixtureServer,
        title: AtomicReference<String>,
        phase: String,
    ): String {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertTrue(session.loadUrl(server.pageUrl(phase)))
        }
        repeat(200) {
            val current = title.get().orEmpty()
            if (current.startsWith("$phase:")) return current
            Thread.sleep(100)
        }
        throw AssertionError("Probe did not finish: phase=$phase; title=${title.get()}")
    }

    private fun awaitTitle(title: AtomicReference<String>, expected: String): Boolean {
        repeat(200) {
            if (title.get() == expected) return true
            Thread.sleep(100)
        }
        return false
    }

    private fun <T> awaitExtensionOperation(
        scope: CoroutineScope,
        operation: suspend () -> T,
    ): T {
        val completed = CountDownLatch(1)
        val result = AtomicReference<T>()
        val failure = AtomicReference<Throwable>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            scope.launch {
                try {
                    result.set(operation())
                } catch (error: Throwable) {
                    failure.set(error)
                } finally {
                    completed.countDown()
                }
            }
        }
        assertTrue("uBlock operation timed out", completed.await(90, TimeUnit.SECONDS))
        failure.get()?.let { error -> throw AssertionError("uBlock operation failed", error) }
        return checkNotNull(result.get())
    }

    private class FixtureServer : AutoCloseable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val requestCounts = ConcurrentHashMap<String, AtomicInteger>()
        private val thread = Thread({ serve() }, "gecko-xray-fixture").apply {
            isDaemon = true
            start()
        }

        fun pageUrl(phase: String) = "http://$PAGE_HOST:${socket.localPort}/?phase=$phase"

        fun cleanPageUrl() = "http://$PAGE_HOST:${socket.localPort}/clean"

        fun hits(phase: String, path: String): Int = requestCounts["$phase:$path"]?.get() ?: 0

        private fun serve() {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { connection ->
                        // Gecko can open speculative TLS connections to the HTTP fixture port.
                        // Bound reads so those connections cannot stall subsequent real requests.
                        connection.soTimeout = 1_000
                        val reader = connection.getInputStream().bufferedReader()
                        val target = reader.readLine().orEmpty().split(' ').getOrNull(1).orEmpty()
                        while (!reader.readLine().isNullOrEmpty()) Unit
                        val path = target.substringBefore('?')
                        val phase = target.substringAfter("phase=", "")
                        requestCounts.computeIfAbsent("$phase:$path") { AtomicInteger() }.incrementAndGet()
                        val isScript = path == BLOCKED_PATH || path == CONTROL_PATH
                        val body = when {
                            isScript -> "/* served by the owned loopback fixture */"
                            path == "/clean" -> "<html><head><title>clean</title></head><body>Clean page</body></html>"
                            else -> page(phase)
                        }.toByteArray()
                        connection.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\n".toByteArray())
                            write(
                                "Content-Type: ${if (isScript) "text/javascript" else "text/html"}; charset=utf-8\r\n"
                                    .toByteArray(),
                            )
                            write("Cache-Control: no-store\r\n".toByteArray())
                            write("Content-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(body)
                            flush()
                        }
                    }
                } catch (error: IOException) {
                    if (socket.isClosed) return
                    // Cancelled navigation or speculative TLS must not stop the HTTP fixture.
                }
            }
        }

        private fun page(phase: String) = """
            <html><head><title>loading</title></head><body>uBlock Privacy X-Ray fixture<script>
              function probe(path) {
                return new Promise(resolve => {
                  const script = document.createElement('script');
                  script.onload = () => resolve('loaded');
                  script.onerror = () => resolve('blocked');
                  script.src = 'http://$REQUEST_HOST:${socket.localPort}' + path + '?phase=$phase';
                  document.head.append(script);
                });
              }
              Promise.all([probe('$BLOCKED_PATH'), probe('$CONTROL_PATH')]).then(results => {
                document.title = '$phase:' + results.join(':');
              });
            </script></body></html>
        """.trimIndent()

        override fun close() {
            socket.close()
            thread.join(2_000)
        }
    }

    private companion object {
        const val U_BLOCK_ID = "uBlock0@raymondhill.net"
        const val PAGE_HOST = "page.candy.localhost"
        const val REQUEST_HOST = "tracker.ads.localhost"
        // Exact rule /ads/cbr.js$script ships inside signed uBlock 1.75.0 EasyList.
        const val BLOCKED_PATH = "/ads/cbr.js"
        const val CONTROL_PATH = "/control.js"
    }
}
