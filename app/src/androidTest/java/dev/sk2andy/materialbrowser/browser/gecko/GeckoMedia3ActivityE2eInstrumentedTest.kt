package dev.sk2andy.materialbrowser.browser.gecko

import android.app.ActivityManager
import android.app.NotificationManager
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.BrowserMediaPlaybackService
import dev.sk2andy.materialbrowser.browser.BrowserMediaTraceOwner
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.sin
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real MainActivity + Gecko decoder journey for Android's Media3 handoff. */
@RunWith(AndroidJUnit4::class)
class GeckoMedia3ActivityE2eInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun setUp() {
        BrowserMediaPlaybackService.resetLifecycleTraceForTesting()
        runCatching {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        assumeTrue(
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        assertTrue(ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong()))
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { BrowserMediaPlaybackService.release(context) }
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun finiteGeckoAudioStaysPlayableInBackgroundAndObeysSystemMediaController() {
        FixtureServer().use { server ->
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var host: View
                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().updateVideoAutoplayBlocked(false)
                    assertTrue(activity.browserControllerForTesting().openUrl(server.url))
                }
                await("decoded Gecko audio") {
                    server.samples.any { it.optString("phase") == "loadeddata" }
                }
                val play = server.last("loadeddata")
                val point = IntArray(2)
                scenario.onActivity { activity ->
                    host = requireNotNull(activity.browserControllerForTesting().selectedGeckoViewForTesting())
                    host.getLocationOnScreen(point)
                    val button = play.getJSONArray("button")
                    val scale = host.width / play.getDouble("viewportWidth")
                    point[0] += ((button.getDouble(0) + button.getDouble(2) / 2) * scale).toInt()
                    point[1] += ((button.getDouble(1) + button.getDouble(3) / 2) * scale).toInt()
                }
                assertTrue(UiDevice.getInstance(instrumentation).click(point[0], point[1]))
                await(
                    description = "trusted Gecko play; samples=${server.samples}",
                    diagnostics = { BrowserMediaPlaybackService.lifecycleTraceForTesting() },
                ) {
                    server.samples.any { sample ->
                        sample.optString("phase") == "timeupdate" &&
                            sample.optDouble("currentTime") > 0.5
                    }
                }
                await(
                    description = "Media3 publication from real Gecko media session",
                    diagnostics = { BrowserMediaPlaybackService.lifecycleTraceForTesting() },
                ) {
                    hasRealMediaPublication()
                }
                await(
                    description = "Media notification was not published",
                    diagnostics = ::mediaNotificationDiagnostics,
                ) { hasMediaNotification() }

                val beforeHome = server.last("timeupdate").getDouble("currentTime")
                UiDevice.getInstance(instrumentation).pressHome()
                await("Gecko playback continues after Home") {
                    server.samples.any { sample ->
                        sample.optString("phase") == "timeupdate" &&
                            sample.optDouble("currentTime") > beforeHome + 0.5
                    }
                }

                val controller = MediaController.Builder(
                    context,
                    SessionToken(context, ComponentName(context, BrowserMediaPlaybackService::class.java)),
                ).buildAsync().get(5L, TimeUnit.SECONDS)
                try {
                    instrumentation.runOnMainSync { controller.pause() }
                    await("MediaController pause reaches real Gecko element") {
                        server.samples.any { it.optString("phase") == "pause" }
                    }
                    val pausedAt = server.last("pause").getDouble("currentTime")
                    SystemClock.sleep(600L)
                    assertFalse(
                        "Gecko element kept advancing after MediaController.pause(): ${server.samples}",
                        server.samples.any { sample ->
                            sample.optString("phase") == "timeupdate" &&
                                sample.optDouble("currentTime") > pausedAt + 0.4
                        },
                    )

                    instrumentation.runOnMainSync { controller.play() }
                    await("MediaController play reaches real Gecko element") {
                        server.samples.count { it.optString("phase") == "play" } >= 2
                    }
                    instrumentation.runOnMainSync { controller.seekTo(6_000L) }
                    await("MediaController seek reaches real Gecko element") {
                        server.samples.any { sample ->
                            sample.optString("phase") == "seeked" &&
                                sample.optDouble("currentTime") in 5.0..7.0
                        }
                    }

                    val beforeSleep = server.last("timeupdate").getDouble("currentTime")
                    val device = UiDevice.getInstance(instrumentation)
                    device.sleep()
                    assertFalse("Device did not enter the screen-off state", device.isScreenOn)
                    SystemClock.sleep(500L)
                    assertTrue("Media session was lost while the screen was off", hasRealMediaPublication())
                    device.wakeUp()
                    assertTrue("Device did not wake", device.isScreenOn)
                    device.pressHome()
                    await("Gecko playback resumes after screen lock and unlock") {
                        server.samples.any { sample ->
                            sample.optString("phase") == "timeupdate" &&
                                sample.optDouble("currentTime") > beforeSleep + 0.5
                        }
                    }
                } finally {
                    instrumentation.runOnMainSync { controller.release() }
                }

                lateinit var expectedTraceOwner: BrowserMediaTraceOwner
                instrumentation.runOnMainSync {
                    val expectedOwner = requireNotNull(
                        BrowserMediaPlaybackService.serviceStateForTesting()
                            .publication
                            ?.snapshot
                            ?.owner,
                    )
                    expectedTraceOwner = requireNotNull(
                        BrowserMediaPlaybackService.lifecycleTraceForTesting()
                            .lastOrNull { event ->
                                event.owner?.engineSessionId == expectedOwner.engineSessionId &&
                                    event.owner.navigationGeneration ==
                                    expectedOwner.navigationGeneration
                            }
                            ?.owner,
                    )
                }
                scenario.onActivity { activity -> activity.finishAndRemoveTask() }
                await("task removal dispatches Stop to the exact real Gecko owner") {
                    BrowserMediaPlaybackService.lifecycleTraceForTesting().any { event ->
                        event.source == "BrowserMediaServiceRegistry" &&
                            event.action == "dispatch:Stop" &&
                            event.owner == expectedTraceOwner
                    }
                }
                await("task removal closes the Activity and underlying Gecko session") {
                    scenario.state == Lifecycle.State.DESTROYED
                }
                await("task removal clears publication, route, session, and service state") {
                    hasNoMediaServiceState()
                }
                await("task removal removes the media notification") { !hasMediaNotification() }
                await("task removal stops the media service") { !isMediaServiceRunning() }
                assertExactStopPrecedesTeardown(expectedTraceOwner)
            }
        }
    }

    private fun await(
        description: String,
        diagnostics: () -> Any = { "" },
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + 30_000L
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(50L)
        }
        assertTrue("Timed out: $description; trace=${diagnostics()}", condition())
    }

    private fun hasMediaNotification(): Boolean = context.getSystemService(NotificationManager::class.java)
        .activeNotifications
        .any { notification -> notification.packageName == context.packageName }

    private fun mediaNotificationDiagnostics(): String {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val active = notificationManager.activeNotifications
            .filter { notification -> notification.packageName == context.packageName }
            .map { notification -> "id=${notification.id},channel=${notification.notification.channelId}" }
        return "trace=${BrowserMediaPlaybackService.lifecycleTraceForTesting()}," +
            "channel=${notificationManager.getNotificationChannel(BrowserMediaPlaybackService.CHANNEL_ID)?.id}," +
            "active=$active"
    }

    private fun hasRealMediaPublication(): Boolean {
        var published = false
        instrumentation.runOnMainSync {
            BrowserMediaPlaybackService.serviceStateForTesting().let { state ->
                published = state.publication?.snapshot?.isPlaying == true &&
                    state.playbackState == Player.STATE_READY &&
                    state.sessionCount == 1
            }
        }
        return published
    }

    private fun hasNoMediaServiceState(): Boolean {
        var cleared = false
        instrumentation.runOnMainSync {
            BrowserMediaPlaybackService.serviceStateForTesting().let { state ->
                cleared = state.publication == null &&
                    !state.hasCommandSink &&
                    state.playbackState == null &&
                    state.sessionCount == null &&
                    state.serviceInstanceId == null
            }
        }
        return cleared
    }

    private fun assertExactStopPrecedesTeardown(expectedOwner: BrowserMediaTraceOwner) {
        val trace = BrowserMediaPlaybackService.lifecycleTraceForTesting()
        val stop = requireNotNull(
            trace.firstOrNull { event ->
                event.source == "BrowserMediaServiceRegistry" &&
                    event.action == "dispatch:Stop" &&
                    event.owner == expectedOwner
            },
        ) { "Exact-owner Stop missing from lifecycle trace: $trace" }
        val teardown = trace.filter { event ->
            when (event.source) {
                "BrowserMediaSystemSession" -> event.action == "release"
                "BrowserMediaServiceRegistry" ->
                    event.action == "clear:All" || event.action == "detach-and-clear"
                "BrowserMediaPlaybackService" -> event.action == "lifecycle:on-destroy"
                else -> false
            }
        }
        assertTrue("No Activity/registry teardown in lifecycle trace: $trace", teardown.isNotEmpty())
        assertTrue(
            "Exact-owner Stop must precede Activity/registry teardown: $trace",
            teardown.all { event -> stop.sequence < event.sequence },
        )
    }

    @Suppress("DEPRECATION")
    private fun isMediaServiceRunning(): Boolean = context.getSystemService(ActivityManager::class.java)
        .getRunningServices(Int.MAX_VALUE)
        .any { service ->
            service.service.className == BrowserMediaPlaybackService::class.java.name
        }

    private class FixtureServer : Closeable {
        private val socket = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${socket.localPort}/"
        private val recorded = mutableListOf<JSONObject>()
        val samples: List<JSONObject> get() = synchronized(recorded) { recorded.toList() }
        private val clients = Executors.newFixedThreadPool(4)
        private val thread = Thread(::serve, "gecko-media3-activity-fixture").apply {
            isDaemon = true
            start()
        }

        fun last(phase: String): JSONObject = samples.last { it.optString("phase") == phase }

        private fun serve() {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: return
                clients.execute {
                    client.use { connection ->
                        val reader = connection.getInputStream().bufferedReader()
                        val request = reader.readLine().orEmpty().split(' ').getOrNull(1).orEmpty()
                        while (!reader.readLine().isNullOrEmpty()) {
                            // Drain request headers before responding.
                        }
                        if (request.startsWith("/telemetry?")) {
                            synchronized(recorded) {
                                recorded += JSONObject(
                                    URLDecoder.decode(request.substringAfter('?'), "UTF-8"),
                                )
                            }
                        }
                        val body = if (request == "/audio.wav") AUDIO else HTML.toByteArray()
                        val type = if (request == "/audio.wav") "audio/wav" else "text/html"
                        connection.getOutputStream().use { output ->
                            output.write(
                                "HTTP/1.1 200 OK\r\nContent-Type: $type\r\n".toByteArray(),
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
            thread.join(1_000L)
            clients.shutdownNow()
            clients.awaitTermination(2L, TimeUnit.SECONDS)
        }
    }

    private companion object {
        val AUDIO: ByteArray by lazy {
            val sampleRate = 8_000
            val data = ByteArray(sampleRate * 20) { index ->
                (128 + 80 * sin(index * 2.0 * Math.PI * 440.0 / sampleRate)).toInt().toByte()
            }
            ByteBuffer.allocate(44 + data.size).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray())
                putInt(36 + data.size)
                put("WAVEfmt ".toByteArray())
                putInt(16)
                putShort(1)
                putShort(1)
                putInt(sampleRate)
                putInt(sampleRate)
                putShort(1)
                putShort(8)
                put("data".toByteArray())
                putInt(data.size)
                put(data)
            }.array()
        }
        const val HTML = """<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'><title>Finite Gecko media</title><style>html,body{margin:0;background:#111}main{position:relative;height:360px}audio{display:block;width:100%;margin-top:220px}button{position:absolute;z-index:1;inset:0 auto auto 0;width:100%;height:160px;background:#246;color:#fff;border:0}</style><main><audio preload=auto controls></audio><button aria-label=Play>Play finite audio</button></main><script>const v=document.querySelector('audio'),b=document.querySelector('button');const rect=e=>{const r=e.getBoundingClientRect();return[r.left,r.top,r.width,r.height]};const send=phase=>fetch('/telemetry?'+encodeURIComponent(JSON.stringify({phase,currentTime:v.currentTime,duration:v.duration,paused:v.paused,readyState:v.readyState,button:rect(b),viewportWidth:innerWidth})));for(const e of ['loadeddata','play','pause','seeked','timeupdate','ended','error'])v.addEventListener(e,()=>send(e));v.src='/audio.wav';v.load();let started=false;const start=phase=>{if(started)return;started=true;send(phase);v.play().then(()=>send('play-resolved')).catch(()=>send('play-error'))};b.onpointerdown=()=>start('site-pointerdown');b.onclick=()=>start('site-click');</script>"""
    }
}
