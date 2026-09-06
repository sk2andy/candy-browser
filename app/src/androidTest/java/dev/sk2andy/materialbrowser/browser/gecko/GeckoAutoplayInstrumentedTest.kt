package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoAutoplayInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun setUp() {
        GestureOnboardingStore(context).markCompleted()
    }

    @Test
    fun blockedPolicyDeniesMutedAutoplay() {
        val result = runAutoplayFixture(blocked = true)

        assertTrue(result.title?.startsWith(BLOCKED_TITLE) == true)
    }

    @Test
    fun disablingBlockedPolicyReloadsPageAndAllowsPlayback() {
        val result = runAutoplayFixture(
            blocked = true,
            unblockAfterFirstResult = true,
            audible = true,
        )

        assertEquals(PLAYING_TITLE, result.title)
    }

    @Ignore(
        "GeckoView 140 rejects direct audible play before the async embedder permission resolves; " +
            "Mozilla bug 2049064 is fixed in Gecko 154, whose Android metadata requires SDK 37",
    )
    @Test
    fun disabledBlockedPolicyAllowsImmediateAudiblePlayback() {
        val result = runAutoplayFixture(
            blocked = false,
            audible = true,
            immediateAudible = true,
        )

        assertEquals(PLAYING_TITLE, result.title)
    }

    private fun runAutoplayFixture(
        blocked: Boolean,
        unblockAfterFirstResult: Boolean = false,
        audible: Boolean = false,
        immediateAudible: Boolean = false,
    ): AutoplayResult {
        FixtureServer(
            audible = audible,
            immediateAudible = immediateAudible,
        ).use { server ->
            val title = AtomicReference<String?>()
            val firstCompleted = CountDownLatch(1)
            val updatedCompleted = CountDownLatch(1)
            val policyUpdated = AtomicBoolean(false)
            fun publishCompletionIfReady() {
                val currentTitle = title.get()
                if (policyUpdated.get()) {
                    if (currentTitle == PLAYING_TITLE) updatedCompleted.countDown()
                } else if (
                    currentTitle?.startsWith(BLOCKED_TITLE) == true ||
                    currentTitle == PLAYING_TITLE
                ) {
                    firstCompleted.countDown()
                }
            }
            lateinit var session: GeckoBrowserSession
            lateinit var view: View
            lateinit var host: FrameLayout
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    host = FrameLayout(activity)
                    activity.addContentView(
                        host,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    session = GeckoRuntimeOwner.getOrCreate(activity.applicationContext)
                        .createSession(
                            profileId = "autoplay-${UUID.randomUUID()}",
                            isPrivate = false,
                        )
                    session.setActive(true)
                    session.setVideoAutoplayBlocked(blocked)
                    session.setStateListener { state ->
                        title.set(state.title)
                        publishCompletionIfReady()
                    }
                    view = session.createView(activity)
                    host.addView(
                        view,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    assertTrue(session.loadUrl(server.url))
                }
                try {
                    assertTrue(
                        "Gecko autoplay fixture timed out",
                        firstCompleted.await(30, TimeUnit.SECONDS),
                    )
                    if (unblockAfterFirstResult) {
                        assertTrue(title.get()?.startsWith(BLOCKED_TITLE) == true)
                        title.set(null)
                        policyUpdated.set(true)
                        scenario.onActivity { session.setVideoAutoplayBlocked(false) }
                        assertTrue(
                            "Gecko autoplay policy update timed out; last title=${title.get()}",
                            updatedCompleted.await(30, TimeUnit.SECONDS),
                        )
                    }
                    instrumentation.waitForIdleSync()
                    return AutoplayResult(title.get())
                } finally {
                    scenario.onActivity {
                        session.releaseView(view)
                        session.close()
                        (host.parent as? ViewGroup)?.removeView(host)
                    }
                }
            }
        }
    }

    private data class AutoplayResult(
        val title: String?,
    )

    private class FixtureServer(
        audible: Boolean,
        immediateAudible: Boolean,
    ) : Closeable {
        private val socket = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${socket.localPort}/"
        private val html = if (audible) {
            audibleHtml(immediate = immediateAudible)
        } else {
            MUTED_HTML
        }
        private val thread = Thread(::serve, "gecko-autoplay-fixture").apply {
            isDaemon = true
            start()
        }

        private fun serve() {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: return
                client.use { connection ->
                    runCatching {
                        connection.getInputStream().bufferedReader().apply {
                            readLine()
                            while (!readLine().isNullOrEmpty()) {
                                // Drain request headers before writing the local response.
                            }
                        }
                        val body = html.toByteArray()
                        connection.getOutputStream().buffered().use { output ->
                            output.write(
                                "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n".toByteArray(),
                            )
                            output.write("Content-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            output.write(body)
                        }
                    }
                }
            }
        }

        override fun close() {
            socket.close()
            thread.join(1_000)
        }
    }

    private companion object {
        const val BLOCKED_TITLE = "blocked"
        const val PLAYING_TITLE = "playing"
        val MUTED_HTML =
            """
            <!doctype html>
            <html><head><title>loading</title></head><body>
              <video muted playsinline></video>
              <script>
                const encodedVideo = [
                  'GkXfo59ChoEBQveBAULygQRC84EIQoKEd2VibUKHgQJChYECGFOAZwEAAAAAAAIXEU2bdLpNu4tTq4QV',
                  'SalmU6yBoU27i1OrhBZUrmtTrIHWTbuMU6uEElTDZ1OsggEyTbuMU6uEHFO7a1OsggIB7AEAAAAAAABZ',
                  'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA',
                  'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAVSalmsCrXsYMPQkBNgIxMYXZmNjMuMS4xMDFXQYxM',
                  'YXZmNjMuMS4xMDFEiYhAj0AAAAAAABZUrmvXrgEAAAAAAABO14EBc8WIDxQnt/iyoUGcgQAitZyDdW5k',
                  'iIEAhoVWX1ZQOIOBASPjg4QdzWUA4JCwgUC6gSSagQJVsIRVuYEBVe6BAOwBAAAAAAAAAgAAElTDZ/pz',
                  'c59jwIBnyJlFo4dFTkNPREVSRIeMTGF2ZjYzLjEuMTAxc3PVY8CLY8WIDxQnt/iyoUFnyKBFo4dFTkNP',
                  'REVSRIeTTGF2YzYzLjEuMTAxIGxpYnZweGfIoUWjiERVUkFUSU9ORIeTMDA6MDA6MDEuMDAwMDAwMDAw',
                  'AB9DtnXL54EAo6iBAACAsAIAnQEqQAAkAABHCIWFiJmEiAICAAaOT8zHm/FYAP7/q1CAo5yBAfQAEQIA',
                  'ARAQABgAGk/0DAAB1f/4AP7/q1CAHFO7a5G7j7OBALeK94EB8YIBsfCBAw=='
                ].join('');
                const video = document.querySelector('video');
                video.defaultMuted = true;
                video.muted = true;
                video.src = 'data:video/webm;base64,' + encodedVideo;
                video.addEventListener('loadedmetadata', () => {
                  video.play().then(
                    () => document.title = '$PLAYING_TITLE',
                    error => document.title = '$BLOCKED_TITLE:' + error.name
                  );
                }, { once: true });
                video.load();
              </script>
            </body></html>
            """.trimIndent()
        fun audibleHtml(immediate: Boolean) =
            """
            <!doctype html>
            <html><head><title>loading</title></head><body>
              <audio></audio>
              <script>
                const audio = document.querySelector('audio');
                audio.src = 'data:audio/wav;base64,' +
                  'UklGRkQDAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YSADAACAkJ6pr6+qn5GBcWJXUVBVX2x8jZynrq+roZSEdGVZUVBTXWp5ipmlra+spJeHd2dbUlBSW2d3h5ekrK+tpZmKeWpdU1BRWWV0hJShq6+up5yNfGxfVVBRV2JxgZGfqq+vqZ6Qf29hVlBQVWBufo6dqK6vqqCTg3JjWFFQVF5re4uapq6vrKKVhnVmWlJQU1toeIiYpK2wraSYiHhoW1NQUlpmdYaVoqyvrqaai3trXlRQUVhjcoOToKqvrqidjn5uYFVQUFZhb4CQnqmvr6qfkYFxYldRUFVfbHyNnKeur6uhlIR0ZVlRUFNdanmKmaWtr6ykl4d3Z1tSUFJbZ3eHl6Ssr62lmYp5al1TUFFZZXSElKGrr66nnI18bF9VUFFXYnGBkZ+qr6+pnpCAb2FWUFBVYG5+jp2orq+qoJODcmNYUVBUXmt7i5qmrq+sopWGdWZaUlBTW2h4iJikrbCtpJiIeGhbU1BSWmZ1hpWirK+uppqLe2teVFBRWGNyg5Ogqq+uqJ2Ofm5gVVBQVmFvgJCeqa+vqp+RgXFiV1FQVV9sfI2cp66vq6GUhHRlWVFQU11qeYqZpa2vrKSXh3dnW1JQUltnd4eXpKyvraWZinlqXVNQUVlldISUoauvrqecjXxsX1VQUVdicYGRn6qvr6mekH9vYVZQUFVgbn6Onaiur6qgk4NyY1hRUFRea3uLmqaur6yilYZ1ZlpSUFNbaHiImKStsK2kmIh4aFtTUFJaZnWGlaKsr66mmot7a15UUFFYY3KDk6Cqr66onY5+bmBVUFBWYW9/kJ6pr6+qn5GBcWJXUVBVX2x8jZynrq+roZSEdGVZUVBTXWp5ipmlra+spJeHd2dbUlBSW2d3h5ekrK+tpZmKeWpdU1BRWWV0hJShq6+up5yNfGxfVVBRV2JxgZGfqq+vqZ6Qf29hVlBQVWBufo6dqK6vqqCTg3JjWFFQVF5re4uapq6vrKKVhnVmWlJQU1toeIiYpK2wraSYiHhoW1NQUlpmdYaVoqyvrqaai3trXlRQUVhjcoOToKqvrqidjn5uYFVQUFZhbw==';
                const play = () => audio.play().then(
                    () => document.title = '$PLAYING_TITLE',
                    error => document.title = '$BLOCKED_TITLE:' + error.name
                );
                ${if (immediate) "play();" else "audio.addEventListener('loadedmetadata', play, { once: true });"}
                audio.load();
              </script>
            </body></html>
            """.trimIndent()
    }
}
