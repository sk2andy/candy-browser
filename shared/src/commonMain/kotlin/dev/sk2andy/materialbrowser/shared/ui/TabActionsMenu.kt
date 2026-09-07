@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuAction

object TabActionsMenuMotion {
    const val ENTER_DURATION_MILLIS = 120
    const val FADE_IN_DURATION_MILLIS = 30
    const val EXIT_DURATION_MILLIS = 160
    const val CLOSED_SCALE = 0.8f
    const val EXIT_SCALE = 0.9f
}

object TabActionsMenuTestTags {
    const val Menu = "tab_actions"
    const val Toolbar = "tab_actions_menu_toolbar"
    const val Favorite = "tab_actions_menu_favorite"
    const val Pin = "tab_actions_menu_pin"
    const val PageGroup = "tab_actions_menu_page_group"
    const val CandyGroup = "tab_actions_menu_candy_group"
    const val Trail = "tab_actions_menu_trail"
    const val Snooze = "tab_actions_snooze"
    const val CloseAllTabs = "tab_actions_menu_close_all_tabs"
    const val DomainMute = "domain_mute_menu_item"
}

enum class TabActionsMenuLabel {
    Title,
    Favorite,
    AddFavorite,
    RemoveFavorite,
    PinTab,
    UnpinTab,
    PageGroup,
    Share,
    OpenExternal,
    Print,
    MuteDomain,
    CandyTrail,
    AddSiteCapsule,
    Summarize,
    Snooze,
    SnoozeUnavailablePrivate,
    MoveToProfile,
    CloseAllTabs,
    CloseAllTabsPinnedSupportingText,
}

data class TabActionsProfile(
    val id: String,
    val emoji: String,
)

data class TabActionsMenuState(
    val tabId: String,
    val pageSubtitle: String,
    val canToggleFavorite: Boolean,
    val isFavorite: Boolean,
    val isPinned: Boolean,
    val canUsePageActions: Boolean,
    val canToggleDomainMute: Boolean,
    val isDomainMuted: Boolean,
    val canOpenCandyTrail: Boolean,
    val canAddSiteCapsule: Boolean,
    val canSummarize: Boolean,
    val canSnooze: Boolean,
    val canCloseAllTabs: Boolean,
    val hasPinnedTabs: Boolean,
    val profiles: List<TabActionsProfile> = emptyList(),
)

interface TabActionsMenuResources {
    @Composable
    fun text(label: TabActionsMenuLabel): String

    @Composable
    fun icon(
        action: BrowserFeatureMenuAction,
        selected: Boolean,
        modifier: Modifier,
    )
}

interface TabActionsMenuEffects {
    @Composable
    fun menuSurface(
        modifier: Modifier,
        shape: Shape,
        content: @Composable () -> Unit,
    )

    @Composable
    fun containerColor(
        color: Color,
        frostedAlpha: Float = 0.68f,
    ): Color
}

object DefaultTabActionsMenuEffects : TabActionsMenuEffects {
    @Composable
    override fun menuSurface(
        modifier: Modifier,
        shape: Shape,
        content: @Composable () -> Unit,
    ) {
        Surface(modifier = modifier, shape = shape, content = content)
    }

    @Composable
    override fun containerColor(color: Color, frostedAlpha: Float): Color = color
}

