package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.GenericShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuAction
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuItem
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuItemKind
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuSection

object BrowserMainMenuMotion {
    const val EXIT_DURATION_MILLIS = 160
    const val EXIT_SCALE = 0.9f

    fun surfaceScale(
        expansionProgress: Float,
        surfaceSize: Float,
        anchorSize: Float,
    ): Float = AddressBarMorphRules.containerScale(
        progress = 1f - expansionProgress.coerceIn(0f, 1f),
        sourceSize = surfaceSize,
        targetSize = anchorSize,
    )

    fun surfaceCornerRadii(
        expansionProgress: Float,
        surfaceWidth: Float,
        surfaceHeight: Float,
        anchorSize: Float,
        surfaceCornerRadius: Float,
    ): AddressBarMorphCornerRadii = AddressBarMorphRules.cornerRadii(
        progress = 1f - expansionProgress.coerceIn(0f, 1f),
        sourceWidth = surfaceWidth,
        sourceHeight = surfaceHeight,
        targetSize = anchorSize,
        sourceCornerRadius = surfaceCornerRadius,
    )

    fun surfaceAlpha(
        expansionProgress: Float,
        preservesVisualEffect: Boolean,
    ): Float = if (preservesVisualEffect) {
        1f
    } else {
        expansionProgress.coerceIn(0f, 1f)
    }
}

object BrowserMenuIconRules {
    fun useFilledVariant(
        action: BrowserFeatureMenuAction,
        selected: Boolean,
    ): Boolean = when (action) {
        BrowserFeatureMenuAction.ToggleFavorite,
        BrowserFeatureMenuAction.TogglePinned,
        -> selected
        else -> true
    }
}

enum class BrowserMainMenuContainerRole {
    Regular,
    Selected,
    Branded,
}

data class BrowserMainMenuStyle(
    val showHeader: Boolean = true,
    val menuCornerRadius: Dp = 16.dp,
    val groupCornerRadius: Dp = 12.dp,
    val groupInnerCornerRadius: Dp = 4.dp,
    val contentHorizontalPadding: Dp = 16.dp,
    val contentVerticalPadding: Dp = 8.dp,
    val toolbarSpacing: Dp = 4.dp,
    val toolbarMinHeight: Dp = 64.dp,
    val toolbarIconSize: Dp = 22.dp,
    val groupItemSpacing: Dp = 2.dp,
    val rowMinHeight: Dp = 44.dp,
    val rowHorizontalPadding: Dp = 16.dp,
    val rowVerticalPadding: Dp = 6.dp,
    val toggleTrackColor: Color? = null,
)

interface BrowserMainMenuResources {
    @Composable
    fun title(): String

    @Composable
    fun sectionTitle(section: BrowserFeatureMenuSection): String

    @Composable
    fun label(item: BrowserFeatureMenuItem, toolbar: Boolean = false): String

    @Composable
    fun accessibilityLabel(item: BrowserFeatureMenuItem): String? = null

    @Composable
    fun supportingText(item: BrowserFeatureMenuItem, snoozedTabCount: Int): String?

    @Composable
    fun icon(item: BrowserFeatureMenuItem, modifier: Modifier)

    @Composable
    fun trailingIcon(modifier: Modifier)
}

interface BrowserMainMenuEffects {
    val style: BrowserMainMenuStyle
        get() = BrowserMainMenuStyle()

    @Composable
    fun menuSurface(
        modifier: Modifier,
        shape: Shape,
        content: @Composable () -> Unit,
    )

    @Composable
    fun containerColor(
        color: Color,
        frostedAlpha: Float = 0.82f,
        role: BrowserMainMenuContainerRole = BrowserMainMenuContainerRole.Regular,
    ): Color

    @Composable
    fun sectionTitleColor(color: Color): Color = color

    /**
     * UIKit visual effects must stay fully opaque while their host view scales.
     * Applying alpha to the host forces offscreen composition and breaks glass.
     */
    fun preservesVisualEffectDuringMorph(): Boolean = false

    fun maxHeightFraction(): Float = BROWSER_MAIN_MENU_MAX_HEIGHT_FRACTION

    fun popupState(expanded: Boolean, visible: Boolean) = Unit
}

object DefaultBrowserMainMenuEffects : BrowserMainMenuEffects {
    @Composable
    override fun menuSurface(
        modifier: Modifier,
        shape: Shape,
        content: @Composable () -> Unit,
    ) {
        Surface(modifier = modifier, shape = shape, content = content)
    }

