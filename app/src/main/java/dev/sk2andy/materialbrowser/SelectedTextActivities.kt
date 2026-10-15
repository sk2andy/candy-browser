package dev.sk2andy.materialbrowser

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.sk2andy.materialbrowser.browser.integration.ProcessTextResearchRequest
import dev.sk2andy.materialbrowser.browser.integration.ProcessTextRules
import dev.sk2andy.materialbrowser.browser.integration.processTextResearchRequests
import dev.sk2andy.materialbrowser.data.AppDataTransferLock
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.reader.ReaderSpeechController
import dev.sk2andy.materialbrowser.reader.ReaderSpeechStatus
import dev.sk2andy.materialbrowser.ui.SelectedTextResearchScreen
import dev.sk2andy.materialbrowser.ui.SelectedTextSpeechScreen
import dev.sk2andy.materialbrowser.ui.theme.CandyTheme
import dev.sk2andy.materialbrowser.ui.theme.setCandyContent

class ResearchSelectedTextActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val text = takeSelectedText(ProcessTextRules.MAX_RESEARCH_CHARACTERS)
        if (text == null || savedInstanceState != null || AppDataTransferLock.isActive(this)) {
            finish()
            return
        }
        enableEdgeToEdge()
        setRecentsScreenshotEnabled(false)
        val store = BrowserSessionStore(this)
        val appearanceSettings = store.loadAppearanceSettings()
        val (profiles, activeProfileId) = store.loadProfiles()
        val eligibleProfiles = ProcessTextRules.eligibleProfiles(profiles, activeProfileId, store.loadProfilesEnabled())
        setCandyContent(animationsEnabled = appearanceSettings.animationsEnabled) {
            val appearanceDark = appearanceSettings.usesDarkColors(isSystemInDarkTheme())
            SideEffect { applyAppearanceSystemBars(appearanceDark) }
            CandyTheme(settings = appearanceSettings) {
                SelectedTextResearchScreen(
                    text = text,
                    profiles = eligibleProfiles,
                    onResearch = research@{ profileId ->
                        val (currentProfiles, currentActiveProfileId) = store.loadProfiles()
                        if (ProcessTextRules.eligibleProfiles(
                                currentProfiles, currentActiveProfileId, store.loadProfilesEnabled(),
                            ).none { it.id == profileId }
                        ) {
                            Toast.makeText(this, R.string.process_text_unavailable, Toast.LENGTH_LONG).show()
                            return@research
                        }
                        val token = processTextResearchRequests.put(
                            ProcessTextResearchRequest(text, profileId), SystemClock.elapsedRealtime(),
                        ) ?: return@research
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .setAction(ProcessTextRules.ACTION_RESEARCH)
                                .putExtra(ProcessTextRules.EXTRA_REQUEST_TOKEN, token)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        )
                        finish()
                    },
                    onDismiss = ::finish,
                )
            }
        }
    }
}

class ReadSelectedTextActivity : ComponentActivity() {
    private var speech: ReaderSpeechController? = null
    private var playbackAllowed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val text = takeSelectedText(ProcessTextRules.MAX_SPEECH_CHARACTERS)
        if (text == null || savedInstanceState != null || AppDataTransferLock.isActive(this)) {
            finish()
            return
        }
        enableEdgeToEdge()
        setRecentsScreenshotEnabled(false)
        val appearanceSettings = BrowserSessionStore(this).loadAppearanceSettings()
        val controller = ReaderSpeechController(this).also { speech = it }
        setCandyContent(animationsEnabled = appearanceSettings.animationsEnabled) {
            val appearanceDark = appearanceSettings.usesDarkColors(isSystemInDarkTheme())
            SideEffect { applyAppearanceSystemBars(appearanceDark) }
            CandyTheme(settings = appearanceSettings) {
                SelectedTextSpeechScreen(text, controller, onDismiss = ::finish, playbackAllowed = playbackAllowed)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        playbackAllowed = true
    }

    override fun onStop() {
        playbackAllowed = false
        if (speech?.state?.status in setOf(ReaderSpeechStatus.Speaking, ReaderSpeechStatus.Paused)) {
            speech?.stop()
        }
        super.onStop()
    }

    override fun onDestroy() {
        speech?.close()
        speech = null
        super.onDestroy()
    }
}

private fun Activity.takeSelectedText(maximumCharacters: Int): String? {
    val incoming = intent
    setIntent(Intent())
    if (incoming.action != Intent.ACTION_PROCESS_TEXT || incoming.type != "text/plain") return null
    val selected = runCatching { incoming.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT) }.getOrNull()
    return ProcessTextRules.selectedText(selected, maximumCharacters)
}
