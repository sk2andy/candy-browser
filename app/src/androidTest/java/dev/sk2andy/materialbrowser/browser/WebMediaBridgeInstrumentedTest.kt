package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.MotionEvent
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

        val observerReady = arrayOfNulls<String>(1)
        activityRule.scenario.onActivity {
            webView.evaluateJavascript(
                """
                (() => {
                  globalThis.candyStyleMutationCount = 0;
                  globalThis.candyPlayCommandCount = 0;
                  document.querySelector('video').play = () => {
                    globalThis.candyPlayCommandCount++;
                    return Promise.resolve();
                  };
                  new MutationObserver(records => {
                    globalThis.candyStyleMutationCount += records.length;
                  }).observe(document.documentElement, {
                    attributes: true,
                    subtree: true,
                    attributeFilter: ['style']
                  });
                  return 'ready';
                })()
                """.trimIndent(),
            ) { result -> observerReady[0] = result }
        }
        awaitCondition { observerReady[0] == "\"ready\"" }

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

        val fixedPresentationLayerCount = arrayOfNulls<String>(1)
        activityRule.scenario.onActivity {
            webView.evaluateJavascript(
                """
                (() => {
                  const video = document.querySelector('video');
                  const elements = [];
                  let element = video;
                  while (element) {
                    elements.push(element);
                    element = element.parentElement;
                  }
                  const fullViewportLayers = elements.filter(element => {
                    const style = getComputedStyle(element);
                    return style.position === 'fixed' && style.zIndex === '2147483647';
                  });
                  const ancestorsAreUnclipped = elements
                    .filter(element => element !== video)
                    .every(element => {
                      const style = getComputedStyle(element);
                      return style.position !== 'fixed' &&
                        style.zIndex === 'auto' &&
                        style.overflowX === 'visible' &&
                        style.overflowY === 'visible' &&
                        style.transform === 'none' &&
                        style.contain === 'none' &&
                        style.clipPath === 'none' &&
                        style.transitionProperty === 'none' &&
                        style.animationName === 'none';
                    });
                  return fullViewportLayers.length === 1 &&
                    fullViewportLayers[0] === video &&
                    ancestorsAreUnclipped;
                })()
                """.trimIndent(),
            ) { result -> fixedPresentationLayerCount[0] = result }
        }
        awaitCondition { fixedPresentationLayerCount[0] != null }
        assertEquals("true", fixedPresentationLayerCount[0])

        val firstPresentationMutationCount = awaitStableJavascriptInt(
            webView = webView,
            expression = "globalThis.candyStyleMutationCount",
        )
        val firstPresentationPlayCommandCount = awaitStableJavascriptInt(
            webView = webView,
            expression = "globalThis.candyPlayCommandCount",
        )
        activityRule.scenario.onActivity {
            val controller = requireNotNull(controller)
            repeat(3) { controller.prepareForPictureInPicture() }
            controller.onPictureInPictureModeChanged(true)
        }
        awaitJavascriptIntAtLeast(
            webView = webView,
            expression = "globalThis.candyPlayCommandCount",
            minimum = firstPresentationPlayCommandCount + 6,
        )
        val repeatedPreparationMutationCount = awaitStableJavascriptInt(
            webView = webView,
            expression = "globalThis.candyStyleMutationCount",
        )
        assertEquals(firstPresentationMutationCount, repeatedPreparationMutationCount)

        val styleDriftResult = arrayOfNulls<String>(1)
        activityRule.scenario.onActivity {
            webView.evaluateJavascript(
                """
                (() => {
                  const video = document.querySelector('video');
                  video.style.setProperty('top', '409px', 'important');
                  video.style.setProperty('width', '527px', 'important');
                  return true;
                })()
                """.trimIndent(),
            ) { result -> styleDriftResult[0] = result }
        }
        awaitCondition { styleDriftResult[0] == "true" }
        val repairedStyleResult = arrayOfNulls<String>(1)
        awaitCondition {
            activityRule.scenario.onActivity {
                webView.evaluateJavascript(
                    """
                    (() => {
                      const style = document.querySelector('video').style;
                      return style.getPropertyValue('top') === '0px' &&
                        style.getPropertyPriority('top') === 'important' &&
                        style.getPropertyValue('width') === '100vw' &&
                        style.getPropertyPriority('width') === 'important';
                    })()
                    """.trimIndent(),
                ) { result -> repairedStyleResult[0] = result }
            }
            repairedStyleResult[0] == "true"
        }

        val reparentResult = arrayOfNulls<String>(1)
        activityRule.scenario.onActivity {
            webView.evaluateJavascript(
                """
                (() => {
                  const holder = document.createElement('div');
                  holder.id = 'live-presentation-holder';
                  holder.style.cssText =
                    'overflow:hidden;transform:translateZ(0);contain:paint;clip-path:inset(40%)';
                  document.body.append(holder);
                  holder.append(document.querySelector('video'));
                  return true;
                })()
                """.trimIndent(),
            ) { result -> reparentResult[0] = result }
        }
        awaitCondition { reparentResult[0] == "true" }
        val repairedReparentResult = arrayOfNulls<String>(1)
        awaitCondition {
            activityRule.scenario.onActivity {
                webView.evaluateJavascript(
                    """
                    (() => {
                      const video = document.querySelector('video');
                      const holderStyle = getComputedStyle(video.parentElement);
                      return video.style.position === 'fixed' &&
                        holderStyle.position === 'relative' &&
                        holderStyle.overflow === 'visible' &&
                        holderStyle.transform === 'none' &&
                        holderStyle.contain === 'none' &&
                        holderStyle.clipPath === 'none';
                    })()
                    """.trimIndent(),
                ) { result -> repairedReparentResult[0] = result }
            }
            repairedReparentResult[0] == "true"
        }
        activityRule.scenario.onActivity {
            requireNotNull(controller).onPictureInPictureModeChanged(false)
        }
        val restoredAnimationResult = arrayOfNulls<String>(1)
        awaitCondition {
            activityRule.scenario.onActivity {
                webView.evaluateJavascript(
                    """
                    (() => {
                      const marker = document.querySelector('#transition-marker');
                      const style = getComputedStyle(marker);
                      return style.transitionProperty === 'transform' &&
                        style.animationName === 'pulse' &&
                        marker.style.getPropertyPriority('transition') === 'important' &&
                        marker.style.getPropertyPriority('animation') === 'important';
                    })()
                    """.trimIndent(),
                ) { result -> restoredAnimationResult[0] = result }
            }
            restoredAnimationResult[0] == "true"
        }

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
    fun repeatedPipPreparationRecoversDroppedPresentationCommand() {
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

        val interceptorReady = arrayOfNulls<String>(1)
        activityRule.scenario.onActivity {
            webView.evaluateJavascript(
                """
                (() => {
                  const bridge = globalThis.CandyWebMediaBridge;
                  const originalHandler = bridge.onmessage;
                  globalThis.candyBlockEnterPresentation = true;
                  globalThis.candyBlockedEnterPresentationCount = 0;
                  bridge.onmessage = event => {
                    const message = JSON.parse(event.data);
                    if (
                      globalThis.candyBlockEnterPresentation &&
                      message.command === 'enter-presentation'
                    ) {
                      globalThis.candyBlockedEnterPresentationCount++;
                      return;
                    }
                    originalHandler.call(bridge, event);
                  };
                  return 'ready';
                })()
                """.trimIndent(),
            ) { result -> interceptorReady[0] = result }
        }
        awaitCondition { interceptorReady[0] == "\"ready\"" }

        activityRule.scenario.onActivity {
            requireNotNull(controller).prepareForPictureInPicture()
        }
        awaitJavascriptIntAtLeast(
            webView = webView,
            expression = "globalThis.candyBlockedEnterPresentationCount",
            minimum = 1,
        )
        val blockedPosition = arrayOfNulls<String>(1)
        activityRule.scenario.onActivity {
            webView.evaluateJavascript("document.querySelector('video').style.position") { result ->
                blockedPosition[0] = result
            }
        }
        awaitCondition { blockedPosition[0] != null }
        assertEquals("\"\"", blockedPosition[0])

        val retryReady = arrayOfNulls<String>(1)
        activityRule.scenario.onActivity {
            webView.evaluateJavascript(
                "globalThis.candyBlockEnterPresentation = false",
            ) { result -> retryReady[0] = result }
        }
        awaitCondition { retryReady[0] != null }
        activityRule.scenario.onActivity {
            requireNotNull(controller).prepareForPictureInPicture()
        }
        val retriedPosition = arrayOfNulls<String>(1)
        awaitCondition {
            activityRule.scenario.onActivity {
                webView.evaluateJavascript("document.querySelector('video').style.position") { result ->
                    retriedPosition[0] = result
                }
            }
            retriedPosition[0] == "\"fixed\""
        }
    }

    @Test
    fun playAfterSuppressedPipPauseReconcilesPagePlaybackState() {
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
            webView = controller.selectedWebViewForTesting().apply {
                settings.mediaPlaybackRequiresUserGesture = false
            }
            webView.loadDataWithBaseURL(
                "https://media.example/",
                ACTUAL_PLAYING_VIDEO_HTML,
                "text/html",
                "utf-8",
                null,
            )
        }
        awaitCondition {
            var playingVideo = false
            activityRule.scenario.onActivity {
                playingVideo = controller?.webMediaState?.let { state ->
                    state.kind == WebMediaKind.Video && state.isPlaying
                } == true && controller?.isPictureInPictureEligible == true
            }
            playingVideo
        }
        activityRule.scenario.onActivity {
            val controller = requireNotNull(controller)
            controller.prepareForPictureInPicture()
            controller.onPictureInPictureModeChanged(true)
        }
        val presentationReady = arrayOfNulls<String>(1)
        awaitCondition {
            activityRule.scenario.onActivity {
                webView.evaluateJavascript(
                    "document.querySelector('video').style.position",
                ) { result -> presentationReady[0] = result }
            }
            presentationReady[0] == "\"fixed\""
        }
        SystemClock.sleep(250)
        activityRule.scenario.onActivity {}

        val reconciliationStarted = arrayOfNulls<String>(1)
        activityRule.scenario.onActivity {
            webView.evaluateJavascript(
                """
                (() => {
                  const video = document.querySelector('video');
                  globalThis.candyPagePlaybackState = 'playing';
                  globalThis.candyNativePauseEvents = 0;
                  globalThis.candyNativePlayEvents = 0;
                  video.addEventListener('pause', () => {
                    globalThis.candyNativePauseEvents++;
                    globalThis.candyPagePlaybackState = 'paused';
                  });
                  video.addEventListener('play', () => {
                    globalThis.candyNativePlayEvents++;
                    globalThis.candyPagePlaybackState = 'playing';
                  });
                  globalThis.candyPagePlaybackState = 'paused';
                  video.pause();
                  return !video.paused;
                })()
                """.trimIndent(),
            ) { result -> reconciliationStarted[0] = result }
        }
        awaitCondition { reconciliationStarted[0] != null }
        assertEquals("true", reconciliationStarted[0])
        activityRule.scenario.onActivity {
            val controller = requireNotNull(controller)
            controller.onPictureInPictureModeChanged(false)
            controller.completePictureInPictureReturn()
        }
        val reconciledState = arrayOfNulls<String>(1)
        awaitCondition {
            activityRule.scenario.onActivity {
                webView.evaluateJavascript(
                    """
                    globalThis.candyPagePlaybackState === 'playing' &&
                      globalThis.candyNativePauseEvents >= 1 &&
                      globalThis.candyNativePlayEvents >= 1 &&
                      !document.querySelector('video').paused
                    """.trimIndent(),
                ) { result -> reconciledState[0] = result }
            }
            reconciledState[0] == "true"
        }
        val lateReturnPauseRequested = arrayOfNulls<String>(1)
        activityRule.scenario.onActivity {
            webView.evaluateJavascript(
                """
                (() => {
                  const video = document.querySelector('video');
                  globalThis.candyPagePlaybackState = 'paused';
                  video.pause();
                  return !video.paused;
                })()
                """.trimIndent(),
            ) { result -> lateReturnPauseRequested[0] = result }
        }
        awaitCondition { lateReturnPauseRequested[0] != null }
        assertEquals("true", lateReturnPauseRequested[0])
        activityRule.scenario.onActivity {
            requireNotNull(controller).completePictureInPictureReturn()
        }
        val lateReturnPauseReconciled = arrayOfNulls<String>(1)
        awaitCondition {
            activityRule.scenario.onActivity {
                webView.evaluateJavascript(
                    """
                    globalThis.candyPagePlaybackState === 'playing' &&
                      globalThis.candyNativePauseEvents >= 2 &&
                      globalThis.candyNativePlayEvents >= 2 &&
                      !document.querySelector('video').paused
                    """.trimIndent(),
                ) { result -> lateReturnPauseReconciled[0] = result }
            }
            lateReturnPauseReconciled[0] == "true"
        }

        activityRule.scenario.onActivity {
            val controller = requireNotNull(controller)
            controller.prepareForPictureInPicture()
            controller.onPictureInPictureModeChanged(true)
            controller.pauseActiveWebMedia()
        }
        val explicitlyPaused = arrayOfNulls<String>(1)
        awaitCondition {
            activityRule.scenario.onActivity {
                webView.evaluateJavascript(
                    """
                    document.querySelector('video').paused &&
                      globalThis.candyPagePlaybackState === 'paused'
                    """.trimIndent(),
                ) { result -> explicitlyPaused[0] = result }
            }
            explicitlyPaused[0] == "true"
        }
        activityRule.scenario.onActivity {
            val controller = requireNotNull(controller)
            controller.onPictureInPictureModeChanged(false)
            controller.completePictureInPictureReturn()
            webView.evaluateJavascript(
                "document.querySelector('video').play().catch(() => {})",
                null,
            )
        }
        val pageResumedPlayback = arrayOfNulls<String>(1)
        awaitCondition {
            activityRule.scenario.onActivity {
                webView.evaluateJavascript(
                    """
                    !document.querySelector('video').paused &&
                      globalThis.candyPagePlaybackState === 'playing'
                    """.trimIndent(),
                ) { result -> pageResumedPlayback[0] = result }
            }
            pageResumedPlayback[0] == "true"
        }
        activityRule.scenario.onActivity {
            val controller = requireNotNull(controller)
            controller.prepareForPictureInPicture()
            controller.onPictureInPictureModeChanged(true)
            assertNotNull(controller.fullscreenVideoState)
        }
        val reenteredPresentation = arrayOfNulls<String>(1)
        awaitCondition {
            activityRule.scenario.onActivity {
                webView.evaluateJavascript(
                    "document.querySelector('video').style.position",
                ) { result -> reenteredPresentation[0] = result }
            }
            reenteredPresentation[0] == "\"fixed\""
        }
        activityRule.scenario.onActivity {
            val controller = requireNotNull(controller)
            controller.onPictureInPictureModeChanged(false)
            controller.completePictureInPictureReturn()
            controller.prepareForPictureInPicture()
        }
        val cancelledPauseRequested = arrayOfNulls<String>(1)
        activityRule.scenario.onActivity {
            webView.evaluateJavascript(
                """
                (() => {
                  const video = document.querySelector('video');
                  video.pause();
                  return !video.paused;
                })()
                """.trimIndent(),
            ) { result -> cancelledPauseRequested[0] = result }
        }
        awaitCondition { cancelledPauseRequested[0] != null }
        assertEquals("true", cancelledPauseRequested[0])
        activityRule.scenario.onActivity {
            requireNotNull(controller).cancelPictureInPictureTransition()
        }
        val cancelledTransitionPaused = arrayOfNulls<String>(1)
        awaitCondition {
            activityRule.scenario.onActivity {
                webView.evaluateJavascript(
                    "document.querySelector('video').paused",
                ) { result -> cancelledTransitionPaused[0] = result }
            }
            cancelledTransitionPaused[0] == "true"
        }
    }

    @Test
    fun playingReplacementVideoTakesOverActivePipPresentation() {
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
                REPLACED_PLAYING_VIDEO_HTML,
                "text/html",
                "utf-8",
                null,
            )
        }
        awaitCondition {
            var positionMillis = 0L
            activityRule.scenario.onActivity {
                positionMillis = controller?.webMediaState?.currentPositionMillis ?: 0L
            }
            positionMillis == 12_000L
        }

        activityRule.scenario.onActivity {
            val controller = requireNotNull(controller)
            controller.prepareForPictureInPicture()
            controller.onPictureInPictureModeChanged(true)
        }
        val firstVideoPosition = arrayOfNulls<String>(1)
        awaitCondition {
            activityRule.scenario.onActivity {
                webView.evaluateJavascript("document.querySelector('#first').style.position") {
                    result -> firstVideoPosition[0] = result
                }
            }
            firstVideoPosition[0] == "\"fixed\""
        }
        SystemClock.sleep(350)
        activityRule.scenario.onActivity {}

        val interceptorReady = arrayOfNulls<String>(1)
        activityRule.scenario.onActivity {
            webView.evaluateJavascript(
                """
                (() => {
                  const bridge = globalThis.CandyWebMediaBridge;
                  const originalHandler = bridge.onmessage;
                  globalThis.candyDroppedReplacementPresentation = false;
                  bridge.onmessage = event => {
                    const message = JSON.parse(event.data);
                    if (
                      !globalThis.candyDroppedReplacementPresentation &&
                      message.command === 'enter-presentation'
                    ) {
                      globalThis.candyDroppedReplacementPresentation = true;
                      return;
                    }
                    originalHandler.call(bridge, event);
                  };
                  return 'ready';
                })()
                """.trimIndent(),
            ) { result -> interceptorReady[0] = result }
        }
        awaitCondition { interceptorReady[0] == "\"ready\"" }

        val replacementStarted = arrayOfNulls<String>(1)
        activityRule.scenario.onActivity {
            webView.evaluateJavascript("startReplacementVideo()") { result ->
                replacementStarted[0] = result
            }
        }
        awaitCondition { replacementStarted[0] == "true" }
        val presentationOwner = arrayOfNulls<String>(1)
        awaitCondition {
            activityRule.scenario.onActivity {
                webView.evaluateJavascript(
                    """
                    globalThis.removedFirst.style.position === '' &&
                      document.querySelector('#second').style.position === 'fixed'
                    """.trimIndent(),
                ) { result -> presentationOwner[0] = result }
            }
            presentationOwner[0] == "true"
        }
        activityRule.scenario.onActivity {
            val state = requireNotNull(requireNotNull(controller).webMediaState)
            assertEquals(20_000L, state.currentPositionMillis)
            assertTrue(state.isPlaying)
        }
        val replacementPresentationWasRetried = arrayOfNulls<String>(1)
        activityRule.scenario.onActivity {
            webView.evaluateJavascript(
                "globalThis.candyDroppedReplacementPresentation",
            ) { result -> replacementPresentationWasRetried[0] = result }
        }
        awaitCondition { replacementPresentationWasRetried[0] == "true" }
    }

    @Test
    fun chromiumCustomViewFallsBackToPresentedWebViewWithoutEndingSession() {
        lateinit var webView: WebView
        var callbackInvoked = false
        var customRevision = 0
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
            controller.showFullscreenVideoForTesting(
                FrameLayout(activity),
                WebChromeClient.CustomViewCallback { callbackInvoked = true },
            )
            customRevision = requireNotNull(controller.fullscreenVideoState).sourceRevision
            controller.prepareForPictureInPicture()
        }
        val moveResult = arrayOfNulls<String>(1)
        activityRule.scenario.onActivity {
            webView.evaluateJavascript(
                """
                (() => {
                  const holder = document.createElement('div');
                  holder.id = 'replacement-holder';
                  document.body.append(holder);
                  holder.append(document.querySelector('video'));
                  return document.querySelector('video').isConnected;
                })()
                """.trimIndent(),
            ) { result -> moveResult[0] = result }
        }
        awaitCondition { moveResult[0] == "true" }

        activityRule.scenario.onActivity {
            val controller = requireNotNull(controller)
            controller.hideFullscreenVideoForTesting()
        }
        val replacementHolderPosition = arrayOfNulls<String>(1)
        awaitCondition {
            activityRule.scenario.onActivity {
                webView.evaluateJavascript(
                    "document.querySelector('#replacement-holder').style.position",
                ) { result -> replacementHolderPosition[0] = result }
            }
            replacementHolderPosition[0] == "\"relative\""
        }
        activityRule.scenario.onActivity { activity ->
            requireNotNull(controller).showFullscreenVideoForTesting(
                FrameLayout(activity),
                WebChromeClient.CustomViewCallback { callbackInvoked = true },
            )
        }
        val removeResult = arrayOfNulls<String>(1)
        activityRule.scenario.onActivity {
            webView.evaluateJavascript(
                """
                (() => {
                  globalThis.removedPresentationHolder =
                    document.querySelector('#replacement-holder');
                  document.body.append(document.querySelector('video'));
                  globalThis.removedPresentationHolder.remove();
                  return document.querySelector('video').isConnected;
                })()
                """.trimIndent(),
            ) { result -> removeResult[0] = result }
        }
        awaitCondition { removeResult[0] == "true" }
        activityRule.scenario.onActivity {
            requireNotNull(controller).hideFullscreenVideoForTesting()
        }
        val removedHolderPosition = arrayOfNulls<String>(1)
        awaitCondition {
            activityRule.scenario.onActivity {
                webView.evaluateJavascript(
                    "globalThis.removedPresentationHolder.style.position",
                ) { result -> removedHolderPosition[0] = result }
            }
            removedHolderPosition[0] == "\"\""
        }
        activityRule.scenario.onActivity { activity ->
            val controller = requireNotNull(controller)
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
    fun sameTabAudioControlsDoNotReplaceMutedVideoPipPresentation() {
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
                "https://mixed-media.example/",
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
            assertEquals(sourceTabId, controller.backgroundAudioTabIdForTesting)
            controller.selectTab(sourceTabId)
            controller.attachSelectedWebView(container)
            sourceWebView.evaluateJavascript(
                """
                (() => {
                  const video = document.createElement('video');
                  video.id = 'muted-pip-video';
                  Object.defineProperty(video, 'paused', { value: false, configurable: true });
                  Object.defineProperty(video, 'ended', { value: false, configurable: true });
                  Object.defineProperty(video, 'currentTime', {
                    value: 16,
                    writable: true,
                    configurable: true
                  });
                  Object.defineProperty(video, 'duration', { value: 120, configurable: true });
                  Object.defineProperty(video, 'videoWidth', { value: 640, configurable: true });
                  Object.defineProperty(video, 'videoHeight', { value: 360, configurable: true });
                  Object.defineProperty(video, 'muted', { value: true, configurable: true });
                  Object.defineProperty(video, 'volume', { value: 0, configurable: true });
                  video.getBoundingClientRect = () => ({
                    left: 0, top: 0, right: 320, bottom: 180, width: 320, height: 180
                  });
                  document.body.append(video);
                  video.dispatchEvent(new Event('playing'));
                  return true;
                })()
                """.trimIndent(),
                null,
            )
        }
        awaitCondition {
            var videoIsActive = false
            activityRule.scenario.onActivity {
                videoIsActive = controller?.webMediaState?.kind == WebMediaKind.Video
            }
            videoIsActive
        }
        activityRule.scenario.onActivity {
            val controller = requireNotNull(controller)
            assertEquals(WebMediaKind.Audio, controller.systemWebMediaState?.kind)
            controller.prepareForPictureInPicture()
            controller.onPictureInPictureModeChanged(true)
        }
        val videoPresented = arrayOfNulls<String>(1)
        awaitCondition {
            activityRule.scenario.onActivity {
                sourceWebView.evaluateJavascript(
                    "document.querySelector('#muted-pip-video').style.position",
                ) { result -> videoPresented[0] = result }
            }
            videoPresented[0] == "\"fixed\""
        }

        activityRule.scenario.onActivity {
            requireNotNull(controller).playActiveWebMedia()
        }
        SystemClock.sleep(350)
        activityRule.scenario.onActivity {}
        val presentationOwner = arrayOfNulls<String>(1)
        activityRule.scenario.onActivity {
            sourceWebView.evaluateJavascript(
                """
                document.querySelector('#muted-pip-video').style.position === 'fixed' &&
                  document.querySelector('audio').style.position !== 'fixed'
                """.trimIndent(),
            ) { result -> presentationOwner[0] = result }
        }
        awaitCondition { presentationOwner[0] != null }
        assertEquals("true", presentationOwner[0])

        activityRule.scenario.onActivity {
            requireNotNull(controller).pauseActiveWebMedia()
        }
        val presentationSurvivedPause = arrayOfNulls<String>(1)
        activityRule.scenario.onActivity {
            sourceWebView.evaluateJavascript(
                "document.querySelector('#muted-pip-video').style.position",
            ) { result -> presentationSurvivedPause[0] = result }
        }
        awaitCondition { presentationSurvivedPause[0] != null }
        assertEquals("\"fixed\"", presentationSurvivedPause[0])
    }

    @Test
    fun sitePictureInPictureButtonUsesAndroidPictureInPictureLifecycle() {
        lateinit var webView: WebView
        var nativeRequestCount = 0
        activityRule.scenario.onActivity { activity ->
            clearSession(activity)
            assumeTrue(
                activity.packageManager.hasSystemFeature(
                    PackageManager.FEATURE_PICTURE_IN_PICTURE,
                ),
            )
            val controller = BrowserController(
                activity = activity,
                onWebPictureInPictureRequested = {
                    nativeRequestCount++
                    true
                },
            ).also { this.controller = it }
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
                "https://pip.example/",
                PICTURE_IN_PICTURE_REQUEST_VIDEO_HTML,
                "text/html",
                "utf-8",
                null,
            )
        }

        awaitCondition {
            var eligible = false
            activityRule.scenario.onActivity {
                eligible = controller?.isPictureInPictureEligible == true
            }
            eligible
        }
        assertEquals("true", evaluate(webView, "String(document.pictureInPictureEnabled)"))
        assertEquals(
            "function",
            evaluate(webView, "typeof document.querySelector('video').requestPictureInPicture"),
        )

        evaluate(
            webView,
            """
            globalThis.programmaticPipResult = 'pending';
            document.querySelector('video').requestPictureInPicture().then(
              () => { globalThis.programmaticPipResult = 'resolved'; },
              error => { globalThis.programmaticPipResult = error.name; }
            );
            """.trimIndent(),
        )
        awaitCondition {
            evaluate(webView, "globalThis.programmaticPipResult") == "NotAllowedError"
        }
        assertEquals(0, nativeRequestCount)

        dispatchTap(webView)
        awaitCondition { nativeRequestCount == 1 }
        assertEquals("pending", evaluate(webView, "globalThis.sitePipResult"))

        activityRule.scenario.onActivity {
            repeat(2) { requireNotNull(controller).onPictureInPictureModeChanged(true) }
        }
        awaitCondition { evaluate(webView, "globalThis.sitePipResult") == "resolved" }
        assertEquals("true", evaluate(webView, "document.pictureInPictureElement !== null"))
        assertEquals("1", evaluate(webView, "String(globalThis.enterPipEventCount)"))
        assertEquals("true", evaluate(webView, "globalThis.sitePipWindowHasSize"))

        activityRule.scenario.onActivity {
            repeat(2) { requireNotNull(controller).onPictureInPictureModeChanged(false) }
        }
        awaitCondition { evaluate(webView, "document.pictureInPictureElement === null") == "true" }
        assertEquals("1", evaluate(webView, "String(globalThis.leavePipEventCount)"))
    }

    @Test
    fun iframeSitePictureInPictureUsesFullscreenVideoSession() {
        lateinit var webView: WebView
        var nativeRequestCount = 0
        activityRule.scenario.onActivity { activity ->
            clearSession(activity)
            assumeTrue(
                activity.packageManager.hasSystemFeature(
                    PackageManager.FEATURE_PICTURE_IN_PICTURE,
                ),
            )
            val controller = BrowserController(
                activity = activity,
                onWebPictureInPictureRequested = {
                    nativeRequestCount++
                    true
                },
            ).also { this.controller = it }
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
                "https://host.example/",
                IFRAME_PICTURE_IN_PICTURE_REQUEST_VIDEO_HTML,
                "text/html",
                "utf-8",
                null,
            )
        }

        awaitCondition {
            evaluate(
                webView,
                "String(frames[0]?.document?.pictureInPictureEnabled === true)",
            ) == "true"
        }
        dispatchTap(webView)
        awaitCondition {
            evaluate(webView, "frames[0].sitePipResult") != "idle"
        }
        awaitCondition {
            nativeRequestCount == 1 ||
                evaluate(webView, "frames[0].sitePipResult") != "pending"
        }
        assertEquals(
            "iframe PiP result=${evaluate(webView, "frames[0].sitePipResult")}",
            1,
            nativeRequestCount,
        )
        assertEquals("pending", evaluate(webView, "frames[0].sitePipResult"))
        activityRule.scenario.onActivity {
            assertEquals(
                FullscreenVideoSource.CustomView,
                requireNotNull(controller).fullscreenVideoState?.source,
            )
            requireNotNull(controller).onPictureInPictureModeChanged(true)
        }
        awaitCondition { evaluate(webView, "frames[0].sitePipResult") == "resolved" }

        activityRule.scenario.onActivity {
            requireNotNull(controller).onPictureInPictureModeChanged(false)
        }
        awaitCondition {
            var source: FullscreenVideoSource? = FullscreenVideoSource.CustomView
            activityRule.scenario.onActivity { source = controller?.fullscreenVideoState?.source }
            source != FullscreenVideoSource.CustomView
        }
        assertEquals(
            "true",
            evaluate(webView, "frames[0].document.pictureInPictureElement === null"),
        )
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

    private fun awaitStableJavascriptInt(
        webView: WebView,
        expression: String,
        timeoutMillis: Long = 5_000,
    ): Int {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        var previous: Int? = null
        var stableReads = 0
        while (SystemClock.uptimeMillis() < deadline) {
            val result = arrayOfNulls<String>(1)
            activityRule.scenario.onActivity {
                webView.evaluateJavascript(expression) { value -> result[0] = value }
            }
            awaitCondition(timeoutMillis = 1_000) { result[0] != null }
            val current = requireNotNull(result[0]).toInt()
            if (current == previous) stableReads++ else stableReads = 0
            if (stableReads >= 2) return current
            previous = current
            SystemClock.sleep(50)
        }
        error("JavaScript value did not stabilize within $timeoutMillis ms")
    }

    private fun awaitJavascriptIntAtLeast(
        webView: WebView,
        expression: String,
        minimum: Int,
        timeoutMillis: Long = 5_000,
    ) {
        var current: Int? = null
        awaitCondition(timeoutMillis) {
            val result = arrayOfNulls<String>(1)
            activityRule.scenario.onActivity {
                webView.evaluateJavascript(expression) { value -> result[0] = value }
            }
            awaitCondition(timeoutMillis = 1_000) { result[0] != null }
            current = requireNotNull(result[0]).toInt()
            requireNotNull(current) >= minimum
        }
        assertTrue("Expected at least $minimum, got $current", requireNotNull(current) >= minimum)
    }

    private fun evaluate(webView: WebView, script: String): String? {
        val result = arrayOfNulls<String>(1)
        activityRule.scenario.onActivity {
            webView.evaluateJavascript(script) { value ->
                result[0] = value?.removeSurrounding("\"")
            }
        }
        awaitCondition(timeoutMillis = 1_000) { result[0] != null }
        return result[0]
    }

    private fun dispatchTap(webView: WebView) {
        activityRule.scenario.onActivity {
            val downTime = SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                MotionEvent.obtain(
                    downTime,
                    SystemClock.uptimeMillis(),
                    action,
                    50f,
                    50f,
                    0,
                ).let { event ->
                    webView.dispatchTouchEvent(event)
                    event.recycle()
                }
            }
        }
    }

    private companion object {
        val PLAYING_VIDEO_HTML =
            """
            <!doctype html>
            <html>
            <head>
              <style>@keyframes pulse { from { opacity: .9; } to { opacity: 1; } }</style>
            </head>
            <body>
              <div
                id="transition-marker"
                style="overflow:hidden;transform:translateZ(0);contain:paint;
                  transition:transform 10s linear!important;
                  animation:pulse 10s linear infinite!important"
              >
                <div style="clip-path:inset(30%);filter:blur(0)">
                  <div style="overflow:hidden;perspective:500px">
                    <video style="width:320px;height:180px"></video>
                  </div>
                </div>
              </div>
              <script>
                const video = document.querySelector('video');
                Object.defineProperty(video, 'paused', { value: false, configurable: true });
                Object.defineProperty(video, 'ended', { value: false, configurable: true });
                Object.defineProperty(video, 'currentTime', { value: 12, writable: true, configurable: true });
                Object.defineProperty(video, 'duration', { value: 120, configurable: true });
                Object.defineProperty(video, 'videoWidth', { value: 640, configurable: true });
                Object.defineProperty(video, 'videoHeight', { value: 360, configurable: true });
                Object.defineProperty(video, 'readyState', { value: 4, configurable: true });
                Object.defineProperty(window, 'innerWidth', { value: 1080, configurable: true });
                Object.defineProperty(window, 'innerHeight', { value: 1920, configurable: true });
                video.getBoundingClientRect = () => ({
                  left: 0, top: 0, right: 320, bottom: 180, width: 320, height: 180
                });
                setTimeout(() => video.dispatchEvent(new Event('playing')), 250);
              </script>
            </body></html>
            """.trimIndent()

        val PICTURE_IN_PICTURE_REQUEST_VIDEO_HTML = PLAYING_VIDEO_HTML
            .replace(
                "<body>",
                """
                <body>
                  <button
                    style="position:fixed;inset:0;width:200px;height:200px;z-index:100"
                    onclick="
                      globalThis.sitePipResult = 'pending';
                      document.querySelector('video').requestPictureInPicture().then(
                        pipWindow => {
                          globalThis.sitePipResult = 'resolved';
                          globalThis.sitePipWindowHasSize =
                            pipWindow.width > 0 && pipWindow.height > 0;
                        },
                        error => { globalThis.sitePipResult = error.name; }
                      );
                    "
                  >PiP</button>
                """.trimIndent(),
            )
            .replace(
                "const video = document.querySelector('video');",
                """
                const video = document.querySelector('video');
                globalThis.sitePipResult = 'idle';
                globalThis.enterPipEventCount = 0;
                globalThis.leavePipEventCount = 0;
                video.addEventListener('enterpictureinpicture', () => {
                  globalThis.enterPipEventCount++;
                });
                video.addEventListener('leavepictureinpicture', () => {
                  globalThis.leavePipEventCount++;
                });
                """.trimIndent(),
            )

        val IFRAME_PICTURE_IN_PICTURE_REQUEST_VIDEO_HTML =
            """
            <!doctype html>
            <html><body style="margin:0">
              <iframe
                allow="fullscreen; picture-in-picture"
                allowfullscreen
                style="position:fixed;inset:0;width:100%;height:100%;border:0"
              ></iframe>
              <button
                style="position:fixed;inset:0;width:200px;height:200px;z-index:10"
                onclick="frames[0].requestSitePip()"
              >PiP</button>
              <script>
                const frame = document.querySelector('iframe');
                frame.srcdoc = `
                  <!doctype html>
                  <html><body style="margin:0">
                    <video style="width:320px;height:180px"></video>
                    <script>
                      const video = document.querySelector('video');
                      globalThis.sitePipResult = 'idle';
                      Object.defineProperty(video, 'paused', {
                        value: false, configurable: true
                      });
                      Object.defineProperty(video, 'ended', {
                        value: false, configurable: true
                      });
                      Object.defineProperty(video, 'currentTime', {
                        value: 12, configurable: true
                      });
                      Object.defineProperty(video, 'duration', {
                        value: 120, configurable: true
                      });
                      Object.defineProperty(video, 'videoWidth', {
                        value: 640, configurable: true
                      });
                      Object.defineProperty(video, 'videoHeight', {
                        value: 360, configurable: true
                      });
                      Object.defineProperty(video, 'readyState', {
                        value: 4, configurable: true
                      });
                      video.getBoundingClientRect = () => ({
                        left: 0, top: 0, right: 320, bottom: 180,
                        width: 320, height: 180
                      });
                      globalThis.requestSitePip = () => {
                        globalThis.sitePipResult = 'pending';
                        video.requestPictureInPicture().then(
                          () => { globalThis.sitePipResult = 'resolved'; },
                          error => { globalThis.sitePipResult = error.name; }
                        );
                      };
                      setTimeout(() => video.dispatchEvent(new Event('playing')), 100);
                    <\/script>
                  </body></html>
                `;
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

        val REPLACED_PLAYING_VIDEO_HTML =
            """
            <!doctype html>
            <html><body>
              <video id="first" style="width:320px;height:180px"></video>
              <script>
                const first = document.querySelector('#first');
                let firstPaused = false;
                let firstTime = 12;
                const configure = (video, paused, currentTime) => {
                  Object.defineProperty(video, 'paused', {
                    get: paused,
                    configurable: true
                  });
                  Object.defineProperty(video, 'ended', { value: false, configurable: true });
                  Object.defineProperty(video, 'currentTime', {
                    get: currentTime,
                    configurable: true
                  });
                  Object.defineProperty(video, 'duration', { value: 120, configurable: true });
                  Object.defineProperty(video, 'videoWidth', { value: 640, configurable: true });
                  Object.defineProperty(video, 'videoHeight', { value: 360, configurable: true });
                };
                configure(first, () => firstPaused, () => firstTime);
                first.getBoundingClientRect = () => ({
                  left: 0, top: 0, right: 320, bottom: 180, width: 320, height: 180
                });
                globalThis.startReplacementVideo = () => {
                  firstPaused = true;
                  firstTime = 0;
                  first.dispatchEvent(new Event('pause'));
                  globalThis.removedFirst = first;
                  first.remove();
                  const second = document.createElement('video');
                  second.id = 'second';
                  second.style.cssText = 'width:320px;height:180px';
                  configure(second, () => false, () => 20);
                  second.getBoundingClientRect = () => ({
                    left: 0,
                    top: 5000,
                    right: 320,
                    bottom: 5180,
                    width: 320,
                    height: 180
                  });
                  document.body.append(second);
                  second.dispatchEvent(new Event('playing'));
                  return true;
                };
                setTimeout(() => first.dispatchEvent(new Event('playing')), 250);
              </script>
            </body></html>
            """.trimIndent()

        val ACTUAL_PLAYING_VIDEO_HTML =
            """
            <!doctype html>
            <html><body>
              <video muted loop playsinline style="width:320px;height:180px"></video>
              <script>
                const video = document.querySelector('video');
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
                video.src = 'data:video/webm;base64,' + encodedVideo;
                video.play().catch(() => {});
              </script>
            </body></html>
            """.trimIndent()
    }
}
