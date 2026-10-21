package dev.sk2andy.materialbrowser.browser.systemwebview

import android.os.SystemClock
import android.util.Base64
import android.view.InputDevice
import android.view.MotionEvent
import android.webkit.WebView
import androidx.core.graphics.Insets
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.engine.BrowserEngineContentKind
import dev.sk2andy.materialbrowser.browser.gecko.AndroidBrowserEngineSessionPort
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEngineEventSink
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPrivacyPolicy
import dev.sk2andy.materialbrowser.browser.gecko.GeckoScrollTestActivity
import dev.sk2andy.materialbrowser.browser.gecko.GeckoViewInsetHost
import dev.sk2andy.materialbrowser.browser.gecko.GeckoViewInsetRules
import dev.sk2andy.materialbrowser.browser.gecko.GeckoViewInsets
import dev.sk2andy.materialbrowser.data.GeckoSafeAreaSettings
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommands
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class SystemWebViewVideoPlaybackInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun manuallyStartedVideoKeepsDecodedFramesWhenReparented() {
        withPlayingFixture { fixture ->
            val before = report(fixture)
            evaluate(fixture, "document.querySelector('#replacement').appendChild(video)")
            val after = awaitDecodedProgress(fixture, before, "Reparenting stopped manually started video")
            assertEquals("replacement", after.getString("parent"))
            assertEquals(before.getInt("pauses"), after.getInt("pauses"))
        }
    }

    @Test
    fun clearingAutoplayAttributeKeepsManuallyStartedVideoPlaying() {
        withPlayingFixture { fixture ->
            listOf("video.autoplay = false", "video.removeAttribute('autoplay')").forEach { clearAutoplay ->
                val before = report(fixture)
                // Both changes occur in one task; observers must inspect the final disabled state.
                evaluate(fixture, "video.setAttribute('autoplay', ''); $clearAutoplay")
                val after = awaitDecodedProgress(fixture, before, "Clearing autoplay stopped user playback")
                assertTrue("Autoplay must remain disabled: $after", !after.getBoolean("autoplay"))
                assertEquals(before.getInt("pauses"), after.getInt("pauses"))
            }
        }
    }

    @Test
    fun trustedHoldChangesSpeedWithoutFreezingAndCommentsStayAboveVideo() {
        withPlayingFixture { fixture ->
            val before = report(fixture)
            val downTime = SystemClock.uptimeMillis()
            sendTouch(fixture, "surface", MotionEvent.ACTION_DOWN, downTime)
            try {
                val accelerated = awaitReport(fixture, "Trusted hold did not enable double speed") {
                    it.getBoolean("trustedHold") && it.getDouble("rate") == 2.0
                }
                awaitDecodedProgress(fixture, accelerated, "Double-speed video stopped producing frames")
            } finally {
                sendTouch(fixture, "surface", MotionEvent.ACTION_UP, downTime)
            }
            val released = awaitReport(fixture, "Releasing hold did not restore normal speed") {
                it.getDouble("rate") == 1.0
            }
            val afterHold = awaitDecodedProgress(fixture, released, "Video stopped after double-speed hold")
            assertEquals(before.getInt("pauses"), afterHold.getInt("pauses"))

            click(fixture, "commentsButton")
            val comments = awaitReport(fixture, "Comments were not above the playing video") {
                it.getBoolean("commentsVisible") && it.getBoolean("commentsHit")
            }
            click(fixture, "comment")
            awaitReport(fixture, "Comments did not receive the trusted touch") { it.getInt("commentClicks") == 1 }
            awaitDecodedProgress(fixture, comments, "Opening comments stopped decoded playback")
        }
    }

    private fun withPlayingFixture(action: (VideoFixture) -> Unit) {
        val encodedVideo = instrumentation.context.assets.open("system_webview_motion.webm").use {
            Base64.encodeToString(it.readBytes(), Base64.NO_WRAP)
        }
        ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
            lateinit var factory: SystemWebViewBrowserEngineFactory
            lateinit var session: AndroidBrowserEngineSessionPort
            lateinit var webView: WebView
            scenario.onActivity { activity ->
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                factory = SystemWebViewBrowserEngineFactory(activity)
                session = factory.create(
                    tabId = "video-${UUID.randomUUID()}",
                    profileId = "default",
                    isPrivate = false,
                    contentKind = BrowserEngineContentKind.RegularTab,
                    privacyPolicy = GeckoPrivacyPolicy.Disabled.copy(
                        cssSafeAreaTopInsetPx = TOP_INSET_PX,
                        geckoSafeAreaSettings = GeckoSafeAreaSettings(),
                    ),
                    eventSink = BrowserEngineEventSink {},
                )
                webView = session.createView(activity) as WebView
                activity.setContentView(webView)
                session.setActive(true)
                session.setVideoAutoplayBlocked(true)
                (webView as GeckoViewInsetHost).updateInsets(
                    GeckoViewInsetRules.resolve(
                        safeArea = GeckoViewInsets(left = 0, top = TOP_INSET_PX, right = 0, bottom = 0),
                        forceNativeSafeArea = false,
                        forceNativeTopSafeArea = false,
                        isFullscreenContent = false,
                        isInsideSafeDrawingHost = false,
                        useNativeCssSafeArea = true,
                    ),
                    WindowInsetsCompat.Builder()
                        .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, TOP_INSET_PX, 0, 0))
                        .build(),
                )
                webView.loadDataWithBaseURL(
                    "https://video.test/",
                    fixtureHtml(encodedVideo),
                    "text/html",
                    "utf-8",
                    null,
                )
            }
            val fixture = VideoFixture(scenario, webView)
            try {
                val ready = awaitReport(fixture, "Decoded video fixture did not load") {
                    it.optInt("readyState") >= 2 && it.optDouble("safeTop", 0.0) > 0
                }
                assertTrue("Fixture must start paused: $ready", ready.getBoolean("paused"))
                click(fixture, "start")
                awaitReport(fixture, "Trusted Play did not start decoded video") {
                    it.getBoolean("trustedPlay") && !it.getBoolean("paused") && it.getInt("frames") >= 3
                }
                action(fixture)
            } finally {
                scenario.onActivity {
                    session.releaseView(webView)
                    session.setActive(false)
                    session.execute(BrowserEngineCommands.close())
                    factory.shutdown()
                }
            }
        }
    }

    private fun awaitDecodedProgress(fixture: VideoFixture, before: JSONObject, message: String): JSONObject =
        awaitReport(fixture, message) {
            !it.getBoolean("paused") &&
                it.getInt("frames") >= before.getInt("frames") + 3 &&
                it.getInt("pixelChanges") >= before.getInt("pixelChanges") + 2 &&
                it.getDouble("mediaTime") > before.getDouble("mediaTime") + 0.1
        }

    private fun awaitReport(
        fixture: VideoFixture,
        message: String,
        condition: (JSONObject) -> Boolean,
    ): JSONObject {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        var latest = JSONObject()
        while (SystemClock.elapsedRealtime() < deadline) {
            latest = report(fixture)
            if (condition(latest)) return latest
            SystemClock.sleep(POLL_MILLIS)
        }
        throw AssertionError("$message: $latest")
    }

    private fun report(fixture: VideoFixture): JSONObject {
        val value = evaluate(fixture, "JSON.stringify(window.videoReport?.() || {})")
        return JSONObject(JSONObject("{\"value\":$value}").getString("value"))
    }

    private fun evaluate(fixture: VideoFixture, script: String): String {
        val result = AtomicReference<String>()
        val ready = CountDownLatch(1)
        fixture.scenario.onActivity {
            fixture.webView.evaluateJavascript(script) {
                result.set(it)
                ready.countDown()
            }
        }
        assertTrue("Video fixture JavaScript timed out", ready.await(5, TimeUnit.SECONDS))
        return result.get()
    }

    private fun click(fixture: VideoFixture, id: String) {
        val downTime = SystemClock.uptimeMillis()
        sendTouch(fixture, id, MotionEvent.ACTION_DOWN, downTime)
        sendTouch(fixture, id, MotionEvent.ACTION_UP, downTime)
    }

    private fun sendTouch(fixture: VideoFixture, id: String, action: Int, downTime: Long) {
        val point = JSONObject(
            evaluate(
                fixture,
                "JSON.stringify((() => { const r = document.getElementById('$id').getBoundingClientRect(); " +
                    "return {x:r.left+r.width/2,y:r.top+r.height/2,width:innerWidth}; })())",
            ).let { JSONObject("{\"value\":$it}").getString("value") },
        )
        val location = IntArray(2)
        var scale = 1f
        fixture.scenario.onActivity {
            fixture.webView.getLocationOnScreen(location)
            scale = fixture.webView.width / point.getDouble("width").toFloat()
        }
        MotionEvent.obtain(
            downTime,
            SystemClock.uptimeMillis(),
            action,
            location[0] + point.getDouble("x").toFloat() * scale,
            location[1] + point.getDouble("y").toFloat() * scale,
            0,
        ).also { event ->
            try {
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                instrumentation.sendPointerSync(event)
            } finally {
                event.recycle()
            }
        }
    }

    private fun fixtureHtml(encodedVideo: String): String = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>
          html,body { margin:0; background:#eee; font-family:sans-serif; }
          #surface { position:sticky; top:0; height:180px; background:black; touch-action:none; user-select:none; }
          video { position:absolute; inset:0; width:100%; height:100%; object-fit:contain; }
          button { position:fixed; left:16px; bottom:16px; height:52px; min-width:120px; }
          #commentsButton { left:160px; }
          #comments { position:fixed; inset:0; z-index:50; background:#fff; }
          #comments[hidden] { display:none; }
          #comment { margin:80px 16px 0; padding:32px 16px; background:#eee; }
        </style></head><body>
        <div id="surface" class="video-player"><div id="original"><video muted playsinline preload="auto"></video></div>
          <div id="replacement"></div><button id="start">Play</button></div>
        <div style="height:1000px">Video page</div><button id="commentsButton">Comments</button>
        <section id="comments" role="dialog" aria-modal="true" hidden><div id="comment">Comment</div></section>
        <script>
          const video = document.querySelector('video');
          const surface = document.querySelector('#surface');
          const comments = document.querySelector('#comments');
          let frames=0, mediaTime=0, pixelChanges=0, previousPixels='', pauses=0;
          let trustedPlay=false, trustedHold=false, commentClicks=0, holdTimer=0;
          const canvas=document.createElement('canvas'); canvas.width=96; canvas.height=54;
          const context=canvas.getContext('2d', {willReadFrequently:true});
          const frame = (_now, metadata) => {
            frames++; mediaTime=metadata.mediaTime;
            context.drawImage(video,0,0,96,54);
            const pixels=Array.from(context.getImageData(0,0,96,54).data).join(',');
            if(previousPixels && pixels!==previousPixels) pixelChanges++;
            previousPixels=pixels;
            video.requestVideoFrameCallback(frame);
          };
          video.requestVideoFrameCallback(frame);
          video.addEventListener('pause',()=>pauses++);
          document.querySelector('#start').addEventListener('click',event=>{
            trustedPlay=event.isTrusted;
            video.play().catch(error=>{window.playError=String(error);});
          });
          surface.addEventListener('contextmenu',event=>event.preventDefault());
          surface.addEventListener('pointerdown',event=>{
            if(!event.isTrusted) return;
            holdTimer=setTimeout(()=>{trustedHold=true; video.playbackRate=2;},650);
          });
          const release=()=>{clearTimeout(holdTimer); video.playbackRate=1;};
          surface.addEventListener('pointerup',release);
          surface.addEventListener('pointercancel',release);
          document.querySelector('#commentsButton').addEventListener('click',()=>{comments.hidden=false;});
          document.querySelector('#comment').addEventListener('click',event=>{if(event.isTrusted) commentClicks++;});
          window.videoReport=()=>{
            const comment=document.querySelector('#comment'), r=comment.getBoundingClientRect();
            const hit=document.elementFromPoint(r.left+r.width/2,r.top+r.height/2);
            return {readyState:video.readyState, paused:video.paused, autoplay:video.autoplay,
              rate:video.playbackRate, frames, mediaTime, pixelChanges, pauses, trustedPlay, trustedHold,
              parent:video.parentElement.id, commentClicks, commentsVisible:!comments.hidden,
              commentsHit:!!hit && comment.contains(hit),
              safeTop:parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--candy-safe-area-inset-top'))||0,
              playError:window.playError||null};
          };
          video.src='data:video/webm;base64,$encodedVideo'; video.load();
        </script></body></html>
    """.trimIndent()

    private data class VideoFixture(
        val scenario: ActivityScenario<GeckoScrollTestActivity>,
        val webView: WebView,
    )

    private companion object {
        const val TOP_INSET_PX = 48
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 50L
    }
}