    @Composable
    override fun containerColor(
        color: Color,
        frostedAlpha: Float,
        role: BrowserMainMenuContainerRole,
    ): Color = color
}

private data class BrowserMainMenuSnapshot(
    val pageSubtitle: String,
    val items: List<BrowserFeatureMenuItem>,
    val snoozedTabCount: Int,
)

@Composable
fun BrowserMainMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    pageSubtitle: String,
    items: List<BrowserFeatureMenuItem>,
    snoozedTabCount: Int,
    screenSize: DpSize,
    resources: BrowserMainMenuResources,
    onAction: (BrowserFeatureMenuItem) -> Unit,
    effects: BrowserMainMenuEffects = DefaultBrowserMainMenuEffects,
    morphAnchorSize: DpSize? = null,
    morphProgress: Float? = null,
) {
    val colors = MaterialTheme.colorScheme
    val style = effects.style
    val menuShape = RoundedCornerShape(style.menuCornerRadius)
    val outerCorners = RoundedCornerShape(style.groupCornerRadius)
    val innerCorners = RoundedCornerShape(style.groupInnerCornerRadius)
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
    val menuWidth = minOf(400.dp, screenSize.width - 24.dp)
    val compactToolbar = menuWidth < 340.dp
    val menuMaxHeight = screenSize.height * effects.maxHeightFraction()
    val menuScrollState = rememberScrollState()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val popupOffset = with(density) { IntOffset(0, (-10).dp.roundToPx()) }
    val menuWidthPx = with(density) { menuWidth.toPx() }
    val menuHeightPx = with(density) { menuMaxHeight.toPx() }
    val anchorWidthPx = morphAnchorSize?.let { with(density) { it.width.toPx() } }
    val anchorHeightPx = morphAnchorSize?.let { with(density) { it.height.toPx() } }
    val anchorSizePx = minOf(anchorWidthPx ?: 0f, anchorHeightPx ?: 0f)
    val menuCornerRadiusPx = when (
        val outline = menuShape.createOutline(
            size = Size(menuWidthPx, menuHeightPx),
            layoutDirection = layoutDirection,
            density = density,
        )
    ) {
        is Outline.Rounded -> outline.roundRect.topRightCornerRadius.x
        else -> 0f
    }
    val requestedSnapshot = BrowserMainMenuSnapshot(
        pageSubtitle = pageSubtitle,
        items = items,
        snoozedTabCount = snoozedTabCount,
    )
    var snapshot by remember { mutableStateOf(requestedSnapshot) }
    if (expanded && requestedSnapshot != snapshot) {
        snapshot = requestedSnapshot
    }
    var popupVisible by remember { mutableStateOf(expanded) }
    var actionCommitted by remember { mutableStateOf(false) }
    val exitProgress = remember { Animatable(if (expanded) 1f else 0f) }
    val menuTransformOrigin = if (layoutDirection == LayoutDirection.Ltr) {
        TransformOrigin(1f, 1f)
    } else {
        TransformOrigin(0f, 1f)
    }
    LaunchedEffect(expanded) {
        if (expanded) {
            actionCommitted = false
            val reversingExit = popupVisible
            popupVisible = true
            if (reversingExit) {
                exitProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = BrowserMainMenuMotion.EXIT_DURATION_MILLIS,
                        easing = LinearOutSlowInEasing,
                    ),
                )
            } else {
                menuScrollState.scrollTo(0)
                if (morphAnchorSize == null) {
                    exitProgress.snapTo(1f)
                } else {
                    exitProgress.snapTo(0f)
                    exitProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = BrowserMainMenuMotion.EXIT_DURATION_MILLIS,
                            easing = LinearOutSlowInEasing,
                        ),
                    )
                }
            }
        } else if (popupVisible) {
            exitProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = BrowserMainMenuMotion.EXIT_DURATION_MILLIS,
                    easing = FastOutLinearInEasing,
                ),
            )
            popupVisible = false
        }
    }
    LaunchedEffect(expanded, popupVisible) {
        effects.popupState(expanded, popupVisible)
    }
    fun dismissThen(item: BrowserFeatureMenuItem) {
        if (!expanded || actionCommitted) return
        actionCommitted = true
        onDismissRequest()
        onAction(item)
    }

    if (popupVisible) {
        val currentMorphProgress = morphProgress ?: exitProgress.value
        val morphRadii = if (anchorSizePx > 0f) {
            BrowserMainMenuMotion.surfaceCornerRadii(
                expansionProgress = currentMorphProgress,
                surfaceWidth = menuWidthPx,
                surfaceHeight = menuHeightPx,
                anchorSize = anchorSizePx,
                surfaceCornerRadius = menuCornerRadiusPx,
            )
        } else {
            null
        }
        val animatedMenuShape = morphRadii?.let { radii ->
            GenericShape { size, _ ->
                addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = size.width,
                        bottom = size.height,
                        topLeftCornerRadius = CornerRadius(radii.horizontal, radii.vertical),
                        topRightCornerRadius = CornerRadius(radii.horizontal, radii.vertical),
                        bottomRightCornerRadius = CornerRadius(radii.horizontal, radii.vertical),
                        bottomLeftCornerRadius = CornerRadius(radii.horizontal, radii.vertical),
                    ),
                )
            }
        } ?: menuShape
        Popup(
            alignment = Alignment.BottomEnd,
            offset = popupOffset,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true),
        ) {
            effects.menuSurface(
                modifier = Modifier
                    .width(menuWidth)
                    .height(menuMaxHeight)
                    .graphicsLayer {
                        alpha = BrowserMainMenuMotion.surfaceAlpha(
                            expansionProgress = currentMorphProgress,
                            preservesVisualEffect = effects.preservesVisualEffectDuringMorph(),
                        )
                        if (anchorWidthPx != null && anchorHeightPx != null) {
                            scaleX = BrowserMainMenuMotion.surfaceScale(
                                expansionProgress = currentMorphProgress,
                                surfaceSize = menuWidthPx,
                                anchorSize = anchorWidthPx,
                            )
                            scaleY = BrowserMainMenuMotion.surfaceScale(
                                expansionProgress = currentMorphProgress,
                                surfaceSize = menuHeightPx,
                                anchorSize = anchorHeightPx,
                            )
                        } else {
                            val scale = BrowserMainMenuMotion.EXIT_SCALE +
                                (1f - BrowserMainMenuMotion.EXIT_SCALE) * exitProgress.value
                            scaleX = scale
                            scaleY = scale
                        }
                        transformOrigin = menuTransformOrigin
                    }
                    .clip(animatedMenuShape)
                    .testTag(BrowserMainMenuTestTags.Menu),
                shape = animatedMenuShape,
            ) {
                BrowserMainMenuContent(
                    snapshot = snapshot,
                    compactToolbar = compactToolbar,
                    resources = resources,
                    effects = effects,
                    firstItemShape = firstItemShape,
                    innerCorners = innerCorners,
                    lastItemShape = lastItemShape,
                    onCommand = ::dismissThen,
                    onToggle = onAction,
                    modifier = Modifier
                        .verticalScroll(menuScrollState)
                        .padding(
                            horizontal = style.contentHorizontalPadding,
                            vertical = style.contentVerticalPadding,
                        ),
                )
            }
        }
    }
}

