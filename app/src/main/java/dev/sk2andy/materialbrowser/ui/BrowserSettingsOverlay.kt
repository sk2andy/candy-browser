@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package dev.sk2andy.materialbrowser.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.UserScriptSaveOutcome
import dev.sk2andy.materialbrowser.browser.userscript.ToppingCatalogRules
import dev.sk2andy.materialbrowser.browser.userscript.ToppingVerifier
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParser
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptRunAt
import dev.sk2andy.materialbrowser.capsule.SiteCapsule
import dev.sk2andy.materialbrowser.data.ToppingCatalogRefreshResult
import kotlin.math.roundToInt

@Composable
internal fun BrowserSettingsOverlay(
    controller: BrowserController,
    visible: Boolean,
    destination: SettingsDestination,
    predictiveBackCommitted: Boolean,
    predictiveBackProgress: Float,
    predictiveBackEdgeSign: Int,
    selectedTab: BrowserTab,
    visibleProfileIds: Set<String>,
    onDestinationChanged: (SettingsDestination) -> Unit,
    onOpenPrivacyXRay: () -> Unit,
    onOpenPermissionRadar: () -> Unit,
    onEditCapsule: (SiteCapsule) -> Unit,
    onDeleteCapsule: (SiteCapsule) -> Unit,
    onImportUserScript: () -> Unit,
    onImportFavoriteBookmarks: () -> Unit,
    onOpenFilterStudio: () -> Unit,
    onExportAppData: () -> Unit,
    onImportAppData: () -> Unit,
    onShowGestureOnboarding: () -> Unit,
    onShowReleaseNotes: () -> Unit,
    onClearData: () -> Unit,
    onOpenLegalUrl: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenFirefoxExtensions: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val toppingCatalogResult = controller.toppingCatalogResult
    val toppingCatalogScripts = controller.userScripts.toList()
    val busyToppingIds = controller.busyToppingIds.toSet()
    val toppingCatalogState = remember(
        toppingCatalogResult,
        controller.isToppingCatalogLoading,
        toppingCatalogScripts,
        busyToppingIds,
    ) {
        fun itemsFor(result: ToppingCatalogRefreshResult): List<ToppingCatalogItem> {
            val catalog = when (result) {
                is ToppingCatalogRefreshResult.Fresh -> result.catalog
                is ToppingCatalogRefreshResult.Cached -> result.catalog
                is ToppingCatalogRefreshResult.Error -> return emptyList()
            }
            return catalog.toppings.map { entry ->
                val installed = toppingCatalogScripts.firstOrNull { script ->
                    script.id == ToppingCatalogRules.stableScriptId(entry.id)
                }
                ToppingCatalogItem(
                    id = entry.id,
                    name = entry.name,
                    description = entry.description,
                    author = entry.author,
                    license = entry.license,
                    version = entry.version,
                    scopes = entry.matches,
                    installed = installed != null,
                    enabled = installed?.enabled == true,
                    updateAvailable = installed != null &&
                        ToppingVerifier.sha256(installed.source.toByteArray()) != entry.sha256,
                    busy = entry.id in busyToppingIds,
                )
            }
        }

        when {
            controller.isToppingCatalogLoading || toppingCatalogResult == null -> {
                ToppingCatalogUiState.Loading
            }
            toppingCatalogResult is ToppingCatalogRefreshResult.Fresh -> {
                ToppingCatalogUiState.Content(itemsFor(toppingCatalogResult))
            }
            toppingCatalogResult is ToppingCatalogRefreshResult.Cached -> {
                ToppingCatalogUiState.Cached(itemsFor(toppingCatalogResult))
            }
            else -> ToppingCatalogUiState.Error()
        }
    }

    AnimatedVisibility(
            visible = visible,
        modifier = Modifier.zIndex(20f),
        enter = slideInHorizontally(
            initialOffsetX = { width ->
                PredictiveBackMotion.entryTranslation(
                    progress = 0f,
                    width = width.toFloat(),
                ).roundToInt()
            },
            animationSpec = tween(
                durationMillis = PredictiveBackMotion.ENTRY_DURATION_MILLIS,
                easing = FastOutSlowInEasing,
            ),
        ),
            exit = if (predictiveBackCommitted) {
            ExitTransition.None
        } else {
            slideOutHorizontally(
                targetOffsetX = { width -> width },
                animationSpec = tween(
                    durationMillis = PredictiveBackMotion.EXIT_DURATION_MILLIS,
                    easing = FastOutSlowInEasing,
                ),
            )
        },
    ) {
        SettingsScreen(
            browserEngineKind = controller.browserEngineKind,
                destination = destination,
            appearanceSettings = controller.appearanceSettings,
            downloadSettings = controller.downloadSettings,
            externalDownloadManagers = controller.externalDownloadManagers,
            blockerSettings = controller.blockerSettings,
            webRtcProtectionMode = controller.webRtcProtectionMode,
            inactiveTabLifetime = controller.inactiveTabLifetime,
            residentTabLimit = controller.residentTabLimit,
            searchEngine = controller.searchEngine,
            pageTranslationProvider = controller.pageTranslationProvider,
            linkLongPressAction = controller.linkLongPressAction,
            linkPeekActionLayout = controller.linkPeekActionLayout,
            searxngSettings = controller.searxngSettings,
            isAiModeToggleVisible = controller.isAiModeToggleVisible,
            searchSuggestionProvider = controller.searchSuggestionProvider,
            isHistorySuggestionsEnabled = controller.isHistorySuggestionsEnabled,
            isRecallEnabled = controller.isRecallEnabled,
            historyRecordingMode = controller.historyRecordingMode,
            tabOverviewMode = controller.tabOverviewMode,
            tabStackFolderMode = controller.tabStackFolderMode,
            tabListStartsAtBottom = controller.tabListStartsAtBottom,
            automaticTabSortingEnabled = controller.automaticTabSortingEnabled,
            dismissResistancePercent = controller.dismissResistancePercent,
            profilesEnabled = controller.profilesEnabled,
            profiles = controller.profiles,
            activeProfileId = controller.activeProfileId,
            tabCount = controller.activeTabs.size,
            addressBarActionLayout = controller.addressBarActionLayout,
            isAddressBarDockingEnabled = controller.isAddressBarDockingEnabled,
            isExternalLinkPreviewEnabled = controller.isExternalLinkPreviewEnabled,
            isFullImmersiveModeEnabled = controller.isFullImmersiveModeEnabled,
            isStartupAnimationEnabled = controller.isStartupAnimationEnabled,
            isHttpPasswordAutofillEnabled = controller.isHttpPasswordAutofillEnabled,
            isHttpPasswordAutofillSupported = controller.isHttpPasswordAutofillSupported,
            isFavoriteLaunchAnimationEnabled =
                controller.isFavoriteLaunchAnimationEnabled,
            isOpenHomeOnStartupEnabled = controller.isOpenHomeOnStartupEnabled,
            isScrollBarEnabled = controller.isScrollBarEnabled,
            isVideoAutoplayBlocked = controller.isVideoAutoplayBlocked,
            isVideoAutoplayBlockingSupported = controller.isVideoAutoplayBlockingSupported,
            developerSettings = controller.developerSettings,
            isDeveloperOptionsUnlocked = controller.isDeveloperOptionsUnlocked,
            isInputDiagnosticsEnabled = controller.isInputDiagnosticsEnabled,
            blockedCount = selectedTab.blockedCount,
            isDefaultBrowser = controller.isDefaultBrowser,
            isUserScriptSupported = controller.isUserScriptSupported,
            siteCapsules = controller.siteCapsules.filter {
                it.profileId in visibleProfileIds
            },
            userScripts = controller.userScripts.map { script ->
                UserscriptUiItem(
                    id = script.id,
                    name = script.name,
                    source = script.source,
                    enabled = script.enabled,
                    runAtLabel = context.getString(
                        when (script.runAt) {
                            UserScriptRunAt.DocumentStart ->
                                R.string.userscript_run_at_document_start
                            UserScriptRunAt.DocumentEnd ->
                                R.string.userscript_run_at_document_end
                        },
                    ),
                    urlPatterns = script.matchPatterns + script.includePatterns,
                )
            },
            toppingCatalogState = toppingCatalogState,
            syncState = controller.syncState,
            syncIconCatalog = controller.syncIconCatalog,
            onDestinationChanged = onDestinationChanged,
            onBrowserEngineKindChanged = controller::updateBrowserEngineKind,
            onAppearanceSettingsChanged = controller::updateAppearanceSettings,
            onDownloadSettingsChanged = controller::updateDownloadSettings,
            onBlockerSettingsChanged = controller::updateBlockerSettings,
            onWebRtcProtectionModeChanged = controller::updateWebRtcProtectionMode,
            onInactiveTabLifetimeChanged = controller::updateInactiveTabLifetime,
            onResidentTabLimitChanged = controller::updateResidentTabLimit,
            onSearchEngineChanged = controller::updateSearchEngine,
            onPageTranslationProviderChanged = controller::updatePageTranslationProvider,
            onLinkLongPressActionChanged = controller::updateLinkLongPressAction,
            onLinkPeekActionLayoutChanged = controller::updateLinkPeekActionLayout,
            onSearxngSettingsChanged = controller::updateSearxngSettings,
            onAiModeToggleVisibleChanged = controller::updateAiModeToggleVisible,
            onSearchSuggestionProviderChanged = controller::updateSearchSuggestionProvider,
            onHistorySuggestionsEnabledChanged =
                controller::updateHistorySuggestionsEnabled,
            onRecallEnabledChanged = controller::updateRecallEnabled,
            onHistoryRecordingModeChanged = controller::updateHistoryRecordingMode,
            onTabOverviewModeChanged = controller::updateTabOverviewMode,
            onTabStackFolderModeChanged = controller::updateTabStackFolderMode,
            onTabListStartsAtBottomChanged = controller::updateTabListStartsAtBottom,
            onAutomaticTabSortingEnabledChanged =
                controller::updateAutomaticTabSortingEnabled,
            onDismissResistancePercentChanged = controller::updateDismissResistancePercent,
            onProfilesEnabledChanged = controller::updateProfilesEnabled,
            onAddressBarActionLayoutChanged = controller::updateAddressBarActionLayout,
            onAddressBarDockingEnabledChanged =
                controller::updateAddressBarDockingEnabled,
            onExternalLinkPreviewEnabledChanged =
                controller::updateExternalLinkPreviewEnabled,
            onFullImmersiveModeEnabledChanged =
                controller::updateFullImmersiveModeEnabled,
            onStartupAnimationEnabledChanged =
                controller::updateStartupAnimationEnabled,
            onHttpPasswordAutofillEnabledChanged =
                controller::updateHttpPasswordAutofillEnabled,
            onFavoriteLaunchAnimationEnabledChanged =
                controller::updateFavoriteLaunchAnimationEnabled,
            onImportFavoriteBookmarks = onImportFavoriteBookmarks,
            onOpenHomeOnStartupEnabledChanged =
                controller::updateOpenHomeOnStartupEnabled,
            onScrollBarEnabledChanged = controller::updateScrollBarEnabled,
            onVideoAutoplayBlockedChanged = controller::updateVideoAutoplayBlocked,
            onDeveloperSettingsChanged = controller::updateDeveloperSettings,
            onInputDiagnosticsEnabledChanged = controller::updateInputDiagnosticsEnabled,
            onCopyDeveloperDiagnostics = controller::copyDeveloperDiagnostics,
            onShowGestureOnboarding = onShowGestureOnboarding,
            onShowReleaseNotes = onShowReleaseNotes,
            onUnlockDeveloperOptions = {
                if (controller.unlockDeveloperOptions()) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.developer_options_unlocked),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onOpenDefaultBrowserSettings = controller::openDefaultBrowserSettings,
            onPrivacyXRay = onOpenPrivacyXRay,
            onPermissionRadar = onOpenPermissionRadar,
            onEditCapsule = onEditCapsule,
            onDeleteCapsule = onDeleteCapsule,
            onToggleUserScript = { id, enabled, onResult ->
                controller.setUserScriptEnabled(id, enabled) { saved ->
                    onResult(
                        if (saved) null
                        else context.getString(R.string.userscript_error_generic),
                    )
                }
            },
            onSaveUserScript = { id, source, onResult ->
                controller.saveUserScript(id, source) { outcome ->
                    onResult(
                        when (outcome) {
                            UserScriptSaveOutcome.Saved -> null
                            UserScriptSaveOutcome.LimitReached -> context.getString(
                                R.string.userscript_error_limit,
                                UserScriptParser.MAX_SCRIPTS,
                            )
                            UserScriptSaveOutcome.Missing,
                            UserScriptSaveOutcome.PersistenceFailed,
                            is UserScriptSaveOutcome.Rejected,
                            is UserScriptSaveOutcome.DependencyFailed,
                            -> context.getString(R.string.userscript_error_generic)
                        },
                    )
                }
            },
            onDeleteUserScript = { id, onResult ->
                controller.deleteUserScript(id) { deleted ->
                    onResult(
                        if (deleted) null
                        else context.getString(R.string.userscript_error_generic),
                    )
                }
            },
            onImportUserScript = onImportUserScript,
            onToggleTopping = { id, enabled ->
                controller.setToppingEnabled(id, enabled) { saved ->
                    if (!saved) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.topping_catalog_action_error),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
            onUpdateTopping = { id ->
                controller.updateTopping(id) { saved ->
                    if (!saved) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.topping_catalog_action_error),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
            onRefreshToppingCatalog = controller::refreshToppingCatalog,
            onConfigureSync = controller::configureSync,
            onEnrollSync = controller::enrollSync,
            onRefreshSync = controller::refreshSync,
            onFilterStudio = onOpenFilterStudio,
            onExportAppData = onExportAppData,
            onImportAppData = onImportAppData,
            onClearData = onClearData,
            onOpenLegalUrl = onOpenLegalUrl,
            onDismiss = onDismiss,
            onOpenFirefoxExtensions = onOpenFirefoxExtensions,
            modifier = Modifier.predictiveBackSurface(
                    predictiveBackProgress,
                    predictiveBackEdgeSign,
            ),
        )
    }

}
