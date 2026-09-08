package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoCastInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences by lazy {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun directFullscreenGeckoVideoPublishesCastCandidate() {
        CastFixtureServer().use { server ->
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertTrue(activity.browserControllerForTesting().usesGeckoEngine)
                    assertTrue(activity.browserControllerForTesting().openUrl(server.pageUrl))
                }
                awaitCondition(description = { "Gecko video metadata did not load" }) {
                    var ready = false
                    scenario.onActivity { activity ->
                        ready = activity.browserControllerForTesting().selectedTab.title == READY_TITLE
                    }
                    ready
                }
                awaitCondition(description = { "Gecko view did not reach tappable size" }) {
                    var tappable = false
                    scenario.onActivity { activity ->
                        tappable = activity.browserControllerForTesting()
                            .selectedGeckoViewForTesting()
                            ?.let { view ->
                                view.isAttachedToWindow && view.width > 0 && view.height > 0
                            } == true
                    }
                    tappable
                }

                val tapPoint = FloatArray(2)
                scenario.onActivity { activity ->
                    val geckoView = requireNotNull(
                        activity.browserControllerForTesting().selectedGeckoViewForTesting(),
                    )
                    assertTrue(geckoView.isAttachedToWindow)
                    geckoView.centerOnScreen(tapPoint)
                }
                tap(x = tapPoint[0], y = tapPoint[1])

                var candidateSnapshot = "not observed"
                awaitCondition(description = { candidateSnapshot }) {
                    var candidatePublished = false
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        val candidate = controller.castMediaCandidate
                        candidateSnapshot =
                            "candidatePresent=${candidate != null}, " +
                            "candidateSourceMatches=${candidate?.source?.url == server.mediaUrl}, " +
                            "candidateType=${candidate?.source?.contentType}, " +
                            "fullscreenPresent=${controller.fullscreenVideoState != null}, " +
                            controller.geckoFullscreenDebugStateForTesting(
                                controller.selectedTab.id,
                            )
                        candidatePublished =
                            candidate?.source?.url == server.mediaUrl &&
                            controller.fullscreenVideoState != null
                    }
                    candidatePublished
                }
                awaitCondition(description = { "Gecko video never entered playing state" }) {
                    var playing = false
                    scenario.onActivity { activity ->
                        playing = activity.browserControllerForTesting().systemMediaState
                            ?.isPlaying == true
                    }
                    playing
                }
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    val candidate = requireNotNull(controller.castMediaCandidate)
                    assertEquals(server.mediaUrl, candidate.source.url)
                    assertEquals("video/webm", candidate.source.contentType)
                    assertEquals("127.0.0.1", candidate.source.origin)
                    assertEquals(controller.selectedTab.id, candidate.identity.tabId)
                    assertFalse(controller.selectedTab.isIncognito)
                    assertNotNull(controller.fullscreenVideoState)
                    assertTrue(controller.pauseCastMedia(candidate))
                }
                awaitCondition(description = { "Gecko video did not pause after Cast handoff" }) {
                    var paused = false
                    scenario.onActivity { activity ->
                        paused = activity.browserControllerForTesting().systemMediaState
                            ?.isPlaying == false
                    }
                    paused
                }
                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().exitFullscreenVideo()
                }
                awaitCondition(description = { "Cast candidate remained after fullscreen exit" }) {
                    var cleared = false
                    scenario.onActivity { activity ->
                        cleared = activity.browserControllerForTesting().castMediaCandidate == null
                    }
                    cleared
                }
                scenario.onActivity { activity ->
                    assertNull(activity.browserControllerForTesting().castMediaCandidate)
                    assertNotNull(activity.browserControllerForTesting().selectedGeckoViewForTesting())
                }
            }
        }
    }

    private fun tap(x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0),
        )
        instrumentation.sendPointerSync(
            MotionEvent.obtain(
                downTime,
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP,
                x,
                y,
                0,
            ),
        )
        instrumentation.waitForIdleSync()
    }

    private fun awaitCondition(
        timeoutMillis: Long = 30_000,
        description: () -> String,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue("${description()} within ${timeoutMillis}ms", condition())
    }

    private fun View.centerOnScreen(target: FloatArray) {
        val location = IntArray(2)
        getLocationOnScreen(location)
        target[0] = location[0] + width / 2f
        target[1] = location[1] + height / 2f
    }

    private class CastFixtureServer : Closeable {
        private val socket = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        val pageUrl = "http://127.0.0.1:${socket.localPort}/"
        val mediaUrl = "http://127.0.0.1:${socket.localPort}$MEDIA_PATH"
        private val thread = Thread(::serve, "gecko-cast-fixture").apply {
            isDaemon = true
            start()
        }

        private fun serve() {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: return
                client.use { connection ->
                    runCatching {
                        val reader = connection.getInputStream().bufferedReader()
                        val requestPath = reader.readLine()
                            ?.split(' ')
                            ?.getOrNull(1)
                        while (!reader.readLine().isNullOrEmpty()) {
                            // Drain request headers before writing the local response.
                        }
                        val body: ByteArray
                        val contentType: String
                        if (requestPath == MEDIA_PATH) {
                            body = VIDEO_BYTES
                            contentType = "video/webm"
                        } else {
                            body = HTML.toByteArray()
                            contentType = "text/html"
                        }
                        connection.getOutputStream().buffered().use { output ->
                            output.write(
                                "HTTP/1.1 200 OK\r\nContent-Type: $contentType\r\n".toByteArray(),
                            )
                            output.write(
                                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray(),
                            )
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
        const val READY_TITLE = "cast-ready"
        const val MEDIA_PATH = "/sample.webm"
        val VIDEO_BYTES: ByteArray = Base64.decode(
            listOf(
                "GkXfo59ChoEBQveBAULygQRC84EIQoKEd2VibUKHgQJChYECGFOAZwEAAAAAAAIXEU2bdLpNu4tTq4QV",
                "SalmU6yBoU27i1OrhBZUrmtTrIHWTbuMU6uEElTDZ1OsggEyTbuMU6uEHFO7a1OsggIB7AEAAAAAAABZ",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAVSalmsCrXsYMPQkBNgIxMYXZmNjMuMS4xMDFXQYxM",
                "YXZmNjMuMS4xMDFEiYhAj0AAAAAAABZUrmvXrgEAAAAAAABO14EBc8WIDxQnt/iyoUGcgQAitZyDdW5k",
                "iIEAhoVWX1ZQOIOBASPjg4QdzWUA4JCwgUC6gSSagQJVsIRVuYEBVe6BAOwBAAAAAAAAAgAAElTDZ/pz",
                "c59jwIBnyJlFo4dFTkNPREVSRIeMTGF2ZjYzLjEuMTAxc3PVY8CLY8WIDxQnt/iyoUFnyKBFo4dFTkNP",
                "REVSRIeTTGF2YzYzLjEuMTAxIGxpYnZweGfIoUWjiERVUkFUSU9ORIeTMDA6MDA6MDEuMDAwMDAwMDAw",
                "AB9DtnXL54EAo6iBAACAsAIAnQEqQAAkAABHCIWFiJmEiAICAAaOT8zHm/FYAP7/q1CAo5yBAfQAEQIA",
                "ARAQABgAGk/0DAAB1f/4AP7/q1CAHFO7a5G7j7OBALeK94EB8YIBsfCBAw==",
            ).joinToString(separator = ""),
            Base64.DEFAULT,
        )
        val HTML =
            """
            <!doctype html>
            <html><head><title>loading</title><style>
              html,body,video,button { margin:0; width:100%; height:100%; background:#000; }
              button { position:fixed; inset:0; color:white; border:0; font-size:32px; }
            </style></head><body>
              <video muted playsinline loop src="$MEDIA_PATH"></video>
              <button>Play fullscreen</button>
              <script>
                const video = document.querySelector('video');
                const button = document.querySelector('button');
                video.defaultMuted = true;
                video.muted = true;
                video.addEventListener('loadedmetadata', () => document.title = '$READY_TITLE');
                button.addEventListener('click', () => {
                  button.remove();
                  video.requestFullscreen();
                });
                document.addEventListener('fullscreenchange', () => {
                  if (document.fullscreenElement) video.play();
                });
                video.load();
              </script>
            </body></html>
            """.trimIndent()
    }
}
