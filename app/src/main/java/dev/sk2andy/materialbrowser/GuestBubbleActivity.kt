package dev.sk2andy.materialbrowser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dev.sk2andy.materialbrowser.browser.SearchSettings
import dev.sk2andy.materialbrowser.browser.guest.GuestBubbleAuthenticator
import dev.sk2andy.materialbrowser.browser.guest.GuestBubblePhase
import dev.sk2andy.materialbrowser.browser.guest.GuestBubblePublicSurfaces
import dev.sk2andy.materialbrowser.browser.guest.GuestBubbleSessionStore
import dev.sk2andy.materialbrowser.browser.systemwebview.GuestBubbleWebView
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.ui.GuestBubbleScreen
import dev.sk2andy.materialbrowser.ui.theme.CandyTheme
import dev.sk2andy.materialbrowser.ui.theme.setCandyContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Deliberately has no BrowserController, owner profiles, app bridges or persistent tab store. */
class GuestBubbleActivity : AppCompatActivity() {
    private lateinit var store: GuestBubbleSessionStore
    private lateinit var authenticator: GuestBubbleAuthenticator
    private var browser by mutableStateOf<GuestBubbleWebView?>(null)
    private var address by mutableStateOf("")
    private var loading by mutableStateOf(false)
    private var canGoBack by mutableStateOf(false)
    private var startupFailed by mutableStateOf(false)
    private var starting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setRecentsScreenshotEnabled(false)
        store = GuestBubbleSessionStore(this)
        authenticator = GuestBubbleAuthenticator(this)
        if (!store.isLocked()) {
            finish()
            return
        }
        if (store.state()?.phase == GuestBubblePhase.Closing) {
            startActivity(GuestBubbleCleanupActivity.createIntent(this))
            finish()
            return
        }
        val appearance = BrowserSessionStore(this).loadAppearanceSettings()
        setCandyContent(animationsEnabled = appearance.animationsEnabled) {
            val dark = appearance.usesDarkColors(isSystemInDarkTheme())
            SideEffect { applyAppearanceSystemBars(dark) }
            CandyTheme(settings = appearance) {
                GuestBubbleScreen(
                    webView = browser?.view,
                    address = address,
                    loading = loading,
                    canGoBack = canGoBack,
                    startupFailed = startupFailed,
                    onNavigate = { input ->
                        if (browser?.navigate(input) == false) showUnavailableAction()
                    },
                    onBack = { browser?.goBack() },
                    onEnd = ::requestEnd,
                    onRetry = ::startBrowser,
                )
            }
        }
        startBrowser()
    }

    private fun startBrowser() {
        if (starting || browser != null) return
        starting = true
        startupFailed = false
        lifecycleScope.launch {
            val claimed = withContext(Dispatchers.IO) { store.claimGuestProcess() }
            if (!claimed || !GuestBubblePublicSurfaces.hide(applicationContext)) {
                Log.w("GuestBubble", "Guest startup rejected; process claimed=$claimed")
                startupFailed = true
                starting = false
                return@launch
            }
            val settings = BrowserSessionStore(this@GuestBubbleActivity)
            browser = try {
                GuestBubbleWebView(
                    context = this@GuestBubbleActivity,
                    searchSettings = SearchSettings(
                        searchEngine = settings.loadSearchEngine(),
                        searxngInstanceUrl = settings.loadSearxngSettings().instanceUrl,
                    ),
                    onNavigation = { url, back ->
                        address = url
                        canGoBack = back
                    },
                    onLoading = { loading = it },
                    onUnavailableAction = ::showUnavailableAction,
                )
            } catch (error: RuntimeException) {
                Log.w("GuestBubble", "Guest browser could not be created", error)
                startupFailed = true
                null
            }
            starting = false
        }
    }

    private fun requestEnd() {
        authenticator.authenticate { authenticated ->
            if (!authenticated || isFinishing) return@authenticate
            if (!store.beginClosing()) {
                showUnavailableAction()
                return@authenticate
            }
            browser?.onPause()
            startActivity(GuestBubbleCleanupActivity.createIntent(this))
            finish()
        }
    }

    private fun showUnavailableAction() {
        Toast.makeText(this, R.string.guest_bubble_unavailable_action, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        browser?.onResume()
    }

    override fun onPause() {
        browser?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        browser?.close()
        browser = null
        super.onDestroy()
    }

    companion object {
        internal fun createIntent(context: Context): Intent =
            Intent(context, GuestBubbleActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
