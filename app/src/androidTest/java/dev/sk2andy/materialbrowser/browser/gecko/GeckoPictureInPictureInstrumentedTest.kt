package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
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
                activity.browserControllerForTesting().completePictureInPictureReturn()
            }
            awaitCondition {
                var restored = false
                scenario.onActivity { activity ->
                    restored = activity.browserControllerForTesting().fullscreenVideoState == null
                }
                restored
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
    fun transientGeckoPauseDoesNotFlipPictureInPicturePlaybackAction() {
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
                controller.reportSelectedGeckoMediaStateForTesting(eligibleMediaState())
                controller.reportSelectedGeckoFullscreenStateForTesting(true)
                controller.prepareForPictureInPicture()
                controller.onPictureInPictureModeChanged(true)
                controller.reportSelectedGeckoMediaStateForTesting(
                    eligibleMediaState().copy(isPlaying = false),
                )

                assertTrue(controller.systemMediaState?.isPlaying == true)

                controller.pauseActiveMedia()

                assertFalse(controller.systemMediaState?.isPlaying == true)
            }
        }
    }

    @Test
    fun inlineVideoOpensInCandyPlayerWithoutWebsiteFullscreen() {
        FixtureServer(INLINE_PLAYER_HTML).use { server ->
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    controller.updateVideoAutoplayBlocked(false)
                    controller.updateInlineMediaPlayerEnabled(false)
                    assertTrue(controller.openUrl(server.url))
                }
                awaitCondition {
                    var loaded = false
                    scenario.onActivity { activity ->
                        val title = activity.browserControllerForTesting().selectedTab.title
                        loaded = title in setOf(INLINE_LOADED_TITLE, INLINE_READY_TITLE) ||
                            title.startsWith(INLINE_ACTION_GEOMETRY_PREFIX)
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
                var needsTap = false
                scenario.onActivity { activity ->
                    needsTap = activity.browserControllerForTesting().selectedTab.title !=
                        INLINE_READY_TITLE
                }
                if (needsTap) {
                    repeat(3) {
                        tap(x = tapPoint[0], y = tapPoint[1])
                        SystemClock.sleep(250)
                    }
                }
                awaitCondition {
                    var ready = false
                    scenario.onActivity { activity ->
                        val title = activity.browserControllerForTesting().selectedTab.title
                        ready = title == INLINE_READY_TITLE ||
                            title.startsWith(INLINE_ACTION_GEOMETRY_PREFIX)
                    }
                    ready
                }
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assertFalse(controller.isInlineMediaPlayerEnabled)
                    assertTrue(controller.canOpenInlineMediaPlayer)
                    controller.updateInlineMediaPlayerEnabled(true)
                }
                awaitCondition {
                    var canOpen = false
                    scenario.onActivity { activity ->
                        canOpen = activity.browserControllerForTesting().canOpenInlineMediaPlayer
                    }
                    canOpen
                }
                var inlineActionGeometry = ""
                awaitCondition {
                    scenario.onActivity { activity ->
                        inlineActionGeometry = activity.browserControllerForTesting().selectedTab.title
                    }
                    inlineActionGeometry.startsWith(INLINE_ACTION_GEOMETRY_PREFIX)
                }
                val inlineActionTapPoint = FloatArray(2)
                val inlineVideoSwipe = FloatArray(4)
                scenario.onActivity { activity ->
                    val host = requireNotNull(
                        activity.browserControllerForTesting().selectedGeckoViewForTesting(),
                    )
                    val location = IntArray(2)
                    val geometry = inlineActionGeometry
                        .removePrefix(INLINE_ACTION_GEOMETRY_PREFIX)
                        .split(':')
                        .map(String::toFloat)
                    assertEquals(5, geometry.size)
                    val (left, top, width, height, viewportWidth) = geometry
                    val webScale = host.width / viewportWidth
                    host.getLocationOnScreen(location)
                    inlineActionTapPoint[0] = location[0] + (left + width / 2f) * webScale
                    inlineActionTapPoint[1] = location[1] + (top + height / 2f) * webScale
                    val videoHeight = host.width * 9f / 16f
                    inlineVideoSwipe[0] = location[0] + host.width / 2f
                    inlineVideoSwipe[1] = location[1] + 48f * webScale + videoHeight / 2f
                    inlineVideoSwipe[2] = inlineVideoSwipe[0]
                    inlineVideoSwipe[3] = inlineVideoSwipe[1] - 140f * webScale
                }
                tap(x = inlineActionTapPoint[0], y = inlineActionTapPoint[1])
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assertFalse(controller.isSelectedWebContentFullscreen)
                }
                var presentationTitle = ""
                awaitCondition(
                    description = { "Inline action did not replace player; title=$presentationTitle" },
                ) {
                    var presented = false
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        presentationTitle = controller.selectedTab.title
                        presented = controller.selectedTab.title == INLINE_ACTIVE_TITLE &&
                            controller.fullscreenVideoState == null
                    }
                    presented
                }
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assertFalse(controller.isSelectedWebContentFullscreen)
                    assertNull(controller.fullscreenVideoState)
                    assertFalse(controller.canOpenInlineMediaPlayer)
                }
                swipe(
                    startX = inlineVideoSwipe[0],
                    startY = inlineVideoSwipe[1],
                    endX = inlineVideoSwipe[2],
                    endY = inlineVideoSwipe[3],
                )
                awaitCondition(
                    description = { "Inline swipe-up did not enter web-content fullscreen" },
                ) {
                    var fullscreen = false
                    scenario.onActivity { activity ->
                        fullscreen = activity.browserControllerForTesting()
                            .isSelectedWebContentFullscreen
                    }
                    fullscreen
                }
                awaitCondition(
                    description = { "Inline swipe moved controls without the video frame" },
                ) {
                    var draggedTogether = false
                    scenario.onActivity { activity ->
                        draggedTogether = activity.browserControllerForTesting()
                            .selectedTab.title == INLINE_DRAGGED_TITLE
                    }
                    draggedTogether
                }
                scenario.onActivity { activity ->
                    assertTrue(
                        activity.browserControllerForTesting()
                            .exitSelectedWebContentFullscreen(),
                    )
                }
                awaitCondition(
                    description = { "Swipe fullscreen exit did not restore inline geometry" },
                ) {
                    var restored = false
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        restored = !controller.isSelectedWebContentFullscreen &&
                            !controller.isMediaLayoutRestorationPending &&
                            controller.selectedTab.title == INLINE_ACTIVE_TITLE
                    }
                    restored
                }
                awaitCondition(
                    description = { "Playing inline video did not become PiP eligible" },
                ) {
                    var eligible = false
                    scenario.onActivity { activity ->
                        eligible = activity.browserControllerForTesting().isPictureInPictureEligible
                    }
                    eligible
                }
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    controller.prepareForPictureInPicture()
                }
                awaitCondition(
                    description = { "Inline player did not enter video-only PiP preparation" },
                ) {
                    var prepared = false
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        prepared = controller.fullscreenVideoState?.host ==
                            FullscreenVideoHost.BrowserViewport
                    }
                    prepared
                }
                awaitCondition(
                    description = { "Video-only DOM layout was not presented before PiP return" },
                ) {
                    var presented = false
                    scenario.onActivity { activity ->
                        presented = activity.browserControllerForTesting()
                            .selectedTab.title == INLINE_PRESENTED_TITLE
                    }
                    presented
                }
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    controller.onPictureInPictureModeChanged(true)
                    controller.onPictureInPictureModeChanged(false)
                    assertEquals(
                        FullscreenVideoHost.BrowserViewport,
                        controller.fullscreenVideoState?.host,
                    )
                }
                SystemClock.sleep(250)
                scenario.onActivity { activity ->
                    assertEquals(
                        INLINE_PRESENTED_TITLE,
                        activity.browserControllerForTesting().selectedTab.title,
                    )
                    activity.browserControllerForTesting().completePictureInPictureReturn()
                }
                awaitCondition(
                    description = { "PiP return layout acknowledgement did not complete" },
                ) {
                    var restored = false
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        restored = controller.fullscreenVideoState == null &&
                            controller.isPictureInPictureEligible
                    }
                    restored
                }
                var restoredInlineTitle = ""
                awaitCondition(
                    description = {
                        "PiP return did not restore the inline Candy Player; " +
                            "title=$restoredInlineTitle"
                    },
                ) {
                    var restoredInline = false
                    scenario.onActivity { activity ->
                        restoredInlineTitle =
                            activity.browserControllerForTesting().selectedTab.title
                        restoredInline = restoredInlineTitle == INLINE_ACTIVE_TITLE
                    }
                    restoredInline
                }
                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().updateInlineMediaPlayerEnabled(false)
                }
                awaitCondition(
                    description = { "Disabling Candy Player did not restore site controls" },
                ) {
                    var restoredSitePlayer = false
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        restoredSitePlayer = controller.selectedTab.title ==
                            INLINE_RESTORED_TITLE &&
                            !controller.isPictureInPictureEligible
                    }
                    restoredSitePlayer
                }
                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().updateInlineMediaPlayerEnabled(true)
                }
                awaitCondition(
                    description = { "Re-enabling Candy Player silently reused stale presentation" },
                ) {
                    var canOpenAgain = false
                    scenario.onActivity { activity ->
                        canOpenAgain = activity.browserControllerForTesting().canOpenInlineMediaPlayer
                    }
                    canOpenAgain
                }
                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().openInlineMediaPlayer()
                }
                awaitCondition(
                    description = { "Candy Player did not reopen after explicit request" },
                ) {
                    var reopenedInline = false
                    scenario.onActivity { activity ->
                        reopenedInline = activity.browserControllerForTesting().selectedTab.title ==
                            INLINE_ACTIVE_TITLE
                    }
                    reopenedInline
                }
                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().reload()
                }
                awaitCondition {
                    var clearedForReload = false
                    scenario.onActivity { activity ->
                        clearedForReload =
                            activity.browserControllerForTesting().fullscreenVideoState == null
                    }
                    clearedForReload
                }
                var reloadedTitle = ""
                awaitCondition(description = { "Reload did not finish; title=$reloadedTitle" }) {
                    scenario.onActivity { activity ->
                        reloadedTitle = activity.browserControllerForTesting().selectedTab.title
                    }
                    reloadedTitle in setOf(INLINE_LOADED_TITLE, INLINE_READY_TITLE) ||
                        reloadedTitle.startsWith(INLINE_ACTION_GEOMETRY_PREFIX)
                }
                if (reloadedTitle != INLINE_READY_TITLE) {
                    repeat(3) {
                        tap(x = tapPoint[0], y = tapPoint[1])
                        SystemClock.sleep(250)
                    }
                }
                var reloadActionTitle = ""
                var canOpenAfterReload = false
                var pictureInPictureEligibleAfterReload = false
                awaitCondition(
                    description = {
                        "Reload did not expose inline action; title=$reloadActionTitle, " +
                            "canOpen=$canOpenAfterReload, " +
                            "pipEligible=$pictureInPictureEligibleAfterReload"
                    },
                ) {
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        reloadActionTitle = controller.selectedTab.title
                        canOpenAfterReload = controller.canOpenInlineMediaPlayer
                        pictureInPictureEligibleAfterReload =
                            controller.isPictureInPictureEligible
                    }
                    canOpenAfterReload
                }
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    controller.updateInlineMediaPlayerEnabled(false)
                    controller.updateVideoAutoplayBlocked(true)
                    assertFalse(controller.isInlineMediaPlayerEnabled)
                }
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
                repeat(3) {
                    tap(x = tapPoint[0], y = tapPoint[1])
                    SystemClock.sleep(250)
                }
                var readyTitle = "not observed"
                awaitCondition(description = { readyTitle }) {
                    var ready = false
                    scenario.onActivity { activity ->
                        readyTitle = activity.browserControllerForTesting().selectedTab.title
                        ready = readyTitle == INLINE_READY_TITLE ||
                            readyTitle.startsWith(INLINE_ACTION_GEOMETRY_PREFIX)
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
                        val controller = activity.browserControllerForTesting()
                        restoredTitle = controller.selectedTab.title
                        restored = !controller.isMediaLayoutRestorationPending &&
                            (
                                restoredTitle == INLINE_RESTORED_TITLE ||
                                    restoredTitle.startsWith(INLINE_ACTION_GEOMETRY_PREFIX)
                            )
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
                        restored = restoredTitle == INLINE_RESTORED_TITLE ||
                            restoredTitle.startsWith(INLINE_ACTION_GEOMETRY_PREFIX)
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
                repeat(3) {
                    tap(x = tapPoint[0], y = tapPoint[1])
                    SystemClock.sleep(250)
                }
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
        assertTrue(UiDevice.getInstance(instrumentation).click(x.toInt(), y.toInt()))
        instrumentation.waitForIdleSync()
    }

    private fun swipe(startX: Float, startY: Float, endX: Float, endY: Float) {
        assertTrue(
            UiDevice.getInstance(instrumentation).swipe(
                startX.toInt(),
                startY.toInt(),
                endX.toInt(),
                endY.toInt(),
                12,
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
        const val INLINE_ACTIVE_TITLE = "inline-active"
        const val INLINE_DRAGGED_TITLE = "inline-dragged-together"
        const val INLINE_PRESENTED_TITLE = "inline-presented"
        const val INLINE_REALIGNED_TITLE = "inline-realigned"
        const val INLINE_RESTORED_TITLE = "inline-restored"
        const val INLINE_ACTION_GEOMETRY_PREFIX = "inline-action:"
        val INLINE_PLAYER_HTML by lazy {
            INLINE_OFFSET_HTML.replace(
                "const requiresControls = false;",
                "const requiresControls = true;",
            ).replace("<video muted", "<video autoplay muted")
        }
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
                const requiresControls = false;
                let originalVideoBounds = null;
                let inlinePresentationObserved = false;
                let inlineGestureMovedTogether = false;
                video.defaultMuted = true;
                video.muted = true;
                video.src = 'data:video/webm;base64,' + encodedVideo;
                video.addEventListener('loadedmetadata', () => {
                  originalVideoBounds = video.getBoundingClientRect();
                  document.title = '$INLINE_LOADED_TITLE';
                });
                video.addEventListener('play', () => {
                  document.title = '$INLINE_READY_TITLE';
                });
                new MutationObserver(() => {
                  if (video.hasAttribute('data-candy-picture-in-picture-video')) return;
                  const controls = document.documentElement.querySelector(
                    '[data-candy-inline-video-controls]'
                  );
                  if (!controls || !originalVideoBounds) return;
                  const videoBounds = video.getBoundingClientRect();
                  const controlsBounds = controls.getBoundingClientRect();
                  const moved = originalVideoBounds.top - videoBounds.top;
                  if (moved > 3 && Math.abs(controlsBounds.top - videoBounds.top) < 2) {
                    inlineGestureMovedTogether = true;
                  }
                }).observe(video, { attributes:true, attributeFilter:['style'] });
                document.addEventListener('fullscreenchange', () => {
                  if (document.fullscreenElement) {
                    document.title = inlineGestureMovedTogether ?
                      '$INLINE_DRAGGED_TITLE' : 'inline-drag-failed';
                    return;
                  }
                  if (!inlineGestureMovedTogether) return;
                  document.title = 'inline-return-pending';
                  setTimeout(() => requestAnimationFrame(() => requestAnimationFrame(() => {
                    const bounds = video.getBoundingClientRect();
                    const original = originalVideoBounds || bounds;
                    const restored = Math.abs(bounds.left - original.left) < 1 &&
                      Math.abs(bounds.top - original.top) < 1 &&
                      Math.abs(bounds.width - original.width) < 1 &&
                      Math.abs(bounds.height - original.height) < 1 &&
                      getComputedStyle(header).visibility === 'visible';
                    document.title = restored ? '$INLINE_ACTIVE_TITLE' :
                      `inline-fullscreen-return-failed:${'$'}{bounds.top}`;
                  })), 250);
                });
                document.addEventListener('click', () => {
                  video.play();
                }, { once:true });
                new MutationObserver(() => {
                  const action = document.documentElement.querySelector(
                    '[data-candy-inline-video-action]'
                  );
                  if (!action || action.hasAttribute('data-fixture-reported')) return;
                  action.setAttribute('data-fixture-reported', '');
                  if (requiresControls) video.pause();
                  setTimeout(() => {
                    if (!action.isConnected) return;
                    const bounds = action.getBoundingClientRect();
                    document.title = '$INLINE_ACTION_GEOMETRY_PREFIX' + [
                      bounds.left,
                      bounds.top,
                      bounds.width,
                      bounds.height,
                      document.documentElement.clientWidth
                    ].join(':');
                  }, 250);
                }).observe(document.documentElement, { childList:true });
                new MutationObserver(() => {
                  if (video.hasAttribute('data-candy-picture-in-picture-video')) return;
                  const hasCandyControls = Boolean(document.documentElement.querySelector(
                    '[data-candy-inline-video-controls]'
                  ));
                  if (hasCandyControls === inlinePresentationObserved) return;
                  requestAnimationFrame(() => requestAnimationFrame(() => {
                    if (video.hasAttribute('data-candy-picture-in-picture-video')) return;
                    const bounds = video.getBoundingClientRect();
                    const original = originalVideoBounds || bounds;
                    const unchanged = Math.abs(bounds.left - original.left) < 1 &&
                      Math.abs(bounds.top - original.top) < 1 &&
                      Math.abs(bounds.width - original.width) < 1 &&
                      Math.abs(bounds.height - original.height) < 1;
                    const pageRemainsVisible =
                      getComputedStyle(header).visibility === 'visible';
                    const hasNoVideoOnlyLayout =
                      !document.documentElement.hasAttribute('data-candy-picture-in-picture') &&
                      !video.hasAttribute('data-candy-picture-in-picture-video') &&
                      !video.style.getPropertyValue('--candy-picture-in-picture-offset-x') &&
                      !video.style.getPropertyValue('--candy-picture-in-picture-offset-y');
                    const controlsStillPresent = Boolean(document.documentElement.querySelector(
                      '[data-candy-inline-video-controls]'
                    ));
                    if (controlsStillPresent) {
                      inlinePresentationObserved = true;
                      document.title = unchanged && pageRemainsVisible &&
                        hasNoVideoOnlyLayout && !video.controls ?
                        '$INLINE_ACTIVE_TITLE' :
                        `inline-active-failed:${'$'}{bounds.left},${'$'}{bounds.top}:` +
                        `${'$'}{bounds.width},${'$'}{bounds.height}:` +
                        `${'$'}{getComputedStyle(header).visibility}:` +
                        `${'$'}{video.controls}`;
                      return;
                    }
                    inlinePresentationObserved = false;
                    document.title = unchanged && pageRemainsVisible &&
                      hasNoVideoOnlyLayout && !video.controls ?
                      '$INLINE_RESTORED_TITLE' :
                      `inline-restore-failed:${'$'}{bounds.left},${'$'}{bounds.top}:` +
                      `${'$'}{getComputedStyle(header).visibility}`;
                  }));
                }).observe(document.documentElement, { childList:true });
                new MutationObserver(() => {
                  const presented = video.hasAttribute('data-candy-picture-in-picture-video');
                  if (!presented && ![
                    '$INLINE_PRESENTED_TITLE',
                    '$INLINE_REALIGNED_TITLE'
                  ].includes(document.title)) return;
                  requestAnimationFrame(() => requestAnimationFrame(() => {
                    if (
                      video.hasAttribute('data-candy-picture-in-picture-video') !== presented
                    ) return;
                    const bounds = video.getBoundingClientRect();
                    if (!presented) {
                      const original = originalVideoBounds || bounds;
                      const restored =
                        Math.abs(bounds.left - original.left) < 1 &&
                        Math.abs(bounds.top - original.top) < 1 &&
                        Math.abs(bounds.width - original.width) < 1 &&
                        Math.abs(bounds.height - original.height) < 1 &&
                        getComputedStyle(header).visibility === 'visible' &&
                        !video.controls &&
                        !document.documentElement.hasAttribute('data-candy-picture-in-picture') &&
                        !video.style.getPropertyValue('--candy-picture-in-picture-offset-x') &&
                        !video.style.getPropertyValue('--candy-picture-in-picture-offset-y');
                      document.title = restored ?
                        (inlinePresentationObserved ?
                          '$INLINE_ACTIVE_TITLE' : '$INLINE_RESTORED_TITLE') :
                        `restore-failed:${'$'}{bounds.top}:` +
                        `${'$'}{getComputedStyle(header).visibility}`;
                      return;
                    }
                    const aligned = Math.abs(bounds.left) < 1 && Math.abs(bounds.top) < 1;
                    const headerHidden = getComputedStyle(header).visibility === 'hidden';
                    const controlsMatch = !requiresControls || !video.controls;
                    document.title = aligned && headerHidden && controlsMatch ?
                      (player.dataset.shifted ? '$INLINE_REALIGNED_TITLE' : '$INLINE_PRESENTED_TITLE') :
                      `inline-failed:${'$'}{bounds.left},${'$'}{bounds.top}:` +
                      `${'$'}{getComputedStyle(header).visibility}:${'$'}{video.controls}`;
                    if (
                      aligned && headerHidden && controlsMatch &&
                      !player.dataset.shifted && !requiresControls
                    ) {
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
