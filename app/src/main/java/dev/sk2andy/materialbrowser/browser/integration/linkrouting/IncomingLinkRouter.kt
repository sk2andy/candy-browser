package dev.sk2andy.materialbrowser.browser.integration.linkrouting

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import dev.sk2andy.materialbrowser.data.LinkRoutingStore
import java.util.concurrent.Executors

internal class IncomingLinkRouter(context: Context, private val controller: BrowserController) {
    private val appContext = context.applicationContext
    private val store = LinkRoutingStore(appContext)
    private val writer = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var closed = false

    var rules by mutableStateOf(store.load())
        private set
    var readerRequest by mutableStateOf<LinkRoutingReaderRequest?>(null)
        private set

    fun updateRules(next: List<LinkRoutingRule>) {
        if (closed) return
        val bounded = LinkRoutingRules.bounded(next)
        rules = bounded
        writer.execute {
            val saved = runCatching { store.save(bounded) }.isSuccess
            if (!saved) handler.post { if (!closed) showUnavailable(R.string.link_routing_save_failed) }
        }
    }

    fun clearReaderRequest() {
        readerRequest = null
    }

    fun open(url: String): Boolean {
        clearReaderRequest()
        val resolution = LinkRoutingRules.resolve(
            url = url,
            rules = rules,
            profiles = controller.profiles,
            capsules = controller.siteCapsules,
            activeProfileId = controller.activeProfileId,
            profilesEnabled = controller.profilesEnabled,
            isPrivate = controller.selectedTab.isIncognito,
        )
        if (resolution == LinkRoutingResolution.NoMatch) return false
        if (resolution !is LinkRoutingResolution.Open) {
            showUnavailable()
            return true
        }
        controller.dismissExternalLinkPreview()
        if (resolution.target.kind == LinkRoutingTargetKind.Capsule) {
            if (!controller.openRoutedSiteCapsule(resolution.target.id!!, resolution.url)) showUnavailable()
            return true
        }
        if (resolution.profileId != controller.activeProfileId && !controller.selectProfile(resolution.profileId)) {
            showUnavailable()
            return true
        }
        controller.leaveSiteCapsule()
        val previousTabId = controller.selectedTabId
        val tabId = controller.createTab(initialUrl = resolution.url, isIncognito = false)
        if (tabId == previousTabId) return true
        if (resolution.target.kind == LinkRoutingTargetKind.Reader) {
            readerRequest = LinkRoutingReaderRequest(tabId, resolution.url)
        }
        return true
    }

    fun openInReader(url: String): Boolean {
        val safeUrl = BrowserUriPolicy.normalizeHttpUrl(url) ?: return false
        clearReaderRequest()
        controller.dismissExternalLinkPreview()
        controller.leaveSiteCapsule()
        val previousTabId = controller.selectedTabId
        val tabId = controller.createTab(initialUrl = safeUrl, isIncognito = false)
        if (tabId == previousTabId) return false
        readerRequest = LinkRoutingReaderRequest(tabId, safeUrl)
        return true
    }

    fun close() {
        closed = true
        clearReaderRequest()
        writer.shutdown()
    }

    private fun showUnavailable(message: Int = R.string.link_routing_target_unavailable) {
        Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
    }
}