@Composable
private fun BrowserMainMenuContent(
    snapshot: BrowserMainMenuSnapshot,
    compactToolbar: Boolean,
    resources: BrowserMainMenuResources,
    effects: BrowserMainMenuEffects,
    firstItemShape: Shape,
    innerCorners: Shape,
    lastItemShape: Shape,
    onCommand: (BrowserFeatureMenuItem) -> Unit,
    onToggle: (BrowserFeatureMenuItem) -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val groupedItems = snapshot.items.groupBy(BrowserFeatureMenuItem::section)
    Column(modifier = modifier) {
        if (effects.style.showHeader) {
            Text(
                text = resources.title(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = snapshot.pageSubtitle,
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
        }
        BrowserMainMenuToolbar(
            items = groupedItems[BrowserFeatureMenuSection.Toolbar].orEmpty(),
            compact = compactToolbar,
            resources = resources,
            effects = effects,
            onClick = onCommand,
        )

        BrowserMainMenuSectionTitle(
            title = resources.sectionTitle(BrowserFeatureMenuSection.Page),
            topPadding = 12,
            effects = effects,
        )
        BrowserMainMenuItemGroup(
            items = groupedItems[BrowserFeatureMenuSection.Page].orEmpty(),
            resources = resources,
            effects = effects,
            snoozedTabCount = snapshot.snoozedTabCount,
            firstItemShape = firstItemShape,
            innerCorners = innerCorners,
            lastItemShape = lastItemShape,
            onCommand = onCommand,
            onToggle = onToggle,
            modifier = Modifier.testTag(BrowserMainMenuTestTags.PageGroup),
        )

        val toppingItems = groupedItems[BrowserFeatureMenuSection.Toppings].orEmpty()
        if (toppingItems.isNotEmpty()) {
            BrowserMainMenuSectionTitle(
                title = resources.sectionTitle(BrowserFeatureMenuSection.Toppings),
                topPadding = 8,
                effects = effects,
            )
            BrowserMainMenuItemGroup(
                items = toppingItems,
                resources = resources,
                effects = effects,
                snoozedTabCount = snapshot.snoozedTabCount,
                firstItemShape = firstItemShape,
                innerCorners = innerCorners,
                lastItemShape = lastItemShape,
                onCommand = onCommand,
                onToggle = onToggle,
                modifier = Modifier.testTag(BrowserMainMenuTestTags.ToppingsGroup),
            )
        }

        Spacer(Modifier.height(8.dp))
        BrowserMainMenuItemGroup(
            items = groupedItems[BrowserFeatureMenuSection.Candy].orEmpty(),
            resources = resources,
            effects = effects,
            snoozedTabCount = snapshot.snoozedTabCount,
            firstItemShape = firstItemShape,
            innerCorners = innerCorners,
            lastItemShape = lastItemShape,
            onCommand = onCommand,
            onToggle = onToggle,
            containerColor = colors.tertiaryContainer,
            contentColor = colors.onTertiaryContainer,
            modifier = Modifier.testTag(BrowserMainMenuTestTags.CandyGroup),
        )

        val browserItems = groupedItems[BrowserFeatureMenuSection.Browser].orEmpty()
        if (browserItems.isNotEmpty()) {
            BrowserMainMenuSectionTitle(
                title = resources.sectionTitle(BrowserFeatureMenuSection.Browser),
                topPadding = 8,
                effects = effects,
            )
            BrowserMainMenuItemGroup(
                items = browserItems,
                resources = resources,
                effects = effects,
                snoozedTabCount = snapshot.snoozedTabCount,
                firstItemShape = firstItemShape,
                innerCorners = innerCorners,
                lastItemShape = lastItemShape,
                onCommand = onCommand,
                onToggle = onToggle,
                modifier = Modifier.testTag(BrowserMainMenuTestTags.BrowserGroup),
            )
        }
    }
}

@Composable
private fun BrowserMainMenuToolbar(
    items: List<BrowserFeatureMenuItem>,
    compact: Boolean,
    resources: BrowserMainMenuResources,
    effects: BrowserMainMenuEffects,
    onClick: (BrowserFeatureMenuItem) -> Unit,
) {
    val primaryItems = if (compact) items.take(3) else items
    val secondaryItems = if (compact) items.drop(3) else emptyList()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(BrowserMainMenuTestTags.Toolbar),
        verticalArrangement = Arrangement.spacedBy(effects.style.toolbarSpacing),
    ) {
        BrowserMainMenuToolbarRow(primaryItems, resources, effects, onClick)
        if (secondaryItems.isNotEmpty()) {
            BrowserMainMenuToolbarRow(secondaryItems, resources, effects, onClick)
        }
    }
}

@Composable
private fun BrowserMainMenuToolbarRow(
    items: List<BrowserFeatureMenuItem>,
    resources: BrowserMainMenuResources,
    effects: BrowserMainMenuEffects,
    onClick: (BrowserFeatureMenuItem) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(effects.style.toolbarSpacing)) {
        items.forEach { item ->
            BrowserMenuToolbarAction(
                label = resources.label(item, toolbar = true),
                icon = { resources.icon(item, Modifier.size(effects.style.toolbarIconSize)) },
                enabled = item.enabled || item.action in RELOAD_ACTIONS,
                selected = item.checked == true,
                accessibilityLabel = resources.accessibilityLabel(item),
                minHeight = effects.style.toolbarMinHeight,
                containerColor = effects.containerColor(
                    if (item.checked == true) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    role = if (item.checked == true) {
                        BrowserMainMenuContainerRole.Selected
                    } else {
                        BrowserMainMenuContainerRole.Regular
                    },
                ),
                onClick = { onClick(item) },
                modifier = Modifier
                    .weight(1f)
                    .then(item.testTagModifier()),
            )
        }
    }
}

