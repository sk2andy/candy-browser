package dev.sk2andy.materialbrowser.browser.gecko

import dev.sk2andy.materialbrowser.browser.CandyTrailHistorySnapshot

internal data class GeckoCandyTrailHistoryEvent(
    val tabId: String,
    val snapshot: CandyTrailHistorySnapshot,
    val title: String,
)

internal fun interface GeckoCandyTrailHistoryEventSink {
    fun onHistoryEvent(
        session: AndroidBrowserEngineSessionPort,
        event: GeckoCandyTrailHistoryEvent,
    )
}

internal class GeckoCandyTrailHistoryTracker(
    private val tabId: String,
    private val eventSink: (GeckoCandyTrailHistoryEvent) -> Unit,
) {
    private var previous: GeckoBrowserHistoryState? = null

    fun onHistoryStateChanged(state: GeckoBrowserHistoryState) {
        if (state.currentIndex !in state.urls.indices) return
        val stableState = state.copy(urls = state.urls.toList())
        val earlier = previous
        previous = stableState
        eventSink(
            GeckoCandyTrailHistoryEvent(
                tabId = tabId,
                snapshot = CandyTrailHistorySnapshot(
                    urls = stableState.urls,
                    currentIndex = stableState.currentIndex,
                    isReload = earlier?.let { old ->
                        old.urls == stableState.urls &&
                            old.currentIndex == stableState.currentIndex
                    } == true,
                ),
                title = stableState.currentTitle.orEmpty(),
            ),
        )
    }
}
