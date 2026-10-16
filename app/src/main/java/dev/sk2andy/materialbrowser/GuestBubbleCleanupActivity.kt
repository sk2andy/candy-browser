package dev.sk2andy.materialbrowser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.sk2andy.materialbrowser.browser.guest.GuestBubblePhase
import dev.sk2andy.materialbrowser.browser.guest.GuestBubblePublicSurfaces
import dev.sk2andy.materialbrowser.browser.guest.GuestBubbleSessionStore
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.ui.theme.CandyTheme
import dev.sk2andy.materialbrowser.ui.theme.setCandyContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Erases guest data after authentication, in a process that has never initialized its WebView. */
class GuestBubbleCleanupActivity : ComponentActivity() {
    private var cleanupRunning by mutableStateOf(false)
    private var cleanupFailed by mutableStateOf(false)
    private val sessionStore by lazy { GuestBubbleSessionStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (sessionStore.state()?.phase) {
            null -> {
                openOwnerBrowser()
                return
            }
            GuestBubblePhase.Active -> {
                startActivity(GuestBubbleActivity.createIntent(this))
                finish()
                return
            }
            GuestBubblePhase.Closing -> Unit
        }
        enableEdgeToEdge()
        setRecentsScreenshotEnabled(false)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        val appearanceSettings = BrowserSessionStore(this).loadAppearanceSettings()
        setCandyContent(animationsEnabled = appearanceSettings.animationsEnabled) {
            val appearanceDark = appearanceSettings.usesDarkColors(isSystemInDarkTheme())
            SideEffect { applyAppearanceSystemBars(appearanceDark) }
            CandyTheme(settings = appearanceSettings) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp)
                            .testTag("guest_bubble_cleanup"),
                        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (cleanupRunning) CircularProgressIndicator()
                        Text(
                            text = stringResource(
                                if (cleanupFailed) R.string.guest_bubble_cleanup_failed
                                else R.string.guest_bubble_cleanup_progress,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        if (cleanupFailed && !cleanupRunning) {
                            Button(onClick = ::startCleanup) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }
            }
        }
        startCleanup()
    }

    private fun startCleanup() {
        if (cleanupRunning) return
        cleanupRunning = true
        cleanupFailed = false
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) { sessionStore.completeCleanup() }
            cleanupRunning = false
            if (success) openOwnerBrowser() else cleanupFailed = true
        }
    }

    private fun openOwnerBrowser() {
        GuestBubblePublicSurfaces.restore(applicationContext)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
        )
        finishAndRemoveTask()
    }

    companion object {
        internal fun createIntent(context: Context): Intent =
            Intent(context, GuestBubbleCleanupActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
