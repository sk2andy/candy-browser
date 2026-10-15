package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuItem
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuItemKind
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuSection
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuBottomSheetContentLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuBottomSheetRules
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuBottomSheetToolbarRules
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuEntry
import kotlinx.coroutines.launch

/** Shared menu actions with fixed address, shortcut and navigation regions. */
@Composable
fun BrowserMenuBottomSheetContent(
    expanded: Boolean,
    items: List<BrowserFeatureMenuItem>,
    contentLayout: BrowserMenuBottomSheetContentLayout,
    resources: BrowserMainMenuResources,
    effects: BrowserMainMenuEffects,
    snoozedTabCount: Int,
    quickActionsTitle: String,
    pageActionsTitle: String,
    pageControlsTitle: String,
    handleDescription: String,
    addressBar: @Composable () -> Unit,
    onCommand: (BrowserFeatureMenuItem) -> Unit,
    onToggle: (BrowserFeatureMenuItem) -> Unit,
    onExtensionCommit: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
    compactHeight: Boolean = false,
    surfaceAlpha: Float = 1f,
    addressBarHorizontalPadding: Dp = 16.dp,
    toolbarEntries: List<BrowserMenuEntry?> = BrowserMenuBottomSheetToolbarRules.DefaultEntries,
    extensionContent: @Composable ColumnScope.((() -> Unit) -> Unit) -> Unit = {},
) {
    val sections = BrowserMenuBottomSheetRules.sections(items, toolbarEntries)
    val scrollState = rememberScrollState()
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val fadingContent = Modifier.graphicsLayer { alpha = surfaceAlpha.coerceIn(0f, 1f) }
    LaunchedEffect(expanded, contentLayout) {
        if (expanded) {
            scrollState.scrollTo(0)
            pagerState.scrollToPage(0)
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = fadingContent
                .then(handleModifier)
                .fillMaxWidth()
                .height(24.dp)
                .semantics { contentDescription = handleDescription }
                .testTag(BrowserMenuBottomSheetTestTags.Handle),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(36.dp, 4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        CircleShape,
                    ),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = addressBarHorizontalPadding)
                .testTag(BrowserMenuBottomSheetTestTags.AddressBar),
        ) {
            addressBar()
        }
        if (sections.shortcuts.isNotEmpty()) {
            Column(
                Modifier
                    .then(fadingContent)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = if (compactHeight) 4.dp else 8.dp)
                    .testTag(BrowserMenuBottomSheetTestTags.Shortcuts),
            ) {
                if (!compactHeight) SheetSectionTitle(quickActionsTitle)
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    sections.shortcutSlots.forEach { item ->
                        if (item == null) {
                            Spacer(Modifier.weight(1f))
                        } else if (compactHeight) {
                            BrowserMenuToolbarAction(
                                label = resources.label(item),
                                icon = { resources.icon(item, Modifier.size(24.dp)) },
                                onClick = { onCommand(item) },
                                enabled = item.enabled,
                                accessibilityLabel = resources.label(item),
                                showLabel = false,
                                minHeight = 48.dp,
                                shape = CircleShape,
                                containerColor = effects.containerColor(
                                    MaterialTheme.colorScheme.surfaceContainerLowest,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .then(item.testTagModifier()),
                            )
                        } else {
                            SheetShortcutAction(
                                item = item,
                                resources = resources,
                                effects = effects,
                                onClick = { onCommand(item) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .then(item.testTagModifier()),
                            )
                        }
                    }
                }
            }
        }
        if (contentLayout == BrowserMenuBottomSheetContentLayout.SwipePages) {
            Row(fadingContent.fillMaxWidth().padding(horizontal = 16.dp)) {
                listOf(pageActionsTitle, pageControlsTitle).forEachIndexed { index, title ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .selectable(
                            selected = pagerState.currentPage == index,
                            role = Role.Tab,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        ),
                        shape = MaterialTheme.shapes.medium,
                        color = effects.containerColor(
                            if (pagerState.currentPage == index) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                        ),
                    ) {
                        Text(
                            text = title,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .then(fadingContent)
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag(BrowserMenuBottomSheetTestTags.Pager),
                verticalAlignment = Alignment.Top,
            ) { page ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag(
                            if (page == 0) BrowserMenuBottomSheetTestTags.PageActions
                            else BrowserMenuBottomSheetTestTags.PageControls,
                        ),
                ) {
                    if (page == 0) {
                        SheetActionGroups(
                            sections.pageActions, resources, effects, snoozedTabCount, onCommand, onToggle,
                        )
                        extensionContent(onExtensionCommit)
                    } else {
                        SheetControlGrid(sections.pageControls, resources, effects, onToggle)
                    }
                }
            }
        } else {
            Column(
                Modifier
                    .then(fadingContent)
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(BrowserMenuBottomSheetTestTags.Content),
            ) {
                if (sections.pageActions.isNotEmpty()) {
                    SheetSectionTitle(pageActionsTitle)
                    SheetActionGroups(
                        sections.pageActions, resources, effects, snoozedTabCount, onCommand, onToggle,
                    )
                }
                extensionContent(onExtensionCommit)
                if (sections.pageControls.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    SheetSectionTitle(pageControlsTitle)
                    SheetControlGrid(sections.pageControls, resources, effects, onToggle)
                }
            }
        }
        if (sections.footer.isNotEmpty()) {
            Box(
                Modifier
                    .then(fadingContent)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = if (compactHeight) 8.dp else 12.dp)
                    .testTag(BrowserMenuBottomSheetTestTags.Footer),
            ) {
                Row(Modifier.fillMaxWidth().testTag(BrowserMainMenuTestTags.Toolbar)) {
                    sections.footerSlots.forEach { item ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (item != null) {
                                BrowserMainMenuToolbarItem(
                                    item = item,
                                    resources = resources,
                                    effects = effects,
                                    onClick = { action ->
                                        if (action.kind == BrowserFeatureMenuItemKind.Toggle) onToggle(action)
                                        else onCommand(action)
                                    },
                                    modifier = if (effects.style.showToolbarLabels) {
                                        Modifier.fillMaxWidth()
                                    } else {
                                        Modifier.size(effects.style.toolbarButtonSize)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SheetShortcutAction(
    item: BrowserFeatureMenuItem,
    resources: BrowserMainMenuResources,
    effects: BrowserMainMenuEffects,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val actionContentColor = if (item.enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f)
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 80.dp),
        enabled = item.enabled,
        shape = RoundedCornerShape(0.dp),
        color = Color.Transparent,
        contentColor = actionContentColor,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = effects.containerColor(colors.surfaceContainerLowest),
                contentColor = actionContentColor,
            ) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    resources.icon(item, Modifier.size(effects.style.toolbarIconSize))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = resources.label(item),
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SheetActionGroups(
    items: List<BrowserFeatureMenuItem>,
    resources: BrowserMainMenuResources,
    effects: BrowserMainMenuEffects,
    snoozedTabCount: Int,
    onCommand: (BrowserFeatureMenuItem) -> Unit,
    onToggle: (BrowserFeatureMenuItem) -> Unit,
) {
    val outer = effects.style.groupCornerRadius
    val inner = effects.style.groupInnerCornerRadius
    items.groupBy(BrowserFeatureMenuItem::section).forEach { (section, group) ->
        if (section != BrowserFeatureMenuSection.Page) {
            Spacer(Modifier.height(12.dp))
            SheetSectionTitle(resources.sectionTitle(section))
        }
        BrowserMainMenuItemGroup(
            items = group,
            resources = resources,
            effects = effects,
            snoozedTabCount = snoozedTabCount,
            firstItemShape = RoundedCornerShape(outer, outer, inner, inner),
            innerCorners = RoundedCornerShape(inner),
            lastItemShape = RoundedCornerShape(inner, inner, outer, outer),
            onCommand = onCommand,
            onToggle = onToggle,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SheetControlGrid(
    items: List<BrowserFeatureMenuItem>,
    resources: BrowserMainMenuResources,
    effects: BrowserMainMenuEffects,
    onToggle: (BrowserFeatureMenuItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { item ->
                    Surface(
                        checked = item.checked == true,
                        onCheckedChange = { onToggle(item) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = 88.dp)
                            .then(item.testTagModifier()).semantics { role = Role.Checkbox },
                        enabled = item.enabled,
                        shape = MaterialTheme.shapes.large,
                        color = effects.containerColor(
                            color = when {
                                !item.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                item.checked == true -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.surfaceContainerLowest
                            },
                            frostedAlpha = 1f,
                            role = if (item.checked == true) {
                                BrowserMainMenuContainerRole.Selected
                            } else {
                                BrowserMainMenuContainerRole.Regular
                            },
                        ),
                        contentColor = when {
                            !item.enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            item.checked == true -> MaterialTheme.colorScheme.onSecondary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            resources.icon(item, Modifier.size(24.dp))
                            Text(
                                text = resources.label(item),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

object BrowserMenuBottomSheetTestTags {
    const val AddressBar = "browser_menu_sheet_address_bar"
    const val Handle = "browser_menu_sheet_handle"
    const val Shortcuts = "browser_menu_sheet_shortcuts"
    const val Content = "browser_menu_sheet_content"
    const val Pager = "browser_menu_sheet_pager"
    const val PageActions = "browser_menu_sheet_page_actions"
    const val PageControls = "browser_menu_sheet_page_controls"
    const val Footer = "browser_menu_sheet_footer"
    const val Scrim = "browser_menu_sheet_scrim"
}
