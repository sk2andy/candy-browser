package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserTab

internal data class TabHandoff(
    val tab: BrowserTab,
    val preview: Bitmap?,
    val favicon: Bitmap?,
    val previewTopInsetPx: Int,
) {
    val tabId: String
        get() = tab.id
}

internal object TabHandoffRules {
    fun shouldRevealLiveContent(
        handoff: TabHandoff,
        tabOverviewVisible: Boolean,
        liveFrameTabId: String?,
    ): Boolean = !tabOverviewVisible &&
        (handoff.tab.url == BLANK_URL || liveFrameTabId == handoff.tabId)
}
