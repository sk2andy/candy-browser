package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.content.res.Configuration
import android.view.View
import dev.sk2andy.materialbrowser.browser.DnsOverHttpsSettings
import dev.sk2andy.materialbrowser.browser.WebRtcProtectionMode
import dev.sk2andy.materialbrowser.browser.BrowserEngineScrollEvent
import dev.sk2andy.materialbrowser.browser.BrowserEngineScrollListener
import dev.sk2andy.materialbrowser.browser.actions.BrowserContentTargetListener
import dev.sk2andy.materialbrowser.browser.actions.WebContentTarget
import dev.sk2andy.materialbrowser.browser.engine.BrowserEngineContentKind
import dev.sk2andy.materialbrowser.browser.engine.BrowserWebContentColorScheme
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommands
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEvent
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineFailureKind
import java.lang.ref.Reference
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.geckoview.GeckoRuntime

class GeckoBrowserEngineAdapterTest {
    @Test
    fun `delayed WebRTC acknowledgement reloads only its still open snapshot`() {
        val liveSession = FakeGeckoBrowserSession()
        val closedSession = FakeGeckoBrowserSession()
        val laterSession = FakeGeckoBrowserSession()
        val runtime = DeferredWebRtcRuntime(liveSession, closedSession, laterSession)
        val factory = GeckoBrowserEngineSessionFactory(runtime)
        val liveAdapter = createFactoryAdapter(factory, "live")
        val closedAdapter = createFactoryAdapter(factory, "closed")

        factory.setWebRtcProtectionMode(WebRtcProtectionMode.Block)
        assertTrue(liveSession.actions.isEmpty())
        closedAdapter.execute(BrowserEngineCommands.close())
        val laterAdapter = createFactoryAdapter(factory, "later")
        runtime.acknowledge()

        assertEquals(listOf("reload"), liveSession.actions)
        assertTrue(closedSession.actions.isEmpty())
        assertTrue(laterSession.actions.isEmpty())
        assertEquals(1, closedSession.closeCount)
        liveAdapter.execute(BrowserEngineCommands.close())
        laterAdapter.execute(BrowserEngineCommands.close())
    }

    @Test
    fun `withheld WebRTC acknowledgement does not retain closed adapter or event owner`() {
        val session = FakeGeckoBrowserSession()
        val runtime = DeferredWebRtcRuntime(session)
        val factory = GeckoBrowserEngineSessionFactory(runtime)
        val queue = ReferenceQueue<Any>()
        val references = closeFactoryAdapterWithPendingPolicy(factory, queue)
        val collected = mutableSetOf<Reference<out Any>>()
        val deadline = System.nanoTime() + 5_000_000_000L
        while (collected.size < references.size && System.nanoTime() < deadline) {
            System.gc()
            System.runFinalization()
            queue.remove(50L)?.let(collected::add)
            var reference = queue.poll()
            while (reference != null) {
                collected += reference
                reference = queue.poll()
            }
        }

        assertEquals("Pending ACK retained closed adapter or its event owner", references.toSet(), collected)
        assertTrue(runtime.hasPendingAcknowledgement)
        runtime.acknowledge()
        assertTrue(session.actions.isEmpty())
        assertEquals(1, session.closeCount)
    }

    private fun closeFactoryAdapterWithPendingPolicy(
        factory: GeckoBrowserEngineSessionFactory,
        queue: ReferenceQueue<Any>,
    ): List<WeakReference<Any>> {
        val owner = FactoryEventOwner()
        val adapter = createFactoryAdapter(factory, "closed", BrowserEngineEventSink(owner::onEvent))
        factory.setWebRtcProtectionMode(WebRtcProtectionMode.Block)
        adapter.execute(BrowserEngineCommands.close())
        return listOf(WeakReference(adapter, queue), WeakReference(owner, queue))
    }

    private fun createFactoryAdapter(
        factory: GeckoBrowserEngineSessionFactory,
        tabId: String,
        eventSink: BrowserEngineEventSink = BrowserEngineEventSink { },
    ): AndroidBrowserEngineSessionPort = factory.create(
        tabId = tabId,
        profileId = "profile",
        isPrivate = false,
        contentKind = BrowserEngineContentKind.RegularTab,
        eventSink = eventSink,
    )

    @Test
    fun `page close requests are delivered without closing the adapter directly`() {
        val session = FakeGeckoBrowserSession()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "auth-popup",
            session = session,
            eventSink = BrowserEngineEventSink { },
        )
        var requests = 0
        adapter.setCloseRequestListener { requests++ }

        session.emitCloseRequest()

