package dev.sk2andy.materialbrowser.browser.actions

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.sk2andy.materialbrowser.data.BrowserDownloadRequest

sealed interface WebContentAction {
    data class OpenLinkInBackground(val url: String) : WebContentAction

    data class DownloadLink(val request: BrowserDownloadRequest) : WebContentAction

    data class DownloadImage(val request: BrowserDownloadRequest) : WebContentAction
}

@Stable
class WebContentActionState {
    internal var revision = 0L
        private set

    var target by mutableStateOf<WebContentTarget?>(null)
        private set
    var sourceTabId by mutableStateOf<String?>(null)
        private set
    var lastDownload by mutableStateOf<DownloadActionResult?>(null)
        private set
    var addressBarPulseNonce by mutableIntStateOf(0)
        private set
    var linkPeekProgress by mutableFloatStateOf(0f)
        private set
    var isLinkPeekArmed by mutableStateOf(false)
        private set
    var isLinkPeekCommitting by mutableStateOf(false)
        private set
    var linkPeekNewTabPulseNonce by mutableIntStateOf(0)
        private set

    val isVisible: Boolean get() = target != null
    val isLinkPeekVisible: Boolean get() = target?.linkUrl != null

    fun show(target: WebContentTarget, sourceTabId: String? = null) {
        revision++
        val sameLink = this.target?.linkUrl != null &&
            this.target?.linkUrl == target.linkUrl && this.sourceTabId == sourceTabId
        this.target = target
        this.sourceTabId = sourceTabId
        if (!sameLink) {
            isLinkPeekCommitting = false
            updateLinkPeek(progress = 0f, armed = false)
        }
    }

    fun dismiss() {
        revision++
        target = null
        sourceTabId = null
        isLinkPeekCommitting = false
        updateLinkPeek(progress = 0f, armed = false)
    }

    fun updateLinkPeek(progress: Float, armed: Boolean) {
        if (isLinkPeekCommitting) return
        linkPeekProgress = progress.coerceIn(0f, 1f)
        isLinkPeekArmed = armed
    }

    fun startLinkPeekCommit() {
        if (target?.linkUrl == null || isLinkPeekCommitting) return
        isLinkPeekCommitting = true
        linkPeekProgress = 1f
        isLinkPeekArmed = true
    }

    fun requestLinkPeekNewTabPulse() {
        linkPeekNewTabPulseNonce++
    }

    fun reportDownload(result: DownloadActionResult) {
        lastDownload = result
    }

    fun consumeDownloadResult() {
        lastDownload = null
    }

    fun requestAddressBarPulse() {
        addressBarPulseNonce++
    }
}

sealed interface DownloadActionResult {
    data class Enqueued(val id: Long, val fileName: String) : DownloadActionResult

    data class HandedOff(val fileName: String, val appName: String) : DownloadActionResult

    data class Failed(val message: String) : DownloadActionResult
}
