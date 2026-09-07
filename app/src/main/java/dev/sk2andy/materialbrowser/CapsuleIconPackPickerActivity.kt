package dev.sk2andy.materialbrowser

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dev.sk2andy.materialbrowser.capsule.CapsuleIconPack
import dev.sk2andy.materialbrowser.capsule.CapsuleIconPackEntry
import dev.sk2andy.materialbrowser.capsule.CapsuleIconPackPickerContract
import dev.sk2andy.materialbrowser.capsule.CapsuleIconPackRepository
import dev.sk2andy.materialbrowser.data.AppDataTransferLock
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.ui.CapsuleIconPackPickerScreen
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class CapsuleIconPackPickerActivity : ComponentActivity() {
    private var packs by mutableStateOf<List<CapsuleIconPack>>(emptyList())
    private var entries by mutableStateOf<List<CapsuleIconPackEntry>>(emptyList())
    private val repository by lazy { CapsuleIconPackRepository(this) }
    private val renderSemaphore = Semaphore(4)
    private var selectedPackageName by mutableStateOf<String?>(null)
    private var loading by mutableStateOf(true)
    private var choosing by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var loadEntriesJob: Job? = null
    private var isFullImmersiveModeEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AppDataTransferLock.isActive(this)) {
            finish()
            return
        }
        enableEdgeToEdge()
        val sessionStore = BrowserSessionStore(this)
        isFullImmersiveModeEnabled = sessionStore.loadFullImmersiveModeEnabled()
        applyFullImmersiveMode(isFullImmersiveModeEnabled)
        val appearanceSettings = sessionStore.loadAppearanceSettings()
        setContent {
            val appearanceDark = appearanceSettings.usesDarkColors(isSystemInDarkTheme())
            SideEffect { applyAppearanceSystemBars(appearanceDark) }
            MaterialBrowserTheme(settings = appearanceSettings) {
                CapsuleIconPackPickerScreen(
                    packs = packs,
                    selectedPackageName = selectedPackageName,
                    entries = entries,
                    loading = loading,
                    choosing = choosing,
                    errorMessage = errorMessage,
                    onSelectPack = ::selectPack,
                    onSelectEntry = ::selectEntry,
                    loadIcon = { entry ->
                        withContext(Dispatchers.IO) {
                            renderSemaphore.withPermit { repository.render(entry) }
                        }
                    },
                    onDismiss = ::finish,
                )
            }
        }
        discoverPacks(savedInstanceState?.getString(STATE_SELECTED_PACKAGE))
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullImmersiveMode(isFullImmersiveModeEnabled)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedPackageName?.let { outState.putString(STATE_SELECTED_PACKAGE, it) }
    }

    private fun discoverPacks(preferredPackageName: String?) {
        loading = true
        lifecycleScope.launch {
            val discovered = withContext(Dispatchers.IO) { repository.discover() }
            packs = discovered
            val preferred = discovered.firstOrNull { it.packageName == preferredPackageName }
                ?: discovered.firstOrNull()
            if (preferred == null) {
                loading = false
            } else {
                selectPack(preferred)
            }
        }
    }

    private fun selectPack(pack: CapsuleIconPack) {
        if (pack.packageName == selectedPackageName && entries.isNotEmpty()) return
        loadEntriesJob?.cancel()
        selectedPackageName = pack.packageName
        entries = emptyList()
        loading = true
        errorMessage = null
        loadEntriesJob = lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { repository.loadEntries(pack) }
            if (selectedPackageName != pack.packageName) return@launch
            entries = loaded
            loading = false
            if (loaded.isEmpty()) {
                errorMessage = getString(R.string.capsule_icon_pack_read_failed)
            }
        }
    }

    private fun selectEntry(entry: CapsuleIconPackEntry) {
        if (choosing) return
        choosing = true
        errorMessage = null
        lifecycleScope.launch {
            val icon = withContext(Dispatchers.IO) {
                renderSemaphore.withPermit { repository.render(entry) }
            }
            choosing = false
            if (icon == null) {
                errorMessage = getString(R.string.capsule_icon_pack_icon_failed)
                return@launch
            }
            val result = CapsuleIconPackPickerContract.resultIntent(icon)
            if (result == null) {
                errorMessage = getString(R.string.capsule_icon_pack_icon_failed)
                return@launch
            }
            setResult(Activity.RESULT_OK, result)
            finish()
        }
    }

    private companion object {
        const val STATE_SELECTED_PACKAGE = "capsule_icon_pack.selected_package"
    }
}
