package dev.sk2andy.materialbrowser

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.lifecycleScope
import dev.sk2andy.materialbrowser.browser.ProfileWallpaper
import dev.sk2andy.materialbrowser.browser.ProfileWallpaperEditorContract
import dev.sk2andy.materialbrowser.browser.ProfileWallpaperEditorSubmission
import dev.sk2andy.materialbrowser.browser.ProfileWallpaperTarget
import dev.sk2andy.materialbrowser.data.AppDataTransferLock
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.ProfileWallpaperStore
import dev.sk2andy.materialbrowser.ui.ProfileWallpaperEditorScreen
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileWallpaperEditorActivity : ComponentActivity() {
    private lateinit var profileId: String
    private lateinit var wallpaperTarget: ProfileWallpaperTarget
    private lateinit var wallpaperStore: ProfileWallpaperStore
    private var bitmap by mutableStateOf<Bitmap?>(null)
    private var initialWallpaper by mutableStateOf(ProfileWallpaper())
    private var hasStoredWallpaper by mutableStateOf(false)
    private var loading by mutableStateOf(true)
    private var errorMessage by mutableStateOf<String?>(null)
    private var targetAspectRatio by mutableStateOf(1f)
    private var bitmapNeedsPersistence = false
    private var isFullImmersiveModeEnabled = false

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        loading = true
        errorMessage = null
        lifecycleScope.launch {
            val candidate = withContext(Dispatchers.IO) {
                wallpaperStore.decodeCandidate(contentResolver, uri)
            }
            loading = false
            if (candidate == null) {
                errorMessage = getString(R.string.profile_wallpaper_invalid_image)
            } else {
                replaceBitmap(candidate)
                bitmapNeedsPersistence = true
                initialWallpaper = ProfileWallpaper()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AppDataTransferLock.isActive(this)) {
            finish()
            return
        }
        val request = ProfileWallpaperEditorContract.requestFrom(intent)
        if (request == null) {
            finish()
            return
        }
        profileId = request.profileId
        wallpaperTarget = request.target
        initialWallpaper = request.wallpaper ?: ProfileWallpaper()
        hasStoredWallpaper = request.wallpaper != null
        wallpaperStore = ProfileWallpaperStore(applicationContext)
        updateTargetAspectRatio()
        enableEdgeToEdge()
        val sessionStore = BrowserSessionStore(this)
        isFullImmersiveModeEnabled = sessionStore.loadFullImmersiveModeEnabled()
        applyFullImmersiveMode(isFullImmersiveModeEnabled)
        val appearanceSettings = sessionStore.loadAppearanceSettings()
        setContent {
            val appearanceDark = appearanceSettings.usesDarkColors(isSystemInDarkTheme())
            val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }
            SideEffect { applyAppearanceSystemBars(appearanceDark) }
            MaterialBrowserTheme(settings = appearanceSettings) {
                ProfileWallpaperEditorScreen(
                    bitmap = imageBitmap,
                    wallpaperTarget = wallpaperTarget,
                    targetAspectRatio = targetAspectRatio,
                    initialWallpaper = initialWallpaper,
                    hasStoredWallpaper = hasStoredWallpaper,
                    loading = loading,
                    errorMessage = errorMessage,
                    onChooseImage = { imagePicker.launch(arrayOf("image/*")) },
                    onSave = ::saveWallpaper,
                    onRemove = ::removeWallpaper,
                    onDismiss = ::finish,
                )
            }
        }
        lifecycleScope.launch {
            val restored = if (request.wallpaper == null) {
                null
            } else {
                withContext(Dispatchers.IO) { wallpaperStore.load(profileId, wallpaperTarget) }
            }
            loading = false
            if (restored != null) {
                replaceBitmap(restored)
                bitmapNeedsPersistence = false
            } else if (request.wallpaper != null) {
                errorMessage = getString(R.string.profile_wallpaper_missing_image)
            } else if (savedInstanceState == null) {
                imagePicker.launch(arrayOf("image/*"))
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullImmersiveMode(isFullImmersiveModeEnabled)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateTargetAspectRatio()
    }

    override fun onDestroy() {
        bitmap = null
        super.onDestroy()
    }

    private fun saveWallpaper(wallpaper: ProfileWallpaper) {
        val candidate = bitmap ?: return
        loading = true
        errorMessage = null
        lifecycleScope.launch {
            val saved = if (bitmapNeedsPersistence) {
                withContext(Dispatchers.IO) {
                    wallpaperStore.save(profileId, wallpaperTarget, candidate)
                }
            } else {
                true
            }
            loading = false
            if (!saved) {
                errorMessage = getString(R.string.profile_wallpaper_save_failed)
                return@launch
            }
            setResult(
                Activity.RESULT_OK,
                ProfileWallpaperEditorContract.resultIntent(
                    ProfileWallpaperEditorSubmission(profileId, wallpaperTarget, wallpaper),
                ),
            )
            finish()
        }
    }

    private fun removeWallpaper() {
        loading = true
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { wallpaperStore.delete(profileId, wallpaperTarget) }
            setResult(
                Activity.RESULT_OK,
                ProfileWallpaperEditorContract.resultIntent(
                    ProfileWallpaperEditorSubmission(
                        profileId,
                        wallpaperTarget,
                        wallpaper = null,
                    ),
                ),
            )
            finish()
        }
    }

    private fun replaceBitmap(replacement: Bitmap) {
        bitmap = replacement
    }

    private fun updateTargetAspectRatio() {
        val bounds = windowManager.currentWindowMetrics.bounds
        targetAspectRatio = bounds.width().toFloat() / bounds.height().coerceAtLeast(1)
    }
}
