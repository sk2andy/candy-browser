package dev.sk2andy.materialbrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.integration.devicesearch.DeviceSearchIndexer
import dev.sk2andy.materialbrowser.browser.integration.devicesearch.DeviceSearchItem
import dev.sk2andy.materialbrowser.browser.integration.devicesearch.DeviceSearchSettings
import dev.sk2andy.materialbrowser.browser.integration.devicesearch.DeviceSearchStore
import dev.sk2andy.materialbrowser.browser.integration.devicesearch.loadDeviceSearchSources
import dev.sk2andy.materialbrowser.data.AppDataTransferLock
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.ui.DeviceSearchSettingsPage
import dev.sk2andy.materialbrowser.ui.theme.CandyTheme
import dev.sk2andy.materialbrowser.ui.theme.setCandyContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceSearchSettingsActivity : ComponentActivity() {
    private var settings by mutableStateOf(DeviceSearchSettings())
    private var items by mutableStateOf<List<DeviceSearchItem>>(emptyList())
    private var profiles by mutableStateOf<List<BrowserProfile>>(emptyList())
    private var busy by mutableStateOf(true)
    private var failed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AppDataTransferLock.isActive(this)) {
            finish()
            return
        }
        enableEdgeToEdge()
        val appearance = BrowserSessionStore(this).loadAppearanceSettings()
        setCandyContent(animationsEnabled = appearance.animationsEnabled) {
            val dark = appearance.usesDarkColors(isSystemInDarkTheme())
            SideEffect { applyAppearanceSystemBars(dark) }
            CandyTheme(settings = appearance) {
                DeviceSearchSettingsPage(
                    settings = settings,
                    items = items,
                    profiles = profiles,
                    busy = busy,
                    failed = failed,
                    onSettingsChanged = ::save,
                    onBack = ::finish,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            val sources = withContext(Dispatchers.IO) { loadDeviceSearchSources(applicationContext) }
            settings = sources.settings
            items = sources.items
            profiles = sources.profiles
            busy = false
        }
    }

    private fun save(next: DeviceSearchSettings) {
        if (busy) return
        busy = true
        failed = false
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) { DeviceSearchStore(applicationContext).save(next) }
            if (!saved) {
                failed = true
                busy = false
                return@launch
            }
            settings = next
            DeviceSearchIndexer.refresh(applicationContext) { success ->
                if (!isFinishing && !isDestroyed) {
                    failed = !success
                    reload()
                }
            }
        }
    }
}