        assertEquals(1, requests)
        assertEquals(0, session.closeCount)
        adapter.setCloseRequestListener(null)
        session.emitCloseRequest()
        assertEquals(1, requests)
    }

    @Test
    fun `closed adapter clears page close requests and rejects new listeners`() {
        val session = FakeGeckoBrowserSession()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "auth-popup",
            session = session,
            eventSink = BrowserEngineEventSink { },
        )
        var requests = 0
        adapter.setCloseRequestListener { requests++ }

        adapter.execute(BrowserEngineCommands.close())
        session.emitCloseRequest()
        adapter.setCloseRequestListener { requests++ }
        session.emitCloseRequest()

        assertEquals(0, requests)
        assertEquals(1, session.closeCount)
    }

    @Test
    fun `renderer crash clears page close requests`() {
        val session = FakeGeckoBrowserSession()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "auth-popup",
            session = session,
            eventSink = BrowserEngineEventSink { },
        )
        var requests = 0
        adapter.setCloseRequestListener { requests++ }

        session.emit(GeckoBrowserSessionState(crashed = true))
        session.emitCloseRequest()

        assertEquals(0, requests)
        assertEquals(1, session.closeCount)
    }

    @Test
    fun `HTTPS error arriving after page stop reports confirmed failure`() {
        val session = FakeGeckoBrowserSession()
        val events = mutableListOf<BrowserEngineEvent>()
        GeckoBrowserEngineSessionAdapter(
            tabId = "https-warning-tab",
            session = session,
            eventSink = BrowserEngineEventSink(events::add),
        )
        val loading = GeckoBrowserSessionState(url = "http://example.com", isLoading = true)
        session.emit(loading)
        val stopped = loading.copy(isLoading = false, lastNavigationSucceeded = false)
        session.emit(stopped)
        assertEquals(
            listOf(BrowserEngineEventType.NavigationStarted, BrowserEngineEventType.StateChanged),
            events.map(BrowserEngineEvent::type),
        )
        events.clear()

        session.emit(
            stopped.copy(
                failureDescription = "Gecko navigation failed",
                failureKind = BrowserEngineFailureKind.HttpsOnly,
            ),
        )

        assertEquals(1, events.size)
        assertEquals(BrowserEngineEventType.NavigationFailed, events.single().type)
        assertEquals(BrowserEngineFailureKind.HttpsOnly, events.single().failureKind)
    }

    @Test
    fun `unsuccessful stop followed by new page does not report navigation failure`() {
        val session = FakeGeckoBrowserSession()
        val events = mutableListOf<BrowserEngineEvent>()
        GeckoBrowserEngineSessionAdapter(
            tabId = "navigation-tab",
            session = session,
            eventSink = events::add,
        )
        val google = GeckoBrowserSessionState(url = "https://www.google.com", isLoading = true)
        session.emit(google)
        session.emit(google.copy(isLoading = false, lastNavigationSucceeded = false))
        val destination = GeckoBrowserSessionState(url = "https://vaerm.de", isLoading = true)
        session.emit(destination)
        session.emit(destination.copy(isLoading = false, lastNavigationSucceeded = true))

        assertEquals(
            listOf(
                BrowserEngineEventType.NavigationStarted,
                BrowserEngineEventType.StateChanged,
                BrowserEngineEventType.NavigationStarted,
                BrowserEngineEventType.NavigationCommitted,
            ),
            events.map(BrowserEngineEvent::type),
        )
        assertTrue(events.all { it.failureDescription == null })
    }

    @Test
    fun `transport error description arriving after page stop reports failure`() {
        val session = FakeGeckoBrowserSession()
        val events = mutableListOf<BrowserEngineEvent>()
        GeckoBrowserEngineSessionAdapter(
            tabId = "failed-tab",
            session = session,
            eventSink = events::add,
        )
        val loading = GeckoBrowserSessionState(url = "https://example.com", isLoading = true)
        session.emit(loading)
        val stopped = loading.copy(isLoading = false, lastNavigationSucceeded = false)
        session.emit(stopped)
        events.clear()
        session.emit(stopped.copy(failureDescription = "Connection refused"))

        assertEquals(BrowserEngineEventType.NavigationFailed, events.single().type)
        assertEquals("Connection refused", events.single().failureDescription)
    }

    @Test
    fun `progress only changes reach shared events while adapter is open`() {
        val session = FakeGeckoBrowserSession()
        val events = mutableListOf<BrowserEngineEvent>()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = BrowserEngineEventSink(events::add),
        )
        val loading = GeckoBrowserSessionState(
            url = "https://example.com/",
            isLoading = true,
            progress = 0,
        )
        session.emit(loading)
        events.clear()

        session.emit(loading.copy(progress = 42))
        session.emit(loading.copy(progress = 80))

        assertEquals(listOf(42, 80), events.map { it.progress })
        assertTrue(events.all { it.type == BrowserEngineEventType.StateChanged })
        assertTrue(events.all { it.isLoading == true })
        adapter.execute(BrowserEngineCommands.close())
        val countAfterClose = events.size
        session.emit(loading.copy(progress = 95))
        assertEquals(countAfterClose, events.size)
    }

    @Test
    fun `scroll events are forwarded only while adapter is open`() {
        val session = FakeGeckoBrowserSession()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = BrowserEngineEventSink { },
        )
        val scrollPositions = mutableListOf<Int>()
        adapter.setScrollListener { event -> scrollPositions += event.scrollYPx }

        session.emitScroll(32)
        adapter.execute(BrowserEngineCommands.close())
        session.emitScroll(64)

        assertEquals(listOf(32), scrollPositions)
    }

    @Test
    fun `shared commands are translated to the Gecko session`() {
        val session = FakeGeckoBrowserSession()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = BrowserEngineEventSink { },
        )

        adapter.execute(BrowserEngineCommands.load("https://example.com"))
        adapter.execute(BrowserEngineCommands.replaceHistory("https://example.com/source"))
        adapter.execute(BrowserEngineCommands.back())
        adapter.execute(BrowserEngineCommands.forward())
        adapter.execute(BrowserEngineCommands.reload())
        adapter.execute(BrowserEngineCommands.stop())

        assertEquals("https://example.com", session.loadedUrl)
        assertEquals(
            listOf(
                "replace:https://example.com/source",
                "back",
                "forward",
                "reload",
                "stop",
            ),
            session.actions,
        )
    }

    @Test
    fun `trail traversal delegates an exact Gecko history index`() {
        val session = FakeGeckoBrowserSession()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = BrowserEngineEventSink { },
        )

        adapter.goToHistoryIndex(2)

        assertEquals(2, session.historyIndex)
    }

    @Test
    fun `history target lookup stays read only and is unavailable after close`() {
        val session = FakeGeckoBrowserSession().apply {
            historyUrls = listOf("https://example.com/one", "https://example.com/two")
            historyCurrentIndex = 1
        }
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = BrowserEngineEventSink { },
        )

        assertEquals("https://example.com/one", adapter.historyUrlAtOffset(-1))
        assertEquals(1, session.historyCurrentIndex)
        adapter.execute(BrowserEngineCommands.close())
        assertNull(adapter.historyUrlAtOffset(-1))
    }

    @Test
    fun `preview capture is delegated with bounded viewport dimensions`() {
        val session = FakeGeckoBrowserSession()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = BrowserEngineEventSink { },
        )

        adapter.capturePreview(
            targetWidthPx = 480,
            visibleViewHeightPx = 900,
            maximumTargetHeightPx = 1_440,
            onComplete = {},
        )

        assertEquals(listOf(480, 900, 1_440), session.previewRequest)
    }

    @Test
    fun `find reader and print actions stay behind Gecko session port`() {
        val session = FakeGeckoBrowserSession()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = BrowserEngineEventSink { },
        )

        adapter.findInPage(query = "candy", forward = false, onComplete = {})
        adapter.clearFindInPage()
        var readerResult: String? = null
        adapter.extractPageForReader { result -> readerResult = result }

        assertEquals("candy" to false, session.findRequest)
        assertEquals("{\"title\":\"Candy\"}", readerResult)
        assertEquals(listOf("clear-find", "reader"), session.actions)
        assertTrue(adapter.printPage())
        assertEquals(listOf("clear-find", "reader", "print"), session.actions)
    }

    @Test
    fun `desktop mode stays behind Gecko session port`() {
        val session = FakeGeckoBrowserSession()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = BrowserEngineEventSink { },
        )

        adapter.setDesktopMode(true)

        assertEquals(true, session.desktopMode)
    }

    @Test
    fun `opaque Gecko state stays behind adapter and rejects restore after close`() {
        val session = FakeGeckoBrowserSession().apply { serializedState = "{native-state}" }
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = BrowserEngineEventSink { },
        )

        assertEquals("{native-state}", adapter.sessionStateSnapshot())
        assertTrue(adapter.restoreSessionState("{restore}"))
        assertEquals("{restore}", session.restoredState)
        adapter.execute(BrowserEngineCommands.close())
        assertNull(adapter.sessionStateSnapshot())
        assertFalse(adapter.restoreSessionState("{stale}"))
    }

    @Test
    fun `autoplay policy stays behind Gecko session port`() {
        val session = FakeGeckoBrowserSession()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = BrowserEngineEventSink { },
        )

        adapter.setVideoAutoplayBlocked(true)

        assertEquals(true, session.autoplayBlocked)
    }

    @Test
    fun `domain mute stays behind Gecko media session port until close`() {
        val session = FakeGeckoBrowserSession()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = BrowserEngineEventSink { },
        )

        adapter.setAudioMuted(true)
        adapter.execute(BrowserEngineCommands.close())
        adapter.setAudioMuted(false)

        assertEquals(listOf(true), session.audioMutedStates)
    }

    @Test
    fun `media commands stay behind Gecko session port`() {
        val session = FakeGeckoBrowserSession()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = BrowserEngineEventSink { },
        )

        adapter.executeMediaCommand(GeckoMediaCommand.Play)
        adapter.seekMedia(12_500)

        assertEquals(GeckoMediaCommand.Play, session.mediaCommand)
        assertEquals(12_500L, session.seekPositionMillis)
    }

    @Test
    fun `picture in picture compositor state stays behind Gecko session port until close`() {
        val session = FakeGeckoBrowserSession()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = BrowserEngineEventSink { },
        )

        adapter.notifyPictureInPictureModeChanged(true)
        adapter.notifyPictureInPictureModeChanged(true)
        adapter.notifyPictureInPictureModeChanged(false)
        adapter.notifyPictureInPictureModeChanged(false)
        adapter.execute(BrowserEngineCommands.close())
        adapter.notifyPictureInPictureModeChanged(true)

        assertEquals(listOf(true, false), session.pictureInPictureStates)
    }

    @Test
    fun `picture in picture playback intent stays behind Gecko session port until close`() {
        val session = FakeGeckoBrowserSession()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = BrowserEngineEventSink { },
        )

        adapter.setPictureInPicturePlaybackExpected(true)
        adapter.setPictureInPicturePlaybackExpected(false)
        adapter.execute(BrowserEngineCommands.close())
        adapter.setPictureInPicturePlaybackExpected(true)

        assertEquals(listOf(true, false), session.pictureInPicturePlaybackStates)
    }

    @Test
    fun `picture in picture restoration acknowledgement stays behind live session`() {
        val session = FakeGeckoBrowserSession()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = BrowserEngineEventSink { },
        )
        val results = mutableListOf<Boolean>()

        adapter.restorePictureInPicturePresentation(results::add)
        adapter.execute(BrowserEngineCommands.close())
        adapter.restorePictureInPicturePresentation(results::add)

        assertEquals(listOf(true, false), results)
        assertEquals(1, session.pictureInPictureRestorationCount)
    }

    @Test
    fun `fullscreen lifecycle stays behind Gecko session port until close`() {
        val session = FakeGeckoBrowserSession()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = BrowserEngineEventSink { },
        )
        val fullscreenStates = mutableListOf<Boolean>()

        adapter.setFullscreenStateListener { fullscreenStates += it }
        session.emitFullscreen(true)
        adapter.exitFullscreen()
        adapter.execute(BrowserEngineCommands.close())
        session.emitFullscreen(false)
        adapter.exitFullscreen()

        assertEquals(listOf(true), fullscreenStates)
        assertEquals(1, session.exitFullscreenCount)
    }

    @Test
    fun `active tab state is forwarded to Gecko extensions until close`() {
        val session = FakeGeckoBrowserSession()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = BrowserEngineEventSink { },
        )

        adapter.setActive(true)
        adapter.setActive(false)
        adapter.execute(BrowserEngineCommands.close())
        adapter.setActive(true)

        assertEquals(listOf(true, false), session.activeStates)
    }

    @Test
    fun `trusted input queries cross the adapter independently from native form state`() {
        val session = FakeUserInputGeckoBrowserSession().apply { formData = true }
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = BrowserEngineEventSink { },
        )
        val results = mutableListOf<Boolean?>()

        adapter.containsUserInput(results::add)
        session.userInput = true
        adapter.containsUserInput(results::add)
        session.userInput = null
        adapter.containsUserInput(results::add)
        adapter.execute(BrowserEngineCommands.close())
        adapter.containsUserInput(results::add)

        assertEquals(listOf(false, true, null, null), results)
        assertEquals(3, session.userInputChecks)
        assertEquals(0, session.formChecks)
    }

    @Test
    fun `sessions without trusted input probes keep conservative form protection`() {
        val session = FakeGeckoBrowserSession().apply { formData = true }
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = BrowserEngineEventSink { },
        )
        val results = mutableListOf<Boolean?>()

        adapter.containsUserInput(results::add)
        session.formData = null
        adapter.containsUserInput(results::add)

        assertEquals(listOf(true, null), results)
        assertEquals(2, session.formChecks)
    }

    @Test
    fun `selected priority remains independent from visibility until close`() {
        val session = FakeGeckoBrowserSession()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = BrowserEngineEventSink { },
        )

        adapter.setSelectedPriority(true)
        adapter.setActive(false)
        adapter.setSelectedPriority(false)
        adapter.execute(BrowserEngineCommands.close())
        adapter.setSelectedPriority(true)

        assertEquals(listOf(true, false), session.selectedPriorities)
        assertEquals(listOf(false), session.activeStates)
    }

    @Test
    fun `long press content targets cross the adapter until close`() {
        val session = FakeGeckoBrowserSession()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = BrowserEngineEventSink { },
        )
        val targets = mutableListOf<WebContentTarget>()
        adapter.setContentTargetListener(targets::add)

        session.emitContentTarget(WebContentTarget(linkUrl = "https://example.com/one"))
        adapter.execute(BrowserEngineCommands.close())
        session.emitContentTarget(WebContentTarget(linkUrl = "https://example.com/stale"))

        assertEquals(
            listOf(WebContentTarget(linkUrl = "https://example.com/one")),
            targets,
        )
    }

    @Test
    fun `main frame navigation requests cross adapter and are detached on close`() {
        val session = FakeGeckoBrowserSession()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = BrowserEngineEventSink { },
        )
        val requests = mutableListOf<GeckoMainFrameNavigationRequest>()
        adapter.setNavigationRequestListener { request ->
            requests += request
            GeckoNavigationRequestDecision.Deny
        }
        val request = GeckoMainFrameNavigationRequest(
            url = "https://outside.example/",
            isRedirect = false,
            hasUserGesture = true,
            isDirectNavigation = false,
        )

        assertEquals(GeckoNavigationRequestDecision.Deny, session.emitNavigationRequest(request))
        adapter.execute(BrowserEngineCommands.close())
        assertEquals(GeckoNavigationRequestDecision.Allow, session.emitNavigationRequest(request))

        assertEquals(listOf(request), requests)
    }

    @Test
    fun `Gecko loading transitions become shared navigation events`() {
        val session = FakeGeckoBrowserSession()
        val events = mutableListOf<BrowserEngineEvent>()
        GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = events::add,
        )

        session.emit(
            GeckoBrowserSessionState(
                url = "https://example.com",
                isLoading = true,
                canGoBack = true,
            ),
        )
        session.emit(
            GeckoBrowserSessionState(
                url = "https://example.com/",
                title = "Example",
                isLoading = false,
                progress = 100,
                canGoBack = true,
                lastNavigationSucceeded = true,
            ),
        )

        assertEquals(
            listOf(
                BrowserEngineEventType.NavigationStarted,
                BrowserEngineEventType.NavigationCommitted,
            ),
            events.map(BrowserEngineEvent::type),
        )
        assertEquals("Example", events.last().title)
        assertTrue(events.last().canGoBack)
        assertFalse(events.last().canGoForward)
    }

    @Test
    fun `cross domain navigation starts again while previous page is loading`() {
        val session = FakeGeckoBrowserSession()
        val events = mutableListOf<BrowserEngineEvent>()
        GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = events::add,
        )
        session.emit(GeckoBrowserSessionState(url = "https://lidl.de/", isLoading = true))
        session.emit(GeckoBrowserSessionState(url = "https://github.com/", isLoading = true))
        session.emit(GeckoBrowserSessionState(url = "https://github.com/explore", isLoading = true))
        session.emit(GeckoBrowserSessionState(url = "https://example.com/", isLoading = true))

        assertEquals(
            listOf("https://lidl.de/", "https://github.com/", "https://example.com/"),
            events.filter { it.type == BrowserEngineEventType.NavigationStarted }.map { it.address },
        )
        assertEquals(BrowserEngineEventType.StateChanged, events[2].type)
    }

    @Test
    fun `same document Gecko changes update shared chrome without ending a load`() {
        val session = FakeGeckoBrowserSession()
        val events = mutableListOf<BrowserEngineEvent>()
        GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = events::add,
        )

        session.emit(
            GeckoBrowserSessionState(
                url = "https://example.com/#details",
                title = "Details",
                canGoBack = true,
            ),
        )

        assertEquals(listOf(BrowserEngineEventType.StateChanged), events.map(BrowserEngineEvent::type))
        assertEquals("https://example.com/#details", events.single().address)
        assertEquals("Details", events.single().title)
    }

    @Test
    fun `Gecko history snapshots preserve traversal identity and mark reloads`() {
        val session = FakeGeckoBrowserSession()
        val historyEvents = mutableListOf<GeckoCandyTrailHistoryEvent>()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = BrowserEngineEventSink { },
            trailHistoryEventSink = GeckoCandyTrailHistoryEventSink { _, event ->
                historyEvents += event
            },
        )

        session.emitHistory(
            GeckoBrowserHistoryState(
                urls = listOf("https://a.example", "https://b.example"),
                currentIndex = 1,
                currentTitle = "B",
            ),
        )
        session.emitHistory(
            GeckoBrowserHistoryState(
                urls = listOf("https://a.example", "https://b.example"),
                currentIndex = 1,
                currentTitle = "B reloaded",
            ),
        )
        adapter.execute(BrowserEngineCommands.close())
        session.emitHistory(
            GeckoBrowserHistoryState(
                urls = listOf("https://a.example"),
                currentIndex = 0,
                currentTitle = "A",
            ),
        )

        assertEquals(2, historyEvents.size)
        assertFalse(historyEvents.first().snapshot.isReload)
        assertTrue(historyEvents.last().snapshot.isReload)
        assertEquals("B reloaded", historyEvents.last().title)
    }

    @Test
    fun `failed or rejected loads emit failure and close is final`() {
        val session = FakeGeckoBrowserSession(loadAccepted = false)
        val events = mutableListOf<BrowserEngineEvent>()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = events::add,
        )

        adapter.execute(BrowserEngineCommands.load("file:///private.txt"))
        adapter.execute(BrowserEngineCommands.close())
        adapter.execute(BrowserEngineCommands.reload())
        var readerCallbackCount = 0
        var closedReaderResult: String? = "unexpected"
        adapter.extractPageForReader { result ->
            readerCallbackCount += 1
            closedReaderResult = result
        }
        session.emit(GeckoBrowserSessionState(isLoading = true))

        assertEquals(
            listOf(BrowserEngineEventType.NavigationFailed, BrowserEngineEventType.Closed),
            events.map(BrowserEngineEvent::type),
        )
        assertEquals(1, session.closeCount)
        assertEquals(1, readerCallbackCount)
        assertNull(closedReaderResult)
        assertTrue(session.actions.isEmpty())
    }

    @Test
    fun `privacy gate failure is emitted even when it completed before listener attachment`() {
        val session = FakeGeckoBrowserSession(
            initialState = GeckoBrowserSessionState(
                lastNavigationSucceeded = false,
                failureDescription = "Candy Privacy host initialization timed out",
            ),
        )
        val events = mutableListOf<BrowserEngineEvent>()

        GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = events::add,
        )

        assertEquals(listOf(BrowserEngineEventType.NavigationFailed), events.map { it.type })
        assertEquals(
            "Candy Privacy host initialization timed out",
            events.single().failureDescription,
        )
    }

    @Test
    fun `stopped load is not reported as navigation failure`() {
        val session = FakeGeckoBrowserSession()
        val events = mutableListOf<BrowserEngineEvent>()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = events::add,
        )
        session.emit(GeckoBrowserSessionState(isLoading = true))

        adapter.execute(BrowserEngineCommands.stop())
        session.emit(
            GeckoBrowserSessionState(
                isLoading = false,
                lastNavigationSucceeded = false,
            ),
        )

        assertEquals(
            listOf(BrowserEngineEventType.NavigationStarted, BrowserEngineEventType.StateChanged),
            events.map(BrowserEngineEvent::type),
        )
    }

    @Test
    fun `late main frame HTTP status is forwarded as shared state`() {
        val session = FakeGeckoBrowserSession()
        val events = mutableListOf<BrowserEngineEvent>()
        GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = events::add,
        )

        session.emit(
            GeckoBrowserSessionState(url = "https://example.com/missing", isLoading = true),
        )
        session.emit(
            GeckoBrowserSessionState(
                url = "https://example.com/missing",
                isLoading = false,
                lastNavigationSucceeded = true,
            ),
        )
        session.emit(
            GeckoBrowserSessionState(
                url = "https://example.com/missing",
                isLoading = false,
                lastNavigationSucceeded = true,
                httpStatusCode = 404,
            ),
        )

        assertEquals(
            listOf(
                BrowserEngineEventType.NavigationStarted,
                BrowserEngineEventType.NavigationCommitted,
                BrowserEngineEventType.StateChanged,
            ),
            events.map(BrowserEngineEvent::type),
        )
        assertEquals(404, events.last().httpStatusCode)
    }

    @Test
    fun `Gecko content process termination detaches dead session but preserves recovery event`() {
        val session = FakeGeckoBrowserSession()
        val events = mutableListOf<BrowserEngineEvent>()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = events::add,
        )

        session.emit(GeckoBrowserSessionState(crashed = true))
        adapter.execute(BrowserEngineCommands.reload())

        assertEquals(listOf(BrowserEngineEventType.Crashed), events.map(BrowserEngineEvent::type))
        assertEquals("Gecko content process terminated", events.single().failureDescription)
        assertTrue(session.actions.isEmpty())
    }
}

