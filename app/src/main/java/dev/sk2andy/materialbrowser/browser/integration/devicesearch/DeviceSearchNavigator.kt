package dev.sk2andy.materialbrowser.browser.integration.devicesearch

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.data.AppDataTransferLock
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import java.util.concurrent.Executors

internal class DeviceSearchNavigator(context: Context, private val controller: BrowserController) {
    private val appContext = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var requestGeneration = 0L
    private var closed = false

    fun cancelPending() {
        requestGeneration++
    }

    fun open(itemId: String?, onOpened: () -> Unit) {
        val request = ++requestGeneration
        if (!DeviceSearchRules.validId(itemId)) return unavailable()
        worker.execute {
            val sourceRevision = DeviceSearchIndexer.sourceRevision()
            val sources = runCatching { loadDeviceSearchSources(appContext) }.getOrNull()
            val item = sources?.let { source ->
                DeviceSearchRules.published(source.settings, source.items).firstOrNull { it.id == itemId }
            }
            val snapshot = item?.snapshotId?.let { id -> sources?.snapshots?.firstOrNull { it.id == id } }
            handler.post {
                if (closed || request != requestGeneration) return@post
                val currentSettings = DeviceSearchStore(appContext).load()
                val browserStore = BrowserSessionStore(appContext)
                val currentProfiles = DeviceSearchRules.eligibleProfiles(
                    browserStore.loadProfiles().first,
                    browserStore.loadProfilesEnabled(),
                )
                if (sourceRevision != DeviceSearchIndexer.sourceRevision() ||
                    AppDataTransferLock.isActive(appContext) || item == null || !currentSettings.enabled || item.id !in currentSettings.selectedIds ||
                    currentProfiles.none { it.id == item.profileId } ||
                    controller.profiles.none { it.id == item.profileId && it.protection == null } ||
                    item.kind == DeviceSearchKind.Article && snapshot == null
                ) {
                    unavailable()
                    DeviceSearchIndexer.refresh(appContext)
                    return@post
                }
                if (item.profileId != controller.activeProfileId && !controller.selectProfile(item.profileId)) {
                    unavailable()
                    return@post
                }
                controller.dismissExternalLinkPreview()
                controller.leaveSiteCapsule()
                val previousTabId = controller.selectedTabId
                val tabId = controller.createTab(
                    initialUrl = if (snapshot != null) BLANK_URL else item.sourceUrl,
                    isIncognito = false,
                )
                if (tabId == previousTabId) {
                    unavailable()
                    return@post
                }
                if (snapshot != null) controller.showDeviceSearchReaderSnapshot(snapshot)
                onOpened()
            }
        }
    }

    fun close() {
        closed = true
        cancelPending()
        worker.shutdown()
    }

    private fun unavailable() {
        Toast.makeText(appContext, R.string.device_search_item_unavailable, Toast.LENGTH_LONG).show()
    }
}
