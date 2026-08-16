package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.os.SystemClock
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebMediaBridgeInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private var controller: BrowserController? = null

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
    }

    @Test
    fun playingVideoPublishesStateAndBecomesPipPresentation() {
        lateinit var webView: WebView
        activityRule.scenario.onActivity { activity ->
            clearSession(activity)
            val controller = BrowserController(activity).also { this.controller = it }
            assumeTrue(
                controller.isVideoAutoplayBlockingSupported &&
                    androidx.webkit.WebViewFeature.isFeatureSupported(
                        androidx.webkit.WebViewFeature.WEB_MESSAGE_LISTENER,
                    ),
            )
            controller.onResume()
            val container = FrameLayout(activity)
            activity.setContentView(container)
            controller.attachSelectedWebView(container)
            webView = controller.selectedWebViewForTesting()
            webView.loadDataWithBaseURL(
                "https://media.example/",
                TOKEN_PROBE_VIDEO_HTML,
                "text/html",
                "utf-8",
                null,
            )
        }

        awaitCondition {
            var state: WebMediaState? = null
            activityRule.scenario.onActivity { state = controller?.webMediaState }
            state?.isPlaying == true
        }

        activityRule.scenario.onActivity {
            val controller = requireNotNull(controller)
            val state = requireNotNull(controller.webMediaState)
            assertEquals(WebMediaKind.Video, state.kind)
            assertEquals(640, state.videoWidth)
            assertTrue("Expected PiP-eligible state, got $state", controller.isPictureInPictureEligible)

            controller.prepareForPictureInPicture()

            assertNotNull(controller.fullscreenVideoState)
            assertTrue(controller.isPictureInPictureEligible)
        }

        val presentationResult = arrayOfNulls<String>(1)
        activityRule.scenario.onActivity {
            webView.evaluateJavascript(
                "document.querySelector('video').style.position",
            ) { result -> presentationResult[0] = result }
        }
        awaitCondition { presentationResult[0] != null }
        assertEquals("\"fixed\"", presentationResult[0])

        val tokenProbeResult = arrayOfNulls<String>(1)
        activityRule.scenario.onActivity {
            webView.evaluateJavascript(
                "typeof globalThis.stolenBridgeToken",
            ) { result -> tokenProbeResult[0] = result }
        }
        awaitCondition { tokenProbeResult[0] != null }
        assertEquals("\"undefined\"", tokenProbeResult[0])

        val moveResult = arrayOfNulls<String>(1)
        activityRule.scenario.onActivity {
            requireNotNull(controller).exitFullscreenVideo()
            webView.evaluateJavascript(
                """
                (() => {
                  const holder = document.createElement('div');
                  document.body.append(holder);
                  holder.append(document.querySelector('video'));
                  return document.querySelector('video').isConnected;
                })()
                """.trimIndent(),
            ) { result -> moveResult[0] = result }
        }
        awaitCondition { moveResult[0] == "true" }
        activityRule.scenario.onActivity {
            requireNotNull(controller).prepareForPictureInPicture()
        }
        val reenteredPosition = arrayOfNulls<String>(1)
        activityRule.scenario.onActivity {
            webView.evaluateJavascript("document.querySelector('video').style.position") { result ->
                reenteredPosition[0] = result
            }
        }
        awaitCondition { reenteredPosition[0] != null }
        assertEquals("\"fixed\"", reenteredPosition[0])
    }

    @Test
    fun chromiumCustomViewFallsBackToPresentedWebViewWithoutEndingSession() {
        lateinit var webView: WebView
        activityRule.scenario.onActivity { activity ->
            clearSession(activity)
            val controller = BrowserController(activity).also { this.controller = it }
            assumeTrue(
                controller.isVideoAutoplayBlockingSupported &&
                    androidx.webkit.WebViewFeature.isFeatureSupported(
                        androidx.webkit.WebViewFeature.WEB_MESSAGE_LISTENER,
                    ),
            )
            controller.onResume()
            val container = FrameLayout(activity)
            activity.setContentView(container)
            controller.attachSelectedWebView(container)
            webView = controller.selectedWebViewForTesting()
            webView.loadDataWithBaseURL(
                "https://media.example/",
                PLAYING_VIDEO_HTML,
                "text/html",
                "utf-8",
                null,
            )
        }
        awaitCondition {
            var playing = false
            activityRule.scenario.onActivity { playing = controller?.webMediaState?.isPlaying == true }
            playing
        }

        activityRule.scenario.onActivity { activity ->
            val controller = requireNotNull(controller)
            var callbackInvoked = false
            controller.showFullscreenVideoForTesting(
                FrameLayout(activity),
                WebChromeClient.CustomViewCallback { callbackInvoked = true },
            )
            val customRevision = requireNotNull(controller.fullscreenVideoState).sourceRevision

            controller.prepareForPictureInPicture()
            controller.hideFullscreenVideoForTesting()

            val fallbackState = requireNotNull(controller.fullscreenVideoState)
            val host = FrameLayout(activity)
            controller.attachFullscreenVideoView(host)
            assertTrue(fallbackState.sourceRevision > customRevision)
            assertSame(webView, host.getChildAt(0))
            assertFalse(callbackInvoked)
        }
    }

    @Test
    fun lateBridgeChannelReplacesHiddenCustomViewAfterPipGracePeriod() {
        lateinit var webView: WebView
        lateinit var customView: FrameLayout
        activityRule.scenario.onActivity { activity ->
            clearSession(activity)
            val controller = BrowserController(activity).also { this.controller = it }
            assumeTrue(
                controller.isVideoAutoplayBlockingSupported &&
                    androidx.webkit.WebViewFeature.isFeatureSupported(
                        androidx.webkit.WebViewFeature.WEB_MESSAGE_LISTENER,
                    ),
            )
            controller.onResume()
            val container = FrameLayout(activity)
            activity.setContentView(container)
            controller.attachSelectedWebView(container)
            webView = controller.selectedWebViewForTesting()
            webView.loadDataWithBaseURL(
                "https://media.example/",
                LATE_PLAYING_VIDEO_HTML,
                "text/html",
                "utf-8",
                null,
            )
        }
        val readyState = arrayOfNulls<String>(1)
        awaitCondition {
            activityRule.scenario.onActivity {
                webView.evaluateJavascript("document.readyState") { result -> readyState[0] = result }
            }
            readyState[0] == "\"complete\""
        }
        activityRule.scenario.onActivity { activity ->
            val controller = requireNotNull(controller)
            customView = FrameLayout(activity)
            controller.showFullscreenVideoForTesting(
                customView,
                WebChromeClient.CustomViewCallback {},
            )
            controller.prepareForPictureInPicture()
            controller.onPictureInPictureModeChanged(true)
            controller.hideFullscreenVideoForTesting()
            val initialHost = FrameLayout(activity)
            controller.attachFullscreenVideoView(initialHost)
            assertSame(customView, initialHost.getChildAt(0))
            webView.evaluateJavascript("createVideo()", null)
        }

        awaitCondition(timeoutMillis = 5_000) {
            var sourceIsWebView = false
            activityRule.scenario.onActivity { activity ->
                val host = FrameLayout(activity)
                controller?.attachFullscreenVideoView(host)
                sourceIsWebView = host.getChildAt(0) === webView
            }
            sourceIsWebView
        }
    }

    @Test
    fun audibleAudioRemainsSystemMediaWhenAnotherTabOpens() {
        var sourceTabId = ""
        lateinit var container: FrameLayout
        lateinit var sourceWebView: WebView
        activityRule.scenario.onActivity { activity ->
            clearSession(activity)
            val controller = BrowserController(activity).also { this.controller = it }
            assumeTrue(
                controller.isVideoAutoplayBlockingSupported &&
                    androidx.webkit.WebViewFeature.isFeatureSupported(
                        androidx.webkit.WebViewFeature.WEB_MESSAGE_LISTENER,
                    ),
            )
            controller.onResume()
            container = FrameLayout(activity)
            activity.setContentView(container)
            controller.attachSelectedWebView(container)
            sourceTabId = controller.selectedTabId
            sourceWebView = controller.selectedWebViewForTesting()
            sourceWebView.settings.mediaPlaybackRequiresUserGesture = false
            sourceWebView.loadDataWithBaseURL(
                "https://audio.example/",
                PLAYING_AUDIO_HTML,
                "text/html",
                "utf-8",
                null,
            )
        }
        awaitCondition {
            var playingAudio = false
            activityRule.scenario.onActivity {
                playingAudio = controller?.webMediaState?.let { state ->
                    state.kind == WebMediaKind.Audio && state.isPlaying
                } == true
            }
            playingAudio
        }

        activityRule.scenario.onActivity {
            val controller = requireNotNull(controller)
            controller.createTab()
            controller.attachSelectedWebView(container)
            controller.selectedWebViewForTesting().loadDataWithBaseURL(
                "https://muted-video.example/",
                PLAYING_MUTED_VIDEO_HTML,
                "text/html",
                "utf-8",
                null,
            )
        }
        awaitCondition {
            var ownersAreSplit = false
            activityRule.scenario.onActivity {
                val controller = requireNotNull(controller)
                ownersAreSplit = controller.webMediaState?.kind == WebMediaKind.Video &&
                    controller.systemWebMediaState?.kind == WebMediaKind.Audio
            }
            ownersAreSplit
        }
        activityRule.scenario.onActivity {
            val controller = requireNotNull(controller)
            controller.prepareForPictureInPicture()
            assertNotNull(controller.fullscreenVideoState)
        }

        activityRule.scenario.onActivity {
            requireNotNull(controller).pauseActiveWebMedia()
        }
        val audioPausedResult = arrayOfNulls<String>(1)
        awaitCondition {
            activityRule.scenario.onActivity {
                sourceWebView.evaluateJavascript("globalThis.audioPaused") { result ->
                    audioPausedResult[0] = result
                }
            }
            audioPausedResult[0] == "true"
        }
        activityRule.scenario.onActivity {
            val controller = requireNotNull(controller)
            val systemState = requireNotNull(controller.systemWebMediaState)
            assertEquals(sourceTabId, systemState.tabId)
            assertFalse(systemState.isPlaying)
            assertEquals(sourceTabId, controller.backgroundAudioTabIdForTesting)
            controller.seekActiveWebMedia(42_000)
        }
        val seekResult = arrayOfNulls<String>(1)
        awaitCondition {
            activityRule.scenario.onActivity {
                sourceWebView.evaluateJavascript("document.querySelector('audio').currentTime") {
                    result -> seekResult[0] = result
                }
            }
            seekResult[0] == "42"
        }
        activityRule.scenario.onActivity {
            requireNotNull(controller).playActiveWebMedia()
        }
        val audioPlayingResult = arrayOfNulls<String>(1)
        awaitCondition {
            activityRule.scenario.onActivity {
                sourceWebView.evaluateJavascript("globalThis.audioPaused") { result ->
                    audioPlayingResult[0] = result
                }
            }
            audioPlayingResult[0] == "false"
        }
        activityRule.scenario.onActivity {
            val controller = requireNotNull(controller)
            val systemState = requireNotNull(controller.systemWebMediaState)
            assertEquals(sourceTabId, systemState.tabId)
            assertTrue(systemState.isPlaying)
            controller.stopActiveWebMedia()
            assertNull(controller.backgroundAudioTabIdForTesting)
            assertNotNull(controller.fullscreenVideoState)
        }
    }

    @Test
    fun audibleAudioBecomesBackgroundOwnerWhenActivityPauses() {
        var sourceTabId = ""
        activityRule.scenario.onActivity { activity ->
            clearSession(activity)
            val controller = BrowserController(activity).also { this.controller = it }
            assumeTrue(
                controller.isVideoAutoplayBlockingSupported &&
                    androidx.webkit.WebViewFeature.isFeatureSupported(
                        androidx.webkit.WebViewFeature.WEB_MESSAGE_LISTENER,
                    ),
            )
            controller.onResume()
            val container = FrameLayout(activity)
            activity.setContentView(container)
            controller.attachSelectedWebView(container)
            sourceTabId = controller.selectedTabId
            controller.selectedWebViewForTesting().apply {
                settings.mediaPlaybackRequiresUserGesture = false
            }.loadDataWithBaseURL(
                "https://audio.example/",
                PLAYING_AUDIO_HTML,
                "text/html",
                "utf-8",
                null,
            )
        }
        awaitCondition {
            var playingAudio = false
            activityRule.scenario.onActivity {
                playingAudio = controller?.webMediaState?.let { state ->
                    state.kind == WebMediaKind.Audio && state.isPlaying
                } == true
            }
            playingAudio
        }

        activityRule.scenario.onActivity {
            val controller = requireNotNull(controller)
            controller.onPause()

            assertEquals(sourceTabId, controller.backgroundAudioTabIdForTesting)
            assertEquals(sourceTabId, requireNotNull(controller.systemWebMediaState).tabId)
        }
    }

    private fun clearSession(activity: ComponentActivity) {
        activity.getSharedPreferences(
            BrowserSessionStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }

    private fun awaitCondition(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        assertTrue("Condition was not met within $timeoutMillis ms", condition())
    }

    private companion object {
        val PLAYING_VIDEO_HTML =
            """
            <!doctype html>
            <html><body>
              <video style="width:320px;height:180px"></video>
              <script>
                const video = document.querySelector('video');
                Object.defineProperty(video, 'paused', { value: false, configurable: true });
                Object.defineProperty(video, 'ended', { value: false, configurable: true });
                Object.defineProperty(video, 'currentTime', { value: 12, writable: true, configurable: true });
                Object.defineProperty(video, 'duration', { value: 120, configurable: true });
                Object.defineProperty(video, 'videoWidth', { value: 640, configurable: true });
                Object.defineProperty(video, 'videoHeight', { value: 360, configurable: true });
                Object.defineProperty(window, 'innerWidth', { value: 1080, configurable: true });
                Object.defineProperty(window, 'innerHeight', { value: 1920, configurable: true });
                video.getBoundingClientRect = () => ({
                  left: 0, top: 0, right: 320, bottom: 180, width: 320, height: 180
                });
                setTimeout(() => video.dispatchEvent(new Event('playing')), 250);
              </script>
            </body></html>
            """.trimIndent()

        val PLAYING_AUDIO_HTML =
            """
            <!doctype html>
            <html><body>
              <audio muted></audio>
              <script>
                const audio = document.querySelector('audio');
                globalThis.audioPaused = false;
                audio.addEventListener('pause', () => { globalThis.audioPaused = true; });
                audio.addEventListener('playing', () => { globalThis.audioPaused = false; });
                const context = new AudioContext();
                const oscillator = context.createOscillator();
                const destination = context.createMediaStreamDestination();
                oscillator.connect(destination);
                oscillator.start();
                audio.srcObject = destination.stream;
                audio.play().then(() => {
                  Object.defineProperty(audio, 'currentTime', {
                    value: 8,
                    writable: true,
                    configurable: true
                  });
                  Object.defineProperty(audio, 'duration', {
                    value: 180,
                    configurable: true
                  });
                  Object.defineProperty(audio, 'muted', {
                    value: false,
                    configurable: true
                  });
                  Object.defineProperty(audio, 'volume', {
                    value: 1,
                    configurable: true
                  });
                  audio.dispatchEvent(new Event('volumechange'));
                });
              </script>
            </body></html>
            """.trimIndent()

        val TOKEN_PROBE_VIDEO_HTML = PLAYING_VIDEO_HTML.replace(
            "const video = document.querySelector('video');",
            """
            Object.prototype.toJSON = function() {
              if (this.bridgeToken) globalThis.stolenBridgeToken = this.bridgeToken;
              return this;
            };
            const video = document.querySelector('video');
            """.trimIndent(),
        )

        val PLAYING_MUTED_VIDEO_HTML = PLAYING_VIDEO_HTML.replace(
            "const video = document.querySelector('video');",
            """
            const video = document.querySelector('video');
            Object.defineProperty(video, 'muted', { value: true, configurable: true });
            Object.defineProperty(video, 'volume', { value: 0, configurable: true });
            """.trimIndent(),
        )

        val LATE_PLAYING_VIDEO_HTML =
            """
            <!doctype html>
            <html><body>
              <script>
                function createVideo() {
                  const video = document.createElement('video');
                  video.style.cssText = 'width:320px;height:180px';
                  Object.defineProperty(video, 'paused', { value: false, configurable: true });
                  Object.defineProperty(video, 'ended', { value: false, configurable: true });
                  Object.defineProperty(video, 'currentTime', { value: 12, configurable: true });
                  Object.defineProperty(video, 'duration', { value: 120, configurable: true });
                  Object.defineProperty(video, 'videoWidth', { value: 640, configurable: true });
                  Object.defineProperty(video, 'videoHeight', { value: 360, configurable: true });
                  video.getBoundingClientRect = () => ({
                    left: 0, top: 0, right: 320, bottom: 180, width: 320, height: 180
                  });
                  document.body.append(video);
                  video.dispatchEvent(new Event('playing'));
                }
              </script>
            </body></html>
            """.trimIndent()
    }
}