@Composable
fun TabActionsFloatingMenu(
    state: TabActionsMenuState?,
    screenSize: DpSize,
    resources: TabActionsMenuResources,
    onToggleFavorite: (String) -> Unit,
    onTogglePinned: (String) -> Unit,
    onMoveToProfile: (String, String) -> Unit,
    onAction: (String, BrowserFeatureMenuAction) -> Unit,
    onDomainMutedChange: (String, Boolean) -> Unit,
    onCloseAllTabs: (String) -> Unit,
    onDismiss: () -> Unit,
    effects: TabActionsMenuEffects = DefaultTabActionsMenuEffects,
    extensionContent: @Composable ColumnScope.() -> Unit = {},
    profileContent: @Composable ColumnScope.() -> Unit = {},
) {
    val menuWidth = minOf(400.dp, screenSize.width - 24.dp)
    val compactToolbar = menuWidth < 340.dp
    val menuPaneTitle = resources.text(TabActionsMenuLabel.Title)
    val menuTransformOrigin = if (LocalLayoutDirection.current == LayoutDirection.Ltr) {
        TransformOrigin(1f, 1f)
    } else {
        TransformOrigin(0f, 1f)
    }
    var presentation by remember { mutableStateOf(state) }
    if (state != null && state != presentation) {
        presentation = state
    }
    val visibilityState = remember { MutableTransitionState(state != null) }
    visibilityState.targetState = state != null
    LaunchedEffect(visibilityState.isIdle, visibilityState.currentState, state) {
        if (visibilityState.isIdle && !visibilityState.currentState && state == null) {
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
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .clearAndSetSemantics { },
            )
            AnimatedVisibility(
                visibleState = visibilityState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 82.dp),
                enter = fadeIn(
                    animationSpec = tween(TabActionsMenuMotion.FADE_IN_DURATION_MILLIS),
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
                effects.menuSurface(
                    modifier = Modifier
                        .width(menuWidth)
                        .heightIn(max = screenSize.height * 0.68f)
                        .testTag(TabActionsMenuTestTags.Menu)
                        .semantics { paneTitle = menuPaneTitle },
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        TabActionsMenuContent(
                            state = presented,
                            resources = resources,
                            effects = effects,
                            compactToolbar = compactToolbar,
                            onToggleFavorite = { onToggleFavorite(presented.tabId) },
                            onTogglePinned = { onTogglePinned(presented.tabId) },
                            onMoveToProfile = { profileId ->
                                onMoveToProfile(presented.tabId, profileId)
                            },
                            onAction = { action -> onAction(presented.tabId, action) },
                            onDomainMutedChange = { muted ->
                                onDomainMutedChange(presented.tabId, muted)
                            },
                            onCloseAllTabs = { onCloseAllTabs(presented.tabId) },
                            extensionContent = extensionContent,
                            profileContent = profileContent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TabActionsMenuContent(
    state: TabActionsMenuState,
    resources: TabActionsMenuResources,
    effects: TabActionsMenuEffects,
    onToggleFavorite: () -> Unit,
    onTogglePinned: () -> Unit,
    onMoveToProfile: (String) -> Unit,
    onAction: (BrowserFeatureMenuAction) -> Unit,
    onDomainMutedChange: (Boolean) -> Unit,
    onCloseAllTabs: () -> Unit,
    modifier: Modifier = Modifier,
    compactToolbar: Boolean = false,
    extensionContent: @Composable ColumnScope.() -> Unit = {},
    profileContent: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val outerCorners = MaterialTheme.shapes.medium
    val innerCorners = MaterialTheme.shapes.extraSmall
    val firstItemShape = RoundedCornerShape(
        topStart = outerCorners.topStart,
        topEnd = outerCorners.topEnd,
        bottomEnd = innerCorners.bottomEnd,
        bottomStart = innerCorners.bottomStart,
    )
    val lastItemShape = RoundedCornerShape(
        topStart = innerCorners.topStart,
        topEnd = innerCorners.topEnd,
        bottomEnd = outerCorners.bottomEnd,
        bottomStart = outerCorners.bottomStart,
    )
    Column(modifier = modifier) {
        Text(
            text = resources.text(TabActionsMenuLabel.Title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = state.pageSubtitle,
            modifier = Modifier.padding(top = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TabActionsMenuTestTags.Toolbar),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BrowserMenuToolbarAction(
                label = resources.text(TabActionsMenuLabel.Favorite),
                icon = {
                    resources.icon(
                        BrowserFeatureMenuAction.ToggleFavorite,
                        state.isFavorite,
                        Modifier.size(22.dp),
                    )
                },
                accessibilityLabel = resources.text(
                    if (state.isFavorite) {
                        TabActionsMenuLabel.RemoveFavorite
                    } else {
                        TabActionsMenuLabel.AddFavorite
                    },
                ),
                enabled = state.canToggleFavorite,
                selected = state.isFavorite,
                horizontalContent = !compactToolbar,
                onClick = onToggleFavorite,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TabActionsMenuTestTags.Favorite),
                containerColor = effects.containerColor(
                    if (state.isFavorite) colors.primaryContainer else colors.surfaceContainerHighest,
                ),
            )
            BrowserMenuToolbarAction(
                label = resources.text(
                    if (state.isPinned) TabActionsMenuLabel.UnpinTab else TabActionsMenuLabel.PinTab,
                ),
                icon = {
                    resources.icon(
                        BrowserFeatureMenuAction.TogglePinned,
                        state.isPinned,
                        Modifier.size(22.dp),
                    )
                },
                accessibilityLabel = resources.text(
                    if (state.isPinned) TabActionsMenuLabel.UnpinTab else TabActionsMenuLabel.PinTab,
                ),
                selected = state.isPinned,
                horizontalContent = !compactToolbar,
                onClick = onTogglePinned,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TabActionsMenuTestTags.Pin),
                containerColor = effects.containerColor(
                    if (state.isPinned) colors.primaryContainer else colors.surfaceContainerHighest,
                ),
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = resources.text(TabActionsMenuLabel.PageGroup),
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Column(
            modifier = Modifier.testTag(TabActionsMenuTestTags.PageGroup),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TabActionsRow(
                label = TabActionsMenuLabel.Share,
                action = BrowserFeatureMenuAction.Share,
                enabled = state.canUsePageActions,
                shape = firstItemShape,
                resources = resources,
                effects = effects,
                onAction = onAction,
            )
            TabActionsRow(
                label = TabActionsMenuLabel.OpenExternal,
                action = BrowserFeatureMenuAction.OpenExternal,
                enabled = state.canUsePageActions,
                shape = innerCorners,
                resources = resources,
                effects = effects,
                onAction = onAction,
            )
            TabActionsRow(
                label = TabActionsMenuLabel.Print,
                action = BrowserFeatureMenuAction.Print,
                enabled = state.canUsePageActions,
                shape = innerCorners,
                resources = resources,
                effects = effects,
                onAction = onAction,
            )
            BrowserMenuIconToggleItem(
                label = resources.text(TabActionsMenuLabel.MuteDomain),
                icon = {
                    resources.icon(
                        BrowserFeatureMenuAction.ToggleDomainMute,
                        state.isDomainMuted,
                        Modifier.size(20.dp),
                    )
                },
                checked = state.isDomainMuted,
                enabled = state.canToggleDomainMute,
                onCheckedChange = onDomainMutedChange,
                modifier = Modifier.testTag(TabActionsMenuTestTags.DomainMute),
                shape = lastItemShape,
                containerColor = effects.containerColor(colors.surfaceContainer),
            )
        }

        extensionContent()

        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.testTag(TabActionsMenuTestTags.CandyGroup),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TabActionsRow(
                label = TabActionsMenuLabel.CandyTrail,
                action = BrowserFeatureMenuAction.OpenCandyTrail,
                enabled = state.canOpenCandyTrail,
                shape = firstItemShape,
                resources = resources,
                effects = effects,
                containerColor = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
                modifier = Modifier.testTag(TabActionsMenuTestTags.Trail),
                onAction = onAction,
            )
            TabActionsRow(
                label = TabActionsMenuLabel.AddSiteCapsule,
                action = BrowserFeatureMenuAction.AddSiteCapsule,
                enabled = state.canAddSiteCapsule,
                shape = innerCorners,
                resources = resources,
                effects = effects,
                containerColor = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
                onAction = onAction,
            )
            TabActionsRow(
                label = TabActionsMenuLabel.Summarize,
                action = BrowserFeatureMenuAction.Summarize,
                enabled = state.canSummarize,
                shape = innerCorners,
                resources = resources,
                effects = effects,
                containerColor = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
                onAction = onAction,
            )
            TabActionsRow(
                label = TabActionsMenuLabel.Snooze,
                action = BrowserFeatureMenuAction.SnoozeTab,
                enabled = state.canSnooze,
                shape = lastItemShape,
                resources = resources,
                effects = effects,
                containerColor = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
                supportingText = if (state.canSnooze) {
                    null
                } else {
                    resources.text(TabActionsMenuLabel.SnoozeUnavailablePrivate)
                },
                modifier = Modifier.testTag(TabActionsMenuTestTags.Snooze),
                onAction = onAction,
            )
        }
        if (state.profiles.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                resources.text(TabActionsMenuLabel.MoveToProfile),
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.profiles.forEach { profile ->
                    Surface(
                        modifier = Modifier
                            .size(52.dp)
                            .clickable(
                                role = Role.Button,
                                onClick = { onMoveToProfile(profile.id) },
                            ),
                        shape = CircleShape,
                        color = colors.secondaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(profile.emoji, fontSize = 24.sp)
                        }
                    }
                }
            }
        }
        profileContent()
        Spacer(Modifier.height(8.dp))
        BrowserMenuRow(
            label = resources.text(TabActionsMenuLabel.CloseAllTabs),
            icon = {
                resources.icon(
                    BrowserFeatureMenuAction.CloseTab,
                    false,
                    Modifier.size(20.dp),
                )
            },
            enabled = state.canCloseAllTabs,
            shape = outerCorners,
            containerColor = effects.containerColor(colors.errorContainer),
            contentColor = colors.onErrorContainer,
            supportingText = if (state.hasPinnedTabs) {
                resources.text(TabActionsMenuLabel.CloseAllTabsPinnedSupportingText)
            } else {
                null
            },
            modifier = Modifier.testTag(TabActionsMenuTestTags.CloseAllTabs),
            onClick = onCloseAllTabs,
        )
    }
}

@Composable
private fun TabActionsRow(
    label: TabActionsMenuLabel,
    action: BrowserFeatureMenuAction,
    enabled: Boolean,
    shape: Shape,
    resources: TabActionsMenuResources,
    effects: TabActionsMenuEffects,
    onAction: (BrowserFeatureMenuAction) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    supportingText: String? = null,
) {
    BrowserMenuRow(
        label = resources.text(label),
        icon = { resources.icon(action, false, Modifier.size(20.dp)) },
        shape = shape,
        enabled = enabled,
        modifier = modifier,
        containerColor = effects.containerColor(containerColor),
        contentColor = contentColor,
        supportingText = supportingText,
        onClick = { onAction(action) },
    )
}
