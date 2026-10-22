package dev.sk2andy.materialbrowser.shared.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    emptySectionMessage: String,
    emptySectionActionLabel: String,
    onOpenMenuSettings: () -> Unit,
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
    customShortcutSlots: Map<Int, BrowserCustomAction?> = emptyMap(),
    customFooterSlots: Map<Int, BrowserCustomAction?> = emptyMap(),
    onCustomAction: (String) -> Unit = {},
    customContent: @Composable ColumnScope.((() -> Unit) -> Unit) -> Unit = {},
) {
    val sections = BrowserMenuBottomSheetRules.sections(items, toolbarEntries)
    val primaryActions = sections.pageActions.filterNot {
        it.section == BrowserFeatureMenuSection.Reading || it.section == BrowserFeatureMenuSection.Toppings
    }
    val readingActions = sections.pageActions.filter { it.section == BrowserFeatureMenuSection.Reading }
    val workflows = sections.pageActions.filter { it.section == BrowserFeatureMenuSection.Toppings }
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
            CandySheetDragIndicator()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = addressBarHorizontalPadding)
                .testTag(BrowserMenuBottomSheetTestTags.AddressBar),
        ) {
            addressBar()
        }
        if (sections.shortcuts.isNotEmpty() || customShortcutSlots.isNotEmpty()) {
            Column(
                Modifier
                    .then(fadingContent)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = if (compactHeight) 4.dp else 8.dp)
                    .testTag(BrowserMenuBottomSheetTestTags.Shortcuts),
            ) {
                if (!compactHeight) SheetSectionTitle(quickActionsTitle)
                Row(
                    Modifier.fillMaxWidth()
                        .heightIn(min = if (compactHeight) 48.dp else 80.dp)
                        .height(IntrinsicSize.Min),
                ) {
                    sections.shortcutSlots.forEachIndexed { index, item ->
                        if (customShortcutSlots.containsKey(index)) {
                            val action = customShortcutSlots[index]
                            if (action == null) {
                                Spacer(Modifier.weight(1f))
                            } else {
                                BrowserMenuToolbarAction(
                                    label = action.label,
                                    icon = { Icon(action.icon, null, Modifier.size(24.dp)) },
                                    onClick = { onCustomAction(action.id) },
                                    enabled = action.enabled,
                                    accessibilityLabel = action.label,
                                    showLabel = !compactHeight,
                                    minHeight = if (compactHeight) 48.dp else 80.dp,
                                    shape = CircleShape,
                                    containerColor = effects.containerColor(
                                        MaterialTheme.colorScheme.surfaceContainerLowest,
                                    ),
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                        .testTag("custom_workflow_shortcut:$index"),
                                )
                            }
                        } else if (item == null) {
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
                        if (primaryActions.isEmpty()) {
                            EmptyMenuSection(emptySectionMessage, emptySectionActionLabel, onOpenMenuSettings)
                        } else {
                            SheetActionGroups(
                                primaryActions, resources, effects, snoozedTabCount, onCommand, onToggle,
                            )
                        }
                        extensionContent(onExtensionCommit)
                        SheetActionGroups(readingActions, resources, effects, snoozedTabCount, onCommand, onToggle)
                        customContent(onExtensionCommit)
                        BrowserMainMenuWorkflows(workflows, resources, effects, snoozedTabCount, onCommand, onToggle)
                    } else {
                        if (sections.pageControls.isEmpty()) {
                            EmptyMenuSection(emptySectionMessage, emptySectionActionLabel, onOpenMenuSettings)
                        } else {
                            SheetControlGrid(sections.pageControls, resources, effects, onToggle)
                        }
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
                val bothPageSectionsEmpty =
                    primaryActions.isEmpty() && sections.pageControls.isEmpty()
                if (bothPageSectionsEmpty) {
                    EmptyMenuSection(emptySectionMessage, emptySectionActionLabel, onOpenMenuSettings)
                } else if (primaryActions.isEmpty()) {
                    EmptyMenuSection(emptySectionMessage, emptySectionActionLabel, onOpenMenuSettings)
                } else {
                    SheetSectionTitle(pageActionsTitle)
                    SheetActionGroups(
                        primaryActions, resources, effects, snoozedTabCount, onCommand, onToggle,
                    )
                }
                extensionContent(onExtensionCommit)
                if (!bothPageSectionsEmpty) {
                    if (sections.pageControls.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        SheetSectionTitle(pageControlsTitle)
                        SheetControlGrid(sections.pageControls, resources, effects, onToggle)
                    } else {
                        Spacer(Modifier.height(16.dp))
                        EmptyMenuSection(emptySectionMessage, emptySectionActionLabel, onOpenMenuSettings)
                    }
                }
                SheetActionGroups(readingActions, resources, effects, snoozedTabCount, onCommand, onToggle)
                customContent(onExtensionCommit)
                BrowserMainMenuWorkflows(workflows, resources, effects, snoozedTabCount, onCommand, onToggle)
            }
        }
        if (sections.footer.isNotEmpty() || customFooterSlots.isNotEmpty()) {
            Box(
                Modifier
                    .then(fadingContent)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = if (compactHeight) 8.dp else 12.dp)
                    .testTag(BrowserMenuBottomSheetTestTags.Footer),
            ) {
                Row(Modifier.fillMaxWidth().testTag(BrowserMainMenuTestTags.Toolbar)) {
                    sections.footerSlots.forEachIndexed { index, item ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (customFooterSlots.containsKey(index)) {
                                BrowserCustomActionButton(
                                    action = customFooterSlots[index],
                                    onAction = onCustomAction,
                                    modifier = Modifier.size(effects.style.toolbarButtonSize)
                                        .testTag("custom_workflow_footer:$index"),
                                )
                            } else if (item != null) {
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
private fun EmptyMenuSection(
    message: String,
    actionLabel: String,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        TextButton(onClick = onOpenSettings) { Text(actionLabel) }
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
                            item.checked == true -> effects.selectedToggleContentColor()
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