private class FactoryEventOwner {
    private val events = mutableListOf<BrowserEngineEvent>()

    fun onEvent(event: BrowserEngineEvent) {
        events += event
    }
}

private class DeferredWebRtcRuntime(vararg sessions: FakeGeckoBrowserSession) : GeckoRuntimeHandle {
    private val pendingSessions = ArrayDeque(sessions.toList())
    private var pendingAcknowledgement: (() -> Unit)? = null
    val hasPendingAcknowledgement: Boolean
        get() = pendingAcknowledgement != null

    override val extensions: GeckoExtensionRuntime
        get() = error("Not used by this ownership test")
    override val toppings: GeckoToppingHostRuntime
        get() = error("Not used by this ownership test")

    override fun createSession(
        profileId: String,
        isolationEnabled: Boolean,
        isPrivate: Boolean,
        privacyPolicy: GeckoPrivacyPolicy,
        privacyEventSink: GeckoPrivacyEventSink,
    ): GeckoBrowserSession = pendingSessions.removeFirst()

    override fun setWebRtcProtectionMode(mode: WebRtcProtectionMode, onReady: () -> Unit) {
        pendingAcknowledgement = onReady
    }

    fun acknowledge() {
        val acknowledgement = checkNotNull(pendingAcknowledgement)
        pendingAcknowledgement = null
        acknowledgement()
    }

