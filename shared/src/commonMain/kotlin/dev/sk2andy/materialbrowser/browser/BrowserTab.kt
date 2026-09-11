package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineFailureKind

data class BrowserTab(
    val id: String,
    val lastAccessedAt: Long,
    val openerTabId: String? = null,
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
    val syncCandyId: String? = null,
    val httpStatusCode: Int? = null,
    val failureKind: BrowserEngineFailureKind? = null,
)

val BrowserTab.isSynced: Boolean
    get() = syncCandyId != null

val BrowserTab.isFreshBlankTab: Boolean
    get() = url == BLANK_URL &&
        title.isBlank() &&
        progress == 0 &&
        !isLoading &&
        !canGoBack &&
        !canGoForward &&
        blockedCount == 0 &&
        error == null &&
        failureKind == null &&
        httpStatusCode == null

const val BLANK_URL = "about:blank"
const val MAX_TABS = 50
