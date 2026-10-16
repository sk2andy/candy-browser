package dev.sk2andy.materialbrowser

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.sk2andy.materialbrowser.browser.ProfileProtectionSession
import dev.sk2andy.materialbrowser.browser.guest.GuestBubbleGate
import dev.sk2andy.materialbrowser.browser.guest.GuestBubbleSessionStore
import dev.sk2andy.materialbrowser.browser.integration.ConstellationActivityContract
import dev.sk2andy.materialbrowser.constellations.ConstellationSnapshot
import dev.sk2andy.materialbrowser.data.AppDataTransferLock
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.ConstellationRepository
import dev.sk2andy.materialbrowser.ui.ConstellationScreen
import dev.sk2andy.materialbrowser.ui.theme.CandyTheme
import dev.sk2andy.materialbrowser.ui.theme.setCandyContent

/** A map has one immutable profile owner for the entire lifetime of this activity. */
class ConstellationActivity : ComponentActivity() {
    private val repository by lazy { ConstellationRepository.get(this) }
    private var profileId: String? = null
    private var snapshot by mutableStateOf(ConstellationSnapshot(profileId = ""))
    private var refreshGeneration = 0
    private val onChanged: () -> Unit = { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        BrowsingHistoryLifecycle.install(application)
        super.onCreate(savedInstanceState)
        if (GuestBubbleGate.redirectIfLocked(this)) return
        profileId = ConstellationActivityContract.profileId(intent)
        val profile = accessibleProfile() ?: return finish()
        snapshot = ConstellationSnapshot(profileId = profile.id)
        enableEdgeToEdge()
        setRecentsScreenshotEnabled(profile.protection == null)
        val appearance = BrowserSessionStore(this).loadAppearanceSettings()
        setCandyContent(animationsEnabled = appearance.animationsEnabled) {
            val dark = appearance.usesDarkColors(isSystemInDarkTheme())
            SideEffect { applyAppearanceSystemBars(dark) }
            CandyTheme(settings = appearance) {
                ConstellationScreen(
                    profileName = profile.emoji,
                    snapshot = snapshot,
                    onEnable = { accessibleProfile()?.let { repository.setEnabled(it.id, true) } },
                    onDisable = { accessibleProfile()?.let { repository.setEnabled(it.id, false) } },
                    onDownloadModel = { if (accessibleProfile() != null) repository.downloadModel() },
                    onOpenPage = { url ->
                        accessibleProfile()?.let { owner ->
                            if (snapshot.documents.any { it.url == url && it.profileId == owner.id }) {
                                setResult(Activity.RESULT_OK,
                                    ConstellationActivityContract.resultIntent(owner.id, url))
                                finish()
                            }
                        }
                    },
                    onDismiss = ::finish,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        repository.addListener(onChanged)
        refresh()
    }

    override fun onStop() {
        repository.removeListener(onChanged)
        refreshGeneration++
        snapshot = ConstellationSnapshot(profileId = profileId.orEmpty())
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (GuestBubbleGate.redirectIfLocked(this)) return
        if (accessibleProfile() == null) finish() else refresh()
    }

    private fun refresh() {
        val owner = accessibleProfile() ?: return finish()
        val generation = ++refreshGeneration
        repository.snapshot(owner.id) { updated ->
            if (!isFinishing && generation == refreshGeneration && accessibleProfile()?.id == updated.profileId) {
                snapshot = updated
            }
        }
    }

    private fun accessibleProfile() = if (
        AppDataTransferLock.isActive(this) || GuestBubbleSessionStore(this).isLocked()
    ) null else {
        val store = BrowserSessionStore(this)
        val profiles = store.loadProfiles().first
        val configured = if (store.loadProfilesEnabled()) profiles else profiles.take(1)
        configured.firstOrNull { it.id == profileId && ProfileProtectionSession.isAccessible(it) }
    }
}