private val RELOAD_ACTIONS = setOf(
    BrowserFeatureMenuAction.Reload,
    BrowserFeatureMenuAction.Stop,
)

@Composable
private fun BrowserMainMenuSectionTitle(
    title: String,
    topPadding: Int,
    effects: BrowserMainMenuEffects,
) {
    Spacer(Modifier.height(topPadding.dp))
    Text(
        text = title,
        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = effects.sectionTitleColor(MaterialTheme.colorScheme.primary),
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun BrowserMainMenuItemGroup(
    items: List<BrowserFeatureMenuItem>,
    resources: BrowserMainMenuResources,
    effects: BrowserMainMenuEffects,
    snoozedTabCount: Int,
    firstItemShape: Shape,
    innerCorners: Shape,
    lastItemShape: Shape,
    onCommand: (BrowserFeatureMenuItem) -> Unit,
    onToggle: (BrowserFeatureMenuItem) -> Unit,
    modifier: Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(effects.style.groupItemSpacing),
    ) {
        items.forEachIndexed { index, item ->
            val shape = when {
                items.size == 1 -> RoundedCornerShape(effects.style.groupCornerRadius)
                index == 0 -> firstItemShape
                index == items.lastIndex -> lastItemShape
                else -> innerCorners
            }
            val itemModifier = item.testTagModifier()
            when {
                item.action == BrowserFeatureMenuAction.ToggleDomainMute -> {
                    BrowserMenuIconToggleItem(
                        label = resources.label(item),
                        icon = { resources.icon(item, Modifier.size(20.dp)) },
                        checked = item.checked == true,
                        enabled = item.enabled,
                        onCheckedChange = { onToggle(item) },
                        modifier = itemModifier,
                        shape = shape,
                        containerColor = effects.containerColor(containerColor),
                        minHeight = effects.style.rowMinHeight,
                        horizontalPadding = effects.style.rowHorizontalPadding,
                        verticalPadding = effects.style.rowVerticalPadding,
                        checkedTrackColor = effects.style.toggleTrackColor,
                    )
                }
                item.kind == BrowserFeatureMenuItemKind.Toggle && item.checked != null -> {
                    BrowserMenuToggleItem(
                        label = resources.label(item),
                        supportingText = resources.supportingText(item, snoozedTabCount).orEmpty(),
                        checked = item.checked,
                        enabled = item.enabled,
                        onCheckedChange = { onToggle(item) },
                        modifier = itemModifier,
                        shape = shape,
                        containerColor = effects.containerColor(containerColor),
                        minHeight = effects.style.rowMinHeight,
                        horizontalPadding = effects.style.rowHorizontalPadding,
                        verticalPadding = effects.style.rowVerticalPadding,
                        checkedTrackColor = effects.style.toggleTrackColor,
                    )
                }
                else -> {
                    BrowserMenuRow(
                        label = resources.label(item),
                        icon = { resources.icon(item, Modifier.size(20.dp)) },
                        shape = shape,
                        onClick = { onCommand(item) },
                        modifier = itemModifier,
                        enabled = item.enabled,
                        containerColor = effects.containerColor(
                            color = containerColor,
                            frostedAlpha = 0.68f,
                            role = if (containerColor == MaterialTheme.colorScheme.tertiaryContainer) {
                                BrowserMainMenuContainerRole.Branded
                            } else {
                                BrowserMainMenuContainerRole.Regular
                            },
                        ),
                        contentColor = contentColor,
                        supportingText = resources.supportingText(item, snoozedTabCount),
                        trailingContent = if (item.hasTrailingIcon()) {
                            { resources.trailingIcon(Modifier.size(20.dp)) }
                        } else {
                            null
                        },
                        minHeight = effects.style.rowMinHeight,
                        horizontalPadding = effects.style.rowHorizontalPadding,
                        verticalPadding = effects.style.rowVerticalPadding,
                    )
                }
            }
        }
    }
}

private fun BrowserFeatureMenuItem.hasTrailingIcon(): Boolean = action in setOf(
    BrowserFeatureMenuAction.OpenSnoozedTabs,
    BrowserFeatureMenuAction.OpenHistory,
    BrowserFeatureMenuAction.OpenSettings,
)

private fun BrowserFeatureMenuItem.testTagModifier(): Modifier = when (action) {
    BrowserFeatureMenuAction.ToggleFavorite -> Modifier.testTag(BrowserMainMenuTestTags.Favorite)
    BrowserFeatureMenuAction.TogglePinned -> Modifier.testTag(BrowserMainMenuTestTags.Pin)
    BrowserFeatureMenuAction.TranslatePage -> Modifier.testTag(BrowserMainMenuTestTags.Translate)
    BrowserFeatureMenuAction.FindInPage -> Modifier.testTag(BrowserMainMenuTestTags.FindInPage)
    BrowserFeatureMenuAction.DuplicateTab -> Modifier.testTag(BrowserMainMenuTestTags.DuplicateTab)
    BrowserFeatureMenuAction.ToggleCookieBannerRemoval ->
        Modifier.testTag(BrowserMainMenuTestTags.CookieBannerRemoval)
    BrowserFeatureMenuAction.ToggleForceVerticalScrolling ->
        Modifier.testTag(BrowserMainMenuTestTags.ForceVerticalScrolling)
    BrowserFeatureMenuAction.ToggleForcePageZooming ->
        Modifier.testTag(BrowserMainMenuTestTags.ForcePageZooming)
    BrowserFeatureMenuAction.ToggleForceSafeArea ->
        Modifier.testTag(BrowserMainMenuTestTags.ForceSafeArea)
    BrowserFeatureMenuAction.ToggleAlwaysBlockPopups ->
        Modifier.testTag(BrowserMainMenuTestTags.AlwaysBlockPopups)
    BrowserFeatureMenuAction.ToggleDesktopView -> Modifier.testTag(BrowserMainMenuTestTags.DesktopView)
    BrowserFeatureMenuAction.ToggleDomainMute -> Modifier.testTag(DomainMuteMenuTestTags.Item)
    BrowserFeatureMenuAction.SnoozeTab -> Modifier.testTag(BrowserMainMenuTestTags.Snooze)
    BrowserFeatureMenuAction.DockAddressBar -> Modifier.testTag(BrowserMainMenuTestTags.DockAddressBar)
    BrowserFeatureMenuAction.OpenSnoozedTabs -> Modifier.testTag(BrowserMainMenuTestTags.SnoozedTabs)
    BrowserFeatureMenuAction.OpenHistory -> Modifier.testTag(BrowserMainMenuTestTags.History)
    BrowserFeatureMenuAction.OpenSettings -> Modifier.testTag(BrowserMainMenuTestTags.Settings)
    BrowserFeatureMenuAction.InvokeToppingCommand ->
        Modifier.testTag(BrowserMainMenuTestTags.userScriptCommand(toppingCommandId.orEmpty()))
    else -> Modifier
}

object BrowserMainMenuTestTags {
    const val Menu = "browser_main_menu"
    const val Toolbar = "browser_main_menu_toolbar"
    const val Favorite = "browser_main_menu_favorite"
    const val Pin = "browser_main_menu_pin"
    const val PageGroup = "browser_main_menu_page_group"
    const val Translate = "browser_main_menu_translate"
    const val CandyGroup = "browser_main_menu_candy_group"
    const val ToppingsGroup = "browser_main_menu_toppings_group"
    const val BrowserGroup = "browser_main_menu_browser_group"
    const val History = "browser_main_menu_history"
    const val Settings = "browser_main_menu_settings"
    const val Snooze = "browser_main_menu_snooze"
    const val SnoozedTabs = "browser_main_menu_snoozed_tabs"
    const val DockAddressBar = "browser_main_menu_dock_address_bar"
    const val CookieBannerRemoval = "browser_main_menu_cookie_banner_removal"
    const val AlwaysBlockPopups = "browser_main_menu_always_block_popups"
    const val ForceVerticalScrolling = "browser_main_menu_force_vertical_scrolling"
    const val DesktopView = "browser_main_menu_desktop_view"
    const val FindInPage = "browser_main_menu_find_in_page"
    const val DuplicateTab = "browser_main_menu_duplicate_tab"
    const val ForcePageZooming = "browser_main_menu_force_page_zooming"
    const val ForceSafeArea = "browser_main_menu_force_safe_area"

    fun userScriptCommand(commandId: String): String =
        "browser_main_menu_topping_command_$commandId"
}

object DomainMuteMenuTestTags {
    const val Item = "domain_mute_menu_item"
}

const val BROWSER_MAIN_MENU_MAX_HEIGHT_FRACTION = 0.8f
