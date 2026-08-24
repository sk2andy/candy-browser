package dev.sk2andy.materialbrowser

import android.Manifest
import android.app.PictureInPictureParams
import android.app.PictureInPictureUiState
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Rational
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserInputDiagnostics
import dev.sk2andy.materialbrowser.browser.FullscreenVideoBounds
import dev.sk2andy.materialbrowser.browser.FullscreenVideoRules
import dev.sk2andy.materialbrowser.browser.WebMediaSystemSession
import dev.sk2andy.materialbrowser.browser.actions.BrowserDownloadManager
import dev.sk2andy.materialbrowser.browser.cast.CastSessionController
import dev.sk2andy.materialbrowser.browser.actions.DownloadActionResult
import dev.sk2andy.materialbrowser.browser.integration.IncomingBrowserIntent
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParseResult
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParser
import dev.sk2andy.materialbrowser.capsule.CapsuleIntentRules
import dev.sk2andy.materialbrowser.capsule.CapsuleLaunchResolution
import dev.sk2andy.materialbrowser.data.BrowserDownloadRequest
import dev.sk2andy.materialbrowser.data.AppDataArchiveEnvironment
import dev.sk2andy.materialbrowser.data.AppDataArchiveRules
import dev.sk2andy.materialbrowser.data.AppDataArchiveRestore
import dev.sk2andy.materialbrowser.data.AppDataArchiveStaging
import dev.sk2andy.materialbrowser.data.AppDataTransferLock
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.SnoozeWakeNotifier
import dev.sk2andy.materialbrowser.data.UserScriptImportReader
import dev.sk2andy.materialbrowser.data.UserScriptImportResult
import dev.sk2andy.materialbrowser.ui.AppDataExportWarningDialog
import dev.sk2andy.materialbrowser.ui.AppDataImportConfirmationDialog
import dev.sk2andy.materialbrowser.ui.AppDataImportPreview
import dev.sk2andy.materialbrowser.ui.BrowserScreen
import dev.sk2andy.materialbrowser.ui.CandySplashScreen
import dev.sk2andy.materialbrowser.ui.FullscreenVideoOverlay
import dev.sk2andy.materialbrowser.ui.GestureOnboardingScreen
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import dev.sk2andy.materialbrowser.update.AvailableAppUpdate
import dev.sk2andy.materialbrowser.update.AppReleaseChannel
import dev.sk2andy.materialbrowser.update.GitHubAppUpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {
    private lateinit var browserController: BrowserController
    private lateinit var webMediaSystemSession: WebMediaSystemSession
    private lateinit var castSessionController: CastSessionController
    private var videoOnlyPresentation by mutableStateOf(false)
    private var fullscreenVideoBounds: Rect? = null
    private var pictureInPictureSourceRectHint: Rect? = null
    private var appliedPictureInPictureState: AppliedPictureInPictureState? = null
    private var pictureInPictureReturnLayoutListener: View.OnLayoutChangeListener? = null
    private var pictureInPictureReturnInProgress = false
    private var pictureInPictureStartedFullscreen = false
    private var pictureInPictureModeEntered = false
    private var isTabOverviewPortraitLocked = false
    private var appDataExportWarningVisible by mutableStateOf(false)
    private var pendingAppDataImport by mutableStateOf<AppDataImportPreview?>(null)
    private var appDataImportLoading = false
    private var appDataTransferActive = false
    private var appliedNightConfiguration = Configuration.UI_MODE_NIGHT_UNDEFINED
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
    private val userScriptImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null && ::browserController.isInitialized) importUserScript(uri)
    }
    private val appDataExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null && ::browserController.isInitialized) startAppDataExport(uri)
    }
    private val appDataImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null && ::browserController.isInitialized) stageAppDataImport(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppearanceNightMode(
            BrowserSessionStore(this).loadAppearanceSettings().appearanceMode,
        )
        super.onCreate(savedInstanceState)
        appliedNightConfiguration = resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK
        if (AppDataArchiveRestore.hasInterruptedRestore(appDataRestoreRecoveryMarker())) {
            val lockToken = AppDataTransferLock.activate(this, Process.myPid())
            if (lockToken != null) {
                appDataTransferActive = true
                val started = runCatching {
                    startActivity(
                        AppDataTransferContract.recoveryIntent(
                            context = this,
                            mainProcessId = Process.myPid(),
                            lockToken = lockToken,
                        ),
                    )
                }.isSuccess
                if (!started) AppDataTransferLock.release(this, lockToken)
            }
            finish()
            return
        }
        if (AppDataTransferLock.isActive(this)) {
            finish()
            return
        }
        AppDataArchiveRestore.cleanupOrphanedWorkDirectories(
            stateDirectory = appDataTransferStateDirectory(),
            recoveryMarker = appDataRestoreRecoveryMarker(),
        )
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
            onFullImmersiveModeChanged = { applyBrowserSystemUi() },
            onWebMediaStateChanged = {
                if (::webMediaSystemSession.isInitialized) {
                    webMediaSystemSession.publish(browserController.systemWebMediaState)
                }
                if (::castSessionController.isInitialized) {
                    castSessionController.updateCandidate(browserController.castMediaCandidate)
                }
                updatePictureInPictureParams()
            },
            onWebPictureInPictureRequested = ::onPictureInPictureRequested,
            onWebPictureInPictureRequestTimedOut = ::cancelPictureInPictureTransition,
        )
        castSessionController = CastSessionController(
            context = this,
            onMediaLoaded = { candidate -> browserController.pauseCastMedia(candidate) },
        ).also { it.updateCandidate(browserController.castMediaCandidate) }
        webMediaSystemSession = WebMediaSystemSession(
            context = this,
            onPlay = browserController::playActiveWebMedia,
            onPause = browserController::pauseActiveWebMedia,
            onStop = browserController::stopActiveWebMedia,
            onSeekTo = browserController::seekActiveWebMedia,
        )
        applyBrowserSystemUi()
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
            val appearanceSettings = browserController.appearanceSettings
            val appearanceDark = appearanceSettings.usesDarkColors(
                isSystemInDarkTheme(),
            )
            SideEffect {
                applyAppearanceNightMode(appearanceSettings.appearanceMode)
                applyAppearanceSystemBars(appearanceDark)
            }
            MaterialBrowserTheme(settings = appearanceSettings) {
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
                val fullscreenVideoState = browserController.fullscreenVideoState
                val selectedTabId = browserController.selectedTabId
                val webViewVideoOnlyPresentation = fullscreenVideoState?.let { state ->
                    videoOnlyPresentation &&
                        !FullscreenVideoRules.hostsSourceInOverlay(
                            host = state.host,
                            videoOnlyPresentation = true,
                        )
                } == true
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
                        val releaseChannel = AppReleaseChannel.forUserCertificateTrust(
                            BuildConfig.TRUST_USER_CERTIFICATES,
                        )
                        updateChecker.findAvailableUpdate(
                            currentVersionName = BuildConfig.VERSION_NAME,
                            channel = releaseChannel,
                        )?.let { update ->
                            availableUpdateVersion = update.versionName
                            availableUpdateUrl = update.downloadUrl
                            availableUpdateFileName = update.fileName
                        }
                    }
                    updateCheckCompleted = true
                }
                LaunchedEffect(
                    fullscreenVideoState,
                    browserController.webMediaState,
                    selectedTabId,
                    videoOnlyPresentation,
                ) {
                    applyBrowserSystemUi()
                    updatePictureInPictureParams()
                    if (
                        browserController.fullscreenVideoState == null &&
                        isInPictureInPictureMode
                    ) {
                        moveTaskToBack(true)
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    BrowserScreen(
                        controller = browserController,
                        castUiState = castSessionController.state,
                        onToggleCastPlayback = castSessionController::togglePlayback,
                        onSeekCast = castSessionController::seekTo,
                        onCastVolumeChange = castSessionController::setDeviceVolume,
                        onDisconnectCast = castSessionController::disconnect,
                        webViewVideoOnlyPresentation = webViewVideoOnlyPresentation,
                        onTabOverviewPortraitLockChanged = ::setTabOverviewPortraitLocked,
                        onImportUserScript = {
                            userScriptImportLauncher.launch(
                                arrayOf(
                                    "application/javascript",
                                    "text/javascript",
                                    "text/plain",
                                ),
                            )
                        },
                        onExportAppData = {
                            if (!browserController.canExportAppData()) {
                                Toast.makeText(
                                    this@MainActivity,
                                    R.string.data_archive_private_tabs_error,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                appDataExportWarningVisible = true
                            }
                        },
                        onImportAppData = {
                            appDataImportLauncher.launch(
                                arrayOf("application/zip", "application/octet-stream"),
                            )
                        },
                    )
                    if (videoOnlyPresentation && !webViewVideoOnlyPresentation) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                    }
                    FullscreenVideoOverlay(
                        controller = browserController,
                        videoOnlyPresentation = videoOnlyPresentation,
                        onBoundsChanged = ::onFullscreenVideoBoundsChanged,
                    )
                    if (!videoOnlyPresentation && onboardingVisible) {
                        GestureOnboardingScreen(
                            onCompleted = {
                                onboardingStore.markCompleted()
                                onboardingVisible = false
                            },
                        )
                    }
                    AnimatedVisibility(
                        visible = splashVisible && !videoOnlyPresentation,
                        exit = fadeOut(tween(260)) + scaleOut(targetScale = 0.96f),
                    ) {
                        CandySplashScreen()
                    }
                }
                if (
                    availableUpdate != null &&
                    !updateDialogDismissed &&
                    !onboardingVisible &&
                    !splashVisible &&
                    !videoOnlyPresentation
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
                if (appDataExportWarningVisible) {
                    AppDataExportWarningDialog(
                        onDismiss = { appDataExportWarningVisible = false },
                        onConfirm = {
                            appDataExportWarningVisible = false
                            appDataExportLauncher.launch(defaultAppDataArchiveFileName())
                        },
                    )
                }
                pendingAppDataImport?.let { pending ->
                    AppDataImportConfirmationDialog(
                        pending = pending,
                        onDismiss = {
                            deleteStagedAppDataArchive(pending.staged.fileName)
                            pendingAppDataImport = null
                        },
                        onConfirm = {
                            pendingAppDataImport = null
                            startAppDataTransfer(R.string.data_archive_import_failed) { lockToken ->
                                AppDataTransferContract.importIntent(
                                    context = this,
                                    stagedFileName = pending.staged.fileName,
                                    mainProcessId = Process.myPid(),
                                    lockToken = lockToken,
                                )
                            }
                        },
                    )
                }
            }
        }
        showAppDataTransferResult(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (appDataTransferActive) return
        setIntent(intent)
        showAppDataTransferResult(intent)
        openIntent(intent)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (appDataTransferActive) return true
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        if (appDataTransferActive) true else super.dispatchKeyEvent(event)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        BrowserInputDiagnostics.activityWindowFocus(hasFocus, currentFocus)
        if (hasFocus &&
            ::browserController.isInitialized &&
            !appDataTransferActive
        ) {
            applyBrowserSystemUi()
        }
    }

    override fun onPause() {
        if (!::browserController.isInitialized || appDataTransferActive) {
            super.onPause()
            return
        }
        prepareForPictureInPictureTransition()
        browserController.onPause()
        super.onPause()
    }

    override fun onUserLeaveHint() {
        if (!appDataTransferActive) prepareForPictureInPictureTransition()
        super.onUserLeaveHint()
    }

    override fun onStart() {
        super.onStart()
        if (::browserController.isInitialized && !appDataTransferActive) {
            browserController.onStart()
        }
    }

    override fun onStop() {
        if (::browserController.isInitialized && !appDataTransferActive) {
            browserController.onStop(isInPictureInPictureMode)
        }
        super.onStop()
    }

    override fun onPictureInPictureRequested(): Boolean {
        if (appDataTransferActive) return false
        if (!canEnterPictureInPicture()) return false
        prepareForPictureInPictureTransition()
        val entered = enterPictureInPictureMode(
            buildPictureInPictureParams(
                autoEnterEnabled = true,
                sourceRectHint = eligiblePictureInPictureSourceRect(true),
            ),
        )
        if (!entered) cancelPictureInPictureTransition()
        return entered
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (appDataTransferActive) return
        if (isInPictureInPictureMode) {
            pictureInPictureModeEntered = true
            videoOnlyPresentation = true
        }
        browserController.onPictureInPictureModeChanged(isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            pictureInPictureReturnInProgress = false
            cancelPictureInPictureReturnLayoutWait()
            if (pictureInPictureStartedFullscreen) {
                pictureInPictureSourceRectHint = pictureInPictureSourceRect(
                    maximumWindowContentBounds(),
                )
            }
        } else {
            pictureInPictureReturnInProgress = true
            completePictureInPictureReturnAfterLayout(newConfig)
        }
        applyBrowserSystemUi()
        updatePictureInPictureParams()
    }

    override fun onPictureInPictureUiStateChanged(pipState: PictureInPictureUiState) {
        super.onPictureInPictureUiStateChanged(pipState)
        if (appDataTransferActive) return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
            pipState.isTransitioningToPip
        ) {
            prepareForPictureInPictureTransition()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        val previousNightConfiguration = appliedNightConfiguration
        super.onConfigurationChanged(newConfig)
        if (appDataTransferActive) return
        appliedNightConfiguration = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (
            previousNightConfiguration != Configuration.UI_MODE_NIGHT_UNDEFINED &&
            previousNightConfiguration != appliedNightConfiguration &&
            ::browserController.isInitialized
        ) {
            browserController.onAppearanceConfigurationChanged()
        }
        applyBrowserSystemUi()
        if (isInPictureInPictureMode && pictureInPictureStartedFullscreen) {
            pictureInPictureSourceRectHint = pictureInPictureSourceRect(
                maximumWindowContentBounds(),
            )
            updatePictureInPictureParams()
        }
    }

    override fun onResume() {
        super.onResume()
        if (appDataTransferActive) return
        reconcilePictureInPictureStateOnResume()
        if (::browserController.isInitialized) browserController.onResume()
        updatePictureInPictureParams()
    }

    private fun reconcilePictureInPictureStateOnResume() {
        if (
            !isInPictureInPictureMode &&
            !pictureInPictureModeEntered &&
            !pictureInPictureReturnInProgress &&
            videoOnlyPresentation
        ) {
            cancelPictureInPictureTransition()
        }
    }

    override fun onDestroy() {
        if (appDataTransferActive) {
            super.onDestroy()
            return
        }
        cancelPictureInPictureReturnLayoutWait()
        if (::castSessionController.isInitialized) castSessionController.release()
        if (::browserController.isInitialized) browserController.destroy()
        if (::webMediaSystemSession.isInitialized) webMediaSystemSession.release()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (!::browserController.isInitialized || appDataTransferActive) {
            super.onSaveInstanceState(outState)
            return
        }
        browserController.activeCapsuleId?.let { outState.putString(STATE_CAPSULE_ID, it) }
        browserController.activeCapsuleTabId?.let { outState.putString(STATE_CAPSULE_TAB_ID, it) }
        super.onSaveInstanceState(outState)
    }

    private fun importUserScript(uri: Uri) {
        lifecycleScope.launch {
            val importResult = withContext(Dispatchers.IO) {
                UserScriptImportReader.read(contentResolver, uri)
            }
            when (importResult) {
                is UserScriptImportResult.Loaded -> {
                    val parsedName = (
                        UserScriptParser.parse(
                            id = "import-preview",
                            source = importResult.source,
                        ) as? UserScriptParseResult.Accepted
                        )?.script?.name
                    browserController.saveUserScript(
                        id = null,
                        source = importResult.source,
                    ) { outcome ->
                        val message = userScriptImportMessage(
                            feedback = UserScriptImportFeedbackRules.from(outcome),
                            importedName = parsedName,
                        )
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
                else -> UserScriptImportFeedbackRules.from(importResult)?.let { feedback ->
                    Toast.makeText(
                        this@MainActivity,
                        userScriptImportMessage(feedback),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    private fun userScriptImportMessage(
        feedback: UserScriptImportFeedback,
        importedName: String? = null,
    ): String = when (feedback) {
        UserScriptImportFeedback.Imported -> getString(
            R.string.userscript_import_success,
            importedName ?: getString(R.string.userscript_title),
        )
        UserScriptImportFeedback.LimitReached -> getString(
            R.string.userscript_error_limit,
            UserScriptParser.MAX_SCRIPTS,
        )
        UserScriptImportFeedback.EmptyFile -> getString(R.string.userscript_import_error_empty)
        UserScriptImportFeedback.FileTooLarge -> getString(
            R.string.userscript_import_error_too_large,
            UserScriptParser.MAX_SOURCE_BYTES / 1_024,
        )
        UserScriptImportFeedback.InvalidUtf8 -> getString(
            R.string.userscript_import_error_invalid_utf8,
        )
        UserScriptImportFeedback.UnreadableFile -> getString(
            R.string.userscript_import_error_unreadable,
        )
        UserScriptImportFeedback.InvalidMetadata -> getString(
            R.string.userscript_import_error_invalid_metadata,
        )
        UserScriptImportFeedback.MissingName -> getString(
            R.string.userscript_import_error_missing_name,
        )
        UserScriptImportFeedback.NameTooLong -> getString(
            R.string.userscript_import_error_name_too_long,
            UserScriptParser.MAX_NAME_CHARS,
        )
        UserScriptImportFeedback.MissingScope -> getString(
            R.string.userscript_import_error_missing_scope,
        )
        UserScriptImportFeedback.TooManyMetadataValues -> getString(
            R.string.userscript_import_error_too_many_metadata_values,
            UserScriptParser.MAX_PATTERNS_PER_KIND,
        )
        UserScriptImportFeedback.InvalidScope -> getString(
            R.string.userscript_import_error_invalid_scope,
        )
        UserScriptImportFeedback.InvalidRunAt -> getString(
            R.string.userscript_import_error_invalid_run_at,
        )
        UserScriptImportFeedback.UnsupportedGrant -> getString(
            R.string.userscript_import_error_unsupported_grant,
        )
        UserScriptImportFeedback.InvalidDependency -> getString(
            R.string.userscript_import_error_invalid_dependency,
        )
        UserScriptImportFeedback.TooManyDependencies -> getString(
            R.string.userscript_import_error_too_many_dependencies,
        )
        UserScriptImportFeedback.DependencyUnavailable -> getString(
            R.string.userscript_import_error_dependency_unavailable,
        )
        UserScriptImportFeedback.DependencyTooLarge -> getString(
            R.string.userscript_import_error_dependency_too_large,
        )
        UserScriptImportFeedback.DependencyInvalidUtf8 -> getString(
            R.string.userscript_import_error_dependency_invalid_utf8,
        )
        UserScriptImportFeedback.DependencyIntegrityMismatch -> getString(
            R.string.userscript_import_error_dependency_integrity,
        )
        UserScriptImportFeedback.SaveFailed -> getString(
            R.string.userscript_import_error_save_failed,
        )
    }

    private fun startAppDataExport(destination: Uri) {
        if (!browserController.canExportAppData()) {
            Toast.makeText(
                this,
                R.string.data_archive_private_tabs_error,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        startAppDataTransfer(
            failureMessage = R.string.data_archive_export_failed,
            canStartFailureMessage = R.string.data_archive_private_tabs_error,
            canStart = browserController::canExportAppData,
        ) { lockToken ->
            AppDataTransferContract.exportIntent(
                context = this,
                destination = destination,
                mainProcessId = Process.myPid(),
                lockToken = lockToken,
            )
        }
    }

    private fun startAppDataTransfer(
        failureMessage: Int,
        canStartFailureMessage: Int = failureMessage,
        canStart: () -> Boolean = { true },
        intent: (String) -> Intent,
    ) {
        val lockToken = AppDataTransferLock.activate(this, Process.myPid())
        if (lockToken == null) {
            Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show()
            return
        }
        appDataTransferActive = true
        val preparing = runCatching {
            browserController.prepareForAppDataTransfer { ready ->
                val canStartNow = ready && canStart()
                val started = canStartNow && runCatching {
                    startActivity(intent(lockToken))
                }.isSuccess
                if (!started) {
                    appDataTransferActive = false
                    AppDataTransferLock.release(this, lockToken)
                    Toast.makeText(
                        this,
                        if (ready && !canStartNow) canStartFailureMessage else failureMessage,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }.isSuccess
        if (!preparing) {
            appDataTransferActive = false
            AppDataTransferLock.release(this, lockToken)
            Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private fun stageAppDataImport(uri: Uri) {
        if (appDataImportLoading) return
        appDataImportLoading = true
        Toast.makeText(this, R.string.data_archive_preparing, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val staged = withContext(Dispatchers.IO) {
                runCatching {
                    val input = checkNotNull(contentResolver.openInputStream(uri))
                    input.use { stream ->
                        AppDataArchiveStaging.stage(stream, appDataArchiveStagingDirectory())
                    }
                }.getOrNull()
            }
            appDataImportLoading = false
            if (staged == null) {
                Toast.makeText(
                    this@MainActivity,
                    R.string.data_archive_import_invalid,
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }
            pendingAppDataImport = AppDataImportPreview(
                staged = staged,
                compatibility = AppDataArchiveRules.compatibility(
                    current = currentAppDataArchiveEnvironment(),
                    archive = staged.inspection.manifest,
                ),
            )
        }
    }

    private fun currentAppDataArchiveEnvironment() = AppDataArchiveEnvironment(
        packageName = packageName,
        appVersionName = BuildConfig.VERSION_NAME,
        appVersionCode = BuildConfig.VERSION_CODE.toLong(),
        webViewVersion = currentWebViewIdentity(),
        sdkInt = Build.VERSION.SDK_INT,
    )

    private fun appDataArchiveStagingDirectory() =
        File(cacheDir, AppDataTransferContract.STAGING_DIRECTORY_NAME)

    private fun deleteStagedAppDataArchive(fileName: String) {
        AppDataArchiveStaging.resolve(appDataArchiveStagingDirectory(), fileName)?.delete()
    }

    private fun showAppDataTransferResult(intent: Intent) {
        val message = when (
            intent.getStringExtra(AppDataTransferContract.RESULT_EXTRA)
        ) {
            AppDataTransferContract.RESULT_EXPORTED -> R.string.data_archive_export_success
            AppDataTransferContract.RESULT_IMPORTED -> R.string.data_archive_import_restored
            AppDataTransferContract.RESULT_EXPORT_FAILED -> R.string.data_archive_export_failed
            AppDataTransferContract.RESULT_IMPORT_FAILED -> R.string.data_archive_import_failed
            AppDataTransferContract.RESULT_IMPORT_RECOVERED ->
                R.string.data_archive_import_recovered
            else -> return
        }
        intent.removeExtra(AppDataTransferContract.RESULT_EXTRA)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun defaultAppDataArchiveFileName(): String =
        "candy-browser-${LocalDate.now().format(DateTimeFormatter.ISO_DATE)}.zip"

    private fun appDataRestoreRecoveryMarker() =
        File(
            appDataTransferStateDirectory(),
            AppDataTransferContract.RESTORE_MARKER_FILE_NAME,
        )

    private fun appDataTransferStateDirectory() =
        File(applicationInfo.dataDir, AppDataArchiveRules.TRANSFER_STATE_DIRECTORY_NAME)

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

    private fun applyAppearanceNightMode(appearanceMode: BrowserAppearanceMode) {
        val nightMode = when (appearanceMode) {
            BrowserAppearanceMode.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            BrowserAppearanceMode.Light -> AppCompatDelegate.MODE_NIGHT_NO
            BrowserAppearanceMode.Dark,
            BrowserAppearanceMode.Amoled,
            -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (delegate.localNightMode != nightMode) delegate.localNightMode = nightMode
    }

    @VisibleForTesting
    fun prepareForPictureInPictureTransitionForTesting() {
        prepareForPictureInPictureTransition()
    }

    @VisibleForTesting
    fun isPictureInPictureEligibleForTesting(): Boolean = canEnterPictureInPicture()

    @VisibleForTesting
    fun pictureInPictureSourceRectHintForTesting(): Rect? =
        appliedPictureInPictureState?.sourceRectHint?.let(::Rect)

    @VisibleForTesting
    fun reconcilePictureInPictureStateOnResumeForTesting() {
        reconcilePictureInPictureStateOnResume()
    }

    @VisibleForTesting
    internal fun setTabOverviewPortraitLocked(locked: Boolean) {
        isTabOverviewPortraitLocked = locked
        applyBrowserSystemUi()
    }

    private fun onFullscreenVideoBoundsChanged(bounds: Rect) {
        if (fullscreenVideoBounds == bounds) return
        fullscreenVideoBounds = Rect(bounds)
        if (isInPictureInPictureMode && pictureInPictureStartedFullscreen) {
            pictureInPictureSourceRectHint = pictureInPictureSourceRect(
                maximumWindowContentBounds(),
            )
        }
        updatePictureInPictureParams()
    }

    private fun prepareForPictureInPictureTransition() {
        if (!::browserController.isInitialized || !canEnterPictureInPicture()) return
        if (!videoOnlyPresentation) {
            pictureInPictureStartedFullscreen = isCurrentWindowFullscreen()
            pictureInPictureSourceRectHint = currentPictureInPictureSourceRect()
        }
        videoOnlyPresentation = true
        browserController.prepareForPictureInPicture()
        updatePictureInPictureParams()
    }

    private fun cancelPictureInPictureTransition() {
        pictureInPictureReturnInProgress = false
        pictureInPictureStartedFullscreen = false
        pictureInPictureModeEntered = false
        cancelPictureInPictureReturnLayoutWait()
        videoOnlyPresentation = false
        pictureInPictureSourceRectHint = null
        browserController.cancelPictureInPictureTransition()
        updatePictureInPictureParams()
    }

    private fun completePictureInPictureReturnAfterLayout(configuration: Configuration) {
        cancelPictureInPictureReturnLayoutWait()
        val decorView = window.decorView
        val density = resources.displayMetrics.density
        val targetWidth = (configuration.screenWidthDp * density).toInt()
        val targetHeight = (configuration.screenHeightDp * density).toInt()
        val tolerance = (PICTURE_IN_PICTURE_RETURN_LAYOUT_TOLERANCE_DP * density).toInt()
        fun isExpandedLayout(width: Int, height: Int): Boolean =
            FullscreenVideoRules.isPictureInPictureReturnLayoutReady(
                width = width,
                height = height,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                tolerance = tolerance,
            )
        if (isExpandedLayout(decorView.width, decorView.height)) {
            finishPictureInPictureReturn()
            return
        }
        val listener = View.OnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            if (!isInPictureInPictureMode && isExpandedLayout(right - left, bottom - top)) {
                cancelPictureInPictureReturnLayoutWait()
                finishPictureInPictureReturn()
            }
        }
        pictureInPictureReturnLayoutListener = listener
        decorView.addOnLayoutChangeListener(listener)
        decorView.postDelayed(
            {
                if (
                    pictureInPictureReturnLayoutListener === listener &&
                    !isInPictureInPictureMode
                ) {
                    cancelPictureInPictureReturnLayoutWait()
                    finishPictureInPictureReturn()
                }
            },
            PICTURE_IN_PICTURE_RETURN_LAYOUT_TIMEOUT_MILLIS,
        )
    }

    private fun cancelPictureInPictureReturnLayoutWait() {
        val listener = pictureInPictureReturnLayoutListener ?: return
        window.decorView.removeOnLayoutChangeListener(listener)
        pictureInPictureReturnLayoutListener = null
    }

    private fun finishPictureInPictureReturn() {
        pictureInPictureReturnInProgress = false
        pictureInPictureStartedFullscreen = false
        pictureInPictureModeEntered = false
        videoOnlyPresentation = false
        pictureInPictureSourceRectHint = null
        browserController.completePictureInPictureReturn()
        applyBrowserSystemUi()
        updatePictureInPictureParams()
    }

    private fun updatePictureInPictureParams() {
        if (!supportsPictureInPicture()) return
        val autoEnterEnabled = canEnterPictureInPicture()
        val sourceRectHint = eligiblePictureInPictureSourceRect(autoEnterEnabled)
        val nextState = AppliedPictureInPictureState(autoEnterEnabled, sourceRectHint)
        if (appliedPictureInPictureState == nextState) return
        appliedPictureInPictureState = nextState
        setPictureInPictureParams(
            buildPictureInPictureParams(autoEnterEnabled, sourceRectHint),
        )
    }

    private fun buildPictureInPictureParams(
        autoEnterEnabled: Boolean,
        sourceRectHint: Rect?,
    ): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(VIDEO_ASPECT_WIDTH, VIDEO_ASPECT_HEIGHT))
            .setAutoEnterEnabled(autoEnterEnabled)
            .setSeamlessResizeEnabled(true)
            .setSourceRectHint(sourceRectHint)
        return builder.build()
    }

    private fun eligiblePictureInPictureSourceRect(autoEnterEnabled: Boolean): Rect? =
        currentPictureInPictureSourceRect()
            ?.takeIf {
                autoEnterEnabled &&
                    !it.isEmpty
            }
            ?.let(::Rect)

    private fun currentPictureInPictureSourceRect(): Rect? {
        pictureInPictureSourceRectHint?.let { return Rect(it) }
        val windowBounds = Rect()
        val visibleBounds = if (
            window.decorView.getGlobalVisibleRect(windowBounds) && !windowBounds.isEmpty
        ) {
            windowBounds
        } else {
            fullscreenVideoBounds ?: return null
        }
        return pictureInPictureSourceRect(visibleBounds)
    }

    private fun pictureInPictureSourceRect(bounds: Rect): Rect? {
        val sourceBounds = FullscreenVideoRules.pictureInPictureSourceBounds(
            windowBounds = FullscreenVideoBounds(
                left = bounds.left,
                top = bounds.top,
                right = bounds.right,
                bottom = bounds.bottom,
            ),
            aspectWidth = VIDEO_ASPECT_WIDTH,
            aspectHeight = VIDEO_ASPECT_HEIGHT,
        ) ?: return null
        return Rect(
            sourceBounds.left,
            sourceBounds.top,
            sourceBounds.right,
            sourceBounds.bottom,
        )
    }

    private fun maximumWindowContentBounds(): Rect {
        val bounds = windowManager.maximumWindowMetrics.bounds
        return Rect(0, 0, bounds.width(), bounds.height())
    }

    private fun isCurrentWindowFullscreen(): Boolean {
        val visibleBounds = Rect()
        if (!window.decorView.getGlobalVisibleRect(visibleBounds) || visibleBounds.isEmpty) {
            return false
        }
        val maximumBounds = windowManager.maximumWindowMetrics.bounds
        val tolerance = (
            PICTURE_IN_PICTURE_RETURN_LAYOUT_TOLERANCE_DP *
                resources.displayMetrics.density
            ).toInt()
        return FullscreenVideoRules.isPictureInPictureReturnLayoutReady(
            width = visibleBounds.width(),
            height = visibleBounds.height(),
            targetWidth = maximumBounds.width(),
            targetHeight = maximumBounds.height(),
            tolerance = tolerance,
        )
    }

    private fun canEnterPictureInPicture(): Boolean =
        supportsPictureInPicture() &&
            ::browserController.isInitialized &&
            browserController.isPictureInPictureEligible

    private fun supportsPictureInPicture(): Boolean = packageManager.hasSystemFeature(
        PackageManager.FEATURE_PICTURE_IN_PICTURE,
    )

    private fun applyBrowserSystemUi() {
        val fullscreenVideoExpanded = ::browserController.isInitialized &&
            browserController.isFullscreenVideoExpanded
        val browserImmersive = ::browserController.isInitialized &&
            browserController.isFullImmersiveModeEnabled
        val state = BrowserWindowStateRules.resolve(
            isWebContentFullscreen = fullscreenVideoExpanded || videoOnlyPresentation,
            isBrowserFullscreen = browserImmersive,
            isTabOverviewPortraitLocked = isTabOverviewPortraitLocked,
            supportsTabOverviewPortraitLock =
                BrowserWindowStateRules.supportsTabOverviewPortraitLock(
                    resources.configuration.smallestScreenWidthDp,
                ),
        )
        applyFullImmersiveMode(state.isImmersive)
        val orientation = when (state.requestedOrientation) {
            BrowserRequestedOrientation.Sensor -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            BrowserRequestedOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            BrowserRequestedOrientation.Unspecified -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (requestedOrientation != orientation) requestedOrientation = orientation
    }

    private companion object {
        const val SPLASH_DURATION_MILLIS = 1_050L
        const val STATE_CAPSULE_ID = "active_site_capsule_id"
        const val STATE_CAPSULE_TAB_ID = "active_site_capsule_tab_id"
        const val VIDEO_ASPECT_WIDTH = 16
        const val VIDEO_ASPECT_HEIGHT = 9
        const val PICTURE_IN_PICTURE_RETURN_LAYOUT_TOLERANCE_DP = 8
        const val PICTURE_IN_PICTURE_RETURN_LAYOUT_TIMEOUT_MILLIS = 3_000L
    }
}

private data class AppliedPictureInPictureState(
    val autoEnterEnabled: Boolean,
    val sourceRectHint: Rect?,
)

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
