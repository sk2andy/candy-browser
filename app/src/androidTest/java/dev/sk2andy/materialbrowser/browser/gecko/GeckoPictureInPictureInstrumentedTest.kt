package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.FullscreenVideoHost
import dev.sk2andy.materialbrowser.browser.FullscreenVideoSource
import dev.sk2andy.materialbrowser.browser.InlineMediaPlayerMode
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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
                val pictureInPictureEngineView = requireNotNull(
                    (stableGeckoHost as ViewGroup).singleChild(),
                )
                assertSame(
                    stableGeckoHost,
                    activity.browserControllerForTesting().selectedGeckoViewForTesting(),
                )
                assertSame(initialEngineView, pictureInPictureEngineView)
                assertNotNull(parentOf(stableGeckoHost))
                assertNotNull(
                    pictureInPictureEngineView.findDescendant(SurfaceView::class.java),
                )
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
            awaitCondition {
                var rendererAttached = false
                scenario.onActivity { activity ->
                    val currentEngineView = (stableGeckoHost as ViewGroup).singleChild()
                    val surface = currentEngineView?.findDescendant(SurfaceView::class.java)
                    rendererAttached =
                        activity.browserControllerForTesting().selectedGeckoViewForTesting() ===
                        stableGeckoHost &&
                        currentEngineView === initialEngineView &&
                        parentOf(stableGeckoHost) != null &&
                        surface?.isAttachedToWindow == true
                }
                rendererAttached
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
                    controller.updateInlineMediaPlayerMode(
                        InlineMediaPlayerMode.ButtonInlineAndFullscreen,
                    )
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
                    assertTrue(controller.isInlineMediaPlayerEnabled)
                    assertTrue(controller.canOpenInlineMediaPlayer)
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

    /**
     * Manual, isolated Android 17 regression; not covered by a whole-class CI run.
     * Select this exact method with android.testInstrumentationRunnerArguments.class in a fresh
     * instrumentation process on a dedicated API 37 emulator (ANDROID_SERIAL must be explicit).
     * Startup DNS prefs use a disposable Gecko profile; no external DNS setup is required.
     */
    @Test
    @SdkSuppress(minSdkVersion = 37, maxSdkVersion = 37)
    fun youtubePlayerKeepsVideoGeometryThroughDelayedSystemPictureInPictureReturn() {
        verifyYoutubeReturnGeometry(YOUTUBE_RETURN_HTML, directReturnOnly = false)
    }

    @Test
    @SdkSuppress(minSdkVersion = 37, maxSdkVersion = 37)
    fun youtubeClippedPlayerKeepsDecodedVideoThroughDirectFullscreenReturn() {
        verifyYoutubeReturnGeometry(YOUTUBE_CLIPPED_RETURN_HTML, directReturnOnly = true)
    }

    @Test
    @SdkSuppress(minSdkVersion = 37, maxSdkVersion = 37)
    fun youtubeClippedPlayerKeepsDecodedVideoThroughFirstSwipeFullscreenReturn() {
        verifyYoutubeReturnGeometry(
            YOUTUBE_CLIPPED_RETURN_HTML,
            directReturnOnly = true,
            firstFullscreenBySwipe = true,
        )
    }

    private fun verifyYoutubeReturnGeometry(
        html: String,
        directReturnOnly: Boolean,
        firstFullscreenBySwipe: Boolean = false,
    ) {
        assertTrue(ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong()))
        prepareIsolatedYoutubeFixtureRuntime()
        FixtureServer(html, "pip-fixture.youtube.com").use { server ->
            ActivityScenario.launch(MainActivity::class.java).use scenarioScope@{ scenario ->
                lateinit var stableGeckoHost: View
                lateinit var stableEngineView: View
                lateinit var stableBrowserContainer: ViewGroup
                lateinit var videoTabId: String
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    controller.updateVideoAutoplayBlocked(false)
                    controller.updateInlineMediaPlayerMode(
                        if (firstFullscreenBySwipe) InlineMediaPlayerMode.ButtonInlineAndFullscreen
                        else InlineMediaPlayerMode.ButtonFullscreen,
                    )
                    assertTrue(controller.openUrl(server.url))
                }
                var loadingState = ""
                awaitCondition(description = { "YouTube fixture baseline missing: $loadingState; ${server.geometry}" }) {
                    var candidateReady = false
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        candidateReady = controller.canOpenInlineMediaPlayer
                        loadingState = "title=${controller.selectedTab.title}; url=${controller.selectedTab.url}; " +
                            "canOpen=${controller.canOpenInlineMediaPlayer}"
                    }
                    candidateReady && server.geometry.any { it.getString("phase") == "baseline" }
                }
                val baseline = server.geometry.first { it.getString("phase") == "baseline" }
                val fullscreenTapPoint = FloatArray(2)
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    videoTabId = controller.selectedTab.id
                    stableGeckoHost = requireNotNull(controller.selectedGeckoViewForTesting())
                    stableEngineView = requireNotNull((stableGeckoHost as ViewGroup).singleChild())
                    stableBrowserContainer = requireNotNull(parentOf(stableGeckoHost) as? ViewGroup)
                    val location = IntArray(2)
                    stableGeckoHost.getLocationOnScreen(location)
                    val bounds = baseline.getJSONArray("action")
                    val scale = stableGeckoHost.width / baseline.getDouble("viewportWidth")
                    fullscreenTapPoint[0] =
                        (location[0] + (bounds.getDouble(0) + bounds.getDouble(2) / 2) * scale).toFloat()
                    fullscreenTapPoint[1] =
                        (location[1] + (bounds.getDouble(1) + bounds.getDouble(3) / 2) * scale).toFloat()
                }
                captureInlinePixelEvidence(stableGeckoHost, baseline, "baseline")
                tap(fullscreenTapPoint[0], fullscreenTapPoint[1])
                if (firstFullscreenBySwipe) {
                    awaitCondition(description = { "Candy inline presentation missing before first swipe" }) {
                        var ready = false
                        scenario.onActivity { activity ->
                            val controller = activity.browserControllerForTesting()
                            ready = controller.isInlineMediaPlayerPresented &&
                                !controller.isSelectedWebContentFullscreen
                        }
                        ready
                    }
                    val location = IntArray(2)
                    scenario.onActivity { stableGeckoHost.getLocationOnScreen(location) }
                    val bounds = baseline.getJSONArray("video")
                    val scale = stableGeckoHost.width / baseline.getDouble("viewportWidth")
                    val x = (location[0] + (bounds.getDouble(0) + bounds.getDouble(2) / 2) * scale).toFloat()
                    val startY = (location[1] + (bounds.getDouble(1) + bounds.getDouble(3) * 0.4) * scale).toFloat()
                    swipe(x, startY, x, startY - (bounds.getDouble(3) * scale * 0.5).toFloat())
                }
                var fullscreenState = ""
                awaitCondition(description = { "Fixture did not enter Candy fullscreen: $fullscreenState; ${server.geometry}" }) {
                    var ready = false
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        fullscreenState = controller.geckoFullscreenDebugStateForTesting(videoTabId) +
                            "; presented=${controller.isInlineMediaPlayerPresented}" +
                            "; pipEligible=${controller.isPictureInPictureEligible}"
                        ready = controller.isSelectedWebContentFullscreen &&
                            controller.isInlineMediaPlayerPresented && controller.isPictureInPictureEligible
                    }
                    ready && server.geometry.any {
                        it.getString("phase") == "fullscreen-controls" && it.getBoolean("controls")
                    }
                }
                val fullscreen = server.geometry.first { it.getString("phase") == "fullscreen-controls" }
                assertTrue("YouTube player policy missing: $fullscreen", fullscreen.getBoolean("sitePlayer"))
                assertTrue("Fullscreen lost frozen origin: $fullscreen", fullscreen.getBoolean("originStyle"))
                val device = UiDevice.getInstance(instrumentation)
                if (directReturnOnly) {
                    scenario.onActivity { activity -> logReturnState(activity, "direct-before-back") }
                }
                if (directReturnOnly) {
                    scenario.onActivity { activity ->
                        assertTrue(activity.browserControllerForTesting().exitSelectedWebContentFullscreen())
                    }
                } else {
                    injectBack()
                }
                if (directReturnOnly) {
                    listOf("direct-immediate", "direct-delayed", "direct-late").forEach { phase ->
                        awaitCondition(
                            waitForIdle = false,
                            description = { "Direct return $phase missing: ${server.geometry}" },
                        ) {
                            server.geometry.any { it.getString("phase") == phase }
                        }
                        val sample = server.geometry.first { it.getString("phase") == phase }
                        scenario.onActivity { activity -> logReturnState(activity, phase) }
                        // The first sample records Android's native exit animation as well as DOM state.
                        // Require decoded pixels and stable geometry after subsequent video frames.
                        val settled = phase != "direct-immediate"
                        captureInlinePixelEvidence(stableGeckoHost, sample, phase, verifyDecodedFrame = settled)
                        // Device load and Gecko's HTTP scheduling can delay screenshot delivery.
                        // Keep both timestamps as evidence; geometry and decoded settled frames
                        // are the acceptance criteria, not the transport latency.
                        if (settled) assertStableInlineGeometry(baseline, sample)
                    }
                    return@scenarioScope
                }
                awaitCondition(description = { "Direct Back did not restore inline layout: ${server.geometry}" }) {
                    var returned = false
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        returned = activity.lifecycle.currentState == Lifecycle.State.RESUMED &&
                            activity.hasWindowFocus() && !controller.isSelectedWebContentFullscreen &&
                            !controller.isMediaLayoutRestorationPending
                    }
                    returned
                }
                server.requestProbe("direct-fullscreen-return")
                awaitCondition(description = { "Direct fullscreen return probe missing: ${server.geometry}" }) {
                    server.geometry.any { it.getString("phase") == "direct-fullscreen-return" }
                }
                val directReturn = server.geometry.first { it.getString("phase") == "direct-fullscreen-return" }
                captureInlinePixelEvidence(stableGeckoHost, directReturn, "direct-fullscreen-return")
                assertStableInlineGeometry(baseline, directReturn)

                // Exercise Candy's actual upward fullscreen gesture after direct fullscreen exit.
                val swipePoints = FloatArray(3)
                scenario.onActivity {
                    val location = IntArray(2)
                    stableGeckoHost.getLocationOnScreen(location)
                    val bounds = directReturn.getJSONArray("controlsRect")
                    val scale = stableGeckoHost.width / directReturn.getDouble("viewportWidth")
                    swipePoints[0] = (location[0] + (bounds.getDouble(0) + bounds.getDouble(2) / 2) * scale).toFloat()
                    swipePoints[1] = (location[1] + (bounds.getDouble(1) + bounds.getDouble(3) * 0.55) * scale).toFloat()
                    swipePoints[2] = (location[1] + (bounds.getDouble(1) + bounds.getDouble(3) * 0.1) * scale).toFloat()
                }
                swipe(swipePoints[0], swipePoints[1], swipePoints[0], swipePoints[2])
                awaitCondition(description = { "Candy gesture did not reenter fullscreen: ${server.geometry}" }) {
                    var fullscreenReady = false
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        fullscreenReady = controller.isSelectedWebContentFullscreen &&
                            controller.isInlineMediaPlayerPresented && controller.isPictureInPictureEligible
                    }
                    fullscreenReady
                }
                scenario.onActivity { activity ->
                    assertTrue(activity.browserControllerForTesting().isSelectedWebContentFullscreen)
                    assertTrue(activity.onPictureInPictureRequested())
                }
                awaitCondition(description = { "Android did not enter system PiP: ${server.geometry}" }) {
                    var entered = false
                    scenario.onActivity { activity -> entered = activity.isInPictureInPictureMode }
                    entered && server.geometry.any { it.getString("phase") == "pip" }
                }
                device.waitForIdle()
                val pipTapPoint = IntArray(2)
                scenario.onActivity { activity ->
                    val decor = activity.window.decorView
                    decor.getLocationOnScreen(pipTapPoint)
                    pipTapPoint[0] += decor.width / 2
                    pipTapPoint[1] += decor.height / 2
                }
                device.click(pipTapPoint[0], pipTapPoint[1])
                val expand = device.wait(
                    Until.findObject(By.res("com.android.systemui", "expand_button")),
                    5_000,
                ) ?: device.findObject(By.desc("Full screen"))
                val hierarchy = ByteArrayOutputStream()
                device.dumpWindowHierarchy(hierarchy)
                Log.i("CandyPipGeometry", "PiP menu: $hierarchy; tap=${pipTapPoint.toList()}")
                assertNotNull("System PiP expand action missing: $hierarchy", expand)
                captureEarlyReturnFrames(scenario, server, stableGeckoHost, baseline, "expand") {
                    expand?.click()
                }
                awaitCondition(description = { "Android did not return from system PiP" }) {
                    var returned = false
                    scenario.onActivity { activity -> returned = !activity.isInPictureInPictureMode }
                    returned
                }
                scenario.onActivity { activity -> logReturnState(activity, "android-return") }
                server.requestProbe("android-return")
                awaitCondition(description = { "Android return DOM probe missing: ${server.geometry}" }) {
                    server.geometry.any { it.getString("phase") == "android-return" }
                }
                val androidReturn = server.geometry.first { it.getString("phase") == "android-return" }
                captureInlinePixelEvidence(stableGeckoHost, androidReturn, "android-return", verifyDecodedFrame = false)
                // System Expand returns the Activity, not necessarily the document to inline mode.
                // Keep this phase separate: Back must never precede the actual Android PiP return.
                if (androidReturn.getBoolean("fullscreen")) {
                    Log.i("CandyPipGeometry", "Android return retained DOM fullscreen; pressing Back")
                    var backReadiness = ""
                    awaitCondition(description = { "Returned Activity not ready for Back: $backReadiness" }) {
                        var ready = false
                        scenario.onActivity { activity ->
                            val controller = activity.browserControllerForTesting()
                            backReadiness = "lifecycle=${activity.lifecycle.currentState}; " +
                                "focus=${activity.hasWindowFocus()}; " +
                                "restorePending=${controller.isMediaLayoutRestorationPending}; " +
                                "androidPip=${activity.isInPictureInPictureMode}"
                            ready = activity.lifecycle.currentState == Lifecycle.State.RESUMED &&
                                activity.hasWindowFocus() && !controller.isMediaLayoutRestorationPending &&
                                !activity.isInPictureInPictureMode
                        }
                        ready
                    }
                    Log.i("CandyPipGeometry", "Back ready: $backReadiness")
                    captureEarlyReturnFrames(scenario, server, stableGeckoHost, baseline, "back", ::injectBack)
                    awaitCondition(description = { "Post-return Back did not leave fullscreen: ${server.geometry}" }) {
                        var fullscreenExited = false
                        scenario.onActivity { activity ->
                            fullscreenExited = activity.lifecycle.currentState == Lifecycle.State.RESUMED &&
                                !activity.browserControllerForTesting().isSelectedWebContentFullscreen
                        }
                        fullscreenExited && server.geometry.any { it.getString("phase") == "fullscreen-exit" }
                    }
                } else {
                    Log.i("CandyPipGeometry", "Android return already inline; no Back needed")
                }
                scenario.onActivity { activity -> logReturnState(activity, "inline-return-start") }
                server.requestProbe("inline-return")
                listOf("immediate", "delayed", "late").forEach { phase ->
                    awaitCondition(description = { "Return geometry $phase missing: ${server.geometry}" }) {
                        server.geometry.any { it.getString("phase") == phase }
                    }
                    scenario.onActivity { activity -> logReturnState(activity, "inline-return-$phase") }
                    captureInlinePixelEvidence(
                        stableGeckoHost,
                        server.geometry.first { it.getString("phase") == phase },
                        "inline-return-$phase",
                        verifyDecodedFrame = phase == "late",
                    )
                }
                var title = ""
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    logReturnState(activity, "inline-return-late")
                    title = controller.selectedTab.title
                    assertEquals(videoTabId, controller.selectedTab.id)
                    assertSame(stableGeckoHost, controller.selectedGeckoViewForTesting())
                    assertSame(stableBrowserContainer, parentOf(stableGeckoHost))
                    assertSame(stableEngineView, (stableGeckoHost as ViewGroup).singleChild())
                    assertNotNull(stableEngineView.findDescendant(SurfaceView::class.java))
                }
                val timeline = server.geometry
                val evidence = "title=$title; timeline=${timeline.joinToString()}"
                Log.i("CandyPipGeometry", evidence)
                listOf("immediate", "delayed", "late").forEach { phase ->
                    val samples = timeline.filter { it.getString("phase") == phase }
                    assertEquals("Missing or repeated $phase sample: $evidence", 1, samples.size)
                    assertStableInlineGeometry(baseline, samples.single())
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

    private fun captureEarlyReturnFrames(
        scenario: ActivityScenario<MainActivity>,
        server: FixtureServer,
        host: View,
        baseline: JSONObject,
        stage: String,
        action: () -> Unit,
    ) {
        val started = System.currentTimeMillis()
        action()
        var index = 0
        val failures = mutableListOf<String>()
        // Observe without waiting for UI idleness or restoration completion. Screenshot transport
        // is not frame-synchronous; retain actual DOM/receipt/capture times instead of a deadline.
        while (System.currentTimeMillis() - started < 2_200) {
            val phase = "first-observed-$stage-${index++}"
            server.requestProbe(phase)
            awaitCondition(waitForIdle = false, description = { "Early return probe missing: $phase" }) {
                server.geometry.any { it.getString("phase") == phase }
            }
            val sample = server.geometry.first { it.getString("phase") == phase }
            sample.put("returnActionAtEpochMillis", started)
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                sample.put(
                    "nativeReturnState",
                    JSONObject()
                        .put("sampledAtEpochMillis", System.currentTimeMillis())
                        .put("androidPip", activity.isInPictureInPictureMode)
                        .put("restorePending", controller.isMediaLayoutRestorationPending)
                        .put("contentFullscreen", controller.isSelectedWebContentFullscreen),
                )
            }
            captureInlinePixelEvidence(host, sample, phase, verifyDecodedFrame = false)
            val cyan = sample.getJSONArray("decodedCyanBoundsScreenPx")
            val originalCyan = baseline.getJSONArray("decodedCyanBoundsScreenPx")
            val native = sample.getJSONObject("nativeReturnState")
            val captureStarted = sample.getLong("screenshotStartedAtEpochMillis")
            // Fullscreen-exit can arrive while native state/screenshot transport is in flight.
            // Use the newest DOM evidence already received when capture began, not the older
            // requested probe that may still say fullscreen and incorrectly skip this frame.
            val frameGeometry = server.geometry
                .filter { it.getLong("nativeReceivedAtEpochMillis") <= captureStarted }
                .maxByOrNull { it.getLong("sampledAtEpochMillis") }
                ?: sample
            Log.i(
                "CandyPipGeometry",
                "frame oracle phase=$phase; captureStarted=$captureStarted; " +
                    "domPhase=${frameGeometry.getString("phase")}; " +
                    "domSampledAt=${frameGeometry.getLong("sampledAtEpochMillis")}; " +
                    "domFullscreen=${frameGeometry.getBoolean("fullscreen")}; native=$native; cyan=$cyan",
            )
            // Android's expanding PiP task is intentionally scaled. Compare only decoded frames
            // that have reached the original width after the native and DOM fullscreen exit.
            if (!native.getBoolean("androidPip") && !native.getBoolean("contentFullscreen") &&
                !native.getBoolean("restorePending") && !frameGeometry.getBoolean("fullscreen") &&
                cyan.getInt(2) >= 0 && kotlin.math.abs(cyan.getInt(2) - originalCyan.getInt(2)) <= 4) {
                runCatching { assertStableInlineGeometry(baseline, frameGeometry) }
                    .exceptionOrNull()?.let { failures.add("$phase: ${it.message}") }
                if (kotlin.math.abs(cyan.getInt(1) - originalCyan.getInt(1)) > 4) {
                    failures.add("$phase: decoded video top moved: dom=$frameGeometry; capture=$sample")
                }
            }
        }
        assertTrue("Visible return geometry changed:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    private fun tap(x: Float, y: Float) {
        assertTrue(UiDevice.getInstance(instrumentation).click(x.toInt(), y.toInt()))
        instrumentation.waitForIdleSync()
    }

    private fun prepareIsolatedYoutubeFixtureRuntime() {
        val isolatedTests = listOf(
            "youtubePlayerKeepsVideoGeometryThroughDelayedSystemPictureInPictureReturn",
            "youtubeClippedPlayerKeepsDecodedVideoThroughDirectFullscreenReturn",
            "youtubeClippedPlayerKeepsDecodedVideoThroughFirstSwipeFullscreenReturn",
        ).map { method -> "${javaClass.name}#$method" }
        assumeTrue(
            "YouTube-host fixture requires an isolated single-method instrumentation run",
            InstrumentationRegistry.getArguments().getString("class") in isolatedTests,
        )
        assumeTrue(
            "YouTube-host fixture requires a fresh Gecko runtime to apply local DNS",
            !GeckoRuntimeOwner.hasRuntimeForTesting(),
        )
        // Gecko documents startup prefs and --profile in its automation configuration:
        // https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/automation.html
        // Keep the disposable profile in app cache until package data cleanup: Gecko may still
        // write after Activity close. The normal profile never receives these fixture prefs.
        val profile = Files.createTempDirectory(context.cacheDir.toPath(), "candy-pip-profile-")
        val configPath = "/data/local/tmp/${context.packageName}-geckoview-config.yaml"
        val backupPath = "/data/local/tmp/${profile.fileName}.yaml"
        val quotedConfig = shellQuote(configPath)
        val quotedBackup = shellQuote(backupPath)
        val fingerprintCommand =
            "if [ -e $quotedConfig ] || [ -L $quotedConfig ]; then " +
                "sha256sum $quotedConfig; else printf absent; fi"
        val original = shell(fingerprintCommand).trim()
        check(original == "absent" || original.matches(Regex("[a-f0-9]{64}  .+"))) {
            "Could not fingerprint Gecko debug config: $original"
        }
        val configuration = """
            args:
              - --profile
              - "$profile"
            prefs:
              network.dns.localDomains: "pip-fixture.youtube.com"
              network.stricttransportsecurity.preloadlist: false
              dom.security.https_first: false
              dom.security.https_only_mode: false
        """.trimIndent()
        val encoded = Base64.encodeToString(configuration.toByteArray(), Base64.NO_WRAP)
        try {
            if (original != "absent") {
                check(shell("mv $quotedConfig $quotedBackup && printf preserved").trim() == "preserved") {
                    "Could not preserve Gecko debug config"
                }
            }
            check(
                shell(
                    "printf '%s' ${shellQuote(encoded)} | base64 -d > $quotedConfig && " +
                        "chmod 644 $quotedConfig && printf ready",
                ).trim() == "ready",
            ) { "Could not install isolated Gecko fixture configuration" }
            instrumentation.runOnMainSync { GeckoRuntimeOwner.getOrCreate(context) }
        } finally {
            // Gecko reads the file during synchronous runtime creation. Restore it immediately,
            // before launching the Activity or running any assertions, including failing ones.
            val restore = if (original != "absent") {
                "if [ -e $quotedBackup ] || [ -L $quotedBackup ]; then " +
                    "mv -f $quotedBackup $quotedConfig; fi"
            } else {
                "rm -f $quotedConfig"
            }
            check(shell("$restore && printf restored").trim() == "restored") {
                "Could not restore previous Gecko debug configuration"
            }
            assertEquals("Gecko debug config changed after restoration", original, shell(fingerprintCommand).trim())
            Log.i("CandyPipGeometry", "Gecko startup config restored: $original")
        }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    private fun shell(command: String): String {
        // executeShellCommand tokenizes its string without shell quoting. Send the quoted sh -c
        // invocation through stdin instead, so compound commands and paths remain one argument.
        val descriptors = instrumentation.uiAutomation.executeShellCommandRw("sh")
        try {
            ParcelFileDescriptor.AutoCloseOutputStream(descriptors[1]).bufferedWriter().use { input ->
                input.write("exec sh -c ${shellQuote(command)} 2>&1\n")
            }
            return ParcelFileDescriptor.AutoCloseInputStream(descriptors[0]).bufferedReader().use { it.readText() }
        } finally {
            descriptors.forEach { it.close() }
        }
    }

    private fun assertStableInlineGeometry(baseline: JSONObject, sample: JSONObject) {
        val evidence = "baseline=$baseline; sample=$sample"
        val bounds = sample.getJSONArray("video")
        val original = baseline.getJSONArray("video")
        val originalHeader = baseline.getJSONArray("header")
        val header = sample.getJSONArray("header")
        assertEquals(
            "Header-to-video gap changed at ${sample.getString("phase")}: $evidence",
            original.getDouble(1) - originalHeader.getDouble(1) - originalHeader.getDouble(3),
            bounds.getDouble(1) - header.getDouble(1) - header.getDouble(3),
            2.0,
        )
        (0..3).forEach { index ->
            assertEquals(
                "Video rectangle component $index changed: $evidence",
                original.getDouble(index),
                bounds.getDouble(index),
                2.0,
            )
        }
        assertTrue("Candy controls missing: $evidence", sample.getBoolean("controls"))
        val controls = sample.getJSONArray("controlsRect")
        val left = maxOf(0.0, bounds.getDouble(0))
        val top = maxOf(0.0, bounds.getDouble(1))
        val right = minOf(sample.getDouble("viewportWidth"), bounds.getDouble(0) + bounds.getDouble(2))
        val bottom = minOf(sample.getDouble("viewportHeight"), bounds.getDouble(1) + bounds.getDouble(3))
        listOf(left, top, right - left, bottom - top).forEachIndexed { index, expected ->
            assertEquals(
                "Candy controls detached at rectangle component $index: $evidence",
                expected,
                controls.getDouble(index),
                2.0,
            )
        }
        assertTrue("YouTube policy missing: $evidence", sample.getBoolean("sitePlayer"))
        assertTrue("Frozen origin missing: $evidence", sample.getBoolean("originStyle"))
        assertFalse("Still fullscreen: $evidence", sample.getBoolean("fullscreen"))
        assertFalse("Stale PiP attributes: $evidence", sample.getBoolean("pipAttributes"))
        assertFalse("Stale PiP style: $evidence", sample.getBoolean("pipStyle"))
        assertFalse("Stale PiP offsets: $evidence", sample.getBoolean("pipOffsets"))
    }

    private fun injectBack() {
        val downTime = SystemClock.uptimeMillis()
        assertTrue(instrumentation.uiAutomation.injectInputEvent(
            KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0),
            true,
        ))
        assertTrue(instrumentation.uiAutomation.injectInputEvent(
            KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0),
            true,
        ))
    }

    private fun captureInlinePixelEvidence(
        host: View,
        sample: JSONObject,
        phase: String,
        verifyDecodedFrame: Boolean = true,
    ) {
        val location = IntArray(2)
        var width = 0
        instrumentation.runOnMainSync {
            host.getLocationOnScreen(location)
            width = host.width
            val insets = ViewCompat.getRootWindowInsets(host)
            sample.put("nativeInsetsAtCapture", JSONObject()
                .put("statusBarTopPx", insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top)
                .put("statusBarIgnoringVisibilityTopPx", insets?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())?.top)
                .put("cutoutTopPx", insets?.getInsets(WindowInsetsCompat.Type.displayCutout())?.top)
                .put("statusBarsVisible", insets?.isVisible(WindowInsetsCompat.Type.statusBars()))
                .put("hostTopPx", location[1])
                .put("hostWidthPx", host.width)
                .put("hostHeightPx", host.height)
                .put("hostPaddingTopPx", host.paddingTop)
                .put("hostScrollY", host.scrollY))
        }
        sample.put("screenshotStartedAtEpochMillis", System.currentTimeMillis())
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        sample.put("screenshotFinishedAtEpochMillis", System.currentTimeMillis())
        try {
            val target = File(context.getExternalFilesDir(null), "pip-geometry-$phase.png")
            target.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val video = sample.getJSONArray("video")
            val scale = width / sample.getDouble("viewportWidth")
            val left = (location[0] + (video.getDouble(0) + video.getDouble(2) * 0.2) * scale)
                .toInt().coerceIn(0, screenshot.width - 1)
            val right = (location[0] + (video.getDouble(0) + video.getDouble(2) * 0.8) * scale)
                .toInt().coerceIn(left + 1, screenshot.width)
            // Inspect the upper half, above Candy's controls, for contiguous near-black rows.
            val top = (location[1] + video.getDouble(1) * scale).toInt()
                .coerceIn(0, screenshot.height - 1)
            val bottom = (location[1] + (video.getDouble(1) + video.getDouble(3) * 0.5) * scale)
                .toInt().coerceIn(top + 1, screenshot.height)
            var darkRun = 0
            var longestDarkRun = 0
            var cyanPixels = 0
            var sampledPixels = 0
            var lowerCyanPixels = 0
            var lowerSampledPixels = 0
            var cyanLeft = screenshot.width
            var cyanTop = screenshot.height
            var cyanRight = -1
            var cyanBottom = -1
            for (y in 0 until screenshot.height step 4) {
                for (x in 0 until screenshot.width step 4) {
                    val pixel = screenshot.getPixel(x, y)
                    if (Color.red(pixel) < 40 && Color.green(pixel) > 140 && Color.blue(pixel) > 140) {
                        cyanLeft = minOf(cyanLeft, x)
                        cyanTop = minOf(cyanTop, y)
                        cyanRight = maxOf(cyanRight, x)
                        cyanBottom = maxOf(cyanBottom, y)
                    }
                }
            }
            sample.put(
                "decodedCyanBoundsScreenPx",
                org.json.JSONArray(listOf(cyanLeft, cyanTop, cyanRight, cyanBottom)),
            )
            for (y in top until bottom) {
                val dark = (left until right step 8).all { x ->
                    val pixel = screenshot.getPixel(x, y)
                    Color.red(pixel) <= 30 && Color.green(pixel) <= 30 && Color.blue(pixel) <= 30
                }
                for (x in left until right step 8) {
                    val pixel = screenshot.getPixel(x, y)
                    if (Color.red(pixel) < 40 && Color.green(pixel) > 140 && Color.blue(pixel) > 140) {
                        cyanPixels++
                    }
                    sampledPixels++
                }
                darkRun = if (dark) darkRun + 1 else 0
                longestDarkRun = maxOf(longestDarkRun, darkRun)
            }
            val lowerLeft = (location[0] + (video.getDouble(0) + video.getDouble(2) * 0.42) * scale)
                .toInt().coerceIn(0, screenshot.width - 1)
            val lowerRight = (location[0] + (video.getDouble(0) + video.getDouble(2) * 0.58) * scale)
                .toInt().coerceIn(lowerLeft + 1, screenshot.width)
            val lowerTop = (location[1] + (video.getDouble(1) + video.getDouble(3) * 0.85) * scale)
                .toInt().coerceIn(0, screenshot.height - 1)
            val lowerBottom = (location[1] + (video.getDouble(1) + video.getDouble(3) * 0.94) * scale)
                .toInt().coerceIn(lowerTop + 1, screenshot.height)
            for (y in lowerTop until lowerBottom step 2) {
                for (x in lowerLeft until lowerRight step 2) {
                    val pixel = screenshot.getPixel(x, y)
                    if (Color.red(pixel) < 40 && Color.green(pixel) > 140 && Color.blue(pixel) > 140) {
                        lowerCyanPixels++
                    }
                    lowerSampledPixels++
                }
            }
            Log.i(
                "CandyPipGeometry",
                "pixels phase=$phase; longestDarkBandPx=$longestDarkRun; " +
                    "longestDarkBandCss=${longestDarkRun / scale}; host=${location.toList()}; " +
                    "decodedCyanPixels=$cyanPixels/$sampledPixels; " +
                    "lowerDecodedCyanPixels=$lowerCyanPixels/$lowerSampledPixels; " +
                    "screenshot=${target.absolutePath}; sample=$sample",
            )
            if (verifyDecodedFrame) {
                assertTrue(
                    "Decoded cyan video missing at $phase: cyan=$cyanPixels/$sampledPixels; " +
                        "darkBandPx=$longestDarkRun; screenshot=${target.absolutePath}; sample=$sample",
                    cyanPixels >= sampledPixels * 0.9,
                )
                assertTrue(
                    "Decoded video hidden behind Candy controls at $phase: " +
                        "cyan=$lowerCyanPixels/$lowerSampledPixels; screenshot=${target.absolutePath}; " +
                        "sample=$sample",
                    lowerCyanPixels >= lowerSampledPixels * 0.9,
                )
            }
        } finally {
            screenshot.recycle()
        }
    }

    private fun logReturnState(activity: MainActivity, phase: String) {
        val controller = activity.browserControllerForTesting()
        val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
        Log.i(
            "CandyPipGeometry",
            "$phase native: androidPip=${activity.isInPictureInPictureMode}; " +
                "contentFullscreen=${controller.isSelectedWebContentFullscreen}; " +
                "restorePending=${controller.isMediaLayoutRestorationPending}; " +
                "candyPresented=${controller.isInlineMediaPlayerPresented}; " +
                "statusBarTopPx=${insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top}; " +
                "statusBarsVisible=${insets?.isVisible(WindowInsetsCompat.Type.statusBars())}; " +
                "title=${controller.selectedTab.title}",
        )
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
        waitForIdle: Boolean = true,
        description: () -> String = { "Condition not met" },
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (waitForIdle) instrumentation.waitForIdleSync()
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

    private class FixtureServer(
        private val html: String = HTML,
        hostname: String = "127.0.0.1",
    ) : Closeable {
        private val socket = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        val url = "http://$hostname:${socket.localPort}/"
        @Volatile
        private var requestedProbe = ""

        fun requestProbe(phase: String) {
            requestedProbe = phase
        }
        private val recordedGeometry = mutableListOf<JSONObject>()
        val geometry: List<JSONObject>
            get() = synchronized(recordedGeometry) { recordedGeometry.toList() }
        private val clients = Executors.newFixedThreadPool(8) { task ->
            Thread(task, "gecko-pip-fixture-client").apply { isDaemon = true }
        }
        private val thread = Thread(::serve, "gecko-pip-fixture").apply {
            isDaemon = true
            start()
        }

        private fun serve() {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: return
                // Gecko may preconnect without sending a request. Never let that socket block
                // the geometry requests whose receive time drives native screenshots.
                client.soTimeout = 2_000
                client.tcpNoDelay = true
                clients.execute {
                    client.use { connection ->
                        var request = ""
                        runCatching {
                            connection.getInputStream().bufferedReader().apply {
                                request = readLine().orEmpty().split(' ').getOrNull(1).orEmpty()
                                if (request.startsWith("/geometry?")) {
                                    val sample = JSONObject(URLDecoder.decode(request.substringAfter('?'), "UTF-8"))
                                    sample.put("nativeReceivedAtEpochMillis", System.currentTimeMillis())
                                    synchronized(recordedGeometry) { recordedGeometry.add(sample) }
                                    Log.i("CandyPipGeometry", sample.toString())
                                }
                                while (!readLine().isNullOrEmpty()) {
                                    // Drain request headers before writing the local response.
                                }
                            }
                            val body = when {
                                request == "/probe" -> requestedProbe.toByteArray()
                                request.startsWith("/geometry?") -> ByteArray(0)
                                else -> html.toByteArray()
                            }
                            connection.getOutputStream().buffered().use { output ->
                                output.write(
                                    "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n".toByteArray(),
                                )
                                output.write(
                                    "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray(),
                                )
                                output.write(body)
                            }
                        }.onFailure { error ->
                            if (request.isNotEmpty() && !socket.isClosed) {
                                Log.w("CandyPipFixture", "Fixture request failed: $request", error)
                            }
                        }
                    }
                }
            }
        }

        override fun close() {
            socket.close()
            thread.join(1_000)
            clients.shutdownNow()
            clients.awaitTermination(2, TimeUnit.SECONDS)
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
        val YOUTUBE_CLIPPED_RETURN_HTML by lazy {
            fun String.replaceFixturePart(original: String, replacement: String): String {
                check(contains(original)) { "Missing YouTube fixture source: $original" }
                return replace(original, replacement)
            }
            YOUTUBE_RETURN_HTML.replaceFixturePart(
                "#player-parent { transform:translate3d(0,0,0); }",
                "#player-parent { position:relative; aspect-ratio:16/9; overflow:hidden; transform:translate3d(0,0,0); }",
            ).replaceFixturePart(
                "#movie_player { position:relative; top:0; left:0; width:100%; aspect-ratio:16/9; }",
                "#movie_player { position:relative; top:0; left:0; width:100%; aspect-ratio:16/9; overflow:hidden; transform:translateZ(0); }",
            ).replaceFixturePart(
                "video { display:block; width:100%; height:100%; background:#080; }",
                "video { position:absolute; top:0; left:0; display:block; width:100%; height:100%; background:#080; }",
            ).replaceFixturePart(
                "const directReturnOnly = false;",
                "const directReturnOnly = true;",
            )
        }
        val YOUTUBE_RETURN_HTML by lazy {
            """
            <!doctype html>
            <html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>youtube-return-loading</title><style>
              html,body { margin:0; width:100%; height:100%; background:#111; }
              header { height:56px; background:#b00; }
              #player-parent { transform:translate3d(0,0,0); }
              #movie_player { position:relative; top:0; left:0; width:100%; aspect-ratio:16/9; }
              video { display:block; width:100%; height:100%; background:#080; }
            </style></head><body><header></header>
            <div id="player-parent"><div id="movie_player" class="html5-video-player">
              <video autoplay muted playsinline loop></video><div class="ytp-chrome-bottom"></div>
            </div></div><script>
              const encodedVideo = [
                // ffmpeg -f lavfi -i color=c=0x00cccc:s=160x90:r=2:d=12 -an -c:v libvpx -b:v 40k fixture.webm
                // Decoded cyan differs from the green CSS fallback; black is always a failed frame.
                'GkXfo59ChoEBQveBAULygQRC84EIQoKEd2VibUKHgQJChYECGFOAZwEAAAAAAASQEU2bdLpNu4tTq4QV',
                'SalmU6yBoU27i1OrhBZUrmtTrIHWTbuMU6uEElTDZ1OsggEyTbuMU6uEHFO7a1OsggR67AEAAAAAAABZ',
                'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA',
                'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAVSalmsCrXsYMPQkBNgIxMYXZmNjMuMS4xMDFXQYxM',
                'YXZmNjMuMS4xMDFEiYhAx3AAAAAAABZUrmvXrgEAAAAAAABO14EBc8WIuEiFyor52QWcgQAitZyDdW5k',
                'iIEAhoVWX1ZQOIOBASPjg4QdzWUA4JCwgaC6gVqagQJVsIRVuYEBVe6BAOwBAAAAAAAAAgAAElTDZ/pz',
                'c59jwIBnyJlFo4dFTkNPREVSRIeMTGF2ZjYzLjEuMTAxc3PVY8CLY8WIuEiFyor52QVnyKBFo4dFTkNP',
                'REVSRIeTTGF2YzYzLjEuMTAxIGxpYnZweGfIoUWjiERVUkFUSU9ORIeTMDA6MDA6MTIuMDAwMDAwMDAw',
                'AB9DtnVBX+eBAKPXgQAAgPAFAJ0BKqAAWgAARwiFhYiFhIgCAgJ1qgP4AgaaE+CGqpNdxDqqTXcQ6qk1',
                '3EOqpNdxDqqTXcQYAP7ujn/+7J/tk/2yf+8Z//W6363W/W63/rWYo5iBAfQAEQIAARAQABgAGFgv9AAI',
                'gIEAAACjmIED6AARAgABEBAAGAAYWC/0AAiAgQAAAKOYgQXcABECAAEQEAAYABhYL/QACICBAAAAo5iB',
                'B9AAEQIAARAQABgAGFgv9AAIgIEAAACjmIEJxAARAgABEBAAGAAYWC/0AAiAgQAAAKOYgQu4ABECAAEQ',
                'EAAYABhYL/QACICBAAAAo5eBDawA8QEAARAQFGAAYWC/0AAiAgQAAKOYgQ+gABECAAEQEAAYABhYL/QA',
                'CICBAAAAo5iBEZQAEQIAARAQABgAGFgv9AAIgIEAAACjmIETiAARAgABEBAAGAAYWC/0AAiAgQAAAB9D',
                'tnVBIeeCFXyjmIEAAAARAgABEBAAGAAYWC/0AAiAgQAAAKOYgQH0ABECAAEQEAAYABhYL/QACICBAAAA',
                'o5iBA+gAEQIAARAQABgAGFgv9AAIgIEAAACjmIEF3AARAgABEBAAGAAYWC/0AAiAgQAAAKOYgQfQABEC',
                'AAEQEAAYABhYL/QACICBAAAAo5iBCcQAEQIAARAQABgAGFgv9AAIgIEAAACjmIELuAARAgABEBAAGAAY',
                'WC/0AAiAgQAAAKOXgQ2sAPEBAAEQEBRgAGFgv9AAIgIEAACjmIEPoAARAgABEBAAGAAYWC/0AAiAgQAA',
                'AKOYgRGUABECAAEQEAAYABhYL/QACICBAAAAo5iBE4gAEQIAARAQABgAGFgv9AAIgIEAAAAfQ7Z1uOeC',
                'KvijmIEAAAARAgABEBAAGAAYWC/0AAiAgQAAAKOYgQH0ABECAAEQEAAYABhYL/QACICBAAAAHFO7a5G7',
                'j7OBALeK94EB8YIBsfCBAw=='
              ].join('');
              const video = document.querySelector('video');
              const player = document.querySelector('#movie_player');
              const parent = document.querySelector('#player-parent');
              const directReturnOnly = false;
              let trustedClicks = 0;
              let baselineRecorded = false;
              let fullscreenControlsRecorded = false;
              let pipObserved = false;
              let pipCleanupRecorded = false;
              let returnedAt = null;
              let lastProbe = '';
              let probePending = false;
              let presentedVideoFrames = 0;
              let fullscreenExitFrame = 0;
              const rect = element => {
                const bounds = element.getBoundingClientRect();
                return [bounds.left, bounds.top, bounds.width, bounds.height];
              };
              const layoutStyle = element => {
                const style = getComputedStyle(element);
                return {
                  position:style.position, top:style.top, height:style.height,
                  paddingTop:style.paddingTop, marginTop:style.marginTop,
                  transform:style.transform, translate:style.translate, overflow:style.overflow,
                  safeAreaInsetTop:style.getPropertyValue('--candy-safe-area-inset-top'),
                  originTop:style.getPropertyValue('--candy-inline-video-fullscreen-origin-top')
                };
              };
              const sample = phase => {
                const controls = document.querySelector('[data-candy-inline-video-controls]');
                const action = document.querySelector('[data-candy-inline-video-action]');
                const geometry = {
                  phase, elapsed:returnedAt === null ? 0 : Math.round(performance.now() - returnedAt),
                  sampledAtEpochMillis:Date.now(),
                  video:rect(video), player:rect(player), parent:rect(parent),
                  body:rect(document.body), root:rect(document.documentElement),
                  bodyStyle:layoutStyle(document.body), rootStyle:layoutStyle(document.documentElement),
                  headerStyle:layoutStyle(document.querySelector('header')),
                  playerStyle:layoutStyle(player), videoStyle:layoutStyle(video),
                  scrollX, scrollY, devicePixelRatio,
                  visualViewportOffsetTop:visualViewport?.offsetTop,
                  controlsRect:controls ? rect(controls) : null,
                  controlsParent:controls?.parentElement?.id || controls?.parentElement?.tagName || null,
                  header:rect(document.querySelector('header')),
                  playerTop:getComputedStyle(player).top,
                  playerTransform:getComputedStyle(player).transform,
                  videoTranslate:getComputedStyle(video).translate,
                  objectFit:getComputedStyle(video).objectFit,
                  objectPosition:getComputedStyle(video).objectPosition,
                  videoWidth:video.videoWidth, videoHeight:video.videoHeight,
                  duration:video.duration, presentedVideoFrames,
                  action:action ? rect(action) : null,
                  viewportWidth:innerWidth, viewportHeight:innerHeight,
                  paused:video.paused, readyState:video.readyState,
                  trustedClicks,
                  currentTime:video.currentTime, mediaError:video.error?.code || null,
                  controls:Boolean(controls && getComputedStyle(controls).display !== 'none'),
                  sitePlayer:player.hasAttribute('data-candy-inline-video-site-player'),
                  originStyle:Boolean(document.querySelector('[data-candy-inline-video-fullscreen-origin-style]')),
                  originPlayer:player.hasAttribute('data-candy-inline-video-fullscreen-origin'),
                  pipRoot:document.documentElement.hasAttribute('data-candy-picture-in-picture'),
                  pipStyle:Boolean(document.querySelector('[data-candy-picture-in-picture-style]')),
                  pipOffsets:Boolean(
                    video.style.getPropertyValue('--candy-picture-in-picture-offset-x') ||
                    video.style.getPropertyValue('--candy-picture-in-picture-offset-y')
                  ),
                  pipAttributes:Boolean(document.querySelector(
                    '[data-candy-picture-in-picture], [data-candy-picture-in-picture-video], ' +
                    '[data-candy-picture-in-picture-ancestor]'
                  )),
                  fullscreen:Boolean(document.fullscreenElement)
                };
                document.title = 'youtube-return-' + phase + ':' + geometry.video.join(',');
                fetch('/geometry?' + encodeURIComponent(JSON.stringify(geometry)));
              };
              const observe = () => {
                const pip = video.hasAttribute('data-candy-picture-in-picture-video');
                if (pip && !pipObserved) { pipObserved = true; sample('pip'); }
                if (pipObserved && !pipCleanupRecorded && !document.querySelector(
                    '[data-candy-picture-in-picture], [data-candy-picture-in-picture-video], ' +
                    '[data-candy-picture-in-picture-ancestor]')) {
                  pipCleanupRecorded = true;
                  sample('pip-attributes-removed');
                }
                if (document.fullscreenElement && !fullscreenControlsRecorded &&
                    document.querySelector('[data-candy-inline-video-controls]')) {
                  fullscreenControlsRecorded = true;
                  requestAnimationFrame(() => requestAnimationFrame(() => sample('fullscreen-controls')));
                }
                if (!baselineRecorded && presentedVideoFrames >= 3 &&
                    !video.paused && video.readyState === 4 && !pip && !document.fullscreenElement &&
                    document.querySelector('[data-candy-inline-video-action]')) {
                  baselineRecorded = true;
                  requestAnimationFrame(() => requestAnimationFrame(() => sample('baseline')));
                }
              };
              const startInlineTimeline = () => {
                if (returnedAt !== null) return;
                returnedAt = performance.now();
                sample('inline-return-start');
                setTimeout(() => sample('immediate'), 100);
                setTimeout(() => { parent.style.transform = 'translate3d(0,32px,0)'; }, 1200);
                setTimeout(() => sample('delayed'), 1800);
                // A second site reflow follows the bridge's five-second frame reconciliation.
                setTimeout(() => { parent.style.transform = 'translate3d(0,64px,0)'; }, 5600);
                setTimeout(() => sample('late'), 6500);
              };
              // Native test progress only requests measurements, never media/fullscreen state.
              // Polling remains independent of PiP attribute removal and fullscreenchange.
              setInterval(async () => {
                if (probePending) return;
                probePending = true;
                try {
                  const probe = await (await fetch('/probe', {cache:'no-store'})).text();
                  if (probe && probe !== lastProbe) {
                    if (probe === 'direct-fullscreen-return' &&
                        presentedVideoFrames < fullscreenExitFrame + 2) return;
                    lastProbe = probe;
                    if (probe === 'inline-return') startInlineTimeline();
                    else sample(probe);
                  }
                } finally {
                  probePending = false;
                }
              }, 50);
              new MutationObserver(observe).observe(document.documentElement, {
                subtree:true, childList:true, attributes:true,
                attributeFilter:[
                  'data-candy-picture-in-picture', 'data-candy-picture-in-picture-video',
                  'data-candy-picture-in-picture-ancestor'
                ]
              });
              if (directReturnOnly) {
                let insetMutation = 0;
                const insetObserver = new MutationObserver(() => {
                  sample('direct-inset-mutation-' + (++insetMutation));
                });
                insetObserver.observe(document.documentElement, {attributes:true, attributeFilter:['style']});
                insetObserver.observe(document.body, {attributes:true, attributeFilter:['style']});
              }
              document.addEventListener('fullscreenchange', () => {
                if (!document.fullscreenElement) fullscreenExitFrame = presentedVideoFrames;
                if (directReturnOnly && !document.fullscreenElement && returnedAt === null) {
                  returnedAt = performance.now();
                  setTimeout(() => sample('direct-immediate'), 100);
                  setTimeout(() => sample('direct-delayed'), 1800);
                  setTimeout(() => sample('direct-late'), 6500);
                }
                sample(document.fullscreenElement ? 'fullscreen' : 'fullscreen-exit');
                if (document.fullscreenElement) {
                  video.play().catch(() => sample('play-rejected'));
                  setTimeout(() => sample('fullscreen-settled'), 1000);
                }
                observe();
              });
              video.addEventListener('loadedmetadata', () => sample('loaded'));
              document.addEventListener('click', event => {
                if (event.isTrusted) trustedClicks++;
                sample('click-capture');
              }, true);
              video.addEventListener('error', () => sample('media-error'));
              video.defaultMuted = true;
              video.muted = true;
              const onVideoFrame = (_now, metadata) => {
                presentedVideoFrames = metadata.presentedFrames;
                observe();
                video.requestVideoFrameCallback(onVideoFrame);
              };
              video.requestVideoFrameCallback(onVideoFrame);
              video.src = 'data:video/webm;base64,' + encodedVideo;
              video.load();
            </script></body></html>
            """.trimIndent()
        }
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
