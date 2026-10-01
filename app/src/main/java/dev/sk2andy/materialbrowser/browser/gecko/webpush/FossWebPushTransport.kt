package dev.sk2andy.materialbrowser.browser.gecko.webpush

import android.content.Context
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** Shares a foreground Autopush socket; WorkManager uses bounded sessions while Candy is closed. */
internal class FossWebPushTransport(
    context: Context,
    private val onMessage: suspend (FossPushMessage) -> Boolean,
    private val onSubscriptionsInvalidated: (List<String>) -> Unit,
    private val serverUrl: String = AUTOPUSH_URL,
) {
    private val store = FossWebPushStore(context)
    private val mutex = Mutex()
    // Accessed only under mutex, including reads and protocol messages from the foreground loop.
    private var foregroundListening = false
    private var foregroundSession: Session? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(0, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun subscribe(
        scope: String,
        appServerKey: ByteArray?,
        keys: WebPushKeyMaterial,
        isPrivate: Boolean,
    ): FossPushSubscription? = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!FossWebPushScopeRules.isPersistentScope(scope, isPrivate)) return@withContext null
            if (appServerKey != null && appServerKey.size != PUBLIC_KEY_BYTES) return@withContext null
            if (keys.publicKey.size != PUBLIC_KEY_BYTES || keys.authSecret.size != AUTH_SECRET_BYTES) {
                return@withContext null
            }
            val initial = store.load()
            val current = initial.subscriptions.firstOrNull { it.scope == scope }
            if (current != null) {
                return@withContext current.takeIf { it.appServerKey.contentEqualsNullable(appServerKey) }
            }
            if (initial.subscriptions.size >= MAX_SUBSCRIPTIONS ||
                initial.pendingUnregister.size >= MAX_PENDING_UNREGISTER
            ) return@withContext null

            val session = sessionForOperation() ?: return@withContext null
            var subscribed = false
            try {
                repeat(2) {
                    if (session.state.subscriptions.size >= MAX_SUBSCRIPTIONS ||
                        session.state.pendingUnregister.size >= MAX_PENDING_UNREGISTER
                    ) return@withContext null
                    val channelId = UUID.randomUUID().toString()
                    val reserved = session.state.copy(
                        pendingUnregister = session.state.pendingUnregister + channelId,
                    )
                    if (!store.save(reserved)) return@withContext null
                    session.state = reserved
                    if (!session.socket.send(FossWebPushProtocol.register(channelId, appServerKey))) {
                        return@withContext null
                    }
                    val reply = session.awaitRegister(channelId)
                    if (reply == null) {
                        session.rollbackRegistration(channelId)
                        return@withContext null
                    }
                    if (reply.status == 409) {
                        if (!session.forgetReservation(channelId)) return@withContext null
                        return@repeat
                    }
                    val endpoint = reply.endpoint
                    if (reply.status != 200 || endpoint == null ||
                        !FossWebPushScopeRules.isSecureEndpoint(endpoint)
                    ) {
                        session.rollbackRegistration(channelId)
                        return@withContext null
                    }
                    val subscription = FossPushSubscription(
                        scope = scope,
                        endpoint = endpoint,
                        channelId = channelId,
                        appServerKey = appServerKey?.clone(),
                        keys = keys.copyOf(),
                    )
                    val updated = session.state.copy(
                        subscriptions = session.state.subscriptions + subscription,
                        pendingUnregister = session.state.pendingUnregister - channelId,
                    )
                    if (!store.save(updated)) {
                        session.rollbackRegistration(channelId)
                        return@withContext null
                    }
                    session.state = updated
                    if (session !== foregroundSession) session.drainPending()
                    subscribed = true
                    return@withContext subscription.copyOf()
                }
                null
            } finally {
                if (!subscribed && session === foregroundSession) closeForegroundSession()
                closeTemporarySession(session)
            }
        }
    }

    suspend fun get(scope: String): FossPushSubscription? = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!FossWebPushScopeRules.isPersistentScope(scope)) return@withContext null
            store.load().subscriptions.firstOrNull { it.scope == scope }?.copyOf()
        }
    }

    suspend fun hasPendingWork(): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val state = store.load()
            state.subscriptions.isNotEmpty() || state.pendingUnregister.isNotEmpty()
        }
    }

    /** Drops only scopes owned by one isolated Gecko profile; server cleanup waits for reconnect. */
    suspend fun removeProfileSubscriptions(profileId: String): List<String>? = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (profileId.isEmpty()) return@withContext emptyList()
            closeForegroundSession()
            val state = store.load()
            val removed = state.subscriptions.filter { subscription ->
                FossWebPushScopeRules.belongsToIsolatedProfile(subscription.scope, profileId)
            }
            if (removed.isEmpty()) return@withContext emptyList()
            val removedChannels = removed.map(FossPushSubscription::channelId)
            val pending = (state.pendingUnregister + removedChannels).distinct()
            if (pending.size > MAX_PENDING_UNREGISTER) return@withContext null
            val updated = state.copy(
                subscriptions = state.subscriptions.filterNot { it.channelId in removedChannels },
                pendingUnregister = pending,
                deliveredVersions = state.deliveredVersions - removedChannels.toSet(),
            )
            if (!store.save(updated)) return@withContext null
            removed.map(FossPushSubscription::scope)
        }
    }

    /** Removing locally first guarantees Gecko will not see a stale subscription on network failure. */
    suspend fun unsubscribe(scope: String): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!FossWebPushScopeRules.isPersistentScope(scope)) return@withContext false
            closeForegroundSession()
            val state = store.load()
            val subscription = state.subscriptions.firstOrNull { it.scope == scope } ?: return@withContext true
            val updated = state.copy(
                subscriptions = state.subscriptions.filterNot { it.scope == scope },
                pendingUnregister = (state.pendingUnregister + subscription.channelId)
                    .distinct()
                    .takeLast(MAX_PENDING_UNREGISTER),
                deliveredVersions = state.deliveredVersions - subscription.channelId,
            )
            if (!store.save(updated)) return@withContext false
            val session = openSession()
            if (session != null) {
                try {
                    session.drainPending()
                } finally {
                    session.close()
                }
            }
            true
        }
    }

    /** Browser-data deletion removes local keys first, even when Autopush is unreachable. */
    suspend fun clearAllData(): List<String>? = mutex.withLock {
        withContext(Dispatchers.IO) {
            closeForegroundSession()
            val state = store.load()
            val scopes = state.subscriptions.map(FossPushSubscription::scope)
            val channels = (state.subscriptions.map(FossPushSubscription::channelId) + state.pendingUnregister)
                .distinct()
                .takeLast(MAX_PENDING_UNREGISTER)
            if (!store.clear()) return@withContext null
            if (channels.isEmpty()) return@withContext scopes
            val cleanupState = FossWebPushState(
                uaid = state.uaid,
                pendingUnregister = channels,
            )
            val session = try {
                openSession(initialState = cleanupState, persistChanges = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (session != null) {
                try {
                    session.drainPending()
                } finally {
                    session.close()
                }
            }
            scopes
        }
    }

    /** One short connection retrieves stored messages and closes after an idle window. */
    suspend fun reconnectOnce(): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val state = store.load()
            if (state.subscriptions.isEmpty() && state.pendingUnregister.isEmpty()) return@withContext true
            val session = sessionForOperation() ?: return@withContext false
            var drained = false
            try {
                session.drainPending().also { drained = it }
            } finally {
                if (!drained && session === foregroundSession) closeForegroundSession()
                closeTemporarySession(session)
            }
        }
    }

    /** Keeps delivery live until the process leaves the foreground; cancellation releases the socket. */
    suspend fun listenWhileForeground() = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!foregroundListening)
            foregroundListening = true
        }
        try {
            while (currentCoroutineContext().isActive) {
                val connected = mutex.withLock {
                    val state = foregroundSession?.state ?: store.load()
                    if (state.subscriptions.isEmpty() && state.pendingUnregister.isEmpty()) {
                        closeForegroundSession()
                        return@withLock false
                    }
                    try {
                        val session = sessionForOperation() ?: return@withLock false
                        session.receiveNext(FOREGROUND_RECEIVE_MILLIS).also { healthy ->
                            if (!healthy) closeForegroundSession()
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        closeForegroundSession()
                        false
                    }
                }
                delay(if (connected) FOREGROUND_YIELD_MILLIS else RECONNECT_DELAY_MILLIS)
            }
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    foregroundListening = false
                    closeForegroundSession()
                }
            }
        }
    }

    private suspend fun sessionForOperation(): Session? {
        if (foregroundSession?.isClosed == true) closeForegroundSession()
        return foregroundSession ?: openSession()?.also { session ->
            if (foregroundListening) foregroundSession = session
        }
    }

    private fun closeTemporarySession(session: Session) {
        if (session.isClosed && session === foregroundSession) closeForegroundSession()
        if (session !== foregroundSession) session.close()
    }

    private fun closeForegroundSession() {
        foregroundSession?.close()
        foregroundSession = null
    }

    private suspend fun openSession(
        initialState: FossWebPushState? = null,
        persistChanges: Boolean = true,
    ): Session? {
        val events = Channel<SocketEvent>(capacity = 64)
        val socket = client.newWebSocket(
            Request.Builder().url(serverUrl).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    events.trySend(SocketEvent.Opened)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (events.trySend(SocketEvent.Message(text)).isFailure) {
                        events.close()
                        webSocket.close(1009, "Push queue full")
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    events.close()
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    events.close()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    events.close()
                }
            },
        )
        val initial = initialState ?: store.load()
        val session = Session(socket, events, initial, persistChanges)
        var ready = false
        try {
            val opened = session.next(OPEN_TIMEOUT_MILLIS)
            if (opened != SocketEvent.Opened || !socket.send(
                    FossWebPushProtocol.hello(
                        initial.uaid,
                        (initial.subscriptions.map(FossPushSubscription::channelId) + initial.pendingUnregister)
                            .distinct(),
                    ),
                )
            ) return null
            val reply = session.nextProtocol(HELLO_TIMEOUT_MILLIS) as? FossWebPushServerMessage.Hello
            if (reply == null || !reply.useWebPush) return null
            if (initial.uaid != reply.uaid) {
                val invalidated = initial.subscriptions.map(FossPushSubscription::scope)
                val updated = FossWebPushState(uaid = reply.uaid)
                if (persistChanges && !store.save(updated)) return null
                session.state = updated
                if (persistChanges && invalidated.isNotEmpty()) {
                    withContext(Dispatchers.Main.immediate) { onSubscriptionsInvalidated(invalidated) }
                }
            }
            session.sendPendingUnregister()
            ready = true
            return session
        } finally {
            if (!ready) session.close()
        }
    }

    private inner class Session(
        val socket: WebSocket,
        private val events: Channel<SocketEvent>,
        var state: FossWebPushState,
        private val persistChanges: Boolean,
    ) {
        var isClosed = false
            private set

        suspend fun next(timeoutMillis: Long): SocketEvent? {
            if (isClosed) return SocketEvent.Closed
            val event = withTimeoutOrNull(timeoutMillis) {
                events.receiveCatching().getOrNull() ?: SocketEvent.Closed
            }
            if (event == SocketEvent.Closed) isClosed = true
            return event
        }

        suspend fun nextProtocol(timeoutMillis: Long): FossWebPushServerMessage? {
            val event = next(timeoutMillis) as? SocketEvent.Message ?: return null
            return FossWebPushProtocol.decode(event.text)
        }

        suspend fun awaitRegister(channelId: String): FossWebPushServerMessage.Register? {
            val deadline = System.nanoTime() + REGISTER_TIMEOUT_MILLIS * 1_000_000
            while (true) {
                val remaining = ((deadline - System.nanoTime()) / 1_000_000).coerceAtLeast(1)
                if (System.nanoTime() >= deadline) return null
                when (val message = nextProtocol(remaining)) {
                    is FossWebPushServerMessage.Register -> if (message.channelId == channelId) return message
                    is FossWebPushServerMessage.Notification -> deliver(message)
                    is FossWebPushServerMessage.Unregister -> completeUnregister(message)
                    FossWebPushServerMessage.Ignored -> Unit
                    else -> return null
                }
            }
        }

        suspend fun drainPending(): Boolean {
            val deadline = System.nanoTime() + MAX_DRAIN_MILLIS * 1_000_000
            while (System.nanoTime() < deadline) {
                val remaining = ((deadline - System.nanoTime()) / 1_000_000).coerceAtLeast(1)
                val event = next(minOf(remaining, IDLE_DRAIN_MILLIS)) ?: return true
                val message = (event as? SocketEvent.Message)?.let { FossWebPushProtocol.decode(it.text) }
                    ?: return false
                when (message) {
                    is FossWebPushServerMessage.Notification -> deliver(message)
                    is FossWebPushServerMessage.Unregister -> completeUnregister(message)
                    FossWebPushServerMessage.Ignored -> Unit
                    else -> return false
                }
            }
            return true
        }

        suspend fun receiveNext(timeoutMillis: Long): Boolean {
            val event = next(timeoutMillis) ?: return true
            return when (val message = (event as? SocketEvent.Message)?.let {
                FossWebPushProtocol.decode(it.text)
            }) {
                is FossWebPushServerMessage.Notification -> {
                    deliver(message)
                    true
                }
                is FossWebPushServerMessage.Unregister -> {
                    completeUnregister(message)
                    true
                }
                FossWebPushServerMessage.Ignored -> true
                else -> false
            }
        }

        fun sendPendingUnregister() {
            state.pendingUnregister.forEach { channelId ->
                socket.send(FossWebPushProtocol.unregister(channelId))
            }
        }

        fun forgetReservation(channelId: String): Boolean {
            val updated = state.copy(pendingUnregister = state.pendingUnregister - channelId)
            if (!store.save(updated)) return false
            state = updated
            return true
        }

        suspend fun rollbackRegistration(channelId: String) {
            socket.send(FossWebPushProtocol.unregister(channelId))
            drainPending()
        }

        private suspend fun deliver(message: FossWebPushServerMessage.Notification) {
            val subscription = state.subscriptions.firstOrNull { it.channelId == message.channelId } ?: return
            val version = (message.version as JsonPrimitive).content
            if (version in state.deliveredVersions[message.channelId].orEmpty()) {
                socket.send(FossWebPushProtocol.acknowledge(message.channelId, message.version))
                return
            }
            val bytes = message.data?.let { raw ->
                runCatching { Base64.getUrlDecoder().decode(raw) }
                    .getOrNull()
                    ?.takeIf { it.size <= MAX_PAYLOAD_BYTES }
                    ?: return
            }
            val delivered = try {
                onMessage(FossPushMessage(subscription.copyOf(), bytes, message.headers))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (delivered) {
                if (version.length in 1..MAX_VERSION_LENGTH) {
                    val recent = (state.deliveredVersions[message.channelId].orEmpty() + version)
                        .takeLast(MAX_RECENT_VERSIONS)
                    val updated = state.copy(
                        deliveredVersions = state.deliveredVersions + (message.channelId to recent),
                    )
                    if (store.save(updated)) state = updated
                }
                socket.send(FossWebPushProtocol.acknowledge(message.channelId, message.version))
            }
        }

        private fun completeUnregister(message: FossWebPushServerMessage.Unregister) {
            if (message.status != 200 || message.channelId !in state.pendingUnregister) return
            val updated = state.copy(pendingUnregister = state.pendingUnregister - message.channelId)
            if (!persistChanges || store.save(updated)) state = updated
        }

        fun close() {
            isClosed = true
            socket.close(1000, "Done")
            events.close()
        }
    }

    private sealed interface SocketEvent {
        data object Opened : SocketEvent
        data class Message(val text: String) : SocketEvent
        data object Closed : SocketEvent
    }

    private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean =
        if (this == null || other == null) this == null && other == null else contentEquals(other)

    private fun WebPushKeyMaterial.copyOf() = WebPushKeyMaterial(
        publicKey = publicKey.clone(),
        privateKeyPkcs8 = privateKeyPkcs8.clone(),
        authSecret = authSecret.clone(),
    )

    private fun FossPushSubscription.copyOf() = copy(
        appServerKey = appServerKey?.clone(),
        keys = keys.copyOf(),
    )

    companion object {
        private const val AUTOPUSH_URL = "wss://push.services.mozilla.com/"
        private const val OPEN_TIMEOUT_MILLIS = 10_000L
        private const val HELLO_TIMEOUT_MILLIS = 10_000L
        private const val REGISTER_TIMEOUT_MILLIS = 20_000L
        private const val IDLE_DRAIN_MILLIS = 2_000L
        private const val MAX_DRAIN_MILLIS = 12_000L
        private const val FOREGROUND_RECEIVE_MILLIS = 1_000L
        private const val FOREGROUND_YIELD_MILLIS = 100L
        private const val RECONNECT_DELAY_MILLIS = 5_000L
        private const val MAX_PAYLOAD_BYTES = 72 * 1_024
        private const val MAX_PENDING_UNREGISTER = 512
        private const val MAX_SUBSCRIPTIONS = 256
        private const val MAX_RECENT_VERSIONS = 10
        private const val MAX_VERSION_LENGTH = 256
        private const val PUBLIC_KEY_BYTES = 65
        private const val AUTH_SECRET_BYTES = 16

        fun schedulePeriodicReconnect(context: Context, enabled: Boolean) {
            FossWebPushReconnectWorker.schedule(context, enabled)
        }
    }
}
