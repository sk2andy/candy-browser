package dev.sk2andy.materialbrowser

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import dev.sk2andy.materialbrowser.capsule.SiteCapsuleEditorContract
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.ui.SiteCapsuleEditorScreen
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme

class SiteCapsuleEditorActivity : ComponentActivity() {
    private var isFullImmersiveModeEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        isFullImmersiveModeEnabled =
            BrowserSessionStore(this).loadFullImmersiveModeEnabled()
        applyFullImmersiveMode(isFullImmersiveModeEnabled)
        val request = SiteCapsuleEditorContract.requestFrom(intent)
        if (request == null) {
            finish()
            return
        }
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
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullImmersiveMode(isFullImmersiveModeEnabled)
    }
}
