@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.FederatedLoginOffer
import dev.sk2andy.materialbrowser.browser.CaptchaCompatibilityOffer
import dev.sk2andy.materialbrowser.data.SnoozedTab
import eightbitlab.com.blurview.BlurTarget

@Composable
internal fun BoxScope.BrowserModalSurfaces(
    controller: BrowserController,
    privacyXRayTabId: String?,
    permissionRadarTabId: String?,
    permissionRadarOrigin: String?,
    filterStudioVisible: Boolean,
    filterStudioSelectedRuleId: String?,
    browserContentBlurTarget: BlurTarget?,
    visibleProfiles: List<BrowserProfile>,
    favoriteFeedbackEvent: FavoriteFeedbackEvent?,
    feedbackSnackbarHostState: SnackbarHostState,
    snoozedTabsVisible: Boolean,
    visibleSnoozedTabs: List<SnoozedTab>,
    onOpenFilterStudio: (String?) -> Unit,
    onPrivacyXRayDismiss: () -> Unit,
    onPermissionOriginSelected: (String?) -> Unit,
    onPermissionRadarDismiss: () -> Unit,
    onFilterStudioDismiss: () -> Unit,
    onFavoriteFeedbackFinished: (Int) -> Unit,
    onSnoozedTabsDismiss: () -> Unit,
) {
    controller.pendingDownloadChoice?.let { choice ->
        DownloadManagerChooserDialog(
            choice = choice,
            onSelect = controller::confirmDownloadChoice,
            onDismiss = controller::dismissDownloadChoice,
        )
    }

    privacyXRayTabId?.let { tabId ->
        val xRayTab = controller.tabs.firstOrNull { it.id == tabId }
        if (xRayTab != null) {
            PrivacyXRaySheet(
                snapshot = controller.privacySnapshot(tabId),
                blockerSettings = controller.blockerSettings,
                siteState = controller.siteProtectionState(tabId),
                backdropSource = browserContentBlurTarget.asCandyChromeBackdropSource(),
                onPause = { persistently ->
                    controller.pauseSiteProtection(tabId, persistently)
                },
                onResume = { controller.resumeSiteProtection(tabId) },
                onRevokeThirdPartyCookieCompatibility = {
                    controller.revokeThirdPartyCookieCompatibility(tabId)
                },
                onRuleAction = { domain, action, siteScoped ->
                    val rule = controller.addFilterRuleFromXRay(
                        tabId = tabId,
                        requestHost = domain,
                        action = action,
                        siteScoped = siteScoped,
                    )
                    if (rule != null) {
                        onOpenFilterStudio(rule.id)
                    }
                },
                onOpenStudio = onOpenFilterStudio,
                onDismiss = onPrivacyXRayDismiss,
            )
        }
    }

    permissionRadarTabId?.let { tabId ->
        val radarTab = controller.tabs.firstOrNull { it.id == tabId }
        if (radarTab != null) {
            val snapshot = controller.permissionRadarSnapshot(tabId, permissionRadarOrigin)
            val profileEmoji = controller.profiles
                .firstOrNull { it.id == radarTab.profileId }
                ?.emoji
                .orEmpty()
            PermissionRadarSheet(
                snapshot = snapshot,
                profileEmoji = profileEmoji,
                onOriginSelected = onPermissionOriginSelected,
                onDecisionChanged = { permission, decision ->
                    snapshot.site?.let { site ->
                        controller.setSitePermissionDecision(
                            tabId = tabId,
                            origin = site.origin,
                            permission = permission,
                            decision = decision,
                        )
                    }
                },
                onResetSite = {
                    snapshot.site?.let { site ->
                        controller.resetSitePermissions(tabId, site.origin)
                    }
                },
                onDismiss = onPermissionRadarDismiss,
            )
        }
    }

    controller.permissionPrompt?.let { prompt ->
        PermissionPromptDialog(
            prompt = prompt,
            onChoice = { choice -> controller.respondToPermissionPrompt(prompt.id, choice) },
        )
    }

    controller.httpAuthPrompt?.let { prompt ->
        HttpAuthPromptDialog(
            prompt = prompt,
            onSubmit = { username, password ->
                controller.respondToHttpAuthPrompt(prompt.id, username, password)
            },
            onCancel = { controller.cancelHttpAuthPrompt(prompt.id) },
        )
    }

    controller.federatedLoginOffer
        ?.takeIf(FederatedLoginOffer::showDialog)
        ?.let { offer ->
            FederatedLoginPromptDialog(
                offer = offer,
                onChoice = { choice ->
                    controller.respondToFederatedLoginOffer(offer.token, choice)
                },
            )
        }

    controller.captchaCompatibilityOffer
        ?.takeIf(CaptchaCompatibilityOffer::showDialog)
        ?.let { offer ->
            CaptchaCompatibilityPromptDialog(
                offer = offer,
                onChoice = { choice ->
                    controller.respondToCaptchaCompatibilityOffer(offer.token, choice)
                },
            )
        }

    if (filterStudioVisible) {
        FilterStudioScreen(
            rules = controller.filterRulesFor(controller.selectedTabId),
            subscriptionRules = controller.filterSubscriptionRulesFor(
                controller.selectedTabId,
            ),
            isIncognito = controller.selectedTab.isIncognito,
            profiles = visibleProfiles,
            currentProfileId = controller.selectedTab.profileId,
            currentUrl = controller.filterStudioTestUrl(controller.selectedTabId),
            recentDomain = controller.privacySnapshot(controller.selectedTabId)
                .domains.firstOrNull()?.host,
            selectedRuleId = filterStudioSelectedRuleId,
            onTest = { controller.testFilterRule(controller.selectedTabId, it) },
            onAdd = controller::addFilterRule,
            onUpdate = controller::updateFilterRule,
            onToggle = controller::setFilterRuleActive,
            onDelete = controller::deleteFilterRule,
            onParseImport = controller::importFilterRules,
            onApplyImport = controller::applyFilterImport,
            onApplySubscription = controller::applyFilterSubscription,
            onExport = controller::exportFilterRules,
            onDismiss = onFilterStudioDismiss,
        )
    }

    favoriteFeedbackEvent?.let { event ->
        FavoriteToggleFeedback(
            event = event,
            onFinished = onFavoriteFeedbackFinished,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 24.dp, bottom = 104.dp)
                .zIndex(5f),
        )
    }

    SnackbarHost(
        hostState = feedbackSnackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 84.dp)
            .zIndex(30f),
    )

    AnimatedVisibility(
        visible = snoozedTabsVisible,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(90)),
    ) {
        SnoozedTabsScreen(
            snoozedTabs = visibleSnoozedTabs,
            profiles = visibleProfiles,
            onBack = onSnoozedTabsDismiss,
            onReschedule = controller::rescheduleSnoozedTab,
            onOpenNow = { tabId ->
                controller.openSnoozedTabNow(tabId)
            },
            onDelete = controller::deleteSnoozedTab,
        )
    }
}
