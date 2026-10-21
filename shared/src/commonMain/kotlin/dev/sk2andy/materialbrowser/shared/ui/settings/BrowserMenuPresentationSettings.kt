package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
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
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuConfigurationSection
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuEntry
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuPresentation

object BrowserMenuPresentationSettingsTestTags {
    const val Presentation = "browser_menu_settings_presentation"
    const val ContentLayout = "browser_menu_settings_content_layout"

    fun toolbarSlot(index: Int): String = "browser_menu_settings_toolbar_slot_$index"
    fun toolbarEntry(entry: BrowserMenuEntry?): String = "browser_menu_settings_toolbar_entry_${entry?.stableId ?: "empty"}"
}

interface BrowserMenuPresentationSettingsResources {
    @Composable
    fun back(): String

    @Composable
    fun sectionTitle(section: BrowserMenuConfigurationSection): String

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
    onOpenPresentation: () -> Unit,
    onOpenContentLayout: () -> Unit,
) {
    SettingsPreferenceGroup(containerColor = containerColor) {
        SettingsSelectionLink(
            title = resources.presentationTitle(),
            value = resources.presentationLabel(layout.presentation),
            modifier = Modifier.testTag(BrowserMenuPresentationSettingsTestTags.Presentation),
            onClick = onOpenPresentation,
        )
        SettingsPreferenceDivider()
        SettingsSelectionLink(
            title = resources.contentLayoutTitle(),
            value = resources.contentLayoutLabel(layout.bottomSheetContentLayout),
            modifier = Modifier.testTag(BrowserMenuPresentationSettingsTestTags.ContentLayout),
            enabled = layout.presentation == BrowserMenuPresentation.BottomSheet,
            onClick = onOpenContentLayout,
        )
        Text(
            text = resources.presentationSummary(),
            modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 18.dp, bottom = 14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(16.dp))
    BrowserMenuBottomSheetToolbarSettings(
        layout = layout,
        resources = resources,
        containerColor = containerColor,
        availableEntries = availableEntries,
        enabled = layout.presentation == BrowserMenuPresentation.BottomSheet,
        onLayoutChanged = onLayoutChanged,
    )
}

@Composable
private fun BrowserMenuBottomSheetToolbarSettings(
    layout: BrowserMenuLayout,
    resources: BrowserMenuPresentationSettingsResources,
    containerColor: Color,
    availableEntries: List<BrowserMenuEntry>,
    enabled: Boolean,
    onLayoutChanged: (BrowserMenuLayout) -> Unit,
) {
    var expandedSlot by remember { mutableStateOf<Int?>(null) }
    val entries = BrowserMenuBottomSheetToolbarRules.normalize(layout.bottomSheetToolbarEntries)
    val toolbarEntries = BrowserMenuBottomSheetToolbarRules.availableEntries().filter(availableEntries::contains)
    SettingsSectionTitle(resources.toolbarTitle())
    Spacer(Modifier.height(8.dp))
    SettingsPreferenceGroup(containerColor = containerColor) {
        entries.forEachIndexed { index, currentEntry ->
            SettingsSelectionLink(
                title = resources.toolbarSlotTitle(index + 1),
                value = currentEntry?.let { entry -> resources.entryLabel(entry) }
                    ?: resources.emptySlotLabel(),
                onClick = { expandedSlot = index },
                modifier = Modifier.testTag(BrowserMenuPresentationSettingsTestTags.toolbarSlot(index)),
                enabled = enabled,
            )
            if (index != entries.lastIndex) SettingsPreferenceDivider()
        }
        Text(
            text = resources.toolbarSummary(),
            modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 18.dp, bottom = 14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(16.dp))
    expandedSlot?.takeIf { enabled && it in entries.indices }?.let { index ->
        val currentEntry = entries[index]
        SettingsSelectionDialog(
            title = resources.toolbarSlotTitle(index + 1),
            backContentDescription = resources.back(),
            artwork = SettingsHeaderArtwork.Gestures,
            onDismissRequest = { expandedSlot = null },
        ) {
            SettingsRadioOption(
                label = resources.emptySlotLabel(),
                selected = currentEntry == null,
                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                modifier = Modifier.testTag(BrowserMenuPresentationSettingsTestTags.toolbarEntry(null)),
                onClick = {
                    expandedSlot = null
                    if (currentEntry != null) {
                        onLayoutChanged(BrowserMenuBottomSheetToolbarRules.assign(layout, index, null))
                    }
                },
            )
            BrowserMenuConfigurationSection.entries.forEach { section ->
                val sectionEntries = toolbarEntries.filter { it.section == section }
                if (sectionEntries.isNotEmpty()) {
                    SettingsSectionTitle(resources.sectionTitle(section))
                    sectionEntries.forEach { entry ->
                        SettingsPreferenceDivider()
                        SettingsRadioOption(
                            label = resources.entryLabel(entry),
                            selected = entry == currentEntry,
                            leadingIcon = { BrowserMenuSettingsEntryIcon(entry) },
                            modifier = Modifier.testTag(BrowserMenuPresentationSettingsTestTags.toolbarEntry(entry)),
                            onClick = {
                                expandedSlot = null
                                if (entry != currentEntry) {
                                    onLayoutChanged(BrowserMenuBottomSheetToolbarRules.assign(layout, index, entry))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Static action identity; choosing a toolbar slot must not depend on current page state. */
@Composable
internal fun BrowserMenuSettingsEntryIcon(entry: BrowserMenuEntry) {
    val imageVector = when (entry) {
        BrowserMenuEntry.Back -> Icons.AutoMirrored.Filled.ArrowBack
        BrowserMenuEntry.Forward -> Icons.AutoMirrored.Filled.ArrowForward
        BrowserMenuEntry.Reload -> Icons.Default.Refresh
        BrowserMenuEntry.Favorite -> Icons.Default.Star
        BrowserMenuEntry.Pin -> Icons.Default.PushPin
        BrowserMenuEntry.ShowTabs -> Icons.Default.ViewCarousel
        BrowserMenuEntry.NewTab -> Icons.Default.Add
        BrowserMenuEntry.CloseTab -> Icons.Default.Close
        BrowserMenuEntry.DuplicateTab -> Icons.Default.ContentCopy
        BrowserMenuEntry.Reader -> Icons.AutoMirrored.Filled.MenuBook
        BrowserMenuEntry.WebAnnotations -> Icons.Default.Edit
        BrowserMenuEntry.Translate -> Icons.Default.Translate
        BrowserMenuEntry.FindInPage -> Icons.Default.Search
        BrowserMenuEntry.Share -> Icons.Default.Share
        BrowserMenuEntry.OpenExternal -> Icons.AutoMirrored.Filled.OpenInNew
        BrowserMenuEntry.Print -> Icons.Default.Print
        BrowserMenuEntry.CookieBannerRemoval -> Icons.Default.Block
        BrowserMenuEntry.ForceVerticalScrolling -> Icons.Default.SwapVert
        BrowserMenuEntry.ForcePageZooming -> Icons.Default.ZoomIn
        BrowserMenuEntry.ForceSafeArea -> Icons.Default.CropFree
        BrowserMenuEntry.AlwaysBlockPopups -> Icons.Default.Block
        BrowserMenuEntry.DesktopView -> Icons.Default.DesktopWindows
        BrowserMenuEntry.DomainMute -> Icons.AutoMirrored.Filled.VolumeOff
        BrowserMenuEntry.StatusBarScroll -> Icons.Default.ArrowUpward
        BrowserMenuEntry.ToppingCommands -> Icons.Default.Extension
        BrowserMenuEntry.FirefoxPageActions -> Icons.Default.Extension
        BrowserMenuEntry.CandyTrail -> Icons.Default.Route
        BrowserMenuEntry.Constellations -> Icons.Default.Route
        BrowserMenuEntry.AddSiteCapsule -> Icons.AutoMirrored.Filled.AddToHomeScreen
        BrowserMenuEntry.CreateSiteWidget -> Icons.AutoMirrored.Filled.AddToHomeScreen
        BrowserMenuEntry.Summarize -> Icons.Default.AutoAwesome
        BrowserMenuEntry.Snooze -> Icons.Default.Snooze
        BrowserMenuEntry.AddressBarDocking -> Icons.Default.ChevronRight
        BrowserMenuEntry.OpenSnoozedTabs -> Icons.Default.Snooze
        BrowserMenuEntry.OpenFavorites -> Icons.Default.Star
        BrowserMenuEntry.OpenDownloads -> Icons.Default.Download
        BrowserMenuEntry.OpenHistory -> Icons.Default.History
        BrowserMenuEntry.OpenFirefoxExtensions -> Icons.Default.Extension
        BrowserMenuEntry.OpenSettings -> Icons.Default.Settings
        BrowserMenuEntry.MoveToProfile -> Icons.AutoMirrored.Filled.OpenInNew
        BrowserMenuEntry.TabStacks -> Icons.Default.ViewCarousel
        BrowserMenuEntry.CloseAllTabs -> Icons.Default.Close
    }
    Icon(imageVector = imageVector, contentDescription = null)
}
