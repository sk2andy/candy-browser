package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.view.View
import dev.sk2andy.materialbrowser.browser.BrowserEngineScrollEvent
import dev.sk2andy.materialbrowser.browser.BrowserEngineScrollListener
import dev.sk2andy.materialbrowser.browser.actions.BrowserContentTargetListener
import dev.sk2andy.materialbrowser.browser.actions.WebContentTarget
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommands
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEvent
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoBrowserEngineAdapterTest {
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
        adapter.execute(BrowserEngineCommands.back())
        adapter.execute(BrowserEngineCommands.forward())
        adapter.execute(BrowserEngineCommands.reload())
        adapter.execute(BrowserEngineCommands.stop())

        assertEquals("https://example.com", session.loadedUrl)
        assertEquals(listOf("back", "forward", "reload", "stop"), session.actions)
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

private class FakeGeckoBrowserSession(
    private val loadAccepted: Boolean = true,
    private val initialState: GeckoBrowserSessionState = GeckoBrowserSessionState(),
) : GeckoBrowserSession {
    override val profileId = "profile"
    override val isPrivate = false

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
    val pictureInPictureStates = mutableListOf<Boolean>()
    val pictureInPicturePlaybackStates = mutableListOf<Boolean>()
    var exitFullscreenCount = 0
    private var listener: GeckoBrowserSessionStateListener? = null
    private var historyListener: GeckoBrowserHistoryStateListener? = null
    private var scrollListener: BrowserEngineScrollListener? = null
    private var contentTargetListener: BrowserContentTargetListener? = null
    private var navigationRequestListener: GeckoNavigationRequestListener? = null
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
