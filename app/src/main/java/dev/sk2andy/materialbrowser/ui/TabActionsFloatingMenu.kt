@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package dev.sk2andy.materialbrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.AddressResolver
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor
import dev.sk2andy.materialbrowser.ui.theme.browserChromeSurfaceTokens
import eightbitlab.com.blurview.BlurTarget

internal object TabActionsMenuMotion {
    const val ENTER_DURATION_MILLIS = 120
    const val FADE_IN_DURATION_MILLIS = 30
    const val EXIT_DURATION_MILLIS = 160
    const val CLOSED_SCALE = 0.8f
    const val EXIT_SCALE = 0.9f
}

private data class TabActionsMenuPresentation(
    val tab: BrowserTab,
    val isFavorite: Boolean,
    val canToggleDomainMute: Boolean,
    val isDomainMuted: Boolean,
    val canCloseAllTabs: Boolean,
    val hasPinnedTabs: Boolean,
)

@Composable
internal fun TabActionsFloatingMenu(
    tab: BrowserTab?,
    blurTarget: BlurTarget? = null,
    profiles: List<BrowserProfile>,
    isFavorite: Boolean,
    canToggleDomainMute: Boolean,
    isDomainMuted: Boolean,
    canCloseAllTabs: Boolean,
    hasPinnedTabs: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenCandyTrail: () -> Unit,
    onTogglePinned: () -> Unit,
    onMoveToProfile: (String) -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit,
    onPrint: () -> Unit,
    onDomainMutedChange: (Boolean) -> Unit,
    onAddSiteCapsule: () -> Unit,
    onSummarize: () -> Unit,
    onSnooze: () -> Unit,
    onCloseAllTabs: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = tab != null, onBack = onDismiss)
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val menuWidth = minOf(400.dp, screenWidth - 24.dp)
    val compactToolbar = menuWidth < 340.dp
    val chromeTokens = browserChromeSurfaceTokens().copy(
        containerColor = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerLow),
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
    )
    val menuPaneTitle = stringResource(R.string.tab_actions_title)
    val menuTransformOrigin = if (LocalLayoutDirection.current == LayoutDirection.Ltr) {
        TransformOrigin(1f, 1f)
    } else {
        TransformOrigin(0f, 1f)
    }
    val requestedPresentation = tab?.let { presentedTab ->
        TabActionsMenuPresentation(
            tab = presentedTab,
            isFavorite = isFavorite,
            canToggleDomainMute = canToggleDomainMute,
            isDomainMuted = isDomainMuted,
            canCloseAllTabs = canCloseAllTabs,
            hasPinnedTabs = hasPinnedTabs,
        )
    }
    var presentation by remember { mutableStateOf(requestedPresentation) }
    if (requestedPresentation != null && requestedPresentation != presentation) {
        presentation = requestedPresentation
    }
    val visibilityState = remember { MutableTransitionState(tab != null) }
    visibilityState.targetState = tab != null
    LaunchedEffect(visibilityState.isIdle, visibilityState.currentState, tab) {
        if (visibilityState.isIdle && !visibilityState.currentState && tab == null) {
            presentation = null
        }
    }
    val presented = presentation
    if (presented != null && (visibilityState.currentState || visibilityState.targetState)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .zIndex(40f),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        role = Role.Button,
                        onClick = onDismiss,
                    )
                    .clearAndSetSemantics { },
            )
            AnimatedVisibility(
                visibleState = visibilityState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 82.dp),
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = TabActionsMenuMotion.FADE_IN_DURATION_MILLIS,
                    ),
                ) + scaleIn(
                    initialScale = TabActionsMenuMotion.CLOSED_SCALE,
                    transformOrigin = menuTransformOrigin,
                    animationSpec = tween(
                        durationMillis = TabActionsMenuMotion.ENTER_DURATION_MILLIS,
                        easing = LinearOutSlowInEasing,
                    ),
                ),
                exit = fadeOut(
                    animationSpec = tween(
                        durationMillis = TabActionsMenuMotion.EXIT_DURATION_MILLIS,
                        easing = FastOutLinearInEasing,
                    ),
                ) + scaleOut(
                    targetScale = TabActionsMenuMotion.EXIT_SCALE,
                    transformOrigin = menuTransformOrigin,
                    animationSpec = tween(
                        durationMillis = TabActionsMenuMotion.EXIT_DURATION_MILLIS,
                        easing = FastOutLinearInEasing,
                    ),
                ),
                label = "Tab actions menu visibility",
            ) {
                val presentedTab = presented.tab
                BrowserChromeSurface(
                    blurTarget = blurTarget,
                    tokens = chromeTokens,
                    modifier = Modifier
                        .width(menuWidth)
                        .heightIn(max = screenHeight * 0.68f)
                        .testTag(SnoozeTestTags.TabActions)
                        .semantics { paneTitle = menuPaneTitle },
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        TabActionsMenuContent(
                            pageSubtitle = if (presentedTab.url == BLANK_URL) {
                                stringResource(R.string.new_tab_title)
                            } else {
                                AddressResolver.displayText(presentedTab.url)
                            },
                            canToggleFavorite = presentedTab.url != BLANK_URL &&
                                !presentedTab.isIncognito,
                            isFavorite = presented.isFavorite,
                            isPinned = presentedTab.isPinned,
                            canUsePageActions = presentedTab.url != BLANK_URL,
                            canToggleDomainMute = presented.canToggleDomainMute,
                            isDomainMuted = presented.isDomainMuted,
                            canAddSiteCapsule = presentedTab.url != BLANK_URL &&
                                !presentedTab.isIncognito &&
                                (presentedTab.url.startsWith("https://") ||
                                    presentedTab.url.startsWith("http://")),
                            canSnooze = !presentedTab.isIncognito,
                            canCloseAllTabs = presented.canCloseAllTabs,
                            hasPinnedTabs = presented.hasPinnedTabs,
                            onToggleFavorite = onToggleFavorite,
                            onTogglePinned = onTogglePinned,
                            onShare = onShare,
                            onOpenExternal = onOpenExternal,
                            onPrint = onPrint,
                            onDomainMutedChange = onDomainMutedChange,
                            onOpenCandyTrail = onOpenCandyTrail,
                            onAddSiteCapsule = onAddSiteCapsule,
                            onSummarize = onSummarize,
                            onSnooze = onSnooze,
                            onCloseAllTabs = onCloseAllTabs,
                            compactToolbar = compactToolbar,
                            profileContent = {
                                val targetProfiles = profiles.filter {
                                    it.id != presentedTab.profileId
                                }
                                if (targetProfiles.isNotEmpty()) {
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        stringResource(R.string.action_move_tab_to_profile),
                                        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        targetProfiles.forEach { profile ->
                                            Surface(
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clickable(
                                                        role = Role.Button,
                                                        onClick = { onMoveToProfile(profile.id) },
                                                    ),
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(profile.emoji, fontSize = 24.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

