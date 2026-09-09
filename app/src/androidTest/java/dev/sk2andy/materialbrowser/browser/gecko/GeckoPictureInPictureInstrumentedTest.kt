package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.FullscreenVideoSource
import dev.sk2andy.materialbrowser.browser.FullscreenVideoHost
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoPictureInPictureInstrumentedTest {
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
    fun selectedRegularFullscreenGeckoVideoSurvivesPictureInPictureLifecycle() {
        lateinit var stableGeckoHost: View
        lateinit var initialEngineView: View
        lateinit var initialBrowserContainer: ViewGroup
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertTrue(controller.usesGeckoEngine)
                assertTrue(controller.openUrl("https://media.example/"))
            }
            awaitCondition {
                var attached = false
                scenario.onActivity { activity ->
                    attached = activity.browserControllerForTesting()
                        .selectedGeckoViewForTesting()
                        ?.isAttachedToWindow == true
                }
                attached
            }
            scenario.onActivity { activity ->
                stableGeckoHost = requireNotNull(
                    activity.browserControllerForTesting().selectedGeckoViewForTesting(),
                )
                initialEngineView = requireNotNull(
                    (stableGeckoHost as ViewGroup).singleChild(),
                )
                initialBrowserContainer = requireNotNull(stableGeckoHost.parent as? ViewGroup)
                assertNotNull(
                    initialEngineView.findDescendant(SurfaceView::class.java),
                )
                activity.browserControllerForTesting().reportSelectedGeckoMediaStateForTesting(
                    eligibleMediaState(),
                )
                activity.browserControllerForTesting()
                    .reportSelectedGeckoFullscreenStateForTesting(true)
            }
            awaitCondition {
                var eligible = false
                scenario.onActivity { activity ->
                    eligible = activity.isPictureInPictureEligibleForTesting()
                }
                eligible
            }
            scenario.onActivity { activity ->
                activity.prepareForPictureInPictureTransitionForTesting()
                val state = activity.browserControllerForTesting().fullscreenVideoState
                assertNotNull(state)
                assertEquals(FullscreenVideoSource.GeckoView, state?.source)
                assertEquals(FullscreenVideoHost.BrowserViewport, state?.host)
                val unchangedEngineView = requireNotNull(
                    (stableGeckoHost as ViewGroup).singleChild(),
                )
                assertSame(
                    stableGeckoHost,
                    activity.browserControllerForTesting().selectedGeckoViewForTesting(),
                )
                assertSame(initialBrowserContainer, parentOf(stableGeckoHost))
                assertSame(initialEngineView, unchangedEngineView)
                assertNotNull(unchangedEngineView.findDescendant(SurfaceView::class.java))
                activity.browserControllerForTesting().onStop(
                    isInPictureInPictureMode = false,
                )
            }
            awaitCondition {
                var surfaceReady = false
                scenario.onActivity { activity ->
                    val host = activity.browserControllerForTesting()
                        .selectedGeckoViewForTesting() as? ViewGroup
                    val surface = host?.singleChild()?.findDescendant(SurfaceView::class.java)
                    surfaceReady = surface?.let { view ->
                        view.isAttachedToWindow && view.width > 0 && view.height > 0
                    } == true
                }
                surfaceReady
            }
            scenario.onActivity { activity ->
                assertNotNull(activity.browserControllerForTesting().fullscreenVideoState)
                val rotatedConfiguration = Configuration(activity.resources.configuration).apply {
                    orientation = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        Configuration.ORIENTATION_PORTRAIT
                    } else {
                        Configuration.ORIENTATION_LANDSCAPE
                    }
                }
                activity.onPictureInPictureModeChanged(
                    true,
                    rotatedConfiguration,
                )
                activity.onPictureInPictureModeChanged(true, rotatedConfiguration)
                assertNotNull(activity.browserControllerForTesting().fullscreenVideoState)
                activity.browserControllerForTesting()
                    .reportSelectedGeckoFullscreenStateForTesting(false)
                assertNotNull(activity.browserControllerForTesting().fullscreenVideoState)
                activity.onPictureInPictureModeChanged(
                    false,
                    Configuration(activity.resources.configuration),
                )
                activity.onPictureInPictureModeChanged(
                    false,
                    Configuration(activity.resources.configuration),
                )
                val restoredEngineView = requireNotNull(
                    (stableGeckoHost as ViewGroup).singleChild(),
                )
                assertSame(
                    stableGeckoHost,
                    activity.browserControllerForTesting().selectedGeckoViewForTesting(),
                )
                assertSame(initialBrowserContainer, parentOf(stableGeckoHost))
                assertSame(initialEngineView, restoredEngineView)
                assertNotNull(restoredEngineView.findDescendant(SurfaceView::class.java))
                assertNull(activity.browserControllerForTesting().fullscreenVideoState)
            }
        }
    }

    @Test
    fun systemPictureInPictureKeepsGeckoRenderHostAttached() {
        lateinit var stableGeckoHost: View
        lateinit var stableEngineView: View
        lateinit var stableBrowserContainer: ViewGroup
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertTrue(controller.openUrl("https://media.example/"))
            }
            awaitCondition {
                var attached = false
                scenario.onActivity { activity ->
                    attached = activity.browserControllerForTesting()
                        .selectedGeckoViewForTesting()
                        ?.isAttachedToWindow == true
                }
                attached
            }
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                stableGeckoHost = requireNotNull(controller.selectedGeckoViewForTesting())
                stableEngineView = requireNotNull((stableGeckoHost as ViewGroup).singleChild())
                stableBrowserContainer = requireNotNull(parentOf(stableGeckoHost) as? ViewGroup)
                controller.reportSelectedGeckoMediaStateForTesting(eligibleMediaState())
                controller.reportSelectedGeckoFullscreenStateForTesting(true)
                assertTrue(activity.onPictureInPictureRequested())
            }
            awaitCondition {
                var entered = false
                scenario.onActivity { activity -> entered = activity.isInPictureInPictureMode }
                entered
            }
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertSame(stableGeckoHost, controller.selectedGeckoViewForTesting())
                assertSame(stableBrowserContainer, parentOf(stableGeckoHost))
                assertSame(stableEngineView, (stableGeckoHost as ViewGroup).singleChild())
                assertTrue(stableGeckoHost.isAttachedToWindow)
                assertNotNull(stableEngineView.findDescendant(SurfaceView::class.java))
            }
        }
    }

    @Test
    fun privateGeckoVideoCannotEnterPictureInPicture() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertTrue(controller.setBlankTabIncognito(true))
                controller.reportSelectedGeckoMediaStateForTesting(eligibleMediaState())
                assertFalse(controller.isPictureInPictureEligible)
                assertFalse(activity.isPictureInPictureEligibleForTesting())
            }
        }
    }

    @Test
    fun videoOnlyPresentationAlignsAnOffsetInlineVideoWithTheViewport() {
        FixtureServer(INLINE_OFFSET_HTML).use { server ->
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertTrue(activity.browserControllerForTesting().openUrl(server.url))
                }
                var loadedTitle = "not observed"
                awaitCondition(description = { loadedTitle }) {
                    var loaded = false
                    scenario.onActivity { activity ->
                        loadedTitle = activity.browserControllerForTesting().selectedTab.title
                        loaded = loadedTitle == INLINE_LOADED_TITLE
                    }
                    loaded
                }
                val tapPoint = FloatArray(2)
                scenario.onActivity { activity ->
                    val host = requireNotNull(
                        activity.browserControllerForTesting().selectedGeckoViewForTesting(),
                    )
                    val location = IntArray(2)
                    host.getLocationOnScreen(location)
                    tapPoint[0] = location[0] + host.width / 2f
                    tapPoint[1] = location[1] + host.height / 2f
                }
                tap(x = tapPoint[0], y = tapPoint[1])
                var readyTitle = "not observed"
                awaitCondition(description = { readyTitle }) {
                    var ready = false
                    scenario.onActivity { activity ->
                        readyTitle = activity.browserControllerForTesting().selectedTab.title
                        ready = readyTitle == INLINE_READY_TITLE
                    }
                    ready
                }
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    controller.reportSelectedGeckoMediaStateForTesting(eligibleMediaState())
                    controller.reportSelectedGeckoFullscreenStateForTesting(true)
                    activity.prepareForPictureInPictureTransitionForTesting()
                }
                var realignedTitle = "not observed"
                var realignedState = "not observed"
                awaitCondition(description = { "$realignedTitle; $realignedState" }) {
                    var realigned = false
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        realignedTitle = controller.selectedTab.title
                        realignedState = controller.geckoFullscreenDebugStateForTesting(
                            controller.selectedTab.id,
                        )
                        realigned = realignedTitle == INLINE_REALIGNED_TITLE
                    }
                    realigned
                }
                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().cancelPictureInPictureTransition()
                }
                var restoredTitle = "not observed"
                awaitCondition(description = { restoredTitle }) {
                    var restored = false
                    scenario.onActivity { activity ->
                        restoredTitle = activity.browserControllerForTesting().selectedTab.title
                        restored = restoredTitle == INLINE_RESTORED_TITLE
                    }
                    restored
                }
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    controller.reportSelectedGeckoMediaStateForTesting(eligibleMediaState())
                    activity.prepareForPictureInPictureTransitionForTesting()
                }
                realignedTitle = "not observed after restart"
                awaitCondition(description = { realignedTitle }) {
                    var realigned = false
                    scenario.onActivity { activity ->
                        realignedTitle = activity.browserControllerForTesting().selectedTab.title
                        realigned = realignedTitle == INLINE_REALIGNED_TITLE
                    }
                    realigned
                }
                scenario.onActivity { activity ->
                    activity.browserControllerForTesting()
                        .reportSelectedGeckoMediaStateForTesting(GeckoMediaSessionState())
                }
                restoredTitle = "not observed after media stop"
                awaitCondition(description = { restoredTitle }) {
                    var restored = false
                    scenario.onActivity { activity ->
                        restoredTitle = activity.browserControllerForTesting().selectedTab.title
                        restored = restoredTitle == INLINE_RESTORED_TITLE
                    }
                    restored
                }
            }
        }
    }

    @Test
    fun localFullscreenVideoKeepsOneRenderHostAcrossPictureInPictureCallbacks() {
        FixtureServer().use { server ->
            lateinit var stableGeckoHost: View
            lateinit var stableEngineView: View
            lateinit var stableBrowserContainer: ViewGroup
            lateinit var videoTabId: String
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    videoTabId = controller.selectedTab.id
                    assertTrue(controller.openUrl(server.url))
                }
                awaitCondition {
                    var ready = false
                    scenario.onActivity { activity ->
                        ready = activity.browserControllerForTesting().selectedTab.title == READY_TITLE
                    }
                    ready
                }
                val tapPoint = FloatArray(2)
                scenario.onActivity { activity ->
                    stableGeckoHost = requireNotNull(
                        activity.browserControllerForTesting().selectedGeckoViewForTesting(),
                    )
                    stableEngineView = requireNotNull(
                        (stableGeckoHost as ViewGroup).singleChild(),
                    )
                    stableBrowserContainer = requireNotNull(
                        parentOf(stableGeckoHost) as? ViewGroup,
                    )
                    val location = IntArray(2)
                    stableGeckoHost.getLocationOnScreen(location)
                    tapPoint[0] = location[0] + stableGeckoHost.width / 2f
                    tapPoint[1] = location[1] + stableGeckoHost.height / 2f
                }
                tap(x = tapPoint[0], y = tapPoint[1])
                awaitCondition {
                    var eligible = false
                    scenario.onActivity { activity ->
                        eligible = activity.browserControllerForTesting().isPictureInPictureEligible
                    }
                    eligible
                }
                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().minimizeFullscreenVideo()
                }
                awaitCondition {
                    var minimizedOnStableHost = false
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        minimizedOnStableHost =
                            controller.fullscreenVideoState?.host == FullscreenVideoHost.Overlay &&
                            controller.selectedGeckoViewForTesting() === stableGeckoHost &&
                            parentOf(stableGeckoHost) !== stableBrowserContainer
                    }
                    minimizedOnStableHost
                }
                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().expandFullscreenVideo()
                }
                awaitCondition {
                    var expandedOnStableHost = false
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        expandedOnStableHost =
                            controller.fullscreenVideoState?.host ==
                            FullscreenVideoHost.BrowserViewport &&
                            controller.selectedGeckoViewForTesting() === stableGeckoHost &&
                            parentOf(stableGeckoHost) === stableBrowserContainer
                    }
                    expandedOnStableHost
                }
                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().createTab()
                }
                var backgroundHostSnapshot = "not observed"
                awaitCondition(description = { backgroundHostSnapshot }) {
                    var backgroundVideoIsHosted = false
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        val currentParent = parentOf(stableGeckoHost)
                        backgroundHostSnapshot =
                            "state=${controller.fullscreenVideoState}, " +
                            "attached=${stableGeckoHost.isAttachedToWindow}, " +
                            "parent=${currentParent?.javaClass?.name}, " +
                            "browserParent=${currentParent === stableBrowserContainer}, " +
                            controller.geckoFullscreenDebugStateForTesting(videoTabId)
                        backgroundVideoIsHosted =
                            controller.fullscreenVideoState?.host ==
                            FullscreenVideoHost.Overlay &&
                            stableGeckoHost.isAttachedToWindow &&
                            parentOf(stableGeckoHost) !== stableBrowserContainer
                    }
                    backgroundVideoIsHosted
                }
                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().selectTab(videoTabId)
                }
                var restoredHostSnapshot = "not observed"
                awaitCondition(description = { restoredHostSnapshot }) {
                    var restoredToBrowserHost = false
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        val currentParent = parentOf(stableGeckoHost)
                        restoredHostSnapshot =
                            "state=${controller.fullscreenVideoState}, " +
                            "selectedViewIsStable=${controller.selectedGeckoViewForTesting() === stableGeckoHost}, " +
                            "attached=${stableGeckoHost.isAttachedToWindow}, " +
                            "parent=${currentParent?.javaClass?.name}, " +
                            "browserParent=${currentParent === stableBrowserContainer}, " +
                            controller.geckoFullscreenDebugStateForTesting(videoTabId)
                        restoredToBrowserHost =
                            controller.fullscreenVideoState?.host ==
                            FullscreenVideoHost.BrowserViewport &&
                            controller.selectedGeckoViewForTesting() === stableGeckoHost &&
                            stableGeckoHost.isAttachedToWindow &&
                            parentOf(stableGeckoHost) is ViewGroup &&
                            (stableGeckoHost as ViewGroup).singleChild() === stableEngineView
                    }
                    restoredToBrowserHost
                }
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    stableBrowserContainer = requireNotNull(stableGeckoHost.parent as? ViewGroup)
                    assertEquals(FullscreenVideoHost.BrowserViewport, controller.fullscreenVideoState?.host)
                    activity.prepareForPictureInPictureTransitionForTesting()
                    activity.onPictureInPictureModeChanged(
                        true,
                        Configuration(activity.resources.configuration),
                    )
                    activity.onPictureInPictureModeChanged(
                        false,
                        Configuration(activity.resources.configuration),
                    )
                    assertSame(stableGeckoHost, controller.selectedGeckoViewForTesting())
                    assertSame(stableBrowserContainer, parentOf(stableGeckoHost))
                    assertSame(stableEngineView, (stableGeckoHost as ViewGroup).singleChild())
                    assertNotNull(stableEngineView.findDescendant(SurfaceView::class.java))
                    controller.exitFullscreenVideo()
                }
                awaitCondition {
                    var exited = false
                    scenario.onActivity { activity ->
                        exited = activity.browserControllerForTesting().selectedTab.title ==
                            EXITED_TITLE
                    }
                    exited
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
        description: () -> String = { "Condition not met" },
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

    private fun eligibleMediaState() = GeckoMediaSessionState(
        isActive = true,
        isPlaying = true,
        isFullscreen = true,
        currentPositionMillis = 1_000,
        durationMillis = 60_000,
        videoWidth = 1_280,
        videoHeight = 720,
        videoTrackCount = 1,
    )

    private fun ViewGroup.singleChild(): View? =
        takeIf { childCount == 1 }?.getChildAt(0)

    private fun parentOf(view: View): Any? = view.parent

    private fun <T : View> View.findDescendant(type: Class<T>): T? {
        if (type.isInstance(this)) return type.cast(this)
        if (this !is ViewGroup) return null
        repeat(childCount) { index ->
            getChildAt(index).findDescendant(type)?.let { descendant -> return descendant }
        }
        return null
    }

    private class FixtureServer(private val html: String = HTML) : Closeable {
        private val socket = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${socket.localPort}/"
        private val thread = Thread(::serve, "gecko-pip-fixture").apply {
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
        const val READY_TITLE = "ready"
        const val EXITED_TITLE = "exited"
        const val INLINE_LOADED_TITLE = "inline-loaded"
        const val INLINE_READY_TITLE = "inline-ready"
        const val INLINE_PRESENTED_TITLE = "inline-presented"
        const val INLINE_REALIGNED_TITLE = "inline-realigned"
        const val INLINE_RESTORED_TITLE = "inline-restored"
        val INLINE_OFFSET_HTML =
            """
            <!doctype html>
            <html><head><title>loading</title><style>
              html,body { margin:0; width:100%; height:100%; background:#000; }
              header { height:48px; background:#f00; }
              #player {
                width:100%;
                aspect-ratio:16/9;
                transform:translateZ(0);
              }
              video { display:block; width:100%; height:100%; background:#080; }
            </style></head><body>
              <header></header><div id="player"><video muted playsinline loop></video></div>
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
                const header = document.querySelector('header');
                const player = document.querySelector('#player');
                video.defaultMuted = true;
                video.muted = true;
                video.src = 'data:video/webm;base64,' + encodedVideo;
                video.addEventListener('loadedmetadata', () => {
                  document.title = '$INLINE_LOADED_TITLE';
                });
                document.addEventListener('click', () => {
                  video.play().then(() => {
                    document.title = '$INLINE_READY_TITLE';
                  });
                }, { once:true });
                new MutationObserver(() => {
                  const presented = video.hasAttribute('data-candy-picture-in-picture-video');
                  if (!presented && ![
                    '$INLINE_PRESENTED_TITLE',
                    '$INLINE_REALIGNED_TITLE'
                  ].includes(document.title)) return;
                  requestAnimationFrame(() => requestAnimationFrame(() => {
                    const bounds = video.getBoundingClientRect();
                    if (!presented) {
                      const restored = bounds.top > 1 &&
                        getComputedStyle(header).visibility === 'visible' &&
                        !document.documentElement.hasAttribute('data-candy-picture-in-picture') &&
                        !video.style.getPropertyValue('--candy-picture-in-picture-offset-x') &&
                        !video.style.getPropertyValue('--candy-picture-in-picture-offset-y');
                      document.title = restored ? '$INLINE_RESTORED_TITLE' :
                        `restore-failed:${'$'}{bounds.top}:` +
                        `${'$'}{getComputedStyle(header).visibility}`;
                      return;
                    }
                    const aligned = Math.abs(bounds.left) < 1 && Math.abs(bounds.top) < 1;
                    const headerHidden = getComputedStyle(header).visibility === 'hidden';
                    document.title = aligned && headerHidden ?
                      (player.dataset.shifted ? '$INLINE_REALIGNED_TITLE' : '$INLINE_PRESENTED_TITLE') :
                      `inline-failed:${'$'}{bounds.left},${'$'}{bounds.top}:${'$'}{getComputedStyle(header).visibility}`;
                    if (aligned && headerHidden && !player.dataset.shifted) {
                      player.dataset.shifted = 'true';
                      setTimeout(() => {
                        player.style.transform = 'translateY(32px)';
                        window.dispatchEvent(new Event('resize'));
                        const reportRealignment = () => {
                          if (!video.hasAttribute('data-candy-picture-in-picture-video')) return;
                          const shiftedBounds = video.getBoundingClientRect();
                          if (
                            Math.abs(shiftedBounds.left) < 1 &&
                            Math.abs(shiftedBounds.top) < 1
                          ) {
                            document.title = '$INLINE_REALIGNED_TITLE';
                            return;
                          }
                          document.title =
                            `realign-failed:${'$'}{shiftedBounds.left},${'$'}{shiftedBounds.top}:` +
                            `${'$'}{getComputedStyle(header).visibility}:` +
                            `${'$'}{video.hasAttribute('data-candy-picture-in-picture-video')}`;
                          setTimeout(reportRealignment, 100);
                        };
                        setTimeout(reportRealignment, 250);
                      }, 250);
                    }
                  }));
                }).observe(document.documentElement, {
                  attributes:true,
                  subtree:true,
                  attributeFilter:['data-candy-picture-in-picture-video']
                });
                video.load();
              </script>
            </body></html>
            """.trimIndent()
        val HTML =
            """
            <!doctype html>
            <html><head><title>loading</title><style>
              html,body,video,button { margin:0; width:100%; height:100%; background:#000; }
              button { position:fixed; inset:0; color:white; border:0; font-size:32px; }
            </style></head><body>
              <video muted playsinline loop></video><button>Play fullscreen</button>
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
                const button = document.querySelector('button');
                video.defaultMuted = true;
                video.muted = true;
                video.src = 'data:video/webm;base64,' + encodedVideo;
                video.addEventListener('loadedmetadata', () => document.title = '$READY_TITLE');
                button.addEventListener('click', () => {
                  button.remove();
                  video.requestFullscreen();
                });
                document.addEventListener('fullscreenchange', () => {
                  document.title = document.fullscreenElement ? 'fullscreen' : '$EXITED_TITLE';
                  if (document.fullscreenElement) video.play();
                });
                video.load();
              </script>
            </body></html>
            """.trimIndent()
    }
}
