package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuBottomSheetContentLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuBottomSheetToolbarRules
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuEntry
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuPresentation

object BrowserMenuPresentationSettingsTestTags {
    const val Presentation = "browser_menu_settings_presentation"
    const val ContentLayout = "browser_menu_settings_content_layout"

    fun toolbarSlot(index: Int): String = "browser_menu_settings_toolbar_slot_$index"
}

interface BrowserMenuPresentationSettingsResources {
    @Composable
    fun presentationTitle(): String

    @Composable
    fun presentationSummary(): String

    @Composable
    fun presentationLabel(presentation: BrowserMenuPresentation): String

    @Composable
    fun contentLayoutTitle(): String

    @Composable
    fun contentLayoutLabel(layout: BrowserMenuBottomSheetContentLayout): String

    @Composable
    fun toolbarTitle(): String

    @Composable
    fun toolbarSummary(): String

    @Composable
    fun toolbarSlotTitle(position: Int): String

    @Composable
    fun emptySlotLabel(): String

    @Composable
    fun entryLabel(entry: BrowserMenuEntry): String
}

@Composable
fun BrowserMenuPresentationSettings(
    layout: BrowserMenuLayout,
    resources: BrowserMenuPresentationSettingsResources,
    containerColor: Color,
    availableEntries: List<BrowserMenuEntry>,
    onLayoutChanged: (BrowserMenuLayout) -> Unit,
) {
    var presentationExpanded by remember { mutableStateOf(false) }
    var contentLayoutExpanded by remember { mutableStateOf(false) }
    Box {
        SettingsChoice(
            title = resources.presentationTitle(),
            value = resources.presentationLabel(layout.presentation),
            expanded = presentationExpanded,
            onClick = { presentationExpanded = true },
            containerColor = containerColor,
            modifier = Modifier.testTag(BrowserMenuPresentationSettingsTestTags.Presentation),
        )
        SettingsDropdown(
            expanded = presentationExpanded,
            onDismissRequest = { presentationExpanded = false },
        ) {
            BrowserMenuPresentation.entries.forEach { presentation ->
                SettingsDropdownItem(
                    label = resources.presentationLabel(presentation),
                    selected = presentation == layout.presentation,
                    onClick = {
                        presentationExpanded = false
                        if (presentation != layout.presentation) onLayoutChanged(layout.copy(presentation = presentation))
                    },
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Box {
        SettingsChoice(
            title = resources.contentLayoutTitle(),
            value = resources.contentLayoutLabel(layout.bottomSheetContentLayout),
            expanded = contentLayoutExpanded,
            onClick = { contentLayoutExpanded = true },
            containerColor = containerColor,
            modifier = Modifier.testTag(BrowserMenuPresentationSettingsTestTags.ContentLayout),
        )
        SettingsDropdown(
            expanded = contentLayoutExpanded,
            onDismissRequest = { contentLayoutExpanded = false },
        ) {
            BrowserMenuBottomSheetContentLayout.entries.forEach { contentLayout ->
                SettingsDropdownItem(
                    label = resources.contentLayoutLabel(contentLayout),
                    selected = contentLayout == layout.bottomSheetContentLayout,
                    onClick = {
                        contentLayoutExpanded = false
                        if (contentLayout != layout.bottomSheetContentLayout) {
                            onLayoutChanged(layout.copy(bottomSheetContentLayout = contentLayout))
                        }
                    },
                )
            }
        }
    }
    Text(
        text = resources.presentationSummary(),
        modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 18.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    if (layout.presentation == BrowserMenuPresentation.BottomSheet) {
        BrowserMenuBottomSheetToolbarSettings(
            layout = layout,
            resources = resources,
            containerColor = containerColor,
            availableEntries = availableEntries,
            onLayoutChanged = onLayoutChanged,
        )
    }
}

@Composable
private fun BrowserMenuBottomSheetToolbarSettings(
    layout: BrowserMenuLayout,
    resources: BrowserMenuPresentationSettingsResources,
    containerColor: Color,
    availableEntries: List<BrowserMenuEntry>,
    onLayoutChanged: (BrowserMenuLayout) -> Unit,
) {
    var expandedSlot by remember { mutableStateOf<Int?>(null) }
    val entries = BrowserMenuBottomSheetToolbarRules.normalize(layout.bottomSheetToolbarEntries)
    val toolbarEntries = BrowserMenuBottomSheetToolbarRules.availableEntries().filter(availableEntries::contains)
    SettingsSectionTitle(resources.toolbarTitle())
    Spacer(Modifier.height(8.dp))
    entries.forEachIndexed { index, currentEntry ->
        Box {
            SettingsChoice(
                title = resources.toolbarSlotTitle(index + 1),
                value = currentEntry?.let { entry -> resources.entryLabel(entry) }
                    ?: resources.emptySlotLabel(),
                expanded = expandedSlot == index,
                onClick = { expandedSlot = index },
                containerColor = containerColor,
                modifier = Modifier.testTag(BrowserMenuPresentationSettingsTestTags.toolbarSlot(index)),
            )
            SettingsDropdown(
                expanded = expandedSlot == index,
                onDismissRequest = { expandedSlot = null },
            ) {
                (listOf(null) + toolbarEntries).forEach { entry ->
                    SettingsDropdownItem(
                        label = entry?.let { resources.entryLabel(it) }
                            ?: resources.emptySlotLabel(),
                        selected = entry == currentEntry,
                        onClick = {
                            expandedSlot = null
                            if (entry != currentEntry) {
                                onLayoutChanged(
                                    BrowserMenuBottomSheetToolbarRules.assign(layout, index, entry),
                                )
                            }
                        },
                    )
                }
            }
        }
        if (index != entries.lastIndex) Spacer(Modifier.height(4.dp))
    }
    Text(
        text = resources.toolbarSummary(),
        modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 18.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
}
