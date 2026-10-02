package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.HttpsOnlyMode
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
        BrowserSessionStore(context).saveHttpsOnlyMode(HttpsOnlyMode.Off)
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

    @Test
    fun disabledPlayerPreservesNativePlaybackFullscreenAndPersistedChoice() {
        verifyDisabledPlayerJourney(startAutomatic = false)
    }

    @Test
    fun activeCandyPlayerCanBeDisabledWithoutPausingNativeVideo() {
        verifyDisabledPlayerJourney(startAutomatic = true)
    }

    private fun verifyDisabledPlayerJourney(startAutomatic: Boolean) {
        val initialMode = if (startAutomatic) InlineMediaPlayerMode.Automatic else InlineMediaPlayerMode.Disabled
        BrowserSessionStore(context).saveInlineMediaPlayerMode(initialMode)
        FixtureServer(disabledMode = true).use { server ->
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assertTrue(controller.inlineMediaPlayerMode == initialMode)
                    assertTrue(controller.openUrl(server.url))
                }
                await("native video metadata and controls", diagnostics = {
                    "requests=${server.requests.takeLast(10).map { it.substringBefore('?') }}; samples=${server.samples.takeLast(5)}"
                }) {
                    server.samples.any { it.getInt("readyState") >= 1 &&
                        it.getBoolean("nativeControls") }
                }
                clickSitePlayer(scenario, server.samples.last())
                if (startAutomatic) {
                    await("automatic Candy controls before disable") {
                        server.samples.any { it.getString("phase") == "automatic-open" }
                    }
                    pauseForEvidence()
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        controller.updateInlineMediaPlayerMode(InlineMediaPlayerMode.Disabled)
                        assertFalse("Disabled controller still accepts Candy opens", controller.canOpenInlineMediaPlayer)
                    }
                }
                await("native playback continues without Candy controls") {
                    server.samples.any { it.getString("phase") == "playback-tick" &&
                        it.getDouble("currentTime") > 0.5 && !it.getBoolean("paused") &&
                        !it.getBoolean("controls") && it.getBoolean("nativeControls") }
                }
                val playing = server.samples.last { it.getString("phase") == "playback-tick" }
                assertTrue("Native controls removed: $playing", playing.getBoolean("nativeControls"))
                assertFalse("Candy overlay survived Off: $playing", playing.getBoolean("controls"))
                assertFalse("Candy launcher survived Off: $playing", playing.getBoolean("launcher"))
                pauseForEvidence()
                clickSitePlayer(scenario, playing)
                await("trusted website fullscreen") {
                    server.samples.any { it.getString("phase") == "site-fullscreen" }
                }
                val fullscreen = server.samples.last { it.getString("phase") == "site-fullscreen" }
                assertTrue(fullscreen.getBoolean("fullscreen"))
                assertTrue(fullscreen.getBoolean("nativeControls"))
                assertFalse(fullscreen.getBoolean("controls"))
                pauseForEvidence()
                scenario.onActivity { activity ->
                    assertTrue(activity.browserControllerForTesting().exitSelectedWebContentFullscreen())
                }
                await("website returns inline") { !server.samples.last().getBoolean("fullscreen") }
                pauseForEvidence()
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertTrue("Off lost after Activity recreation", activity.browserControllerForTesting().inlineMediaPlayerMode == InlineMediaPlayerMode.Disabled)
                }
            }
        }
    }

    private fun pauseForEvidence() {
        val pauseMillis = InstrumentationRegistry.getArguments()
            .getString("evidencePauseMillis")?.toLongOrNull()?.coerceIn(0L, 5_000L) ?: 0L
        if (pauseMillis > 0) SystemClock.sleep(pauseMillis)
    }

    private fun clickSitePlayer(scenario: ActivityScenario<MainActivity>, sample: JSONObject) {
        val point = IntArray(2)
        scenario.onActivity { activity ->
            val host = requireNotNull(activity.browserControllerForTesting().selectedGeckoViewForTesting())
            host.getLocationOnScreen(point)
            val button = sample.getJSONArray("button")
            val scale = host.width / sample.getDouble("viewportWidth")
            point[0] += ((button.getDouble(0) + button.getDouble(2) / 2) * scale).toInt()
            point[1] += ((button.getDouble(1) + button.getDouble(3) / 2) * scale).toInt()
        }
        assertTrue(UiDevice.getInstance(instrumentation).click(point[0], point[1]))
    }

    @Test
    fun trustedDoubleTapsSeekInlineAndFullscreenWithClampedBounds() {
        FixtureServer(seekJourney = true).use { server ->
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var host: View
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assertTrue(controller.openUrl(server.url))
                }
                await("decoded thumbnail") {
                    server.samples.any { it.getString("phase") == "paused-ready-stable" }
                }
                scenario.onActivity { activity ->
                    host = requireNotNull(activity.browserControllerForTesting().selectedGeckoViewForTesting())
                }
                val sitePlay = fixturePoint(scenario, host, server.samples.last(), "button", 0.5, 0.5)
                assertTrue(UiDevice.getInstance(instrumentation).click(sitePlay[0], sitePlay[1]))
                await("paused Candy Player at six seconds; ${server.samples}") {
                    server.samples.any { it.getString("phase") == "seek-ready" }
                }
                SystemClock.sleep(500)
                pauseForEvidence()
                fun doubleTap(fractionX: Double) {
                    val sample = server.samples.last()
                    val point = fixturePoint(scenario, host, sample, "video", fractionX, 0.3)
                    tapPoint(point)
                    SystemClock.sleep(60)
                    tapPoint(point)
                }
                fun awaitTime(expected: Double) = await("video time $expected; ${server.samples.takeLast(3)}") {
                    server.samples.last().getDouble("currentTime").let { kotlin.math.abs(it - expected) < 0.3 }
                }
                doubleTap(0.18)
                awaitTime(0.0)
                pauseForEvidence()
                SystemClock.sleep(1_100)
                doubleTap(0.82)
                awaitTime(10.0)
                pauseForEvidence()
                SystemClock.sleep(1_100)
                // Trusted upward free-surface swipe uses Candy's existing fullscreen gesture.
                val sample = server.samples.last()
                val start = fixturePoint(scenario, host, sample, "video", 0.18, 0.52)
                val end = fixturePoint(scenario, host, sample, "video", 0.18, 0.05)
                assertTrue(UiDevice.getInstance(instrumentation).swipe(start[0], start[1], end[0], end[1], 20))
                await("Candy fullscreen") { server.samples.last().getBoolean("fullscreen") }
                UiDevice.getInstance(instrumentation)
                    .wait(Until.findObject(By.text("Got it")), 2_000)?.click()
                SystemClock.sleep(700)
                doubleTap(0.18)
                awaitTime(0.0)
                pauseForEvidence()
                SystemClock.sleep(1_100)
                doubleTap(0.82)
                awaitTime(10.0)
                pauseForEvidence()
                SystemClock.sleep(1_100)
                assertTrue("Double tap unexpectedly resumed playback", server.samples.last().getBoolean("paused"))
                assertTrue("Candy controls disappeared", server.samples.last().getBoolean("controls"))
                doubleTap(0.82)
                awaitTime(12.0)
                pauseForEvidence()
                SystemClock.sleep(1_100)
                assertTrue("Seeking to the end unexpectedly resumed playback", server.samples.last().getBoolean("paused"))
            }
        }
    }

    private fun fixturePoint(
        scenario: ActivityScenario<MainActivity>,
        host: View,
        sample: JSONObject,
        element: String,
        fractionX: Double,
        fractionY: Double,
    ): IntArray = IntArray(2).also { point ->
        scenario.onActivity {
            host.getLocationOnScreen(point)
            val bounds = sample.getJSONArray(element)
            val scale = host.width / sample.getDouble("viewportWidth")
            point[0] += ((bounds.getDouble(0) + bounds.getDouble(2) * fractionX) * scale).toInt()
            point[1] += ((bounds.getDouble(1) + bounds.getDouble(3) * fractionY) * scale).toInt()
        }
    }

    private fun tapPoint(point: IntArray) {
        val downTime = SystemClock.uptimeMillis()
        val pointer = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }
        val coordinates = MotionEvent.PointerCoords().apply {
            x = point[0].toFloat()
            y = point[1].toFloat()
            pressure = 1f
            size = 1f
        }
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), action, 1,
                arrayOf(pointer), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0,
            )
            try {
                assertTrue(instrumentation.uiAutomation.injectInputEvent(event, false))
            } finally {
                event.recycle()
            }
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(35)
        }
    }

    private fun await(
        description: String,
        diagnostics: () -> String = { "" },
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        assertTrue("Timed out: $description; ${diagnostics()}", condition())
    }

    private fun ViewGroup.singleChild(): View = checkNotNull(takeIf { childCount == 1 }?.getChildAt(0))
    private fun <T : View> View.findDescendant(type: Class<T>): T? {
        if (type.isInstance(this)) return type.cast(this)
        if (this !is ViewGroup) return null
        repeat(childCount) { getChildAt(it).findDescendant(type)?.let { child -> return child } }
        return null
    }

    private class FixtureServer(
        disabledMode: Boolean = false,
        seekJourney: Boolean = false,
    ) : Closeable {
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
        ).let { page ->
            if (!seekJourney) page else page.replace(
                "if(c&&!opened){opened=true;send('automatic-open')}",
                "if(c&&!opened){opened=true;send('automatic-open');setTimeout(()=>{v.pause();v.currentTime=6;send('seek-ready');setInterval(()=>send('seek-sample'),100)},1000)}",
            )
        }
        private val servedHtml = if (disabledMode) {
            html.replace("<video playsinline muted>", "<video playsinline muted controls loop>")
                .replace("controls:!!document.querySelector", "nativeControls:v.controls,launcher:!!document.querySelector('[data-candy-inline-video-action]'),controls:!!document.querySelector")
                .replace("b.onclick=()=>{send('site-click');v.play()", "b.onclick=()=>{if(!v.paused){v.requestFullscreen().then(()=>send('site-fullscreen'));return}send('site-click');v.play()")
                .replace("v.addEventListener('error'", "const status=document.createElement('p');status.style='color:white;font:18px sans-serif;padding:16px';document.body.append(status);setInterval(()=>{status.textContent='Website player: '+v.currentTime.toFixed(1)+'s';send('playback-tick')},200);v.addEventListener('error'")
        } else {
            html
        }

        private fun serve() {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: return
                clients.execute {
                    client.use { connection ->
                        connection.soTimeout = 2_000
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
                            request.startsWith("/geometry?") -> ByteArray(0)
                            request == "/video.webm" -> VIDEO
                            else -> servedHtml.toByteArray()
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
