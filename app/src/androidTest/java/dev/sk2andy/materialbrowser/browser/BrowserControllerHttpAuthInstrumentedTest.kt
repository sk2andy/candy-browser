package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.credentials.HttpAuthPrompt
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserControllerHttpAuthInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private var controller: BrowserController? = null
    private var server: BasicAuthTestServer? = null

    @After
    fun tearDown() {
        activityRule.scenario.onActivity { activity ->
            controller?.destroy()
            controller = null
            activity.getSharedPreferences(
                BrowserSessionStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
        }
        server?.close()
        server = null
    }

    @Test
    fun httpChallengePromptsAndProceedsWithEnteredCredentials() {
        val authServer = BasicAuthTestServer(username = "candy", password = "secret")
            .also { server = it }
        startController(authServer.url)

        val prompt = awaitPrompt()
        assertEquals("127.0.0.1", prompt.host)
        assertEquals(BasicAuthTestServer.REALM, prompt.realm)
        assertFalse(prompt.isPageSecure)

        activityRule.scenario.onActivity {
            requireNotNull(controller).respondToHttpAuthPrompt(
                promptId = prompt.id,
                username = "candy",
                password = "secret",
            )
        }

        assertTrue(authServer.awaitAuthorizedRequest())
        activityRule.scenario.onActivity {
            assertNull(requireNotNull(controller).httpAuthPrompt)
        }
    }

    @Test
    fun cancelingPromptStopsChallengeWithoutSendingCredentials() {
        val authServer = BasicAuthTestServer(username = "candy", password = "secret")
            .also { server = it }
        startController(authServer.url)

        val prompt = awaitPrompt()
        activityRule.scenario.onActivity {
            requireNotNull(controller).cancelHttpAuthPrompt(prompt.id)
            assertNull(requireNotNull(controller).httpAuthPrompt)
        }

        assertFalse(authServer.awaitAuthorizedRequest(timeoutMillis = 500L))
    }

    @Test
    fun stoppingControllerCancelsChallengeAndIgnoresLateResponse() {
        val authServer = BasicAuthTestServer(username = "candy", password = "secret")
            .also { server = it }
        startController(authServer.url)

        val prompt = awaitPrompt()
        activityRule.scenario.onActivity {
            requireNotNull(controller).onStop()
            assertNull(requireNotNull(controller).httpAuthPrompt)
            requireNotNull(controller).respondToHttpAuthPrompt(
                promptId = prompt.id,
                username = "candy",
                password = "secret",
            )
        }

        assertFalse(authServer.awaitAuthorizedRequest(timeoutMillis = 500L))
    }

    private fun startController(url: String) {
        activityRule.scenario.onActivity { activity ->
            activity.getSharedPreferences(
                BrowserSessionStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
            controller = BrowserController(activity).also { browserController ->
                browserController.onResume()
                browserController.submitAddress(url)
            }
        }
    }

    private fun awaitPrompt(): HttpAuthPrompt {
        val deadline = SystemClock.elapsedRealtime() + PROMPT_TIMEOUT_MILLIS
        var prompt: HttpAuthPrompt? = null
        while (prompt == null && SystemClock.elapsedRealtime() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            activityRule.scenario.onActivity {
                prompt = requireNotNull(controller).httpAuthPrompt
            }
            if (prompt == null) SystemClock.sleep(25L)
        }
        return requireNotNull(prompt) { "HTTP authentication prompt was not shown" }
    }

    private class BasicAuthTestServer(
        username: String,
        password: String,
    ) : AutoCloseable {
        private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()
        private val closed = AtomicBoolean()
        private val authorizedRequest = CountDownLatch(1)
        private val expectedAuthorization = "Basic " + Base64.getEncoder().encodeToString(
            "$username:$password".toByteArray(StandardCharsets.UTF_8),
        )

        val port: Int = serverSocket.localPort
        val url: String = "http://127.0.0.1:$port/private"

        init {
            executor.execute {
                while (!closed.get()) {
                    val socket = runCatching(serverSocket::accept).getOrNull() ?: break
                    runCatching { respond(socket) }
                }
            }
        }

        fun awaitAuthorizedRequest(
            timeoutMillis: Long = REQUEST_TIMEOUT_MILLIS,
        ): Boolean = authorizedRequest.await(timeoutMillis, TimeUnit.MILLISECONDS)

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            runCatching(serverSocket::close)
            executor.shutdownNow()
        }

        private fun respond(socket: Socket) {
            socket.use { connection ->
                val reader = BufferedReader(
                    InputStreamReader(connection.getInputStream(), StandardCharsets.US_ASCII),
                )
                reader.readLine() ?: return
                var authorization: String? = null
                while (true) {
                    val line = reader.readLine() ?: return
                    if (line.isEmpty()) break
                    if (line.startsWith("Authorization:", ignoreCase = true)) {
                        authorization = line.substringAfter(':').trim()
                    }
                }
                val response = if (authorization == expectedAuthorization) {
                    authorizedRequest.countDown()
                    "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html; charset=utf-8\r\n" +
                        "Content-Length: 2\r\n" +
                        "Connection: close\r\n\r\nOK"
                } else {
                    "HTTP/1.1 401 Unauthorized\r\n" +
                        "WWW-Authenticate: Basic realm=\"$REALM\"\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n\r\n"
                }
                connection.getOutputStream().write(
                    response.toByteArray(StandardCharsets.US_ASCII),
                )
                connection.getOutputStream().flush()
            }
        }

        companion object {
            const val REALM = "Candy Test"
            private const val REQUEST_TIMEOUT_MILLIS = 10_000L
        }
    }

    private companion object {
        const val PROMPT_TIMEOUT_MILLIS = 10_000L
    }
}