    override fun clearBrowsingData(data: GeckoBrowsingData, onComplete: (Boolean) -> Unit) =
        onComplete(true)

    override fun setBlockThirdPartyCookies(blocked: Boolean) = Unit

    override fun setDnsOverHttpsSettings(settings: DnsOverHttpsSettings) = Unit

    override fun setWebContentFontSizeFactor(factor: Float) = Unit

    override fun setWebContentColorScheme(colorScheme: BrowserWebContentColorScheme) = Unit

    override fun onConfigurationChanged(configuration: Configuration) = Unit

    override fun bindWebAuthnActivityDelegate(delegate: GeckoRuntime.ActivityDelegate) = Unit

    override fun unbindWebAuthnActivityDelegate(delegate: GeckoRuntime.ActivityDelegate) = Unit
}

private open class FakeGeckoBrowserSession(
    private val loadAccepted: Boolean = true,
    private val initialState: GeckoBrowserSessionState = GeckoBrowserSessionState(),
) : GeckoBrowserSession {
    override val profileId = "profile"
    override val isPrivate = false

    var formData: Boolean? = false
    var formChecks = 0
    var loadedUrl: String? = null
    val actions = mutableListOf<String>()
    var closeCount = 0
    var previewRequest: List<Int>? = null
    var findRequest: Pair<String, Boolean>? = null
    var desktopMode: Boolean? = null
    var serializedState: String? = null
    var restoredState: String? = null
    var autoplayBlocked: Boolean? = null
    val audioMutedStates = mutableListOf<Boolean>()
    var mediaCommand: GeckoMediaCommand? = null
    var seekPositionMillis: Long? = null
    var historyIndex: Int? = null
    var historyUrls: List<String> = emptyList()
    var historyCurrentIndex: Int = -1
    val activeStates = mutableListOf<Boolean>()
    val selectedPriorities = mutableListOf<Boolean>()
    val pictureInPictureStates = mutableListOf<Boolean>()
    val pictureInPicturePlaybackStates = mutableListOf<Boolean>()
    var pictureInPictureRestorationCount = 0
    var exitFullscreenCount = 0
    private var listener: GeckoBrowserSessionStateListener? = null
    private var historyListener: GeckoBrowserHistoryStateListener? = null
    private var scrollListener: BrowserEngineScrollListener? = null
    private var contentTargetListener: BrowserContentTargetListener? = null
    private var navigationRequestListener: GeckoNavigationRequestListener? = null
    private var closeRequestListener: GeckoCloseRequestListener? = null
    private var fullscreenStateListener: GeckoFullscreenStateListener? = null

    override fun setStateListener(listener: GeckoBrowserSessionStateListener?) {
        this.listener = listener
        listener?.onStateChanged(initialState)
    }

    override fun setHistoryStateListener(listener: GeckoBrowserHistoryStateListener?) {
        historyListener = listener
    }

    override fun setContentTargetListener(listener: BrowserContentTargetListener?) {
        contentTargetListener = listener
    }

    override fun setNavigationRequestListener(listener: GeckoNavigationRequestListener?) {
        navigationRequestListener = listener
    }

    override fun setCloseRequestListener(listener: GeckoCloseRequestListener?) {
        closeRequestListener = listener
    }

    fun emitCloseRequest() {
        closeRequestListener?.onCloseRequest()
    }

    override fun setMediaStateListener(listener: GeckoMediaSessionStateListener?) = Unit

    override fun setFullscreenStateListener(listener: GeckoFullscreenStateListener?) {
        fullscreenStateListener = listener
    }

    override fun setScrollListener(listener: BrowserEngineScrollListener?) {
        scrollListener = listener
    }

    override fun setVideoAutoplayBlocked(blocked: Boolean) {
        autoplayBlocked = blocked
    }

    override fun setAudioMuted(muted: Boolean) {
        audioMutedStates += muted
    }

    override fun executeMediaCommand(command: GeckoMediaCommand) {
        mediaCommand = command
    }

    override fun seekMedia(positionMillis: Long) {
        seekPositionMillis = positionMillis
    }

    override fun notifyPictureInPictureModeChanged(inPictureInPicture: Boolean) {
        pictureInPictureStates += inPictureInPicture
    }

    override fun setPictureInPicturePlaybackExpected(expected: Boolean) {
        pictureInPicturePlaybackStates += expected
    }

    override fun restorePictureInPicturePresentation(onResult: (Boolean) -> Unit) {
        pictureInPictureRestorationCount++
        onResult(true)
    }

    override fun exitFullscreen() {
        exitFullscreenCount++
    }

    override fun goToHistoryIndex(index: Int) {
        historyIndex = index
    }

    override fun historyUrlAtOffset(offset: Int): String? =
        historyUrls.getOrNull(historyCurrentIndex + offset)

    override fun createView(context: Context): View = error("Not used by this unit test")

    override fun awaitContentPresented(listener: () -> Unit) = listener()

    override fun releaseView(view: View) = Unit

    override fun setActive(active: Boolean) {
        activeStates += active
    }

    override fun containsFormData(onResult: (Boolean?) -> Unit) {
        formChecks++
        onResult(formData)
    }

    override fun setSelectedPriority(selected: Boolean) {
        selectedPriorities += selected
    }

    fun emitFullscreen(fullscreen: Boolean) {
        fullscreenStateListener?.onStateChanged(fullscreen)
    }

    override fun capturePreview(
        targetWidthPx: Int,
        visibleViewHeightPx: Int,
        maximumTargetHeightPx: Int,
        onComplete: (android.graphics.Bitmap?) -> Unit,
    ): BrowserEnginePreviewCapture {
        previewRequest = listOf(targetWidthPx, visibleViewHeightPx, maximumTargetHeightPx)
        return BrowserEnginePreviewCapture { }
    }

    override fun findInPage(
        query: String,
        forward: Boolean,
        onComplete: (GeckoFindResult?) -> Unit,
    ) {
        findRequest = query to forward
        onComplete(
            GeckoFindResult(
                activeMatchOrdinal = 0,
                matchCount = 1,
                isDoneCounting = true,
            ),
        )
    }

    override fun clearFindInPage() {
        actions += "clear-find"
    }

    override fun extractPageForReader(onComplete: (String?) -> Unit) {
        actions += "reader"
        onComplete("{\"title\":\"Candy\"}")
    }

    override fun printPage(): Boolean {
        actions += "print"
        return true
    }

    override fun setDesktopMode(enabled: Boolean) {
        desktopMode = enabled
    }

    override fun sessionStateSnapshot(): String? = serializedState

    override fun restoreSessionState(encodedState: String): Boolean {
        restoredState = encodedState
        return true
    }

    override fun loadUrl(url: String): Boolean {
        loadedUrl = url
        return loadAccepted
    }

    override fun replaceHistoryUrl(url: String): Boolean {
        actions += "replace:$url"
        return loadAccepted
    }

    override fun goBack() {
        actions += "back"
    }

    override fun goForward() {
        actions += "forward"
    }

    override fun reload() {
        actions += "reload"
    }

    override fun stop() {
        actions += "stop"
    }

    override fun close() {
        closeCount += 1
    }

    fun emit(state: GeckoBrowserSessionState) {
        listener?.onStateChanged(state)
    }

    fun emitHistory(state: GeckoBrowserHistoryState) {
        historyListener?.onHistoryStateChanged(state)
    }

    fun emitScroll(scrollYPx: Int) {
        scrollListener?.onScrollChanged(BrowserEngineScrollEvent(scrollYPx))
    }

    fun emitContentTarget(target: WebContentTarget) {
        contentTargetListener?.onLongPress(target)
    }

    fun emitNavigationRequest(
        request: GeckoMainFrameNavigationRequest,
    ): GeckoNavigationRequestDecision = navigationRequestListener?.onNavigationRequest(request)
        ?: GeckoNavigationRequestDecision.Allow
}

private class FakeUserInputGeckoBrowserSession : FakeGeckoBrowserSession() {
    var userInput: Boolean? = false
    var userInputChecks = 0

    override fun containsUserInput(onResult: (Boolean?) -> Unit) {
        userInputChecks++
        onResult(userInput)
    }
}
