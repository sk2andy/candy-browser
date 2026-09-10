package dev.sk2andy.materialbrowser

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureUiState
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.InputDevice
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import dev.sk2andy.materialbrowser.browser.BrowserActivityResultIdentity
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserHardwareInputAction
import dev.sk2andy.materialbrowser.browser.BrowserHardwareInputRules
import dev.sk2andy.materialbrowser.browser.BrowserHardwareKey
import dev.sk2andy.materialbrowser.browser.BrowserHardwareKeyStroke
import dev.sk2andy.materialbrowser.browser.BrowserInputDiagnostics
import dev.sk2andy.materialbrowser.browser.BrowserMouseButton
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.FullscreenVideoRules
import dev.sk2andy.materialbrowser.browser.ReleaseNotesPresentationRules
import dev.sk2andy.materialbrowser.browser.StartupPresentationRules
import dev.sk2andy.materialbrowser.browser.BrowserMediaSystemSession
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.cast.CastSessionController
import dev.sk2andy.materialbrowser.browser.cast.CastUiState
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionManagementContext
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionManagerCoordinator
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionManagerPresentation
import dev.sk2andy.materialbrowser.browser.gecko.GeckoRuntimeOwner
import dev.sk2andy.materialbrowser.browser.gecko.GeckoWebAuthnActivityDelegate
import dev.sk2andy.materialbrowser.browser.engine.BrowserEngineProcessRestart
import dev.sk2andy.materialbrowser.browser.integration.CandySearchWidgetRules
import dev.sk2andy.materialbrowser.browser.integration.FavoritesActivityContract
import dev.sk2andy.materialbrowser.browser.integration.HistoryActivityContract
import dev.sk2andy.materialbrowser.browser.integration.IncomingBrowserIntent
import dev.sk2andy.materialbrowser.browser.integration.LauncherShortcutPublisher
import dev.sk2andy.materialbrowser.browser.integration.LauncherShortcutRules
import dev.sk2andy.materialbrowser.capsule.CapsuleIntentRules
import dev.sk2andy.materialbrowser.capsule.CapsuleLaunchResolution
import dev.sk2andy.materialbrowser.data.AppDataArchiveEnvironment
import dev.sk2andy.materialbrowser.data.AppDataArchiveRules
import dev.sk2andy.materialbrowser.data.AppDataArchiveRestore
import dev.sk2andy.materialbrowser.data.AppDataArchiveStaging
import dev.sk2andy.materialbrowser.data.AppDataTransferLock
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesContent
import dev.sk2andy.materialbrowser.data.ReleaseNotesRepository
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import dev.sk2andy.materialbrowser.data.SnoozeWakeNotifier
import dev.sk2andy.materialbrowser.ui.AppDataExportWarningDialog
import dev.sk2andy.materialbrowser.ui.AppDataImportConfirmationDialog
import dev.sk2andy.materialbrowser.ui.AppDataImportPreview
import dev.sk2andy.materialbrowser.ui.BrowserScreen
import dev.sk2andy.materialbrowser.ui.CandySplashScreen
import dev.sk2andy.materialbrowser.ui.FirefoxExtensionManagerOverlay
import dev.sk2andy.materialbrowser.ui.FullscreenVideoOverlay
import dev.sk2andy.materialbrowser.ui.GestureOnboardingScreen
import dev.sk2andy.materialbrowser.ui.ReleaseNotesScreen
import dev.sk2andy.materialbrowser.ui.theme.CandyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {
    private lateinit var browserController: BrowserController
    private lateinit var browserMediaSystemSession: BrowserMediaSystemSession
    private lateinit var castSessionController: CastSessionController
    private lateinit var releaseNotesStore: ReleaseNotesStore
    private lateinit var pictureInPictureController: MainActivityPictureInPictureController
    private lateinit var userScriptImporter: UserScriptImporter
    private lateinit var launcherShortcutIntentHandler: LauncherShortcutIntentHandler
    private lateinit var geckoWebAuthnActivityDelegate: GeckoWebAuthnActivityDelegate
    private val launcherShortcutPublisher by lazy {
        LauncherShortcutPublisher(applicationContext)
    }
    private var releaseNotesContent: ReleaseNotesContent? = null
    private var videoOnlyPresentation by mutableStateOf(false)
    private var isTabOverviewPortraitLocked = false
    private var incomingBrowserNavigationRequestId by mutableIntStateOf(0)
    private var launcherAddressEditorRequestId by mutableIntStateOf(0)
    private var hardwareTabChangeRequestId by mutableIntStateOf(0)
    private var onboardingVisible by mutableStateOf(false)
    private var initialOnboardingRequired = false
    private var releaseNotesVisible by mutableStateOf(false)
    private var externalLaunchTabId by mutableStateOf<String?>(null)
    private var appDataExportWarningVisible by mutableStateOf(false)
    private var pendingAppDataImport by mutableStateOf<AppDataImportPreview?>(null)
    private var firefoxExtensionsVisible by mutableStateOf(false)
    private var firefoxExtensionManager: GeckoExtensionManagerCoordinator? = null
    private var appDataImportLoading = false
    private var appDataTransferActive = false
    private val consumedHardwareShortcutKeys = mutableSetOf<Int>()
    private val replayedHardwareInputKeys = mutableSetOf<Int>()
    private val consumedMouseNavigationButtons = mutableSetOf<MouseNavigationButtonToken>()
    private var lastMouseNavigationFingerprint: MouseNavigationFingerprint? = null
    private var geckoWebAuthnActivityIdentity: BrowserActivityResultIdentity? = null
    private var activityDestroyed = false
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
    private val geckoWebAuthnLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (::geckoWebAuthnActivityDelegate.isInitialized) {
            geckoWebAuthnActivityDelegate.onActivityResult(result.resultCode, result.data)
        }
    }
    private val userScriptImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null && ::userScriptImporter.isInitialized) userScriptImporter.import(uri)
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
    private val historyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (!::browserController.isInitialized) return@registerForActivityResult
        browserController.reloadHistory()
        browserController.applyHistoryClearRequests(
            HistoryActivityContract.clearRequestsFrom(result.data),
        )
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        HistoryActivityContract.navigationRequestFrom(result.data)?.let { request ->
            if (browserController.openHistoryEntry(request.url, request.profileId)) {
                incomingBrowserNavigationRequestId++
            }
        }
    }
    private val favoritesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (!::browserController.isInitialized) return@registerForActivityResult
        browserController.reloadFavorites()
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        FavoritesActivityContract.navigationUrlFrom(result.data)?.let { url ->
            if (browserController.openFavorite(url)) incomingBrowserNavigationRequestId++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        BrowsingHistoryLifecycle.install(application)
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
        val isColdStart = savedInstanceState == null
        val hasIncomingBrowserRequest = IncomingBrowserIntent.from(intent) != null
        val isColdExternalLinkLaunch = isColdStart && hasIncomingBrowserRequest
        val onboardingStore = GestureOnboardingStore(this)
        val hadCompletedOnboarding = onboardingStore.hasCompletedAnyVersion()
        val onboardingRequired = onboardingStore.shouldShow()
        initialOnboardingRequired = onboardingRequired && !hadCompletedOnboarding
        onboardingVisible = onboardingRequired
        releaseNotesStore = ReleaseNotesStore(this)
        if (
            intent.action == Intent.ACTION_MAIN ||
            savedInstanceState?.getBoolean(STATE_RELEASE_NOTES_VISIBLE) == true
        ) {
            loadReleaseNotesContent()
        }
        val releaseNotesRequired = shouldPresentReleaseNotes(
            isNewLaunch = savedInstanceState == null,
            intentAction = intent.action,
            isInitialOnboardingRequired = initialOnboardingRequired,
        )
        releaseNotesVisible = savedInstanceState
            ?.getBoolean(STATE_RELEASE_NOTES_VISIBLE)
            ?: releaseNotesRequired
        val snoozeWakeNotifier = SnoozeWakeNotifier(this).also { it.ensureChannel() }
        val browserEngineKind = BrowserSessionStore(this).loadAndroidBrowserEngineKind()
        if (browserEngineKind == AndroidBrowserEngineKind.GeckoView) {
            geckoWebAuthnActivityDelegate = GeckoWebAuthnActivityDelegate(
                launch = { pendingIntent ->
                    geckoWebAuthnLauncher.launch(IntentSenderRequest.Builder(pendingIntent).build())
                },
                onPendingChanged = { pending ->
                    geckoWebAuthnActivityIdentity = if (
                        pending && ::browserController.isInitialized
                    ) {
                        browserController.selectedActivityResultIdentity()
                    } else {
                        null
                    }
                },
                isPendingRequestCurrent = {
                    ::browserController.isInitialized &&
                        geckoWebAuthnActivityIdentity
                            ?.let(browserController::isActivityResultIdentityCurrent) == true
                },
            )
            GeckoRuntimeOwner.bindWebAuthnActivityDelegate(
                context = applicationContext,
                delegate = geckoWebAuthnActivityDelegate,
            )
        }
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
            onMediaStateChanged = {
                if (!activityDestroyed) {
                    ensureMediaControllers()
                    if (::browserMediaSystemSession.isInitialized) {
                        browserMediaSystemSession.publish(browserController.systemMediaState)
                    }
                    if (::castSessionController.isInitialized) {
                        castSessionController.updateCandidate(browserController.castMediaCandidate)
                    }
                    updatePictureInPictureParams()
                }
            },
            onBrowserEngineChangeRequested = {
                BrowserEngineProcessRestart.restart(this)
            },
        )
        pictureInPictureController = MainActivityPictureInPictureController(
            activity = this,
            browserController = browserController,
            isVideoOnlyPresentation = { videoOnlyPresentation },
            setVideoOnlyPresentation = { videoOnlyPresentation = it },
            applyBrowserSystemUi = ::applyBrowserSystemUi,
        )
        userScriptImporter = UserScriptImporter(
            context = this,
            lifecycleScope = lifecycleScope,
            browserController = browserController,
        )
        launcherShortcutIntentHandler = LauncherShortcutIntentHandler(
            context = this,
            browserController = browserController,
            publisher = launcherShortcutPublisher,
            onNavigationRequested = { incomingBrowserNavigationRequestId++ },
            onAddressEditorRequested = { launcherAddressEditorRequestId++ },
        )
        if (!isColdExternalLinkLaunch) ensureMediaControllers()
        applyBrowserSystemUi()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            browserController.onWindowInsetsChanged(insets)
            insets
        }
        val restoredCapsuleId = savedInstanceState?.getString(STATE_CAPSULE_ID)
        val restoreExternalLinkPreview = savedInstanceState
            ?.getBoolean(STATE_EXTERNAL_LINK_PREVIEW_ACTIVE)
            ?: false
        externalLaunchTabId = savedInstanceState
            ?.getString(STATE_EXTERNAL_LAUNCH_TAB_ID)
            ?.takeIf { tabId -> browserController.tabs.any { it.id == tabId } }
        if (restoredCapsuleId != null) {
            val restoredTabId = savedInstanceState.getString(STATE_CAPSULE_TAB_ID)
            if (!browserController.restoreSiteCapsule(restoredCapsuleId, restoredTabId)) {
                browserController.openNormalHomeFromInvalidCapsule()
            }
        } else if (savedInstanceState == null) {
            openIntent(intent)
            openHomePageForLauncherLaunch(intent)
        } else if (restoreExternalLinkPreview) {
            IncomingBrowserIntent.from(intent)?.let { request ->
                if (
                    browserController.isExternalLinkPreviewEnabled &&
                    browserController.openExternalLinkPreview(
                        url = request.url,
                        restoredAppHandoffExpirationElapsedRealtime = savedInstanceState
                            .getLong(STATE_EXTERNAL_LINK_PREVIEW_APP_HANDOFF_EXPIRATION)
                            .takeIf {
                                savedInstanceState.containsKey(
                                    STATE_EXTERNAL_LINK_PREVIEW_APP_HANDOFF_EXPIRATION,
                                )
                            },
                    )
                ) {
                    incomingBrowserNavigationRequestId++
                }
            }
        }
        val startupPresentation = StartupPresentationRules.resolve(
            isColdStart = savedInstanceState == null,
            isLauncherLaunch = intent.action == Intent.ACTION_MAIN,
            isStartupAnimationEnabled = browserController.isStartupAnimationEnabled,
            isOnboardingRequired = onboardingRequired,
            isReleaseNotesRequired = releaseNotesRequired,
        )
        setContent {
            val appearanceSettings = browserController.appearanceSettings
            val appearanceDark = appearanceSettings.usesDarkColors(
                isSystemInDarkTheme(),
            )
            SideEffect {
                applyAppearanceNightMode(appearanceSettings.appearanceMode)
                applyAppearanceSystemBars(appearanceDark)
            }
            CandyTheme(settings = appearanceSettings) {
                val launcherShortcutState = LauncherShortcutRules.state(
                    profiles = browserController.localBrowserProfiles,
                    tabs = browserController.tabs.toList(),
                    activeProfileId = browserController.activeProfileId,
                    profilesEnabled = browserController.profilesEnabled,
                )
                val candySearchWidgetState = CandySearchWidgetRules.state(
                    profiles = browserController.localBrowserProfiles,
                    profilesEnabled = browserController.profilesEnabled,
                )
                var splashVisible by remember {
                    mutableStateOf(startupPresentation.showSplash)
                }
                val fullscreenVideoState = browserController.fullscreenVideoState
                val selectedTabId = browserController.selectedTabId
                val webViewVideoOnlyPresentation = videoOnlyPresentation &&
                    fullscreenVideoState?.let { state ->
                        !FullscreenVideoRules.hostsSourceInOverlay(
                            host = state.host,
                        )
                    } == true
                val showReleaseNotes = releaseNotesVisible &&
                    releaseNotesContent != null &&
                    !onboardingVisible &&
                    !splashVisible &&
                    !videoOnlyPresentation
                LaunchedEffect(Unit) {
                    if (splashVisible) {
                        delay(SPLASH_DURATION_MILLIS)
                        splashVisible = false
                    }
                }
                LaunchedEffect(launcherShortcutState) {
                    launcherShortcutPublisher.publishSerially(launcherShortcutState)
                }
                LaunchedEffect(candySearchWidgetState) {
                    CandySearchWidgetProvider.updateAll(
                        context = applicationContext,
                        state = candySearchWidgetState,
                    )
                }
                LaunchedEffect(showReleaseNotes) {
                    if (showReleaseNotes) {
                        releaseNotesStore.markHandled(BuildConfig.VERSION_CODE.toLong())
                    }
                }
                LaunchedEffect(
                    fullscreenVideoState,
                    browserController.systemMediaState,
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
                    val castController = if (::castSessionController.isInitialized) {
                        castSessionController
                    } else {
                        null
                    }
                    BrowserScreen(
                        controller = browserController,
                        castUiState = castController?.state ?: CastUiState(),
                        onToggleCastPlayback = { castController?.togglePlayback() },
                        onSeekCast = { positionMillis -> castController?.seekTo(positionMillis) },
                        onCastVolumeChange = { volume -> castController?.setDeviceVolume(volume) },
                        onDisconnectCast = { castController?.disconnect() },
                        webViewVideoOnlyPresentation = webViewVideoOnlyPresentation,
                        videoOnlyPresentation = videoOnlyPresentation,
                        incomingBrowserNavigationRequestId =
                            incomingBrowserNavigationRequestId,
                        externalLaunchTabId = externalLaunchTabId,
                        onReturnToExternalApp = {
                            browserController.dismissExternalLinkPreview()
                            externalLaunchTabId = null
                            moveTaskToBack(true)
                        },
                        onExternalPreviewCommitted = { tabId ->
                            externalLaunchTabId = tabId
                        },
                        onTabOverviewPortraitLockChanged = ::setTabOverviewPortraitLocked,
                        onOpenFavorites = {
                            favoritesLauncher.launch(
                                FavoritesActivityContract.launchIntent(this@MainActivity),
                            )
                        },
                        onOpenDownloads = {
                            startActivity(Intent(this@MainActivity, DownloadsActivity::class.java))
                        },
                        onOpenHistory = {
                            historyLauncher.launch(
                                HistoryActivityContract.launchIntent(this@MainActivity),
                            )
                        },
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
                        onOpenFirefoxExtensions = if (
                            browserController.usesGeckoEngine &&
                            !browserController.selectedTab.isIncognito
                        ) {
                            {
                                openFirefoxExtensions(
                                    GeckoExtensionManagerPresentation.Options,
                                )
                            }
                        } else {
                            null
                        },
                        onManageFirefoxExtensions = if (browserController.usesGeckoEngine) {
                            {
                                openFirefoxExtensions(
                                    GeckoExtensionManagerPresentation.Management,
                                )
                            }
                        } else {
                            null
                        },
                        openAddressEditorOnLaunch = startupPresentation.openAddressEditor,
                        launcherAddressEditorRequestId = launcherAddressEditorRequestId,
                        hardwareTabChangeRequestId = hardwareTabChangeRequestId,
                    )
                    if (firefoxExtensionsVisible) {
                        firefoxExtensionManager?.let { manager ->
                            FirefoxExtensionManagerOverlay(
                                state = manager.state,
                                onInstall = manager::install,
                                onSetEnabled = manager::setEnabled,
                                onSetPrivate = manager::setAllowedInPrivateBrowsing,
                                onUpdate = manager::update,
                                onUninstall = manager::uninstall,
                                onOpenOptionsPage = { extension ->
                                    manager.openOptionsPage(extension.id) {
                                        firefoxExtensionsVisible = false
                                    }
                                },
                                onPermissionDecision = manager::completePermissionRequest,
                                onDismiss = {
                                    manager.dismiss()
                                    firefoxExtensionsVisible = false
                                },
                            )
                        }
                    }
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
                                if (initialOnboardingRequired) {
                                    releaseNotesStore.markHandled(
                                        BuildConfig.VERSION_CODE.toLong(),
                                    )
                                    releaseNotesVisible = false
                                }
                                initialOnboardingRequired = false
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
                    releaseNotesContent?.takeIf { showReleaseNotes }?.let { content ->
                        ReleaseNotesScreen(
                            versionName = content.versionName,
                            document = content.document,
                            onDone = { releaseNotesVisible = false },
                            onOpenLink = { url ->
                                if (browserController.openUrl(url, inNewTab = true)) {
                                    releaseNotesVisible = false
                                }
                            },
                        )
                    }
                }
                AppUpdatePrompt(
                    context = this,
                    visible = !onboardingVisible &&
                        !releaseNotesVisible &&
                        !splashVisible &&
                        !videoOnlyPresentation,
                )
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
        openHomePageForLauncherLaunch(intent)
        if (intent.action == Intent.ACTION_MAIN) loadReleaseNotesContent()
        if (
            shouldPresentReleaseNotes(
                isNewLaunch = true,
                intentAction = intent.action,
                isInitialOnboardingRequired = initialOnboardingRequired,
            )
        ) {
            releaseNotesVisible = true
        }
        if (
            StartupPresentationRules.shouldOpenAddressEditor(
                isLauncherLaunch = intent.action == Intent.ACTION_MAIN,
                isStartupAnimationEnabled = browserController.isStartupAnimationEnabled,
                isOnboardingRequired = onboardingVisible,
                isReleaseNotesRequired = releaseNotesVisible,
            )
        ) {
            launcherAddressEditorRequestId++
        }
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (appDataTransferActive) return true
        if (event.action == KeyEvent.ACTION_UP && replayedHardwareInputKeys.remove(event.keyCode)) {
            return true
        }
        if (
            event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount > 0 &&
            event.keyCode in replayedHardwareInputKeys
        ) {
            return true
        }
        if (event.action == KeyEvent.ACTION_UP && consumedHardwareShortcutKeys.remove(event.keyCode)) {
            return true
        }
        if (
            event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount > 0 &&
            event.keyCode in consumedHardwareShortcutKeys
        ) {
            return true
        }
        val mouseNavigationAction = event
            .takeIf { keyEvent ->
                keyEvent.action == KeyEvent.ACTION_DOWN &&
                    keyEvent.repeatCount == 0 &&
                    keyEvent.isFromSource(InputDevice.SOURCE_MOUSE)
            }
            ?.toBrowserMouseNavigationAction()
        if (mouseNavigationAction != null && isBrowserHardwareInputAvailable()) {
            consumedHardwareShortcutKeys += event.keyCode
            performMouseNavigationOnce(
                action = mouseNavigationAction,
                deviceId = event.deviceId,
                eventTime = event.eventTime,
            )
            return true
        }
        val action = event
            .takeIf { keyEvent -> keyEvent.action == KeyEvent.ACTION_DOWN }
            ?.toBrowserHardwareKeyStroke()
            ?.let(BrowserHardwareInputRules::keyboardAction)
        if (action != null && isBrowserHardwareInputAvailable()) {
            consumedHardwareShortcutKeys += event.keyCode
            performBrowserHardwareInput(action)
            return true
        }
        if (
            event.action == KeyEvent.ACTION_DOWN &&
            event.isFromSource(InputDevice.SOURCE_KEYBOARD) &&
            currentFocus?.onCheckIsTextEditor() != true
        ) {
            if (requestBrowserEngineFocusForHardwareInput()) {
                if (browserController.replayFirstKeyStrokeToSelectedBrowserEngine(event)) {
                    replayedHardwareInputKeys += event.keyCode
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (appDataTransferActive) return true
        if (event.actionMasked == MotionEvent.ACTION_BUTTON_RELEASE) {
            val releasedButton = event.toBrowserMouseButton()
            val removed = if (releasedButton == BrowserMouseButton.Other) {
                consumedMouseNavigationButtons.removeAll { token ->
                    token.deviceId == event.deviceId
                }
            } else {
                consumedMouseNavigationButtons.remove(
                    MouseNavigationButtonToken(event.deviceId, releasedButton),
                )
            }
            if (removed) return true
        }
        val action = event
            .takeIf { motionEvent ->
                motionEvent.actionMasked == MotionEvent.ACTION_BUTTON_PRESS &&
                    motionEvent.isFromSource(InputDevice.SOURCE_CLASS_POINTER)
            }
            ?.toBrowserMouseButton()
            ?.let(BrowserHardwareInputRules::mouseAction)
        if (action != null && isBrowserHardwareInputAvailable()) {
            consumedMouseNavigationButtons += MouseNavigationButtonToken(
                deviceId = event.deviceId,
                button = event.toBrowserMouseButton(),
            )
            performMouseNavigationOnce(
                action = action,
                deviceId = event.deviceId,
                eventTime = event.eventTime,
            )
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            event.unclassifiedVerticalScroll()?.let { scrollUnits ->
                if (isBrowserHardwareInputAvailable()) {
                    requestBrowserEngineFocusForHardwareInput()
                    val mouseWheelEvent = event.asMouseWheelEvent(scrollUnits)
                    try {
                        if (
                            browserController.dispatchGenericMotionEventToSelectedBrowserEngine(
                                mouseWheelEvent,
                            )
                        ) {
                            return true
                        }
                    } finally {
                        mouseWheelEvent.recycle()
                    }
                    val deltaPx = (-scrollUnits *
                        ViewConfiguration.get(this).scaledVerticalScrollFactor).toInt()
                    if (
                        deltaPx != 0 &&
                        browserController.scrollSelectedBrowserEngineByVerticalOffset(deltaPx)
                    ) {
                        return true
                    }
                }
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onProvideKeyboardShortcuts(
        data: MutableList<KeyboardShortcutGroup>,
        menu: Menu?,
        deviceId: Int,
    ) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        data += KeyboardShortcutGroup(
            getString(R.string.app_name),
            listOf(
                KeyboardShortcutInfo(
                    getString(R.string.cd_open_search),
                    'L',
                    KeyEvent.META_CTRL_ON,
                ),
                KeyboardShortcutInfo(
                    getString(R.string.cd_new_tab),
                    'T',
                    KeyEvent.META_CTRL_ON,
                ),
                KeyboardShortcutInfo(
                    getString(R.string.cd_close_tab),
                    'W',
                    KeyEvent.META_CTRL_ON,
                ),
                KeyboardShortcutInfo(
                    getString(R.string.action_reload),
                    'R',
                    KeyEvent.META_CTRL_ON,
                ),
                KeyboardShortcutInfo(
                    getString(R.string.action_reload),
                    KeyEvent.KEYCODE_F5,
                    0,
                ),
                KeyboardShortcutInfo(
                    getString(R.string.action_find_in_page),
                    'F',
                    KeyEvent.META_CTRL_ON,
                ),
                KeyboardShortcutInfo(
                    getString(R.string.action_switch_to_tab),
                    KeyEvent.KEYCODE_TAB,
                    KeyEvent.META_CTRL_ON,
                ),
                KeyboardShortcutInfo(
                    getString(R.string.action_switch_to_tab),
                    KeyEvent.KEYCODE_TAB,
                    KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON,
                ),
                KeyboardShortcutInfo(
                    getString(R.string.action_back),
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.META_ALT_ON,
                ),
                KeyboardShortcutInfo(
                    getString(R.string.action_forward),
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.META_ALT_ON,
                ),
            ),
        )
    }

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
        if (::geckoWebAuthnActivityDelegate.isInitialized) {
            geckoWebAuthnActivityDelegate.onHostPaused()
        }
        browserController.onPause()
        super.onPause()
    }

    override fun onUserLeaveHint() {
        if (!appDataTransferActive) {
            browserController.dismissExternalLinkPreview()
            if (
                ::pictureInPictureController.isInitialized &&
                pictureInPictureController.requestPictureInPicture()
            ) {
                return
            }
            prepareForPictureInPictureTransition()
        }
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
            browserController.onStop(
                isInPictureInPictureMode = isInPictureInPictureMode,
                protectedTabIds = setOfNotNull(geckoWebAuthnActivityIdentity?.tabId),
            )
        }
        super.onStop()
    }

    override fun onPictureInPictureRequested(): Boolean {
        if (appDataTransferActive || !::pictureInPictureController.isInitialized) return false
        return pictureInPictureController.requestPictureInPicture()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (appDataTransferActive || !::pictureInPictureController.isInitialized) return
        pictureInPictureController.onModeChanged(isInPictureInPictureMode, newConfig)
    }

    override fun onPictureInPictureUiStateChanged(pipState: PictureInPictureUiState) {
        super.onPictureInPictureUiStateChanged(pipState)
        if (appDataTransferActive || !::pictureInPictureController.isInitialized) return
        pictureInPictureController.onUiStateChanged(pipState)
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
        if (::pictureInPictureController.isInitialized) {
            pictureInPictureController.onConfigurationChanged()
        }
    }

    override fun onResume() {
        super.onResume()
        if (appDataTransferActive) return
        if (::pictureInPictureController.isInitialized) {
            pictureInPictureController.reconcileStateOnResume()
        }
        if (::browserController.isInitialized) browserController.onResume()
        if (::geckoWebAuthnActivityDelegate.isInitialized) {
            geckoWebAuthnActivityDelegate.onHostResumed()
        }
        updatePictureInPictureParams()
    }

    override fun onDestroy() {
        activityDestroyed = true
        firefoxExtensionManager?.close()
        firefoxExtensionManager = null
        if (::geckoWebAuthnActivityDelegate.isInitialized) {
            GeckoRuntimeOwner.unbindWebAuthnActivityDelegate(geckoWebAuthnActivityDelegate)
            geckoWebAuthnActivityDelegate.close()
        }
        if (appDataTransferActive) {
            super.onDestroy()
            return
        }
        if (::pictureInPictureController.isInitialized) pictureInPictureController.onDestroy()
        if (::castSessionController.isInitialized) castSessionController.release()
        if (::browserController.isInitialized) browserController.destroy()
        if (::browserMediaSystemSession.isInitialized) browserMediaSystemSession.release()
        super.onDestroy()
    }

    private fun openFirefoxExtensions(presentation: GeckoExtensionManagerPresentation) {
        if (!::browserController.isInitialized) return
        val selectedTab = browserController.selectedTab
        val manager = firefoxExtensionManager ?: GeckoExtensionManagerCoordinator.create(
            context = applicationContext,
            scope = lifecycleScope,
            onPageRuntimeChanged = browserController::reloadSelectedPageAfterExtensionChange,
            onOpenOptionsPage = browserController::openSelectedFirefoxExtensionOptionsPage,
        ).also { created -> firefoxExtensionManager = created }
        manager.open(
            GeckoExtensionManagementContext(
                profileId = selectedTab.profileId,
                isPrivate = selectedTab.isIncognito,
            ),
            presentation = presentation,
        )
        firefoxExtensionsVisible = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (!::browserController.isInitialized || appDataTransferActive) {
            super.onSaveInstanceState(outState)
            return
        }
        browserController.activeCapsuleId?.let { outState.putString(STATE_CAPSULE_ID, it) }
        browserController.activeCapsuleTabId?.let { outState.putString(STATE_CAPSULE_TAB_ID, it) }
        outState.putBoolean(
            STATE_EXTERNAL_LINK_PREVIEW_ACTIVE,
            browserController.externalLinkPreviewState != null,
        )
        browserController.externalLinkPreviewState
            ?.appHandoffExpiresAtElapsedRealtime
            ?.let { expiration ->
                outState.putLong(
                    STATE_EXTERNAL_LINK_PREVIEW_APP_HANDOFF_EXPIRATION,
                    expiration,
                )
            }
        externalLaunchTabId?.let { outState.putString(STATE_EXTERNAL_LAUNCH_TAB_ID, it) }
        outState.putBoolean(STATE_RELEASE_NOTES_VISIBLE, releaseNotesVisible)
        super.onSaveInstanceState(outState)
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
        webViewVersion = currentBrowserEngineIdentity(),
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
        if (
            intent.action == LauncherShortcutRules.ACTION_OPEN_APP &&
            launcherShortcutIntentHandler.open(intent)
        ) return
        externalLaunchTabId = null
        val incomingRequest = IncomingBrowserIntent.from(intent)
        if (incomingRequest == null) browserController.dismissExternalLinkPreview()
        if (launcherShortcutIntentHandler.open(intent)) return
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
        incomingRequest?.let { request ->
            if (
                intent.action == Intent.ACTION_VIEW &&
                browserController.openIncomingAppLink(request.url)
            ) {
                incomingBrowserNavigationRequestId++
                return
            }
            if (
                browserController.isExternalLinkPreviewEnabled &&
                browserController.openExternalLinkPreview(
                    url = request.url,
                    allowInitialAppHandoff = true,
                )
            ) {
                incomingBrowserNavigationRequestId++
                return
            }
            browserController.dismissExternalLinkPreview()
            if (
                !browserController.openUrl(
                    url = request.url,
                    inNewTab = true,
                    authorizeInitialExternalNavigation = true,
                )
            ) return
            externalLaunchTabId = browserController.selectedTabId
            incomingBrowserNavigationRequestId++
        }
    }

    private fun openHomePageForLauncherLaunch(intent: Intent) {
        if (
            StartupPresentationRules.shouldOpenHomePage(
                isLauncherLaunch = intent.action == Intent.ACTION_MAIN,
                isOpenHomeOnStartupEnabled = browserController.isOpenHomeOnStartupEnabled,
            )
        ) {
            browserController.openNormalHome()
        }
    }

    private fun performBrowserHardwareInput(action: BrowserHardwareInputAction): Boolean {
        val externalPreview = browserController.externalLinkPreviewState
        return when (action) {
            BrowserHardwareInputAction.FocusAddress -> {
                if (externalPreview != null) browserController.dismissExternalLinkPreview()
                if (browserController.activeSiteCapsule != null) browserController.leaveSiteCapsule()
                launcherAddressEditorRequestId++
                true
            }
            BrowserHardwareInputAction.NewTab -> {
                if (externalPreview != null) browserController.dismissExternalLinkPreview()
                val previousTabId = browserController.selectedTabId
                browserController.createTab()
                if (browserController.selectedTabId != previousTabId) {
                    launcherAddressEditorRequestId++
                }
                true
            }
            BrowserHardwareInputAction.CloseTab -> {
                if (externalPreview != null) {
                    false
                } else {
                    val previousTabId = browserController.selectedTabId
                    browserController.closeTab(browserController.selectedTabId)
                    if (browserController.selectedTabId != previousTabId) {
                        hardwareTabChangeRequestId++
                    }
                    true
                }
            }
            BrowserHardwareInputAction.Reload -> {
                if (externalPreview != null) {
                    false
                } else {
                    if (browserController.selectedTab.url != BLANK_URL) {
                        browserController.reload()
                    }
                    true
                }
            }
            BrowserHardwareInputAction.FindInPage -> when {
                externalPreview != null -> browserController.openExternalLinkPreviewFindInPage(
                    externalPreview.sessionId,
                )
                browserController.activeSiteCapsule != null -> false
                else -> browserController.openFindInPage()
            }
            BrowserHardwareInputAction.PreviousTab -> {
                val changed = externalPreview == null &&
                    browserController.selectAdjacentTab(forward = false)
                if (changed) hardwareTabChangeRequestId++
                changed
            }
            BrowserHardwareInputAction.NextTab -> {
                val changed = externalPreview == null &&
                    browserController.selectAdjacentTab(forward = true)
                if (changed) hardwareTabChangeRequestId++
                changed
            }
            BrowserHardwareInputAction.GoBack -> when {
                externalPreview != null -> browserController.goBackInExternalLinkPreview(
                    externalPreview.sessionId,
                )
                browserController.selectedTab.canGoBack -> {
                    browserController.goBack()
                    true
                }
                else -> false
            }
            BrowserHardwareInputAction.GoForward -> {
                if (externalPreview == null && browserController.selectedTab.canGoForward) {
                    browserController.goForward()
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun performMouseNavigationOnce(
        action: BrowserHardwareInputAction,
        deviceId: Int,
        eventTime: Long,
    ) {
        val previous = lastMouseNavigationFingerprint
        val duplicate = previous?.action == action &&
            previous.deviceId == deviceId &&
            eventTime - previous.eventTime in 0..MOUSE_NAVIGATION_DUPLICATE_WINDOW_MILLIS
        lastMouseNavigationFingerprint = MouseNavigationFingerprint(
            action = action,
            deviceId = deviceId,
            eventTime = eventTime,
        )
        if (!duplicate) performBrowserHardwareInput(action)
    }

    private fun isBrowserHardwareInputAvailable(): Boolean =
        ::browserController.isInitialized &&
            !onboardingVisible &&
            !releaseNotesVisible &&
            !firefoxExtensionsVisible &&
            !appDataExportWarningVisible &&
            pendingAppDataImport == null

    private fun requestBrowserEngineFocusForHardwareInput(): Boolean =
        isBrowserHardwareInputAvailable() && browserController.requestSelectedBrowserEngineFocus()

    @VisibleForTesting
    fun browserControllerForTesting(): BrowserController = browserController

    @VisibleForTesting
    fun prepareForPictureInPictureTransitionForTesting() {
        pictureInPictureController.prepareForTransition()
    }

    @VisibleForTesting
    fun isPictureInPictureEligibleForTesting(): Boolean = pictureInPictureController.isEligible()

    @VisibleForTesting
    fun pictureInPictureSourceRectHintForTesting(): Rect? =
        pictureInPictureController.appliedSourceRectHint()

    @VisibleForTesting
    fun reconcilePictureInPictureStateOnResumeForTesting() {
        pictureInPictureController.reconcileStateOnResume()
    }

    @VisibleForTesting
    internal fun setTabOverviewPortraitLocked(locked: Boolean) {
        isTabOverviewPortraitLocked = locked
        applyBrowserSystemUi()
    }

    @VisibleForTesting
    internal fun setReleaseNotesVisible(visible: Boolean) {
        releaseNotesVisible = visible
    }

    private fun onFullscreenVideoBoundsChanged(bounds: Rect) {
        pictureInPictureController.onFullscreenVideoBoundsChanged(bounds)
    }

    private fun prepareForPictureInPictureTransition() {
        if (::pictureInPictureController.isInitialized) {
            pictureInPictureController.prepareForTransition()
        }
    }

    private fun cancelPictureInPictureTransition() {
        if (::pictureInPictureController.isInitialized) {
            pictureInPictureController.cancelTransition()
        }
    }

    private fun updatePictureInPictureParams() {
        if (::pictureInPictureController.isInitialized) pictureInPictureController.updateParams()
    }

    @Suppress("DEPRECATION")
    private fun isUpdatedInstallation(): Boolean = runCatching {
        packageManager.getPackageInfo(packageName, 0).let { packageInfo ->
            packageInfo.lastUpdateTime > packageInfo.firstInstallTime
        }
    }.getOrDefault(false)

    private fun shouldPresentReleaseNotes(
        isNewLaunch: Boolean,
        intentAction: String?,
        isInitialOnboardingRequired: Boolean,
    ): Boolean {
        if (!isNewLaunch || intentAction != Intent.ACTION_MAIN || releaseNotesContent == null) {
            return false
        }
        return ReleaseNotesPresentationRules.shouldPresent(
            isNewLaunch = true,
            isLauncherLaunch = true,
            isAppUpdate = isUpdatedInstallation(),
            isInitialOnboardingRequired = isInitialOnboardingRequired,
            contentAvailable = true,
            currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
            lastHandledVersionCode = releaseNotesStore.lastHandledVersionCode(),
        )
    }

    private fun loadReleaseNotesContent() {
        if (releaseNotesContent != null) return
        releaseNotesContent = ReleaseNotesRepository(this).load(
            BuildConfig.RELEASE_NOTES_VERSION,
        )
    }

    private fun ensureMediaControllers() {
        if (activityDestroyed || !::browserController.isInitialized) return
        if (!::castSessionController.isInitialized) {
            castSessionController = CastSessionController(
                context = this,
                onMediaLoaded = { candidate -> browserController.pauseCastMedia(candidate) },
            )
        }
        castSessionController.updateCandidate(browserController.castMediaCandidate)
        if (!::browserMediaSystemSession.isInitialized) {
            browserMediaSystemSession = BrowserMediaSystemSession(
                context = this,
                onPlay = browserController::playActiveMedia,
                onPause = browserController::pauseActiveMedia,
                onStop = browserController::stopActiveMedia,
                onSeekTo = browserController::seekActiveMedia,
            )
        }
    }

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
        applyFullImmersiveMode(
            enabled = state.isImmersive,
            keepWindowFullHeightForIme = true,
        )
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
        const val STATE_EXTERNAL_LINK_PREVIEW_ACTIVE = "external_link_preview_active"
        const val STATE_EXTERNAL_LINK_PREVIEW_APP_HANDOFF_EXPIRATION =
            "external_link_preview_app_handoff_expiration"
        const val STATE_EXTERNAL_LAUNCH_TAB_ID = "external_launch_tab_id"
        const val STATE_RELEASE_NOTES_VISIBLE = "release_notes_visible"
        const val MOUSE_NAVIGATION_DUPLICATE_WINDOW_MILLIS = 16L
    }
}

private data class MouseNavigationFingerprint(
    val action: BrowserHardwareInputAction,
    val deviceId: Int,
    val eventTime: Long,
)

private data class MouseNavigationButtonToken(
    val deviceId: Int,
    val button: BrowserMouseButton,
)

private fun KeyEvent.toBrowserHardwareKeyStroke(): BrowserHardwareKeyStroke =
    BrowserHardwareKeyStroke(
        key = when (keyCode) {
            KeyEvent.KEYCODE_L -> BrowserHardwareKey.L
            KeyEvent.KEYCODE_T -> BrowserHardwareKey.T
            KeyEvent.KEYCODE_W -> BrowserHardwareKey.W
            KeyEvent.KEYCODE_R -> BrowserHardwareKey.R
            KeyEvent.KEYCODE_F -> BrowserHardwareKey.F
            KeyEvent.KEYCODE_TAB -> BrowserHardwareKey.Tab
            KeyEvent.KEYCODE_DPAD_LEFT -> BrowserHardwareKey.Left
            KeyEvent.KEYCODE_DPAD_RIGHT -> BrowserHardwareKey.Right
            KeyEvent.KEYCODE_F5 -> BrowserHardwareKey.F5
            else -> BrowserHardwareKey.Other
        },
        ctrlPressed = isCtrlPressed,
        metaPressed = isMetaPressed,
        altPressed = isAltPressed,
        shiftPressed = isShiftPressed,
        repeatCount = repeatCount,
    )

private fun MotionEvent.toBrowserMouseButton(): BrowserMouseButton = when {
    actionButton == MotionEvent.BUTTON_BACK ||
        buttonState and MotionEvent.BUTTON_BACK != 0 -> BrowserMouseButton.Back
    actionButton == MotionEvent.BUTTON_FORWARD ||
        buttonState and MotionEvent.BUTTON_FORWARD != 0 -> BrowserMouseButton.Forward
    else -> BrowserMouseButton.Other
}

private fun MotionEvent.unclassifiedVerticalScroll(): Float? {
    if (isFromSource(InputDevice.SOURCE_CLASS_POINTER)) return null
    val verticalScroll = getAxisValue(MotionEvent.AXIS_VSCROLL).takeIf { value -> value != 0f }
        ?: getAxisValue(MotionEvent.AXIS_SCROLL)
    return verticalScroll.takeIf { value -> value != 0f }
}

private fun MotionEvent.asMouseWheelEvent(verticalScroll: Float): MotionEvent {
    val pointerProperties = Array(pointerCount) { pointerIndex ->
        MotionEvent.PointerProperties().also { properties ->
            getPointerProperties(pointerIndex, properties)
            properties.toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
    }
    val pointerCoordinates = Array(pointerCount) { pointerIndex ->
        MotionEvent.PointerCoords().also { coordinates ->
            getPointerCoords(pointerIndex, coordinates)
            coordinates.setAxisValue(MotionEvent.AXIS_VSCROLL, verticalScroll)
        }
    }
    return MotionEvent.obtain(
        downTime,
        eventTime,
        MotionEvent.ACTION_SCROLL,
        pointerCount,
        pointerProperties,
        pointerCoordinates,
        metaState,
        buttonState,
        xPrecision,
        yPrecision,
        deviceId,
        edgeFlags,
        InputDevice.SOURCE_MOUSE,
        flags,
    )
}

private fun KeyEvent.toBrowserMouseNavigationAction(): BrowserHardwareInputAction? = when (keyCode) {
    KeyEvent.KEYCODE_BACK -> BrowserHardwareInputAction.GoBack
    KeyEvent.KEYCODE_FORWARD -> BrowserHardwareInputAction.GoForward
    else -> null
}
