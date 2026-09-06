package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.view.View
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommands
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEvent
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoBrowserEngineAdapterTest {
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
    fun `find and print actions stay behind Gecko session port`() {
        val session = FakeGeckoBrowserSession()
        val adapter = GeckoBrowserEngineSessionAdapter(
            tabId = "tab-1",
            session = session,
            eventSink = BrowserEngineEventSink { },
        )

        adapter.findInPage(query = "candy", forward = false, onComplete = {})
        adapter.clearFindInPage()

        assertEquals("candy" to false, session.findRequest)
        assertEquals(listOf("clear-find"), session.actions)
        assertTrue(adapter.printPage())
        assertEquals(listOf("clear-find", "print"), session.actions)
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
        session.emit(GeckoBrowserSessionState(isLoading = true))

        assertEquals(
            listOf(BrowserEngineEventType.NavigationFailed, BrowserEngineEventType.Closed),
            events.map(BrowserEngineEvent::type),
        )
        assertEquals(1, session.closeCount)
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
    fun `Gecko crash detaches dead session but preserves recovery event`() {
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
    val activeStates = mutableListOf<Boolean>()
    private var listener: GeckoBrowserSessionStateListener? = null

    override fun setStateListener(listener: GeckoBrowserSessionStateListener?) {
        this.listener = listener
        listener?.onStateChanged(initialState)
    }

    override fun createView(context: Context): View = error("Not used by this unit test")

    override fun releaseView(view: View) = Unit

    override fun setActive(active: Boolean) {
        activeStates += active
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

    override fun printPage(): Boolean {
        actions += "print"
        return true
    }

    override fun setDesktopMode(enabled: Boolean) {
        desktopMode = enabled
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
}
