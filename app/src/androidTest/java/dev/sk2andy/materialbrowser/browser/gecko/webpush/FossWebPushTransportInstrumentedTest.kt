package dev.sk2andy.materialbrowser.browser.gecko.webpush

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FossWebPushTransportInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = FossWebPushStore(context)
    private val sessions = LinkedBlockingQueue<PushSocket>()
    private val delivered = LinkedBlockingQueue<String>()
    private val closeNextRegistration = AtomicBoolean()
    @Volatile
    private var sendStaleOnUnregister = false
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        assertTrue(store.clear())
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse().withWebSocketUpgrade(PushSocket())
            }
            start()
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
        assertTrue(store.clear())
    }

    @Test
    fun foregroundKeepsSocketBeyondIdleAndDeliversLatePushWhileOperationsComplete() = runBlocking {
        val subscription = subscription("https://example.com/first/", FIRST_CHANNEL)
        seed(subscription)
        val transport = transport()
        val listening = launch(Dispatchers.IO) { transport.listenWhileForeground() }
        val socket = awaitSession()
        try {
            assertFalse("Foreground socket closed after the old idle window", socket.closed.await(2_500, TimeUnit.MILLISECONDS))
            assertEquals(subscription.scope, withTimeout(4_000) { transport.get(subscription.scope) }?.scope)
            val second = withTimeout(4_000) {
                transport.subscribe(
                    scope = "https://example.com/second/",
                    appServerKey = null,
                    keys = WebPushCrypto.generateKeyMaterial(),
                    isPrivate = false,
                )
            }
            assertEquals("https://example.com/second/", second?.scope)
            assertEquals(1, server.requestCount)

            socket.notify(subscription.channelId, "late-v1")
            assertEquals(subscription.scope, delivered.poll(5, TimeUnit.SECONDS))
            socket.assertAcknowledgement(subscription.channelId, "late-v1")
            assertEquals(1, server.requestCount)
        } finally {
            withTimeout(5_000) { listening.cancelAndJoin() }
        }
        assertTrue("Cancellation did not close the socket", socket.closed.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun profileRemovalAndUnsubscribeDiscardStaleChannelsFromForegroundSessions() = runBlocking {
        val profile = subscription(
            "https://example.com/profile/^geckoViewSessionContextId=gvctx616c706861",
            FIRST_CHANNEL,
        )
        val regular = subscription("https://example.com/regular/", SECOND_CHANNEL)
        seed(profile, regular)
        sendStaleOnUnregister = true
        val transport = transport()
        val listening = launch(Dispatchers.IO) { transport.listenWhileForeground() }
        val oldSocket = awaitSession()
        try {
            assertEquals(listOf(profile.scope), withTimeout(4_000) { transport.removeProfileSubscriptions("alpha") })
            assertNull(withTimeout(4_000) { transport.get(profile.scope) })
            assertTrue(oldSocket.closed.await(5, TimeUnit.SECONDS))
            val activeSocket = awaitSession()
            assertEquals(profile.channelId, activeSocket.awaitMessage("unregister").string("channelID"))

            activeSocket.notify(profile.channelId, "removed-profile-v1")
            activeSocket.notify(regular.channelId, "regular-v1")
            assertEquals(regular.scope, delivered.poll(5, TimeUnit.SECONDS))
            activeSocket.assertAcknowledgement(regular.channelId, "regular-v1")
            assertNull(store.load().deliveredVersions[profile.channelId])

            assertTrue(withTimeout(6_000) { transport.unsubscribe(regular.scope) })
            assertNull(withTimeout(4_000) { transport.get(regular.scope) })
            assertTrue(activeSocket.closed.await(5, TimeUnit.SECONDS))
            val cleanupSocket = awaitSession()
            assertEquals(regular.channelId, cleanupSocket.awaitMessage("unregister").string("channelID"))
            assertTrue(cleanupSocket.closed.await(5, TimeUnit.SECONDS))
            assertTrue(store.load().subscriptions.isEmpty())
            assertNull("Removed channels reached the callback", delivered.poll(300, TimeUnit.MILLISECONDS))
        } finally {
            withTimeout(5_000) { listening.cancelAndJoin() }
        }
    }

    @Test
    fun socketClosedDuringRegistrationReconnectsAndDeliversExistingSubscription() = runBlocking {
        val subscription = subscription("https://example.com/existing/", FIRST_CHANNEL)
        seed(subscription)
        val transport = transport()
        val listening = launch(Dispatchers.IO) { transport.listenWhileForeground() }
        val oldSocket = awaitSession()
        try {
            closeNextRegistration.set(true)
            assertNull(withTimeout(4_000) {
                transport.subscribe(
                    scope = "https://example.com/interrupted/",
                    appServerKey = null,
                    keys = WebPushCrypto.generateKeyMaterial(),
                    isPrivate = false,
                )
            })
            assertTrue(oldSocket.closed.await(5, TimeUnit.SECONDS))
            val replacement = awaitSession()
            replacement.notify(subscription.channelId, "reconnected-v1")
            assertEquals(subscription.scope, delivered.poll(5, TimeUnit.SECONDS))
            replacement.assertAcknowledgement(subscription.channelId, "reconnected-v1")
            assertEquals(2, server.requestCount)
        } finally {
            withTimeout(5_000) { listening.cancelAndJoin() }
        }
    }

    @Test
    fun backgroundReconnectReturnsAndClosesAfterBoundedIdle() = runBlocking {
        seed(subscription("https://example.com/background/", FIRST_CHANNEL))
        val transport = transport()
        assertTrue(withTimeout(6_000) { transport.reconnectOnce() })
        val socket = awaitSession()
        assertTrue(socket.closed.await(5, TimeUnit.SECONDS))
        assertEquals(1, server.requestCount)
        assertNull(delivered.poll())
    }

    private fun transport() = FossWebPushTransport(
        context = context,
        onMessage = { message ->
            delivered.add(message.subscription.scope)
            true
        },
        onSubscriptionsInvalidated = { error("Local server unexpectedly changed UAID: $it") },
        serverUrl = server.url("/").toString(),
    )

    private fun subscription(scope: String, channelId: String) = FossPushSubscription(
        scope = scope,
        endpoint = "https://updates.push.services.mozilla.com/wpush/$channelId",
        channelId = channelId,
        appServerKey = null,
        keys = WebPushCrypto.generateKeyMaterial(),
    )

    private fun seed(vararg subscriptions: FossPushSubscription) {
        assertTrue(store.save(FossWebPushState(uaid = UAID, subscriptions = subscriptions.toList())))
    }

    private fun awaitSession(): PushSocket = checkNotNull(sessions.poll(10, TimeUnit.SECONDS)) {
        "Autopush socket was not opened"
    }.also { it.awaitMessage("hello") }

    private fun JsonObject.string(name: String): String = (get(name) as JsonPrimitive).content

    private inner class PushSocket : WebSocketListener() {
        val closed = CountDownLatch(1)
        private val messages = LinkedBlockingQueue<JsonObject>()
        private lateinit var socket: WebSocket

        override fun onOpen(webSocket: WebSocket, response: Response) {
            socket = webSocket
            sessions.add(this)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = Json.parseToJsonElement(text) as JsonObject
            messages.add(message)
            when (message.string("messageType")) {
                "hello" -> webSocket.send(
                    """{"messageType":"hello","status":200,"uaid":"$UAID","use_webpush":true}""",
                )
                "register" -> {
                    if (closeNextRegistration.getAndSet(false)) {
                        webSocket.close(1011, "Registration interrupted")
                        return
                    }
                    val channel = message.string("channelID")
                    webSocket.send(
                        """{"messageType":"register","status":200,"channelID":"$channel","pushEndpoint":"https://updates.push.services.mozilla.com/wpush/$channel"}""",
                    )
                }
                "unregister" -> {
                    val channel = message.string("channelID")
                    if (sendStaleOnUnregister) notify(channel, "stale-unregister-v1")
                    webSocket.send("""{"messageType":"unregister","status":200,"channelID":"$channel"}""")
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            closed.countDown()
        }

        fun notify(channelId: String, version: String) {
            assertTrue(socket.send("""{"messageType":"notification","channelID":"$channelId","version":"$version"}"""))
        }

        fun awaitMessage(type: String): JsonObject {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (true) {
                val remaining = deadline - System.nanoTime()
                check(remaining > 0) { "Missing Autopush $type message" }
                val message = checkNotNull(messages.poll(remaining, TimeUnit.NANOSECONDS)) {
                    "Missing Autopush $type message"
                }
                if (message.string("messageType") == type) return message
            }
        }

        fun assertAcknowledgement(channelId: String, version: String) {
            val update = (awaitMessage("ack")["updates"] as JsonArray).single() as JsonObject
            assertEquals(channelId, update.string("channelID"))
            assertEquals(version, update.string("version"))
            assertEquals("100", update.string("code"))
        }
    }

    companion object {
        private const val UAID = "0123456789abcdef0123456789abcdef"
        private const val FIRST_CHANNEL = "11111111-2222-3333-4444-555555555555"
        private const val SECOND_CHANNEL = "66666666-7777-8888-9999-000000000000"
    }
}
