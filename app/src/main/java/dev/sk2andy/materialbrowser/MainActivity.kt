package dev.sk2andy.materialbrowser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.annotation.VisibleForTesting
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.integration.IncomingBrowserIntent
import dev.sk2andy.materialbrowser.capsule.CapsuleIntentRules
import dev.sk2andy.materialbrowser.capsule.CapsuleLaunchResolution
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.ui.BrowserScreen
import dev.sk2andy.materialbrowser.ui.CandySplashScreen
import dev.sk2andy.materialbrowser.ui.GestureOnboardingScreen
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var browserController: BrowserController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val onboardingStore = GestureOnboardingStore(this)
        val onboardingRequired = onboardingStore.shouldShow()
        browserController = BrowserController(this)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            browserController.onWindowInsetsChanged(insets)
            insets
        }
        val restoredCapsuleId = savedInstanceState?.getString(STATE_CAPSULE_ID)
        if (restoredCapsuleId != null) {
            val restoredTabId = savedInstanceState.getString(STATE_CAPSULE_TAB_ID)
            if (!browserController.restoreSiteCapsule(restoredCapsuleId, restoredTabId)) {
                browserController.openNormalHomeFromInvalidCapsule()
            }
        } else if (savedInstanceState == null) {
            openIntent(intent)
        }
        setContent {
            MaterialBrowserTheme {
                var onboardingVisible by rememberSaveable {
                    mutableStateOf(onboardingRequired)
                }
                var splashVisible by remember {
                    mutableStateOf(
                        savedInstanceState == null && intent.action == Intent.ACTION_MAIN,
                    )
                }
                LaunchedEffect(Unit) {
                    if (splashVisible) {
                        delay(SPLASH_DURATION_MILLIS)
                        splashVisible = false
                    }
                }
                Box {
                    BrowserScreen(browserController)
                    if (onboardingVisible) {
                        GestureOnboardingScreen(
                            onCompleted = {
                                onboardingStore.markCompleted()
                                onboardingVisible = false
                            },
                        )
                    }
                    AnimatedVisibility(
                        visible = splashVisible,
                        exit = fadeOut(tween(260)) + scaleOut(targetScale = 0.96f),
                    ) {
                        CandySplashScreen()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openIntent(intent)
    }

    override fun onPause() {
        browserController.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::browserController.isInitialized) browserController.onResume()
    }

    override fun onDestroy() {
        if (::browserController.isInitialized) browserController.destroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        browserController.activeCapsuleId?.let { outState.putString(STATE_CAPSULE_ID, it) }
        browserController.activeCapsuleTabId?.let { outState.putString(STATE_CAPSULE_TAB_ID, it) }
        super.onSaveInstanceState(outState)
    }

    private fun openIntent(intent: Intent) {
        when (
            val resolution = browserController.resolveCapsuleLaunch(
                action = intent.action,
                capsuleId = intent.getStringExtra(CapsuleIntentRules.EXTRA_CAPSULE_ID),
            )
        ) {
            is CapsuleLaunchResolution.Open -> {
                if (!browserController.openSiteCapsule(resolution.capsule.id)) {
                    browserController.openNormalHomeFromInvalidCapsule()
                }
                return
            }
            CapsuleLaunchResolution.NormalHome -> {
                browserController.openNormalHomeFromInvalidCapsule()
                return
            }
            CapsuleLaunchResolution.NotCapsuleIntent -> Unit
        }
        if (intent.action == Intent.ACTION_MAIN) browserController.leaveSiteCapsule()
        IncomingBrowserIntent.from(intent)?.let { request ->
            browserController.openUrl(request.url)
        }
    }

    @VisibleForTesting
    fun browserControllerForTesting(): BrowserController = browserController

    private companion object {
        const val SPLASH_DURATION_MILLIS = 1_050L
        const val STATE_CAPSULE_ID = "active_site_capsule_id"
        const val STATE_CAPSULE_TAB_ID = "active_site_capsule_tab_id"
    }
}
