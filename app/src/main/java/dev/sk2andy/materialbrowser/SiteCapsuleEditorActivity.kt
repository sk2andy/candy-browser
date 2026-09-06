package dev.sk2andy.materialbrowser

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.sk2andy.materialbrowser.capsule.CapsuleCustomIconEditorContract
import dev.sk2andy.materialbrowser.capsule.SiteCapsuleEditorContract
import dev.sk2andy.materialbrowser.data.AppDataTransferLock
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.ui.SiteCapsuleEditorScreen
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme

class SiteCapsuleEditorActivity : ComponentActivity() {
    private var customIcon by mutableStateOf<Bitmap?>(null)
    private var customIconRevision by mutableIntStateOf(0)
    private var isFullImmersiveModeEnabled = false

    private val customIconEditor = registerForActivityResult(
        CapsuleCustomIconEditorContract(),
    ) { icon ->
        if (icon != null) {
            customIcon = icon
            customIconRevision++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AppDataTransferLock.isActive(this)) {
            finish()
            return
        }
        enableEdgeToEdge()
        isFullImmersiveModeEnabled =
            BrowserSessionStore(this).loadFullImmersiveModeEnabled()
        applyFullImmersiveMode(isFullImmersiveModeEnabled)
        val request = SiteCapsuleEditorContract.requestFrom(intent)
        if (request == null) {
            finish()
            return
        }
        customIconRevision = savedInstanceState?.getInt(STATE_CUSTOM_ICON_REVISION) ?: 0
        val restoredCustomIcon = savedInstanceState
            ?.getByteArray(STATE_CUSTOM_ICON)
            ?.let(CapsuleCustomIconEditorContract::decodeIcon)
        customIcon = restoredCustomIcon ?: request.customIcon
        if (customIcon == null) customIconRevision = 0
        val appearanceSettings = BrowserSessionStore(this).loadAppearanceSettings()
        setContent {
            val appearanceDark = appearanceSettings.usesDarkColors(isSystemInDarkTheme())
            SideEffect { applyAppearanceSystemBars(appearanceDark) }
            MaterialBrowserTheme(settings = appearanceSettings) {
                SiteCapsuleEditorScreen(
                    request = request,
                    onSubmit = { submission ->
                        setResult(
                            Activity.RESULT_OK,
                            SiteCapsuleEditorContract.resultIntent(submission),
                        )
                        finish()
                    },
                    onDismiss = ::finish,
                    customIcon = customIcon,
                    customIconRevision = customIconRevision,
                    onEditCustomIcon = { customIconEditor.launch(customIcon) },
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullImmersiveMode(isFullImmersiveModeEnabled)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_CUSTOM_ICON_REVISION, customIconRevision)
        val icon = customIcon
        if (customIconRevision > 0 && icon != null) {
            CapsuleCustomIconEditorContract.encodeIcon(icon)?.let { bytes ->
                outState.putByteArray(STATE_CUSTOM_ICON, bytes)
            }
        }
    }

    private companion object {
        const val STATE_CUSTOM_ICON = "site_capsule_editor.custom_icon"
        const val STATE_CUSTOM_ICON_REVISION = "site_capsule_editor.custom_icon_revision"
    }
}
