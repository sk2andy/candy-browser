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
import androidx.lifecycle.lifecycleScope
import dev.sk2andy.materialbrowser.browser.integration.HistoryActivityContract
import dev.sk2andy.materialbrowser.data.AppDataTransferLock
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.BrowsingHistoryRepository
import dev.sk2andy.materialbrowser.data.CandyTrailRepository
import dev.sk2andy.materialbrowser.data.HistoryClearRequest
import dev.sk2andy.materialbrowser.data.HistoryEntry
import dev.sk2andy.materialbrowser.data.RecallRepository
import dev.sk2andy.materialbrowser.data.SnoozedTabStore
import dev.sk2andy.materialbrowser.ui.HistoryScreen
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import dev.sk2andy.materialbrowser.recall.RecallMatch
import dev.sk2andy.materialbrowser.recall.RecallRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : ComponentActivity() {
    private val historyRepository by lazy { BrowsingHistoryRepository.get(this) }
    private val candyTrailRepository by lazy { CandyTrailRepository.get(this) }
    private val recallRepository by lazy { RecallRepository.get(this) }
    private var history by mutableStateOf<List<HistoryEntry>>(emptyList())
    private var recallMatches by mutableStateOf<List<RecallMatch>>(emptyList())
    private var recallRequestId = 0
    private val clearRequests = mutableListOf<HistoryClearRequest>()
    private var isMutationInProgress = false
    private var hasHistoryMutations = false
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
        val recallEnabled = store.loadRecallEnabled()

        setContent {
            val appearanceDark = appearanceSettings.usesDarkColors(isSystemInDarkTheme())
            SideEffect { applyAppearanceSystemBars(appearanceDark) }
            MaterialBrowserTheme(settings = appearanceSettings) {
                HistoryScreen(
                    profiles = profiles,
                    activeProfileId = activeProfileId,
                    history = history,
                    recallMatches = recallMatches,
                    recordingMode = recordingMode,
                    onRecallCriteriaChanged = { query, profileIds ->
                        val requestId = ++recallRequestId
                        val recallQuery = RecallRules.historyQuery(query)
                        if (!recallEnabled || recallQuery == null) {
                            recallMatches = emptyList()
                        } else {
                            recallMatches = emptyList()
                            recallRepository.search(
                                profileIds = profileIds,
                                query = recallQuery,
                                limit = RecallRules.MAX_HISTORY_RESULTS,
                            ) { matches ->
                                if (requestId == recallRequestId && !isFinishing) {
                                    recallMatches = matches
                                }
                            }
                        }
                    },
                    onRecordingModeChange = { mode ->
                        if (historyRepository.setRecordingMode(mode)) recordingMode = mode
                    },
                    onDeleteEntries = deleteEntries@{ entries ->
                        if (isMutationInProgress) return@deleteEntries
                        isMutationInProgress = true
                        val previous = history
                        lifecycleScope.launch {
                            try {
                                val mutation = withContext(Dispatchers.IO) {
                                    historyRepository.remove(entries)
                                }
                                history = mutation.history
                                if (!mutation.committed) {
                                    Toast.makeText(
                                        this@HistoryActivity,
                                        R.string.history_clear_failed,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    hasHistoryMutations = hasHistoryMutations || history != previous
                                    val deletedKeys = entries.mapNotNullTo(hashSetOf()) { entry ->
                                        RecallRules.canonicalUrl(entry.url)?.let { url ->
                                            "${entry.profileId}\u0000$url"
                                        }
                                    }
                                    recallMatches = recallMatches.filterNot { match ->
                                        "${match.profileId}\u0000${match.url}" in deletedKeys
                                    }
                                }
                            } finally {
                                isMutationInProgress = false
                            }
                        }
                    },
                    onClearHistory = clearHistory@{ request ->
                        if (isMutationInProgress) return@clearHistory
                        isMutationInProgress = true
                        val previous = history
                        val trailTabIds = persistentTrailTabs().asSequence()
                            .filterNot { tab -> tab.isIncognito }
                            .filter { tab -> tab.profileId in request.profileIds }
                            .mapTo(linkedSetOf()) { tab -> tab.id }
                        lifecycleScope.launch {
                            try {
                                val mutation = withContext(Dispatchers.IO) {
                                    historyRepository.clearRange(request, trailTabIds)
                                }
                                history = mutation.history
                                if (!mutation.committed) {
                                    Toast.makeText(
                                        this@HistoryActivity,
                                        R.string.history_clear_failed,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else if (history != previous) {
                                    hasHistoryMutations = true
                                    clearRequests += request
                                    candyTrailRepository.processPendingRedactions()
                                    setResult(
                                        Activity.RESULT_OK,
                                        HistoryActivityContract.resultIntent(
                                            clearRequests = clearRequests,
                                        ),
                                    )
                                }
                                if (mutation.committed) {
                                    recallMatches = recallMatches.filterNot { match ->
                                        match.profileId in request.profileIds &&
                                            match.visitedAt >= request.sinceInclusiveMillis &&
                                            match.visitedAt < request.untilExclusiveMillis
                                    }
                                }
                            } finally {
                                isMutationInProgress = false
                            }
                        }
                    },
                    onOpenEntry = { entry ->
                        if (!isMutationInProgress) {
                            setResult(
                                Activity.RESULT_OK,
                                HistoryActivityContract.resultIntent(entry, clearRequests),
                            )
                            finish()
                        }
                    },
                    onBack = {
                        if (!isMutationInProgress) finishWithResult()
                    },
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
        if (hasHistoryMutations || clearRequests.isNotEmpty()) {
            setResult(
                Activity.RESULT_OK,
                HistoryActivityContract.resultIntent(clearRequests = clearRequests),
            )
        }
        finish()
    }
}
