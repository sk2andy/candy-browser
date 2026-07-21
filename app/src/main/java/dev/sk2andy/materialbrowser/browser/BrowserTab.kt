package dev.sk2andy.materialbrowser.browser

import android.graphics.Bitmap

data class BrowserTab(
    val id: String,
    val lastAccessedAt: Long,
    val profileId: String = DEFAULT_PROFILE_ID,
    val isIncognito: Boolean = false,
    val isPinned: Boolean = false,
    val title: String = "",
    val url: String = BLANK_URL,
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val blockedCount: Int = 0,
    val error: String? = null,
)

data class TabPreview(
    val tabId: String,
    val bitmap: Bitmap,
)

internal val BrowserTab.isFreshBlankTab: Boolean
    get() = url == BLANK_URL &&
        title.isBlank() &&
        progress == 0 &&
        !isLoading &&
        !canGoBack &&
        !canGoForward &&
        blockedCount == 0 &&
        error == null

const val BLANK_URL = "about:blank"
const val MAX_TABS = 12
