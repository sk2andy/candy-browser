package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.CandyTrail
import dev.sk2andy.materialbrowser.browser.CandyTrailHistoryBinding
import dev.sk2andy.materialbrowser.browser.CandyTrailHistoryReconciler
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommands
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoCandyTrailInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun GeckoHistoryDrivesTrailBackForwardAndBranching() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = FixtureServer()
        val recorder = TrailRecorder(TAB_ID)
        lateinit var session: AndroidBrowserEngineSessionPort
        lateinit var view: View
        instrumentation.runOnMainSync {
            session = GeckoBrowserEngineSessionFactory(context).create(
                tabId = TAB_ID,
                profileId = "trail-${UUID.randomUUID()}",
                isPrivate = false,
                trailHistoryEventSink = GeckoCandyTrailHistoryEventSink { _, event ->
                    recorder.onHistoryEvent(event)
                },
                eventSink = BrowserEngineEventSink { event ->
                    if (event.type == BrowserEngineEventType.NavigationCommitted) {
                        recorder.onNavigationCommitted(event.address, event.title)
                    }
                },
            )
            view = session.createView(context)
            session.setActive(true)
        }

        try {
            execute(session, BrowserEngineCommands.load(server.url("a")))
            assertTrue(recorder.awaitCurrentUrl(server.url("a")))
            execute(session, BrowserEngineCommands.load(server.url("b")))
            assertTrue(recorder.awaitCurrentUrl(server.url("b")))
            assertEquals(2, recorder.snapshot().nodes.size)

            execute(session, BrowserEngineCommands.back())
            assertTrue(recorder.awaitCurrentUrl(server.url("a")))
            recorder.prepareForwardTraversal()
            execute(session, BrowserEngineCommands.forward())
            assertTrue(recorder.awaitCurrentUrl(server.url("b")))
            assertEquals(2, recorder.snapshot().nodes.size)

            execute(session, BrowserEngineCommands.back())
            assertTrue(recorder.awaitCurrentUrl(server.url("a")))
            execute(session, BrowserEngineCommands.load(server.url("c")))
            assertTrue(recorder.awaitCurrentUrl(server.url("c")))

            val trail = recorder.snapshot()
            assertEquals(3, trail.nodes.size)
            val root = trail.nodes.single { node -> node.url == server.url("a") }
            assertEquals(
                setOf(server.url("b"), server.url("c")),
                trail.nodes.filter { node -> node.parentId == root.id }
                    .mapTo(mutableSetOf()) { node -> node.url },
            )
            assertEquals("Page C", trail.nodes.single { node -> node.url == server.url("c") }.title)
        } finally {
            instrumentation.runOnMainSync {
                session.setActive(false)
                session.releaseView(view)
                session.execute(BrowserEngineCommands.close())
            }
            server.close()
        }
    }

    private fun execute(
        session: AndroidBrowserEngineSessionPort,
        command: dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommand,
    ) = instrumentation.runOnMainSync { session.execute(command) }

    private class TrailRecorder(private val tabId: String) {
        private var trail: CandyTrail? = null
        private var binding = CandyTrailHistoryBinding()
        private var pendingTargetId: String? = null
        private var visitedAt = 0L

        @Synchronized
        fun onHistoryEvent(event: GeckoCandyTrailHistoryEvent) {
            val currentUrl = event.snapshot.urls.getOrNull(event.snapshot.currentIndex)
            val matchingPendingTargetId = pendingTargetId?.takeIf { targetId ->
                trail?.nodes?.any { node -> node.id == targetId && node.url == currentUrl } == true
            }
            val result = CandyTrailHistoryReconciler.reconcile(
                trail = trail,
                tabId = tabId,
                previous = binding,
                snapshot = event.snapshot,
                title = event.title,
                visitedAt = ++visitedAt,
                pendingTargetNodeId = matchingPendingTargetId,
            )
            if (matchingPendingTargetId != null) {
                pendingTargetId = null
            }
            trail = result.trail
            binding = result.binding
        }

        @Synchronized
        fun onNavigationCommitted(url: String?, title: String?) {
            val current = trail ?: return
            val safeUrl = url ?: return
            trail = CandyTrailHistoryReconciler.refineCurrentTitle(
                trail = current,
                url = safeUrl,
                title = title.orEmpty(),
                visitedAt = ++visitedAt,
            )
        }

        @Synchronized
        fun prepareForwardTraversal() {
            pendingTargetId = binding.entries.getOrNull(binding.currentIndex + 1)?.nodeId
        }

        fun awaitCurrentUrl(expected: String): Boolean {
            val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
            while (SystemClock.elapsedRealtime() < deadline) {
                if (currentUrl() == expected) return true
                SystemClock.sleep(POLL_MILLIS)
            }
            return currentUrl() == expected
        }

        @Synchronized
        fun snapshot(): CandyTrail = trail ?: CandyTrail(tabId)

        @Synchronized
        private fun currentUrl(): String? = trail?.nodes
            ?.firstOrNull { node -> node.id == trail?.currentNodeId }
            ?.url
    }

    private class FixtureServer : AutoCloseable {
        private val socket = ServerSocket(0, REQUEST_BACKLOG, InetAddress.getByName("127.0.0.1"))
        private val baseUrl = "http://127.0.0.1:${socket.localPort}/"
        private val thread = Thread({ serve() }, "gecko-trail-fixture").apply {
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
                        while (!reader.readLine().isNullOrEmpty()) {
                            // Consume request headers.
                        }
                        val title = "Page ${path.trim('/').uppercase().ifBlank { "Root" }}"
                        val body = "<html><head><title>$title</title></head><body>$title</body></html>"
                            .toByteArray()
                        connection.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\n".toByteArray())
                            write("Content-Type: text/html; charset=utf-8\r\n".toByteArray())
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
        const val TAB_ID = "00000000-0000-0000-0000-000000000001"
        const val POLL_MILLIS = 25L
        const val REQUEST_BACKLOG = 4
        const val TIMEOUT_MILLIS = 20_000L
    }
}
