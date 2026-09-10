package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.WebRtcProtectionMode
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoWebRtcProtectionInstrumentedTest {
    @Test
    fun protectedModesPreventDirectIceCandidatesAndPeerConnections() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = WebRtcFixtureServer()
        lateinit var runtime: GeckoRuntimeHandle
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            runtime = GeckoRuntimeOwner.getOrCreate(context)
        }

        try {
            setMode(runtime, WebRtcProtectionMode.ProtectIpAddresses)
            assertPageTitle(
                context = context,
                runtime = runtime,
                server = server,
                path = "/ice",
                expectedTitle = PROTECTED_TITLE,
                profileId = "webrtc-proxy-only",
            )
            setMode(runtime, WebRtcProtectionMode.Block)
            assertPageTitle(
                context = context,
                runtime = runtime,
                server = server,
                path = "/availability",
                expectedTitle = BLOCKED_TITLE,
                profileId = "webrtc-blocked",
            )
            setMode(runtime, WebRtcProtectionMode.Standard)
            assertPageTitle(
                context = context,
                runtime = runtime,
                server = server,
                path = "/availability",
                expectedTitle = AVAILABLE_TITLE,
                profileId = "webrtc-standard",
            )
        } finally {
            setMode(runtime, WebRtcProtectionMode.ProtectIpAddresses)
            server.close()
        }
    }

    private fun setMode(runtime: GeckoRuntimeHandle, mode: WebRtcProtectionMode) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            runtime.setWebRtcProtectionMode(mode)
        }
    }

    private fun assertPageTitle(
        context: Context,
        runtime: GeckoRuntimeHandle,
        server: WebRtcFixtureServer,
        path: String,
        expectedTitle: String,
        profileId: String,
    ) {
        val titleReached = CountDownLatch(1)
        val finalState = AtomicReference<GeckoBrowserSessionState>()
        lateinit var session: GeckoBrowserSession
        lateinit var view: android.view.View
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            session = runtime.createSession(
                profileId = profileId,
                isPrivate = false,
                privacyPolicy = GeckoPrivacyPolicy.Disabled.copy(pageHost = PAGE_HOST),
            )
            view = session.createView(context)
            session.setStateListener { state ->
                finalState.set(state)
                if (state.title == expectedTitle) titleReached.countDown()
            }
            session.setActive(true)
            assertTrue(session.loadUrl(server.pageUrl(path)))
        }
        try {
            assertTrue(
                "WebRTC fixture did not reach $expectedTitle; state=${finalState.get()}",
                titleReached.await(20, TimeUnit.SECONDS),
            )
            assertEquals(expectedTitle, finalState.get().title)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                session.releaseView(view)
                session.setActive(false)
                session.close()
            }
        }
    }

    private class WebRtcFixtureServer : AutoCloseable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val thread = Thread({ serve() }, "gecko-webrtc-fixture").apply {
            isDaemon = true
            start()
        }

        fun pageUrl(path: String) = "http://$PAGE_HOST:${socket.localPort}$path"

        private fun serve() {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { connection ->
                        val reader = connection.getInputStream().bufferedReader()
                        val requestLine = reader.readLine().orEmpty()
                        while (true) {
                            val header = reader.readLine() ?: break
                            if (header.isEmpty()) break
                        }
                        val body = if (requestLine.contains(" /ice ")) icePage() else availabilityPage()
                        val bytes = body.toByteArray()
                        connection.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\n".toByteArray())
                            write("Content-Type: text/html; charset=utf-8\r\n".toByteArray())
                            write("Content-Length: ${bytes.size}\r\n".toByteArray())
                            write("Connection: close\r\n\r\n".toByteArray())
                            write(bytes)
                            flush()
                        }
                    }
                } catch (error: SocketException) {
                    if (socket.isClosed) return
                }
            }
        }

        private fun availabilityPage(): String = """
            <!doctype html><html><head><title>Checking WebRTC</title></head><body><script>
              try {
                const connection = new RTCPeerConnection();
                connection.close();
                document.title = '$AVAILABLE_TITLE';
              } catch (error) {
                document.title = '$BLOCKED_TITLE';
              }
            </script></body></html>
        """.trimIndent()

        private fun icePage(): String = """
            <!doctype html><html><head><title>Checking ICE</title></head><body><script>
              (async () => {
                try {
                  const candidates = [];
                  const connection = new RTCPeerConnection({ iceServers: [] });
                  connection.createDataChannel('probe');
                  connection.onicecandidate = event => {
                    if (event.candidate?.candidate) candidates.push(event.candidate.candidate);
                    if (event.candidate === null) finish();
                  };
                  const finish = () => {
                    connection.close();
                    const leaksDirectAddress = candidates.some(candidate =>
                      / typ (host|srflx)( |$)/.test(candidate)
                    );
                    document.title = leaksDirectAddress ? '$LEAKED_TITLE' : '$PROTECTED_TITLE';
                  };
                  await connection.setLocalDescription(await connection.createOffer());
                  setTimeout(finish, 3000);
                } catch (error) {
                  document.title = '$PROTECTED_TITLE';
                }
              })();
            </script></body></html>
        """.trimIndent()

        override fun close() {
            socket.close()
            thread.join(2_000)
        }
    }

    private companion object {
        const val PAGE_HOST = "localhost"
        const val AVAILABLE_TITLE = "WebRTC available"
        const val BLOCKED_TITLE = "WebRTC blocked"
        const val PROTECTED_TITLE = "WebRTC direct addresses protected"
        const val LEAKED_TITLE = "WebRTC direct address leaked"
    }
}
