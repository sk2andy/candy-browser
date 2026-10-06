package dev.sk2andy.materialbrowser.browser

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import dev.sk2andy.materialbrowser.browser.actions.BrowserContentTargetListener
import dev.sk2andy.materialbrowser.browser.gecko.AndroidBrowserEngineSessionPort
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEnginePreviewCapture
import dev.sk2andy.materialbrowser.browser.gecko.GeckoFindResult
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaCommand
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaSessionState
import dev.sk2andy.materialbrowser.browser.gecko.GeckoMediaSessionStateListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoNavigationRequestListener
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPrivacyPolicy
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.DeveloperSettings
import dev.sk2andy.materialbrowser.data.FaviconRepository
import dev.sk2andy.materialbrowser.data.HistoryRecordingMode
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.data.ProfileWallpaperStore
import dev.sk2andy.materialbrowser.data.TabPreviewRepository
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommand
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommandType
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEvent
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class BrowserControllerBackgroundMemoryInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null
    private lateinit var originalTabs: Pair<List<BrowserTab>, String?>
    private lateinit var originalProfiles: Pair<List<BrowserProfile>, String>
    private lateinit var originalEngineKind: AndroidBrowserEngineKind
    private lateinit var originalHistoryRecordingMode: HistoryRecordingMode
    private lateinit var originalInactiveTabLifetime: InactiveTabLifetime
    private lateinit var originalDeveloperSettings: DeveloperSettings
    private var originalResidentTabLimit = 0
    private var originalProfilesEnabled = false

    @Before
    fun setUp() {
        composeRule.runOnIdle {
            val store = BrowserSessionStore(composeRule.activity)
            originalTabs = store.loadTabs()
            originalProfiles = store.loadProfiles()
            originalEngineKind = store.loadAndroidBrowserEngineKind()
            originalHistoryRecordingMode = store.loadHistoryRecordingMode()
            originalInactiveTabLifetime = store.loadInactiveTabLifetime()
            originalDeveloperSettings = store.loadDeveloperSettings()
            originalResidentTabLimit = store.loadResidentTabLimit()
            originalProfilesEnabled = store.loadProfilesEnabled()
            assertTrue(store.saveTabsImmediately(emptyList(), ""))
            assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
            assertTrue(store.saveHistoryRecordingMode(HistoryRecordingMode.Disabled))
            store.saveInactiveTabLifetime(InactiveTabLifetime.Never)
            store.saveResidentTabLimit(10)
            store.saveDeveloperSettings(DeveloperSettings())
            store.saveProfilesEnabled(true)
            controller = BrowserController(composeRule.activity)
        }
    }

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            try {
                controller?.destroy()
            } finally {
                val store = BrowserSessionStore(composeRule.activity)
                assertTrue(store.saveAndroidBrowserEngineKind(originalEngineKind))
                assertTrue(store.saveHistoryRecordingMode(originalHistoryRecordingMode))
                store.saveInactiveTabLifetime(originalInactiveTabLifetime)
                store.saveResidentTabLimit(originalResidentTabLimit)
                store.saveDeveloperSettings(originalDeveloperSettings)
                store.saveProfiles(originalProfiles.first, originalProfiles.second)
                store.saveProfilesEnabled(originalProfilesEnabled)
                assertTrue(store.saveTabsImmediately(originalTabs.first, originalTabs.second.orEmpty()))
            }
        }
    }

    @Test
    fun stoppedActivityReleasesViewAndRejectsSynchronousReattachmentWithoutClosingSelectedSession() {
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            val session = installSelectedSession()
            val host = FrameLayout(composeRule.activity)
            composeRule.activity.addContentView(
                host,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            browserController.onStart()
            val oldView = requireNotNull(browserController.attachSelectedBrowserEngineView(host))
            session.onReleaseView = {
                assertNull(browserController.attachSelectedBrowserEngineView(host))
            }

            browserController.onStop()
            browserController.onAppBackgrounded()

            assertNull(oldView.parent)
            assertNull(browserController.selectedBrowserEngineViewForTesting())
            assertNull(browserController.attachSelectedBrowserEngineView(host))
            assertEquals(1, session.createViewCount)
            assertEquals(1, session.releaseViewCount)
            assertTrue(session.commands.isEmpty())
            assertTrue(session.tabId in browserController.residentTabIdsForTesting())

            browserController.onStart()
            val newView = requireNotNull(browserController.attachSelectedBrowserEngineView(host))

            assertNotSame(oldView, newView)
            assertSame(host, newView.parent)
            assertEquals(2, session.createViewCount)
            assertTrue(session.commands.isEmpty())
        }
    }

    @Test
    fun selectedPriorityRemainsIndependentOfStoppedRendererVisibility() {
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            val first = installSelectedSession()
            val second = createSelectedSession()
            browserController.selectTab(first.tabId)
            assertEquals(listOf(false, true), first.selectedPriorityStates)
            assertEquals(listOf(false), second.selectedPriorityStates)

            browserController.onStart()
            browserController.onStop()
            browserController.onAppBackgrounded()

            assertEquals(listOf(false, true), first.selectedPriorityStates)
            assertEquals(listOf(false), second.selectedPriorityStates)
            assertEquals(false, first.activeStates.last())
        }
    }

    @Test
    fun backgroundWarmQuotaKeepsNewestUnselectedSessionsBesideSelected() {
        lateinit var oldest: MemorySession
        lateinit var middle: MemorySession
        lateinit var newest: MemorySession
        lateinit var selected: MemorySession
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            oldest = installSelectedSession()
            middle = createSelectedSession()
            newest = createSelectedSession()
            selected = createSelectedSession()
            updateMemorySettings(backgroundWarmTabCount = 2)

            browserController.onAppBackgrounded()
            browserController.trimBackgroundMemoryForTesting()
        }
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            assertEquals(
                setOf(middle.tabId, newest.tabId, selected.tabId),
                browserController.residentTabIdsForTesting(),
            )
            assertEquals(listOf(BrowserEngineCommandType.Close), oldest.commands.map { it.type })
            assertTrue(middle.commands.isEmpty())
            assertTrue(newest.commands.isEmpty())
            assertTrue(selected.commands.isEmpty())
        }
    }

    @Test
    fun backgroundTransitionImmediatelyTrimsWithoutMemorySignal() {
        lateinit var background: MemorySession
        lateinit var selected: MemorySession
        composeRule.runOnIdle {
            background = installSelectedSession()
            selected = createSelectedSession()

            requireNotNull(controller).onAppBackgrounded()
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            requireNotNull(controller).residentTabIdsForTesting() == setOf(selected.tabId)
        }
        composeRule.runOnIdle {
            assertEquals(listOf(BrowserEngineCommandType.Close), background.commands.map { it.type })
            assertTrue(selected.commands.isEmpty())
        }
    }

    @Test
    fun foregroundReturnCancelsImmediateBackgroundResult() {
        lateinit var background: MemorySession
        lateinit var selected: MemorySession
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            background = installSelectedSession()
            selected = createSelectedSession()

            browserController.onAppBackgrounded()
            browserController.onAppForegrounded()
        }
        composeRule.runOnIdle {
            assertEquals(
                setOf(background.tabId, selected.tabId),
                requireNotNull(controller).residentTabIdsForTesting(),
            )
            assertEquals(1, background.inputCheckCount)
            assertTrue(background.commands.isEmpty())
        }
    }

    @Test
    fun backgroundUnknownInputResultRetriesAndThenUnloads() {
        lateinit var background: MemorySession
        lateinit var selected: MemorySession
        composeRule.runOnIdle {
            background = installSelectedSession(deferFormCheck = true)
            selected = createSelectedSession()
            requireNotNull(controller).onAppBackgrounded()
            assertEquals(1, background.inputCheckCount)
            background.completeInputCheck(null)
        }
        composeRule.waitUntil(timeoutMillis = 3_000L) { background.inputCheckCount == 2 }
        composeRule.runOnUiThread { background.completeInputCheck(false) }
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            requireNotNull(controller).residentTabIdsForTesting() == setOf(selected.tabId)
        }
        composeRule.runOnIdle {
            assertEquals(listOf(BrowserEngineCommandType.Close), background.commands.map { it.type })
        }
    }

    @Test
    fun backgroundTimeoutRetriesAndLateReplyDoesNotConsumeNewerAttempt() {
        lateinit var background: MemorySession
        lateinit var selected: MemorySession
        composeRule.runOnIdle {
            background = installSelectedSession(deferFormCheck = true)
            selected = createSelectedSession()
            requireNotNull(controller).onAppBackgrounded()
            assertEquals(1, background.inputCheckCount)
        }
        composeRule.waitUntil(timeoutMillis = 4_000L) { background.inputCheckCount == 2 }
        composeRule.runOnUiThread { background.completeInputCheck(false, index = 0) }
        composeRule.runOnUiThread {
            assertEquals(
                setOf(background.tabId, selected.tabId),
                requireNotNull(controller).residentTabIdsForTesting(),
            )
            assertTrue(background.commands.isEmpty())
            background.completeInputCheck(false, index = 1)
        }
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            requireNotNull(controller).residentTabIdsForTesting() == setOf(selected.tabId)
        }
    }

    @Test
    fun backgroundUnknownInputRetriesAreBoundedAndForegroundCancelsScheduledRetry() {
        lateinit var background: MemorySession
        lateinit var selected: MemorySession
        composeRule.runOnIdle {
            background = installSelectedSession(containsFormData = null)
            selected = createSelectedSession()
            requireNotNull(controller).onAppBackgrounded()
        }
        composeRule.waitUntil(timeoutMillis = 3_000L) { background.inputCheckCount == 3 }
        composeRule.runOnIdle {
            assertEquals(
                setOf(background.tabId, selected.tabId),
                requireNotNull(controller).residentTabIdsForTesting(),
            )
            requireNotNull(controller).onAppForegrounded()
            requireNotNull(controller).onAppBackgrounded()
            requireNotNull(controller).onAppForegrounded()
        }
        // A foreground sweep must not pick up the queued retry from the old background generation.
        composeRule.runOnIdle {
            assertEquals(4, background.inputCheckCount)
            assertTrue(background.commands.isEmpty())
        }
    }

    @Test
    fun backgroundInputFreeTabUnloadsDespiteNativeFormState() {
        lateinit var background: MemorySession
        lateinit var selected: MemorySession
        composeRule.runOnIdle {
            background = installSelectedSession(containsFormData = true, containsUserInput = false)
            selected = createSelectedSession()
            requireNotNull(controller).onAppBackgrounded()
        }
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            requireNotNull(controller).residentTabIdsForTesting() == setOf(selected.tabId)
        }
        composeRule.runOnIdle {
            assertEquals(0, background.formCheckCount)
            assertEquals(1, background.inputCheckCount)
            assertEquals(listOf(BrowserEngineCommandType.Close), background.commands.map { it.type })
        }
    }

    @Test
    fun increasingWarmQuotaInvalidatesPendingEvictionCheck() {
        lateinit var background: MemorySession
        lateinit var selected: MemorySession
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            background = installSelectedSession(deferFormCheck = true)
            selected = createSelectedSession()
            browserController.onAppBackgrounded()
            browserController.trimBackgroundMemoryForTesting()
            assertEquals(1, background.inputCheckCount)

            updateMemorySettings(backgroundWarmTabCount = 1)
            background.completeInputCheck(false)
        }
        composeRule.runOnIdle {
            assertEquals(
                setOf(background.tabId, selected.tabId),
                requireNotNull(controller).residentTabIdsForTesting(),
            )
            assertTrue(background.commands.isEmpty())
        }
    }

    @Test
    fun foregroundIdleEvictsEligibleTabAndKeepsSelectedPrivateFormsAndAudio() {
        lateinit var audio: MemorySession
        lateinit var form: MemorySession
        lateinit var unknown: MemorySession
        lateinit var privateSession: MemorySession
        lateinit var eligible: MemorySession
        lateinit var selected: MemorySession
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            audio = installSelectedSession()
            browserController.reportSelectedGeckoMediaStateForTesting(
                GeckoMediaSessionState(
                    isActive = true,
                    isPlaying = true,
                    audioTrackCount = 1,
                    durationMillis = 60_000L,
                ),
            )
            form = createSelectedSession(containsFormData = true)
            unknown = createSelectedSession(containsFormData = null)
            privateSession = createSelectedSession(isIncognito = true)
            eligible = createSelectedSession()
            selected = createSelectedSession()

            browserController.trimIdleResidentSessionsForTesting(
                SystemClock.elapsedRealtime() + 180_000L,
            )
        }
        composeRule.runOnIdle {
            assertEquals(
                setOf(audio.tabId, form.tabId, unknown.tabId, privateSession.tabId, selected.tabId),
                requireNotNull(controller).residentTabIdsForTesting(),
            )
            assertEquals(0, audio.formCheckCount)
            assertEquals(1, form.formCheckCount)
            assertEquals(1, unknown.formCheckCount)
            assertEquals(0, privateSession.formCheckCount)
            assertEquals(0, selected.formCheckCount)
            assertEquals(listOf(BrowserEngineCommandType.Close), eligible.commands.map { it.type })
        }
    }

    @Test
    fun configuredForegroundIdleTimeoutChangesEvictionDeadline() {
        lateinit var idle: MemorySession
        lateinit var selected: MemorySession
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            idle = installSelectedSession()
            selected = createSelectedSession()
            browserController.trimIdleResidentSessionsForTesting(
                SystemClock.elapsedRealtime() + 61_000L,
            )
            assertEquals(0, idle.formCheckCount)

            updateMemorySettings(foregroundTabIdleTimeoutMinutes = 1)
            browserController.trimIdleResidentSessionsForTesting(
                SystemClock.elapsedRealtime() + 61_000L,
            )
        }
        composeRule.runOnIdle {
            assertEquals(setOf(selected.tabId), requireNotNull(controller).residentTabIdsForTesting())
            assertEquals(listOf(BrowserEngineCommandType.Close), idle.commands.map { it.type })
        }
    }

    @Test
    fun idleClockStartsWhenLongSelectedTabLosesFocus() {
        lateinit var previous: MemorySession
        lateinit var selected: MemorySession
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            previous = installSelectedSession()
            selected = createSelectedSession()
            browserController.selectTab(previous.tabId)
            setLastAccessElapsedRealtime(previous.tabId, SystemClock.elapsedRealtime() - 180_001L)

            browserController.selectTab(selected.tabId)
            browserController.trimIdleResidentSessionsForTesting(SystemClock.elapsedRealtime() + 1_000L)
        }
        composeRule.runOnIdle {
            assertEquals(
                setOf(previous.tabId, selected.tabId),
                requireNotNull(controller).residentTabIdsForTesting(),
            )
            assertEquals(0, previous.formCheckCount)
            assertTrue(previous.commands.isEmpty())
        }
    }

    @Test
    fun foregroundIdleCheckAfterReactivationAtSameClockTickDoesNotEvict() {
        lateinit var idle: MemorySession
        lateinit var selected: MemorySession
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            idle = installSelectedSession(deferFormCheck = true)
            selected = createSelectedSession()
            val sameClockTick = SystemClock.elapsedRealtime()
            setLastAccessElapsedRealtime(idle.tabId, sameClockTick)
            browserController.trimIdleResidentSessionsForTesting(
                sameClockTick + 180_000L,
            )
            assertEquals(1, idle.formCheckCount)

            browserController.selectTab(idle.tabId)
            browserController.selectTab(selected.tabId)
            // Simulate repeated elapsed-realtime ticks; access sequence must still invalidate the check.
            setLastAccessElapsedRealtime(idle.tabId, sameClockTick)
            idle.completeFormCheck(false)
        }
        composeRule.runOnIdle {
            assertEquals(
                setOf(idle.tabId, selected.tabId),
                requireNotNull(controller).residentTabIdsForTesting(),
            )
            assertTrue(idle.commands.isEmpty())
        }
    }

    @Test
    fun foregroundIdleCheckAfterBackgroundTransitionDoesNotEvict() {
        lateinit var idle: MemorySession
        lateinit var selected: MemorySession
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            idle = installSelectedSession(deferFormCheck = true)
            selected = createSelectedSession()
            browserController.trimIdleResidentSessionsForTesting(
                SystemClock.elapsedRealtime() + 180_000L,
            )
            assertEquals(1, idle.formCheckCount)

            browserController.onAppBackgrounded()
            idle.completeFormCheck(false)
        }
        composeRule.runOnIdle {
            assertEquals(
                setOf(idle.tabId, selected.tabId),
                requireNotNull(controller).residentTabIdsForTesting(),
            )
            assertTrue(idle.commands.isEmpty())
        }
    }

    @Test
    fun backgroundEvictsConfirmedInputFreeTabAndKeepsSelectedSession() {
        lateinit var background: MemorySession
        lateinit var selected: MemorySession
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            background = installSelectedSession()
            selected = createSelectedSession()

            browserController.onAppBackgrounded()
            browserController.trimBackgroundMemoryForTesting()
        }
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)

            assertEquals(setOf(selected.tabId), browserController.residentTabIdsForTesting())
            assertEquals(1, background.inputCheckCount)
            assertEquals(0, selected.inputCheckCount)
            assertEquals(listOf(BrowserEngineCommandType.Close), background.commands.map { it.type })
            assertTrue(selected.commands.isEmpty())
            assertTrue(browserController.tabs.any { it.id == background.tabId })
            assertEquals("https://memory.example/${background.tabId}", browserController.tabs.first {
                it.id == background.tabId
            }.url)
        }
    }

    @Test
    fun backgroundKeepsPrivateTabsAndConfirmedOrUnknownInputState() {
        lateinit var form: MemorySession
        lateinit var unknown: MemorySession
        lateinit var privateSession: MemorySession
        lateinit var eligible: MemorySession
        lateinit var selected: MemorySession
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            form = installSelectedSession(containsFormData = true)
            unknown = createSelectedSession(containsFormData = null)
            privateSession = createSelectedSession(isIncognito = true)
            eligible = createSelectedSession()
            selected = createSelectedSession()

            browserController.onAppBackgrounded()
            browserController.trimBackgroundMemoryForTesting()
        }
        composeRule.waitUntil(timeoutMillis = 3_000L) { unknown.inputCheckCount == 3 }
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)

            assertEquals(
                setOf(form.tabId, unknown.tabId, privateSession.tabId, selected.tabId),
                browserController.residentTabIdsForTesting(),
            )
            assertEquals(1, form.inputCheckCount)
            assertEquals(3, unknown.inputCheckCount)
            assertEquals(0, privateSession.inputCheckCount)
            assertTrue(form.commands.isEmpty())
            assertTrue(unknown.commands.isEmpty())
            assertTrue(privateSession.commands.isEmpty())
            assertEquals(listOf(BrowserEngineCommandType.Close), eligible.commands.map { it.type })
        }
    }

    @Test
    fun deferredInputCheckAfterForegroundDoesNotEvict() {
        lateinit var background: MemorySession
        lateinit var selected: MemorySession
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            background = installSelectedSession(deferFormCheck = true)
            selected = createSelectedSession()
            browserController.onAppBackgrounded()
            browserController.trimBackgroundMemoryForTesting()
            assertEquals(1, background.inputCheckCount)

            browserController.onAppForegrounded()
            background.completeInputCheck(false)
        }
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)

            assertEquals(
                setOf(background.tabId, selected.tabId),
                browserController.residentTabIdsForTesting(),
            )
            assertTrue(background.commands.isEmpty())
        }
    }

    @Test
    fun deferredInputCheckAfterSameUrlNavigationDoesNotEvictNewDocument() {
        lateinit var background: MemorySession
        lateinit var selected: MemorySession
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            background = installSelectedSession(deferFormCheck = true)
            selected = createSelectedSession()
            browserController.onAppBackgrounded()
            browserController.trimBackgroundMemoryForTesting()
            assertEquals(1, background.inputCheckCount)

            commitNavigation(background.tabId)
            background.completeInputCheck(false)
        }
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)

            assertEquals(
                setOf(background.tabId, selected.tabId),
                browserController.residentTabIdsForTesting(),
            )
            assertTrue(background.commands.isEmpty())
        }
    }

    @Test
    fun deferredInputCheckAfterSessionReplacementDoesNotEvictReplacement() {
        lateinit var background: MemorySession
        lateinit var selected: MemorySession
        lateinit var replacement: MemorySession
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            background = installSelectedSession(deferFormCheck = true)
            selected = createSelectedSession()
            browserController.onAppBackgrounded()
            browserController.trimBackgroundMemoryForTesting()
            assertEquals(1, background.inputCheckCount)
            replacement = MemorySession(background.tabId)
            browserController.installGeckoEngineSessionForTesting(replacement)

            background.completeInputCheck(false)
        }
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)

            assertEquals(
                setOf(background.tabId, selected.tabId),
                browserController.residentTabIdsForTesting(),
            )
            assertEquals(listOf(BrowserEngineCommandType.Close), background.commands.map { it.type })
            assertTrue(replacement.commands.isEmpty())
        }
    }

    @Test
    fun deferredInputCheckAfterTabSelectionDoesNotEvictSelectedTab() {
        lateinit var background: MemorySession
        lateinit var previousSelection: MemorySession
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            background = installSelectedSession(deferFormCheck = true)
            previousSelection = createSelectedSession()
            browserController.onAppBackgrounded()
            browserController.trimBackgroundMemoryForTesting()
            assertEquals(1, background.inputCheckCount)

            browserController.selectTab(background.tabId)
            background.completeInputCheck(false)
        }
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)

            assertEquals(background.tabId, browserController.selectedTabId)
            assertEquals(
                setOf(background.tabId),
                browserController.residentTabIdsForTesting(),
            )
            assertTrue(background.commands.isEmpty())
            assertEquals(
                listOf(BrowserEngineCommandType.Close),
                previousSelection.commands.map { it.type },
            )
        }
    }

    @Test
    fun hiddenUiCompletesPendingWallpaperOpeningAndForegroundLoadsFreshBitmap() {
        val workerStarted = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        val staleLoadFinished = CountDownLatch(1)
        val freshOpeningReady = CountDownLatch(1)
        var originalOpeningReadyCount = 0
        var freshOpeningReadyCount = 0
        var profileId: String? = null
        lateinit var wallpaperExecutor: ExecutorService
        try {
            composeRule.runOnIdle {
                val browserController = requireNotNull(controller)
                profileId = requireNotNull(browserController.createProfile("🌄"))
                wallpaperExecutor = browserController.javaClass
                    .getDeclaredField("profileWallpaperExecutor")
                    .apply { isAccessible = true }
                    .get(browserController) as ExecutorService
                wallpaperExecutor.execute {
                    workerStarted.countDown()
                    releaseWorker.await(10L, TimeUnit.SECONDS)
                }
            }
            assertTrue(workerStarted.await(5L, TimeUnit.SECONDS))
            composeRule.runOnIdle {
                val browserController = requireNotNull(controller)
                val wallpaperProfileId = requireNotNull(profileId)
                val wallpaper = colorfulBitmap()
                try {
                    assertTrue(
                        ProfileWallpaperStore(composeRule.activity).save(
                            wallpaperProfileId,
                            ProfileWallpaperTarget.TabSwitcher,
                            wallpaper,
                        ),
                    )
                } finally {
                    wallpaper.recycle()
                }
                assertTrue(
                    browserController.updateProfileWallpaper(
                        wallpaperProfileId,
                        ProfileWallpaperTarget.TabSwitcher,
                        ProfileWallpaper(),
                    ),
                )
                browserController.loadActiveProfileTabSwitcherWallpaper {
                    originalOpeningReadyCount++
                }
                wallpaperExecutor.execute { staleLoadFinished.countDown() }
                assertEquals(0, originalOpeningReadyCount)

                browserController.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)

                assertEquals(1, originalOpeningReadyCount)
                assertNull(browserController.activeProfileTabSwitcherWallpaperBitmap)
            }
            releaseWorker.countDown()
            assertTrue(staleLoadFinished.await(5L, TimeUnit.SECONDS))
            composeRule.runOnIdle {
                val browserController = requireNotNull(controller)
                assertEquals(1, originalOpeningReadyCount)
                assertNull(browserController.activeProfileTabSwitcherWallpaperBitmap)

                browserController.onStart()
                browserController.loadActiveProfileTabSwitcherWallpaper {
                    freshOpeningReadyCount++
                    freshOpeningReady.countDown()
                }
            }
            assertTrue(freshOpeningReady.await(5L, TimeUnit.SECONDS))
            composeRule.runOnIdle {
                val browserController = requireNotNull(controller)
                assertEquals(1, originalOpeningReadyCount)
                assertEquals(1, freshOpeningReadyCount)
                assertEquals(8, browserController.activeProfileTabSwitcherWallpaperBitmap?.width)
                assertEquals(8, browserController.activeProfileTabSwitcherWallpaperBitmap?.height)
            }
        } finally {
            releaseWorker.countDown()
            composeRule.runOnIdle {
                profileId?.let(ProfileWallpaperStore(composeRule.activity)::delete)
            }
        }
    }

    @Test
    fun backgroundDropsRegularImagesAndForegroundRestoresDiskCopiesKeepingPrivateImages() {
        lateinit var regular: MemorySession
        lateinit var privateSession: MemorySession
        lateinit var privatePreview: Bitmap
        lateinit var privateFavicon: Bitmap
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            regular = installSelectedSession()
            privateSession = createSelectedSession(isIncognito = true)
            createSelectedSession()
            val preview = colorfulBitmap()
            val favicon = colorfulBitmap()
            privatePreview = colorfulBitmap()
            privateFavicon = colorfulBitmap()
            browserController.previews[regular.tabId] = preview
            browserController.favicons[regular.tabId] = favicon
            browserController.previews[privateSession.tabId] = privatePreview
            browserController.favicons[privateSession.tabId] = privateFavicon
            val previewRepository = TabPreviewRepository.get(composeRule.activity)
            val faviconRepository = FaviconRepository.get(composeRule.activity)
            previewRepository.save(regular.tabId, preview)
            faviconRepository.save(regular.tabId, favicon)
            assertTrue(previewRepository.flush())
            assertTrue(faviconRepository.flush())

            browserController.onAppBackgrounded()
            browserController.trimBackgroundMemoryForTesting()

            assertNull(browserController.previews[regular.tabId])
            assertNull(browserController.favicons[regular.tabId])
            assertSame(privatePreview, browserController.previews[privateSession.tabId])
            assertSame(privateFavicon, browserController.favicons[privateSession.tabId])
            assertFalse(privatePreview.isRecycled)
            assertFalse(privateFavicon.isRecycled)
            browserController.onAppForegrounded()
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            val browserController = requireNotNull(controller)
            browserController.previews[regular.tabId] != null &&
                browserController.favicons[regular.tabId] != null
        }
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            val restoredPreview = requireNotNull(browserController.previews[regular.tabId])
            assertEquals(8, restoredPreview.width)
            assertEquals(8, restoredPreview.height)
            assertTrue(Color.blue(restoredPreview.getPixel(0, 0)) > 200)
            assertTrue(Color.red(restoredPreview.getPixel(0, 0)) < 32)
            assertEquals(
                Color.YELLOW,
                requireNotNull(browserController.favicons[regular.tabId]).getPixel(7, 7),
            )
            assertSame(privatePreview, browserController.previews[privateSession.tabId])
            assertSame(privateFavicon, browserController.favicons[privateSession.tabId])
        }
    }

    @Test
    fun restorationPreviewUsesSharpDiskImageAndOldOwnerCannotClearNewHandoff() {
        val firstOwner = Any()
        val secondOwner = Any()
        lateinit var session: MemorySession
        lateinit var thumbnail: Bitmap
        val repository = TabPreviewRepository.get(composeRule.activity)
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            session = installSelectedSession()
            thumbnail = colorfulBitmap()
            browserController.previews[session.tabId] = thumbnail
            val sharp = Bitmap.createBitmap(1_080, 2_410, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.BLUE)
            }
            repository.saveCapture(session.tabId, sharp)
            assertTrue(repository.flush())
            browserController.requestRestorationPreview(session.tabId, firstOwner)
            browserController.requestRestorationPreview(session.tabId, secondOwner)
        }
        composeRule.waitUntil(5_000L) { requireNotNull(controller).restorationPreview != null }
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            val preview = requireNotNull(browserController.restorationPreview)
            assertEquals(session.tabId, preview.tabId)
            assertEquals(1_080, preview.bitmap.width)
            assertEquals(2_410, preview.bitmap.height)
            assertSame(thumbnail, browserController.previews[session.tabId])
            browserController.releaseRestorationPreview(firstOwner)
            assertSame(preview, browserController.restorationPreview)
            browserController.releaseRestorationPreview(secondOwner)
            assertNull(browserController.restorationPreview)
            assertFalse(preview.bitmap.isRecycled)
            repository.delete(session.tabId)
        }
    }

    @Test
    fun backgroundInvalidatesPendingSharpPreviewBeforeItsDecodeCompletes() {
        val repository = TabPreviewRepository.get(composeRule.activity)
        val owner = Any()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        lateinit var session: MemorySession
        try {
            composeRule.runOnIdle {
                session = installSelectedSession()
                repository.saveCapture(session.tabId, colorfulBitmap())
                assertTrue(repository.flush())
                repository.loadRestorationPreview(session.tabId) { bitmap ->
                    try {
                        entered.countDown()
                        assertTrue(release.await(5, TimeUnit.SECONDS))
                    } finally {
                        bitmap?.recycle()
                    }
                }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                requireNotNull(controller).requestRestorationPreview(session.tabId, owner)
                requireNotNull(controller).onAppBackgrounded()
            }
            release.countDown()
            assertTrue(repository.flush())
            composeRule.runOnIdle {
                assertNull(requireNotNull(controller).restorationPreview)
                repository.delete(session.tabId)
            }
        } finally {
            release.countDown()
        }
    }

    @Test
    fun selectionChangeRejectsPendingRestorationPreview() {
        assertPendingRestorationPreviewRejected { browserController, _ ->
            browserController.createTab(isIncognito = false)
        }
    }

    @Test
    fun profileMoveRejectsPendingRestorationPreview() {
        assertPendingRestorationPreviewRejected { browserController, session ->
            val targetProfile = BrowserProfile(id = "preview-target", emoji = "🧪")
            browserController.profiles += targetProfile
            assertTrue(browserController.moveTabToProfile(session.tabId, targetProfile.id))
        }
    }

    @Test
    fun profileLockRejectsPendingRestorationPreviewBeforeComposeDisposesOwner() {
        assertPendingRestorationPreviewRejected { browserController, _ ->
            val index = browserController.profiles.indexOfFirst { it.id == browserController.activeProfileId }
            browserController.profiles[index] = browserController.profiles[index].copy(
                protection = ProfileProtection(ProfileLockTrigger.AppBackgrounded),
            )
            val lock = browserController.javaClass.getDeclaredMethod("lockProtectedProfiles", Function1::class.java)
                .apply { isAccessible = true }
            lock.invoke(browserController, { _: ProfileProtection -> true })
            assertTrue(browserController.isActiveProfileLocked)
        }
    }

    private fun assertPendingRestorationPreviewRejected(
        changeOwner: (BrowserController, MemorySession) -> Unit,
    ) {
        val repository = TabPreviewRepository.get(composeRule.activity)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        lateinit var session: MemorySession
        try {
            composeRule.runOnIdle {
                val browserController = requireNotNull(controller)
                session = installSelectedSession()
                repository.saveCapture(session.tabId, colorfulBitmap())
                assertTrue(repository.flush())
                repository.loadRestorationPreview(session.tabId) { bitmap ->
                    try {
                        entered.countDown()
                        assertTrue(release.await(5, TimeUnit.SECONDS))
                    } finally {
                        bitmap?.recycle()
                    }
                }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                browserController.requestRestorationPreview(session.tabId, Any())
                changeOwner(browserController, session)
            }
            release.countDown()
            assertTrue(repository.flush())
            composeRule.runOnIdle {
                assertNull(requireNotNull(controller).restorationPreview)
                repository.delete(session.tabId)
            }
        } finally {
            release.countDown()
        }
    }

    @Test
    fun privateTabsNeverRequestPersistedRestorationImages() {
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            val session = createSelectedSession(isIncognito = true)

            browserController.requestRestorationPreview(session.tabId, Any())

            assertNull(browserController.restorationPreview)
        }
    }

    @Test
    fun backgroundAudioKeepsPlaybackButDeactivatesRendererAndRetainsSession() {
        lateinit var audio: MemorySession
        var backgroundedAt = 0L
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            audio = installSelectedSession()
            browserController.onStart()
            browserController.reportSelectedGeckoMediaStateForTesting(
                GeckoMediaSessionState(
                    isActive = true,
                    isPlaying = true,
                    audioTrackCount = 1,
                    durationMillis = 60_000L,
                ),
            )
            assertTrue(requireNotNull(browserController.media3Publication()).snapshot.isPlaying)
            audio.activeStates.clear()
            audio.mediaCommands.clear()

            backgroundedAt = SystemClock.elapsedRealtime()
            browserController.onStop()
            browserController.onAppBackgrounded()
            browserController.trimBackgroundMemoryForTesting()

            assertEquals(listOf(false), audio.activeStates)
            assertFalse(audio.mediaCommands.contains(GeckoMediaCommand.Pause))
            assertTrue(requireNotNull(browserController.media3Publication()).snapshot.isPlaying)
            assertTrue(audio.tabId in browserController.residentTabIdsForTesting())
        }
        composeRule.waitUntil(timeoutMillis = 8_000L) {
            SystemClock.elapsedRealtime() - backgroundedAt >= 6_000L
        }
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            assertFalse(audio.mediaCommands.contains(GeckoMediaCommand.Pause))
            assertTrue(requireNotNull(browserController.media3Publication()).snapshot.isPlaying)
            assertTrue(audio.tabId in browserController.residentTabIdsForTesting())
        }
    }

    @Test
    fun backgroundBudgetKeepsAudioOwnerAfterSelectingAnotherTab() {
        lateinit var audio: MemorySession
        lateinit var selected: MemorySession
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            audio = installSelectedSession()
            browserController.reportSelectedGeckoMediaStateForTesting(
                GeckoMediaSessionState(
                    isActive = true,
                    isPlaying = true,
                    audioTrackCount = 1,
                    durationMillis = 60_000L,
                ),
            )
            assertTrue(requireNotNull(browserController.media3Publication()).snapshot.isPlaying)
            selected = createSelectedSession()

            browserController.onAppBackgrounded()
            browserController.trimBackgroundMemoryForTesting()
        }
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            assertEquals(
                setOf(audio.tabId, selected.tabId),
                browserController.residentTabIdsForTesting(),
            )
            assertEquals(0, audio.formCheckCount)
            assertTrue(audio.commands.isEmpty())
            assertFalse(audio.mediaCommands.contains(GeckoMediaCommand.Pause))
        }
    }

    @Test
    fun hiddenUiMemorySignalDropsImagesWithoutEvictingSessions() {
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            val background = installSelectedSession()
            val selected = createSelectedSession()
            browserController.previews[background.tabId] = colorfulBitmap()
            browserController.favicons[selected.tabId] = colorfulBitmap()

            browserController.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)

            assertTrue(browserController.previews.isEmpty())
            assertTrue(browserController.favicons.isEmpty())
            assertEquals(
                setOf(background.tabId, selected.tabId),
                browserController.residentTabIdsForTesting(),
            )
            assertEquals(0, background.formCheckCount)
            assertEquals(1, background.trimUiMemoryCount)
            assertEquals(1, selected.trimUiMemoryCount)
        }
    }

    @Test
    fun backgroundMemorySignalDoesNotDuplicateImmediateInputCheck() {
        lateinit var background: MemorySession
        lateinit var selected: MemorySession
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            background = installSelectedSession()
            selected = createSelectedSession()
            browserController.onAppBackgrounded()
            assertEquals(1, background.inputCheckCount)

            browserController.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        }
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            assertEquals(setOf(selected.tabId), browserController.residentTabIdsForTesting())
            assertEquals(1, background.inputCheckCount)
        }
    }

    @Test
    fun foregroundMemorySignalKeepsVisibleUiAndSessions() {
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            val session = installSelectedSession()
            val preview = colorfulBitmap()
            browserController.previews[session.tabId] = preview
            browserController.onStart()

            browserController.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)

            assertSame(preview, browserController.previews[session.tabId])
            assertEquals(0, session.trimUiMemoryCount)
            assertEquals(0, session.formCheckCount)
            assertTrue(session.commands.isEmpty())
        }
    }

    @Test
    fun visiblePictureInPictureKeepsRendererActiveWhenActivityStops() {
        composeRule.runOnIdle {
            val browserController = requireNotNull(controller)
            val session = installSelectedSession()
            browserController.onStart()
            session.activeStates.clear()
            session.mediaCommands.clear()

            browserController.onStop(isInPictureInPictureMode = true)

            assertTrue(session.activeStates.isEmpty())
            assertFalse(session.mediaCommands.contains(GeckoMediaCommand.Pause))
            assertTrue(session.tabId in browserController.residentTabIdsForTesting())
        }
    }

    private fun createSelectedSession(
        isIncognito: Boolean = false,
        containsFormData: Boolean? = false,
    ): MemorySession {
        requireNotNull(controller).createTab(isIncognito = isIncognito)
        return installSelectedSession(containsFormData = containsFormData)
    }

    private fun updateMemorySettings(
        foregroundTabIdleTimeoutMinutes: Int? = null,
        backgroundWarmTabCount: Int? = null,
    ) {
        val browserController = requireNotNull(controller)
        val settings = browserController.developerSettings
        val memorySettings = settings.browserMemorySettings
        browserController.updateDeveloperSettings(
            settings.copy(
                browserMemorySettings = memorySettings.copy(
                    foregroundTabIdleTimeoutMinutes = foregroundTabIdleTimeoutMinutes
                        ?: memorySettings.foregroundTabIdleTimeoutMinutes,
                    backgroundWarmTabCount = backgroundWarmTabCount
                        ?: memorySettings.backgroundWarmTabCount,
                ),
            ),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun setLastAccessElapsedRealtime(tabId: String, timestamp: Long) {
        val browserController = requireNotNull(controller)
        val accessTimes = browserController.javaClass
            .getDeclaredField("residentSessionLastAccessElapsedRealtime")
            .apply { isAccessible = true }
            .get(browserController) as MutableMap<String, Long>
        accessTimes[tabId] = timestamp
    }

    private fun installSelectedSession(
        containsFormData: Boolean? = false,
        deferFormCheck: Boolean = false,
        containsUserInput: Boolean? = containsFormData,
    ): MemorySession {
        val browserController = requireNotNull(controller)
        return MemorySession(
            tabId = browserController.selectedTabId,
            formData = containsFormData,
            deferFormCheck = deferFormCheck,
            userInput = containsUserInput,
        ).also { session ->
            browserController.installGeckoEngineSessionForTesting(session)
            commitNavigation(session.tabId)
            session.commands.clear()
        }
    }

    private fun commitNavigation(tabId: String) {
        val browserController = requireNotNull(controller)
        listOf(
            BrowserEngineEventType.NavigationStarted,
            BrowserEngineEventType.NavigationCommitted,
        ).forEach { type ->
            browserController.dispatchGeckoEngineEventForTesting(
                BrowserEngineEvent(
                    tabId = tabId,
                    type = type,
                    address = "https://memory.example/$tabId",
                    title = "Memory fixture",
                    canGoBack = false,
                    canGoForward = false,
                    failureDescription = null,
                ),
            )
        }
    }

    private fun colorfulBitmap(): Bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.BLUE)
        for (y in 4 until height) {
            for (x in 0 until width) setPixel(x, y, Color.YELLOW)
        }
    }

    private class MemorySession(
        override val tabId: String,
        private val formData: Boolean? = false,
        private val deferFormCheck: Boolean = false,
        private val userInput: Boolean? = formData,
    ) : AndroidBrowserEngineSessionPort {
        val commands = mutableListOf<BrowserEngineCommand>()
        val mediaCommands = mutableListOf<GeckoMediaCommand>()
        val activeStates = mutableListOf<Boolean>()
        val selectedPriorityStates = mutableListOf<Boolean>()
        var createViewCount = 0
            private set
        var releaseViewCount = 0
            private set
        var onReleaseView: (() -> Unit)? = null
        var formCheckCount = 0
            private set
        var inputCheckCount = 0
            private set
        var trimUiMemoryCount = 0
            private set
        private var formCallback: ((Boolean?) -> Unit)? = null
        private val inputCallbacks = mutableListOf<(Boolean?) -> Unit>()
        private var active = false

        override fun containsUserInput(onResult: (Boolean?) -> Unit) {
            inputCheckCount++
            if (deferFormCheck) inputCallbacks += onResult else onResult(userInput)
        }

        fun completeInputCheck(containsUserInput: Boolean?, index: Int = inputCallbacks.lastIndex) {
            inputCallbacks[index](containsUserInput)
        }

        override fun containsFormData(onResult: (Boolean?) -> Unit) {
            formCheckCount++
            if (deferFormCheck) formCallback = onResult else onResult(formData)
        }

        fun completeFormCheck(containsFormData: Boolean?) {
            val callback = requireNotNull(formCallback)
            formCallback = null
            callback(containsFormData)
        }

        override fun execute(command: BrowserEngineCommand) {
            commands += command
        }

        override fun trimUiMemory() {
            trimUiMemoryCount++
        }

        override fun setActive(active: Boolean) {
            if (this.active == active) return
            this.active = active
            activeStates += active
        }

        override fun executeMediaCommand(command: GeckoMediaCommand) {
            mediaCommands += command
        }

        override fun setSelectedPriority(selected: Boolean) {
            selectedPriorityStates += selected
        }

        override fun createView(context: Context): View {
            createViewCount++
            return View(context)
        }

        override fun releaseView(view: View) {
            releaseViewCount++
            onReleaseView?.invoke()
        }
        override fun awaitContentPresented(listener: () -> Unit) = Unit
        override fun capturePreview(
            targetWidthPx: Int,
            visibleViewHeightPx: Int,
            maximumTargetHeightPx: Int,
            onComplete: (Bitmap?) -> Unit,
        ): BrowserEnginePreviewCapture? = null

        override fun findInPage(query: String, forward: Boolean, onComplete: (GeckoFindResult?) -> Unit) =
            onComplete(null)

        override fun clearFindInPage() = Unit
        override fun printPage(): Boolean = false
        override fun setDesktopMode(enabled: Boolean) = Unit
        override fun setMediaStateListener(listener: GeckoMediaSessionStateListener?) = Unit
        override fun setScrollListener(listener: BrowserEngineScrollListener?) = Unit
        override fun setContentTargetListener(listener: BrowserContentTargetListener?) = Unit
        override fun setNavigationRequestListener(listener: GeckoNavigationRequestListener?) = Unit
        override fun setVideoAutoplayBlocked(blocked: Boolean) = Unit
        override fun setAudioMuted(muted: Boolean) = Unit
        override fun seekMedia(positionMillis: Long) = Unit
        override fun goToHistoryIndex(index: Int) = Unit
        override fun historyUrlAtOffset(offset: Int): String? = null
        override fun extractPageForReader(onComplete: (String?) -> Unit) = onComplete(null)
        override fun updatePrivacyPolicy(
            policy: GeckoPrivacyPolicy,
            reloadOnCookiePermissionChange: Boolean,
            onReady: () -> Unit,
        ) = onReady()
    }
}
