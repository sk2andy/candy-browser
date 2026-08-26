package dev.sk2andy.materialbrowser

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.sk2andy.materialbrowser.browser.integration.HistoryActivityContract
import dev.sk2andy.materialbrowser.data.AppDataTransferLock
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.BrowsingHistoryRepository
import dev.sk2andy.materialbrowser.data.CandyTrailRepository
import dev.sk2andy.materialbrowser.data.HistoryClearRequest
import dev.sk2andy.materialbrowser.data.HistoryEntry
import dev.sk2andy.materialbrowser.data.SnoozedTabStore
import dev.sk2andy.materialbrowser.ui.HistoryScreen
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme

class HistoryActivity : ComponentActivity() {
    private val historyRepository by lazy { BrowsingHistoryRepository.get(this) }
    private val candyTrailRepository by lazy { CandyTrailRepository.get(this) }
    private var history by mutableStateOf<List<HistoryEntry>>(emptyList())
    private val clearRequests = mutableListOf<HistoryClearRequest>()
    private var isFullImmersiveModeEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        BrowsingHistoryLifecycle.install(application)
        super.onCreate(savedInstanceState)
        if (AppDataTransferLock.isActive(this)) {
            finish()
            return
        }
        enableEdgeToEdge()
        val store = BrowserSessionStore(this)
        isFullImmersiveModeEnabled = store.loadFullImmersiveModeEnabled()
        applyFullImmersiveMode(isFullImmersiveModeEnabled)
        val (storedProfiles, storedActiveProfileId) = store.loadProfiles()
        val profiles = if (store.loadProfilesEnabled()) storedProfiles else storedProfiles.take(1)
        val activeProfileId = storedActiveProfileId.takeIf { candidate ->
            profiles.any { profile -> profile.id == candidate }
        } ?: profiles.first().id
        clearRequests += HistoryActivityContract.clearRequestsFrom(savedInstanceState)
        history = historyRepository.snapshot()
        var recordingMode by mutableStateOf(historyRepository.recordingMode())
        val appearanceSettings = store.loadAppearanceSettings()

        setContent {
            val appearanceDark = appearanceSettings.usesDarkColors(isSystemInDarkTheme())
            SideEffect { applyAppearanceSystemBars(appearanceDark) }
            MaterialBrowserTheme(settings = appearanceSettings) {
                HistoryScreen(
                    profiles = profiles,
                    activeProfileId = activeProfileId,
                    history = history,
                    recordingMode = recordingMode,
                    onRecordingModeChange = { mode ->
                        if (historyRepository.setRecordingMode(mode)) recordingMode = mode
                    },
                    onDeleteEntries = { entries ->
                        history = historyRepository.remove(entries)
                    },
                    onClearHistory = { request ->
                        val previous = history
                        val trailTabIds = persistentTrailTabs().asSequence()
                            .filterNot { tab -> tab.isIncognito }
                            .filter { tab -> tab.profileId in request.profileIds }
                            .mapTo(linkedSetOf()) { tab -> tab.id }
                        val mutation = historyRepository.clearRange(request, trailTabIds)
                        history = mutation.history
                        if (!mutation.committed) {
                            Toast.makeText(
                                this,
                                R.string.history_clear_failed,
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else if (history != previous) {
                            clearRequests += request
                            candyTrailRepository.processPendingRedactions()
                            setResult(
                                Activity.RESULT_OK,
                                HistoryActivityContract.resultIntent(
                                    clearRequests = clearRequests,
                                ),
                            )
                        }
                    },
                    onOpenEntry = { entry ->
                        setResult(
                            Activity.RESULT_OK,
                            HistoryActivityContract.resultIntent(entry, clearRequests),
                        )
                        finish()
                    },
                    onBack = ::finishWithResult,
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullImmersiveMode(isFullImmersiveModeEnabled)
    }

    override fun onResume() {
        super.onResume()
        history = historyRepository.snapshot()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        HistoryActivityContract.saveClearRequests(outState, clearRequests)
        super.onSaveInstanceState(outState)
    }

    private fun persistentTrailTabs() = buildList {
        addAll(BrowserSessionStore(this@HistoryActivity).loadTabs().first)
        addAll(SnoozedTabStore(this@HistoryActivity).load().map { snoozed -> snoozed.tab })
    }.distinctBy { tab -> tab.id }

    private fun finishWithResult() {
        if (clearRequests.isNotEmpty()) {
            setResult(
                Activity.RESULT_OK,
                HistoryActivityContract.resultIntent(clearRequests = clearRequests),
            )
        }
        finish()
    }
}
