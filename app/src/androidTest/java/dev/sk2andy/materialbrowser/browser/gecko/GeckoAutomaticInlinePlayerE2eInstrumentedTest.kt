package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.os.SystemClock
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.InlineMediaPlayerMode
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Activity + GeckoView automatic-mode journey with a trusted site tap. */
@RunWith(AndroidJUnit4::class)
class GeckoAutomaticInlinePlayerE2eInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun setUp() {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        // Match GeckoPictureInPictureInstrumentedTest: release-note chrome must not cover
        // the first real Gecko frame or steal the trusted fixture tap.
        assertTrue(ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong()))
        // Persist through same store used by Settings, before MainActivity creates controller.
        BrowserSessionStore(context).saveInlineMediaPlayerMode(InlineMediaPlayerMode.Automatic)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun automaticPlayerWaitsForLoadedDataBeforeOpeningCandyControls() {
        FixtureServer().use { server ->
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var host: View
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assertTrue("Persisted Automatic setting was not loaded", controller.inlineMediaPlayerMode == InlineMediaPlayerMode.Automatic)
                    assertTrue(controller.openUrl(server.url))
                }
                await("paused thumbnail telemetry") {
                    server.samples.any { it.getString("phase") == "thumbnail" }
                }
                await("decoded paused thumbnail remains without Candy controls") {
                    server.samples.any { it.getString("phase") == "paused-ready-stable" }
                }
                scenario.onActivity { activity ->
                    host = requireNotNull(activity.browserControllerForTesting().selectedGeckoViewForTesting())
                    assertNotNull((host as ViewGroup).singleChild().findDescendant(SurfaceView::class.java))
                    val pausedReady = server.samples.last { it.getString("phase") == "paused-ready-stable" }
                    assertTrue("Fixture did not preload a paused decoded video: $pausedReady", pausedReady.getInt("readyState") >= 2)
                    assertTrue("Fixture video has no intrinsic size: $pausedReady", pausedReady.getInt("videoWidth") > 0 && pausedReady.getInt("videoHeight") > 0)
                    assertTrue("Fixture video unexpectedly played before the trusted tap: $pausedReady", pausedReady.getBoolean("paused"))
                    assertFalse("Automatic opened Candy controls for a paused decoded thumbnail: ${server.samples}", pausedReady.getBoolean("controls"))
                }

                // Trusted site play: no JavaScript dispatch or controller test hook.
                val point = IntArray(2)
                val thumbnail = server.samples.first { it.getString("phase") == "paused-ready-stable" }
                scenario.onActivity {
                    host.getLocationOnScreen(point)
                    val button = thumbnail.getJSONArray("button")
                    val scale = host.width / thumbnail.getDouble("viewportWidth")
                    point[0] += ((button.getDouble(0) + button.getDouble(2) / 2) * scale).toInt()
                    point[1] += ((button.getDouble(1) + button.getDouble(3) / 2) * scale).toInt()
                }
                assertTrue(UiDevice.getInstance(instrumentation).click(point[0], point[1]))
                await("trusted site play; samples=${server.samples}; requests=${server.requests}") {
                    server.samples.any { it.getString("phase") == "play-resolved" && !it.getBoolean("paused") }
                }
                await("loaded data and automatic Candy controls") {
                    server.samples.any { it.getString("phase") == "automatic-open" &&
                        it.getInt("readyState") >= 2 &&
                        it.getInt("videoWidth") > 0 &&
                        it.getInt("videoHeight") > 0 &&
                        it.getBoolean("controls") }
                }
            }
        }
    }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        assertTrue("Timed out: $description", condition())
    }

    private fun ViewGroup.singleChild(): View = checkNotNull(takeIf { childCount == 1 }?.getChildAt(0))
    private fun <T : View> View.findDescendant(type: Class<T>): T? {
        if (type.isInstance(this)) return type.cast(this)
        if (this !is ViewGroup) return null
        repeat(childCount) { getChildAt(it).findDescendant(type)?.let { child -> return child } }
        return null
    }

    private class FixtureServer : Closeable {
        private val socket = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${socket.localPort}/"
        private val recorded = mutableListOf<JSONObject>()
        private val requested = mutableListOf<String>()
        val samples: List<JSONObject> get() = synchronized(recorded) { recorded.toList() }
        val requests: List<String> get() = synchronized(requested) { requested.toList() }
        private val clients = Executors.newFixedThreadPool(4)
        private val thread = Thread(::serve).apply { isDaemon = true; start() }
        private val html = HTML.replace(
            "watch();send('thumbnail');requestAnimationFrame(()=>requestAnimationFrame(()=>setTimeout(()=>send('thumbnail-stable'),400)));b.onpointerdown=()=>send('site-pointerdown');b.onclick=()=>{send('site-click');v.src='/video.webm';v.load();v.play().then(()=>send('play-resolved')).catch(e=>{playError=String(e);send('play-error')})};v.addEventListener('loadeddata',()=>send('loadeddata'));",
            "watch();v.src='/video.webm';v.load();send('thumbnail');v.addEventListener('loadeddata',()=>{send('paused-ready');requestAnimationFrame(()=>requestAnimationFrame(()=>setTimeout(()=>send('paused-ready-stable'),400)))},{once:true});b.onpointerdown=()=>send('site-pointerdown');b.onclick=()=>{send('site-click');v.play().then(()=>send('play-resolved')).catch(e=>{playError=String(e);send('play-error')})};",
        )
        private fun serve() {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: return
                clients.execute {
                    client.use { connection ->
                        val reader = connection.getInputStream().bufferedReader()
                        val request = reader.readLine().orEmpty().split(' ').getOrNull(1).orEmpty()
                        synchronized(requested) { requested += request }
                        while (!reader.readLine().isNullOrEmpty()) {
                            // Drain request headers before responding; Gecko keeps fetches alive.
                        }
                        if (request.startsWith("/geometry?")) {
                            val sample = JSONObject(URLDecoder.decode(request.substringAfter('?'), "UTF-8"))
                            sample.put("nativeReceivedAtEpochMillis", System.currentTimeMillis())
                            synchronized(recorded) { recorded += sample }
                        }
                        val body = when {
                            request == "/video.webm" -> VIDEO
                            else -> html.toByteArray()
                        }
                        val type = if (request == "/video.webm") "video/webm" else "text/html"
                        connection.getOutputStream().use { output ->
                            output.write("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            output.write(body)
                        }
                    }
                }
            }
        }
        override fun close() { socket.close(); thread.join(1_000); clients.shutdownNow(); clients.awaitTermination(2, TimeUnit.SECONDS) }
    }

    private companion object {
        val VIDEO = android.util.Base64.decode("GkXfo59ChoEBQveBAULygQRC84EIQoKEd2VibUKHgQJChYECGFOAZwEAAAAAAASQEU2bdLpNu4tTq4QVSalmU6yBoU27i1OrhBZUrmtTrIHWTbuMU6uEElTDZ1OsggEyTbuMU6uEHFO7a1OsggR67AEAAAAAAABZAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAVSalmsCrXsYMPQkBNgIxMYXZmNjMuMS4xMDFXQYxMYXZmNjMuMS4xMDFEiYhAx3AAAAAAABZUrmvXrgEAAAAAAABO14EBc8WIuEiFyor52QWcgQAitZyDdW5kiIEAhoVWX1ZQOIOBASPjg4QdzWUA4JCwgaC6gVqagQJVsIRVuYEBVe6BAOwBAAAAAAAAAgAAElTDZ/pzc59jwIBnyJlFo4dFTkNPREVSRIeMTGF2ZjYzLjEuMTAxc3PVY8CLY8WIuEiFyor52QVnyKBFo4dFTkNPREVSRIeTTGF2YzYzLjEuMTAxIGxpYnZweGfIoUWjiERVUkFUSU9ORIeTMDA6MDA6MTIuMDAwMDAwMDAwAB9DtnVBX+eBAKPXgQAAgPAFAJ0BKqAAWgAARwiFhYiFhIgCAgJ1qgP4AgaaE+CGqpNdxDqqTXcQ6qk13EOqpNdxDqqTXcQYAP7ujn/+7J/tk/2yf+8Z//W6363W/W63/rWYo5iBAfQAEQIAARAQABgAGFgv9AAIgIEAAACjmIED6AARAgABEBAAGAAYWC/0AAiAgQAAAKOYgQXcABECAAEQEAAYABhYL/QACICBAAAAo5iBB9AAEQIAARAQABgAGFgv9AAIgIEAAACjmIEJxAARAgABEBAAGAAYWC/0AAiAgQAAAKOYgQu4ABECAAEQEAAYABhYL/QACICBAAAAo5eBDawA8QEAARAQFGAAYWC/0AAiAgQAAKOYgQ+gABECAAEQEAAYABhYL/QACICBAAAAo5iBEZQAEQIAARAQABgAGFgv9AAIgIEAAACjmIETiAARAgABEBAAGAAYWC/0AAiAgQAAAB9DtnVBIeeCFXyjmIEAAAARAgABEBAAGAAYWC/0AAiAgQAAAKOYgQH0ABECAAEQEAAYABhYL/QACICBAAAAo5iBA+gAEQIAARAQABgAGFgv9AAIgIEAAACjmIEF3AARAgABEBAAGAAYWC/0AAiAgQAAAKOYgQfQABECAAEQEAAYABhYL/QACICBAAAAo5iBCcQAEQIAARAQABgAGFgv9AAIgIEAAACjmIELuAARAgABEBAAGAAYWC/0AAiAgQAAAKOXgQ2sAPEBAAEQEBRgAGFgv9AAIgIEAACjmIEPoAARAgABEBAAGAAYWC/0AAiAgQAAAKOYgRGUABECAAEQEAAYABhYL/QACICBAAAAo5iBE4gAEQIAARAQABgAGFgv9AAIgIEAAAAfQ7Z1uOeCKvijmIEAAAARAgABEBAAGAAYWC/0AAiAgQAAAKOYgQH0ABECAAEQEAAYABhYL/QACICBAAAAHFO7a5G7j7OBALeK94EB8YIBsfCBAw==", android.util.Base64.DEFAULT)
        val HTML = """<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'><style>html,body{margin:0;background:#111}header{height:56px;background:#b00}#player-parent{position:relative;overflow:hidden;transform:translateZ(0)}#movie_player{position:relative;width:100%;aspect-ratio:16/9;overflow:hidden;transform:translateZ(0)}video{display:block;width:100%;height:100%;background:#000}button{position:absolute;inset:56px 0 auto;width:100%;height:56.25vw;background:transparent;border:0}</style><header></header><div id=player-parent><div id=movie_player class=html5-video-player><video playsinline muted></video><button aria-label=Play></button></div></div><script>const v=document.querySelector('video'),p=document.querySelector('#movie_player'),h=document.querySelector('header'),b=document.querySelector('button');let playError='';const r=e=>{let b=e.getBoundingClientRect();return[b.left,b.top,b.width,b.height]};let opened=false;const send=phase=>fetch('/geometry?'+encodeURIComponent(JSON.stringify({phase,at:Date.now(),video:r(v),player:r(p),header:r(h),button:r(b),viewportWidth:innerWidth,viewportHeight:innerHeight,readyState:v.readyState,videoWidth:v.videoWidth,videoHeight:v.videoHeight,paused:v.paused,currentTime:v.currentTime,playError,controls:!!document.querySelector('[data-candy-inline-video-controls]'),fullscreen:!!document.fullscreenElement})));const watch=()=>{let c=!!document.querySelector('[data-candy-inline-video-controls]');if(c&&!opened){opened=true;send('automatic-open')}requestAnimationFrame(watch)};watch();send('thumbnail');requestAnimationFrame(()=>requestAnimationFrame(()=>setTimeout(()=>send('thumbnail-stable'),400)));b.onpointerdown=()=>send('site-pointerdown');b.onclick=()=>{send('site-click');v.src='/video.webm';v.load();v.play().then(()=>send('play-resolved')).catch(e=>{playError=String(e);send('play-error')})};v.addEventListener('loadeddata',()=>send('loadeddata'));v.addEventListener('error',()=>send('media-error'));</script>"""
    }
}
