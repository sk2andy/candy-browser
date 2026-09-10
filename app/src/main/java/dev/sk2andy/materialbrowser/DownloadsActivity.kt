package dev.sk2andy.materialbrowser

import android.content.Intent
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.sk2andy.materialbrowser.data.AppDataTransferLock
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.DownloadEntry
import dev.sk2andy.materialbrowser.data.DownloadRepository
import dev.sk2andy.materialbrowser.ui.DownloadsScreen
import dev.sk2andy.materialbrowser.ui.theme.CandyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadsActivity : ComponentActivity() {
    private val repository by lazy { DownloadRepository(this) }
    private var downloads by mutableStateOf<List<DownloadEntry>>(emptyList())
    private var isClearing by mutableStateOf(false)
    private var isFullImmersiveModeEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AppDataTransferLock.isActive(this)) {
            finish()
            return
        }
        enableEdgeToEdge()
        val store = BrowserSessionStore(this)
        isFullImmersiveModeEnabled = store.loadFullImmersiveModeEnabled()
        applyFullImmersiveMode(isFullImmersiveModeEnabled)
        val appearanceSettings = store.loadAppearanceSettings()

        setContent {
            val appearanceDark = appearanceSettings.usesDarkColors(isSystemInDarkTheme())
            SideEffect { applyAppearanceSystemBars(appearanceDark) }
            CandyTheme(settings = appearanceSettings) {
                DownloadsScreen(
                    downloads = downloads,
                    isClearing = isClearing,
                    onClearFinished = ::clearFinished,
                    onOpenDownload = ::openDownload,
                    onBack = ::finish,
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    reload()
                    delay(if (downloads.any { entry -> entry.status.isActive }) 750L else 3_000L)
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullImmersiveMode(isFullImmersiveModeEnabled)
    }

    private suspend fun reload() {
        downloads = withContext(Dispatchers.IO) { repository.snapshot() }
    }

    private fun clearFinished(entries: List<DownloadEntry>) {
        if (isClearing) return
        isClearing = true
        lifecycleScope.launch {
            try {
                val cleared = withContext(Dispatchers.IO) { repository.clear(entries) }
                reload()
                if (!cleared) {
                    Toast.makeText(
                        this@DownloadsActivity,
                        R.string.downloads_clear_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } finally {
                isClearing = false
            }
        }
    }

    private fun openDownload(entry: DownloadEntry) {
        val uri = runCatching { repository.contentUri(entry) }.getOrNull() ?: run {
            Toast.makeText(this, R.string.downloads_open_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val mime = entry.mime.takeIf(String::isNotBlank)
            ?: contentResolver.getType(uri)
            ?: "*/*"
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }.onFailure {
            Toast.makeText(this, R.string.downloads_open_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
