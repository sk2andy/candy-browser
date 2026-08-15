package dev.sk2andy.materialbrowser

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.view.ViewCompat
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserInputDiagnostics
import dev.sk2andy.materialbrowser.browser.actions.BrowserDownloadManager
import dev.sk2andy.materialbrowser.browser.actions.DownloadActionResult
import dev.sk2andy.materialbrowser.browser.integration.IncomingBrowserIntent
import dev.sk2andy.materialbrowser.capsule.CapsuleIntentRules
import dev.sk2andy.materialbrowser.capsule.CapsuleLaunchResolution
import dev.sk2andy.materialbrowser.data.BrowserDownloadRequest
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.SnoozeWakeNotifier
import dev.sk2andy.materialbrowser.ui.BrowserScreen
import dev.sk2andy.materialbrowser.ui.CandySplashScreen
import dev.sk2andy.materialbrowser.ui.GestureOnboardingScreen
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import dev.sk2andy.materialbrowser.update.AvailableAppUpdate
import dev.sk2andy.materialbrowser.update.GitHubAppUpdateChecker
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var browserController: BrowserController
    private val webPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (::browserController.isInitialized) browserController.onRuntimePermissionResult(results)
    }
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (::browserController.isInitialized) {
            browserController.onFileChooserResult(result.resultCode, result.data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val onboardingStore = GestureOnboardingStore(this)
        val onboardingRequired = onboardingStore.shouldShow()
        val snoozeWakeNotifier = SnoozeWakeNotifier(this).also { it.ensureChannel() }
        browserController = BrowserController(
            activity = this,
            requestRuntimePermissions = { permissions ->
                webPermissionLauncher.launch(permissions.toTypedArray())
            },
            launchFileChooser = fileChooserLauncher::launch,
            requestSnoozeNotificationPermission = {
                if (!snoozeWakeNotifier.hasPostNotificationPermission()) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onFullImmersiveModeChanged = ::applyFullImmersiveMode,
        )
        applyFullImmersiveMode(browserController.isFullImmersiveModeEnabled)
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
            val appearanceDark = browserController.appearanceSettings.usesDarkColors(
                isSystemInDarkTheme(),
            )
            SideEffect { applyAppearanceSystemBars(appearanceDark) }
            MaterialBrowserTheme(settings = browserController.appearanceSettings) {
                var onboardingVisible by rememberSaveable {
                    mutableStateOf(onboardingRequired)
                }
                var splashVisible by remember {
                    mutableStateOf(
                        savedInstanceState == null && intent.action == Intent.ACTION_MAIN,
                    )
                }
                var updateCheckCompleted by rememberSaveable { mutableStateOf(false) }
                var availableUpdateVersion by rememberSaveable { mutableStateOf<String?>(null) }
                var availableUpdateUrl by rememberSaveable { mutableStateOf<String?>(null) }
                var availableUpdateFileName by rememberSaveable { mutableStateOf<String?>(null) }
                var updateDialogDismissed by rememberSaveable { mutableStateOf(false) }
                val updateChecker = remember { GitHubAppUpdateChecker() }
                val updateDownloadManager = remember { BrowserDownloadManager(this) }
                val availableUpdate = availableUpdateVersion?.let { version ->
                    val url = availableUpdateUrl ?: return@let null
                    val fileName = availableUpdateFileName ?: return@let null
                    AvailableAppUpdate(version, url, fileName)
                }
                LaunchedEffect(Unit) {
                    if (splashVisible) {
                        delay(SPLASH_DURATION_MILLIS)
                        splashVisible = false
                    }
                }
                LaunchedEffect(updateCheckCompleted) {
                    if (updateCheckCompleted) return@LaunchedEffect
                    if (BuildConfig.ENABLE_GITHUB_UPDATES) {
                        updateChecker.findAvailableUpdate(BuildConfig.VERSION_NAME)?.let { update ->
                            availableUpdateVersion = update.versionName
                            availableUpdateUrl = update.downloadUrl
                            availableUpdateFileName = update.fileName
                        }
                    }
                    updateCheckCompleted = true
                }
                Box {
                    BrowserScreen(
                        controller = browserController,
                        onTabOverviewPortraitLockChanged = ::setTabOverviewPortraitLocked,
                    )
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
                if (
                    availableUpdate != null &&
                    !updateDialogDismissed &&
                    !onboardingVisible &&
                    !splashVisible
                ) {
                    AppUpdateDialog(
                        update = availableUpdate,
                        onDismiss = { updateDialogDismissed = true },
                        onDownload = {
                            val result = updateDownloadManager.enqueue(
                                BrowserDownloadRequest(
                                    url = availableUpdate.downloadUrl,
                                    fileName = availableUpdate.fileName,
                                    mimeType = AvailableAppUpdate.APK_MIME_TYPE,
                                ),
                            )
                            Toast.makeText(
                                this,
                                when (result) {
                                    is DownloadActionResult.Enqueued ->
                                        getString(R.string.toast_download_started, result.fileName)
                                    is DownloadActionResult.HandedOff ->
                                        getString(R.string.toast_download_handed_off, result.appName)
                                    is DownloadActionResult.Failed -> result.message
                                },
                                Toast.LENGTH_SHORT,
                            ).show()
                            if (result is DownloadActionResult.Enqueued) {
                                updateDialogDismissed = true
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openIntent(intent)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val hadWindowFocus = window.decorView.hasWindowFocus()
        val focusedView = currentFocus
        val handled = super.dispatchTouchEvent(event)
        BrowserInputDiagnostics.activityDispatch(
            event = event,
            handled = handled,
            hasWindowFocus = hadWindowFocus,
            focusedView = focusedView,
        )
        return handled
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        BrowserInputDiagnostics.activityWindowFocus(hasFocus, currentFocus)
        if (hasFocus && ::browserController.isInitialized) {
            applyFullImmersiveMode(browserController.isFullImmersiveModeEnabled)
        }
    }

    override fun onPause() {
        browserController.onPause()
        super.onPause()
    }

    override fun onStart() {
        super.onStart()
        if (::browserController.isInitialized) browserController.onStart()
    }

    override fun onStop() {
        if (::browserController.isInitialized) browserController.onStop()
        super.onStop()
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
        if (intent.action == SnoozeWakeNotifier.ACTION_OPEN_RESTORED_TAB) {
            intent.getStringExtra(SnoozeWakeNotifier.EXTRA_TAB_ID)?.let { tabId ->
                browserController.openSnoozedWakeTab(tabId)
            }
            return
        }
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

    private fun setTabOverviewPortraitLocked(locked: Boolean) {
        val orientation = if (locked) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (requestedOrientation != orientation) requestedOrientation = orientation
    }

    private companion object {
        const val SPLASH_DURATION_MILLIS = 1_050L
        const val STATE_CAPSULE_ID = "active_site_capsule_id"
        const val STATE_CAPSULE_TAB_ID = "active_site_capsule_tab_id"
    }
}

@Composable
private fun AppUpdateDialog(
    update: AvailableAppUpdate,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Text(
                stringResource(
                    R.string.update_available_message,
                    update.versionName,
                ),
            )
        },
        confirmButton = {
            Button(onClick = onDownload) {
                Text(stringResource(R.string.action_download_update))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_later))
            }
        },
    )
}
