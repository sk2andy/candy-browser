package dev.sk2andy.materialbrowser

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.lifecycleScope
import dev.sk2andy.materialbrowser.capsule.CapsuleCustomIconEditorContract
import dev.sk2andy.materialbrowser.capsule.CapsuleCustomIconProcessor
import dev.sk2andy.materialbrowser.capsule.CapsuleIconCrop
import dev.sk2andy.materialbrowser.capsule.CapsuleIconPackPickerContract
import dev.sk2andy.materialbrowser.data.AppDataTransferLock
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.ui.CapsuleCustomIconEditorScreen
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CapsuleCustomIconEditorActivity : ComponentActivity() {
    private var bitmap by mutableStateOf<Bitmap?>(null)
    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var candidateUri: String? = null
    private var persistBitmapInSavedState = false
    private var imageRevision = 0
    private var isFullImmersiveModeEnabled = false

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        loadCandidate(uri, newSelection = true)
    }

    private val iconPackPicker = registerForActivityResult(
        CapsuleIconPackPickerContract(),
    ) { icon ->
        if (icon == null) return@registerForActivityResult
        candidateUri = null
        imageRevision++
        errorMessage = null
        replaceBitmap(icon, persistInSavedState = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AppDataTransferLock.isActive(this)) {
            finish()
            return
        }
        val restoredBitmap = savedInstanceState
            ?.getByteArray(STATE_PACK_ICON)
            ?.let(CapsuleCustomIconEditorContract::decodeIcon)
        bitmap = restoredBitmap ?: CapsuleCustomIconEditorContract.currentIconFrom(intent)
        persistBitmapInSavedState = restoredBitmap != null
        candidateUri = savedInstanceState?.getString(STATE_CANDIDATE_URI)
        imageRevision = savedInstanceState?.getInt(STATE_IMAGE_REVISION) ?: 0
        loading = candidateUri != null
        enableEdgeToEdge()
        val sessionStore = BrowserSessionStore(this)
        isFullImmersiveModeEnabled = sessionStore.loadFullImmersiveModeEnabled()
        applyFullImmersiveMode(isFullImmersiveModeEnabled)
        val appearanceSettings = sessionStore.loadAppearanceSettings()
        setContent {
            val appearanceDark = appearanceSettings.usesDarkColors(isSystemInDarkTheme())
            SideEffect { applyAppearanceSystemBars(appearanceDark) }
            MaterialBrowserTheme(settings = appearanceSettings) {
                CapsuleCustomIconEditorScreen(
                    bitmap = bitmap?.asImageBitmap(),
                    imageRevision = imageRevision,
                    loading = loading,
                    errorMessage = errorMessage,
                    onChooseImage = { imagePicker.launch(arrayOf("image/*")) },
                    onChooseIconPack = { iconPackPicker.launch(Unit) },
                    onSave = ::saveIcon,
                    onDismiss = ::finish,
                )
            }
        }
        val restoredCandidateUri = candidateUri
        if (restoredCandidateUri != null) {
            loadCandidate(Uri.parse(restoredCandidateUri), newSelection = false)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullImmersiveMode(isFullImmersiveModeEnabled)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        candidateUri?.let { outState.putString(STATE_CANDIDATE_URI, it) }
        outState.putInt(STATE_IMAGE_REVISION, imageRevision)
        if (persistBitmapInSavedState) {
            bitmap?.let(CapsuleCustomIconEditorContract::encodeIcon)?.let { bytes ->
                outState.putByteArray(STATE_PACK_ICON, bytes)
            }
        }
    }

    private fun saveIcon(crop: CapsuleIconCrop) {
        val source = bitmap ?: return
        val icon = CapsuleCustomIconProcessor.crop(source, crop)
        if (icon == null) {
            errorMessage = getString(R.string.capsule_custom_icon_save_failed)
            return
        }
        setResult(
            Activity.RESULT_OK,
            CapsuleCustomIconEditorContract.resultIntent(icon),
        )
        icon.recycle()
        finish()
    }

    private fun replaceBitmap(
        replacement: Bitmap,
        persistInSavedState: Boolean,
    ) {
        bitmap = replacement
        persistBitmapInSavedState = persistInSavedState
    }

    private fun loadCandidate(uri: Uri, newSelection: Boolean) {
        if (newSelection) {
            candidateUri = uri.toString()
            imageRevision++
        }
        loading = true
        errorMessage = null
        lifecycleScope.launch {
            val candidate = withContext(Dispatchers.IO) {
                CapsuleCustomIconProcessor.decodeCandidate(contentResolver, uri)
            }
            loading = false
            if (candidate == null) {
                candidateUri = null
                errorMessage = getString(R.string.capsule_custom_icon_invalid_image)
                return@launch
            }
            candidateUri = uri.toString()
            replaceBitmap(candidate, persistInSavedState = false)
        }
    }

    private companion object {
        const val STATE_CANDIDATE_URI = "capsule_custom_icon.candidate_uri"
        const val STATE_IMAGE_REVISION = "capsule_custom_icon.image_revision"
        const val STATE_PACK_ICON = "capsule_custom_icon.pack_icon"
    }
}
