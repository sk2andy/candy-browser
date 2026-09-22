package dev.sk2andy.materialbrowser

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.sk2andy.materialbrowser.browser.ProfileProtectionSession
import dev.sk2andy.materialbrowser.capsule.CapsuleCustomIconEditorContract
import dev.sk2andy.materialbrowser.capsule.CapsuleCustomIconEditorRequest
import dev.sk2andy.materialbrowser.capsule.SiteCapsuleEditorContract
import dev.sk2andy.materialbrowser.data.AppDataTransferLock
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.ui.SiteCapsuleEditorScreen
import dev.sk2andy.materialbrowser.ui.theme.CandyTheme
import dev.sk2andy.materialbrowser.ui.theme.setCandyContent

class SiteCapsuleEditorActivity : ComponentActivity() {
    private var customIcon by mutableStateOf<Bitmap?>(null)
    private var customIconRevision by mutableIntStateOf(0)
    private var isFullImmersiveModeEnabled = false
    private var protectedProfileIds: Set<String> = emptySet()

    private val customIconEditor = registerForActivityResult(
        CapsuleCustomIconEditorContract(),
    ) { icon ->
        if (!areProfilesAccessible()) {
            finish()
            return@registerForActivityResult
        }
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
        val storedProfiles = BrowserSessionStore(this).loadProfiles().first
        protectedProfileIds = request.profiles.asSequence()
            .map { profile -> profile.id }
            .mapNotNull { profileId ->
                storedProfiles.firstOrNull { profile ->
                    profile.id == profileId && profile.protection != null
                }?.id
            }
            .toSet()
        if (!areProfilesAccessible()) {
            finish()
            return
        }
        setRecentsScreenshotEnabled(protectedProfileIds.isEmpty())
        customIconRevision = savedInstanceState?.getInt(STATE_CUSTOM_ICON_REVISION) ?: 0
        val restoredCustomIcon = savedInstanceState
            ?.getByteArray(STATE_CUSTOM_ICON)
            ?.let(CapsuleCustomIconEditorContract::decodeIcon)
        customIcon = restoredCustomIcon ?: request.customIcon
        if (customIcon == null) customIconRevision = 0
        val appearanceSettings = BrowserSessionStore(this).loadAppearanceSettings()
        setCandyContent(animationsEnabled = appearanceSettings.animationsEnabled) {
            val appearanceDark = appearanceSettings.usesDarkColors(isSystemInDarkTheme())
            SideEffect { applyAppearanceSystemBars(appearanceDark) }
            CandyTheme(settings = appearanceSettings) {
                SiteCapsuleEditorScreen(
                    request = request,
                    onSubmit = { submission ->
                        if (!areProfilesAccessible()) {
                            finish()
                            return@SiteCapsuleEditorScreen
                        }
                        setResult(
                            Activity.RESULT_OK,
                            SiteCapsuleEditorContract.resultIntent(submission),
                        )
                        finish()
                    },
                    onDismiss = ::finish,
                    customIcon = customIcon,
                    customIconRevision = customIconRevision,
                    onEditCustomIcon = {
                        customIconEditor.launch(
                            CapsuleCustomIconEditorRequest(
                                icon = customIcon,
                                protectedProfileIds = protectedProfileIds,
                            ),
                        )
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
        if (!areProfilesAccessible()) finish()
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

    private fun areProfilesAccessible(): Boolean {
        if (protectedProfileIds.isEmpty()) return true
        val profiles = BrowserSessionStore(this).loadProfiles().first
        return protectedProfileIds.all { profileId ->
            ProfileProtectionSession.isAccessible(
                profiles.firstOrNull { profile -> profile.id == profileId },
            )
        }
    }

    private companion object {
        const val STATE_CUSTOM_ICON = "site_capsule_editor.custom_icon"
        const val STATE_CUSTOM_ICON_REVISION = "site_capsule_editor.custom_icon_revision"
    }
}
