package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Web
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
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuConfigurationSection
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuEntry
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayoutRules
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLocation

object BrowserMenuSettingsTestTags {
    const val Intro = "browser_menu_settings_intro"

    fun entry(entry: BrowserMenuEntry): String = "browser_menu_settings_${entry.stableId}"
    fun location(location: BrowserMenuLocation): String = "browser_menu_settings_location_${location.stableId}"
}

interface BrowserMenuSettingsResources {
    @Composable
    fun title(): String

    @Composable
    fun back(): String

    @Composable
    fun intro(): String

    @Composable
    fun sectionTitle(section: BrowserMenuConfigurationSection): String

    @Composable
    fun entryLabel(entry: BrowserMenuEntry): String

    @Composable
    fun locationLabel(location: BrowserMenuLocation): String
}

@Composable
fun BrowserMenuSettingsPage(
    layout: BrowserMenuLayout,
    resources: BrowserMenuSettingsResources,
    containerColor: Color,
    availableEntries: List<BrowserMenuEntry> = BrowserMenuEntry.entries,
    onLocationChanged: (BrowserMenuEntry, BrowserMenuLocation) -> Unit,
    onBack: () -> Unit,
    presentationSettings: @Composable () -> Unit = {},
) {
    var expandedEntry by remember { mutableStateOf<BrowserMenuEntry?>(null) }
    SettingsPage(
        title = resources.title(),
        backContentDescription = resources.back(),
        onBack = onBack,
    ) {
        presentationSettings()
        Text(
            text = resources.intro(),
            modifier = Modifier.testTag(BrowserMenuSettingsTestTags.Intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        BrowserMenuConfigurationSection.entries.forEach { section ->
            val entries = availableEntries.filter { entry -> entry.section == section }
            if (entries.isEmpty()) return@forEach
            SettingsSectionTitle(resources.sectionTitle(section))
            Spacer(Modifier.height(8.dp))
            SettingsPreferenceGroup(containerColor = containerColor) {
                entries.forEachIndexed { index, entry ->
                    val currentLocation = BrowserMenuLayoutRules.location(layout, entry)
                    SettingsSelectionLink(
                        title = resources.entryLabel(entry),
                        value = resources.locationLabel(currentLocation),
                        modifier = Modifier.testTag(BrowserMenuSettingsTestTags.entry(entry)),
                        onClick = { expandedEntry = entry },
                    )
                    if (index != entries.lastIndex) SettingsPreferenceDivider()
                }
            }
            if (section != BrowserMenuConfigurationSection.entries.last()) {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    expandedEntry?.takeIf(availableEntries::contains)?.let { entry ->
        val currentLocation = BrowserMenuLayoutRules.location(layout, entry)
        SettingsSelectionDialog(
            title = resources.entryLabel(entry),
            backContentDescription = resources.back(),
            artwork = SettingsHeaderArtwork.Gestures,
            onDismissRequest = { expandedEntry = null },
        ) {
            BrowserMenuLayoutRules.allowedLocations(entry).forEachIndexed { index, location ->
                if (index > 0) SettingsPreferenceDivider()
                SettingsRadioOption(
                    label = resources.locationLabel(location),
                    selected = currentLocation == location,
                    modifier = Modifier.testTag(BrowserMenuSettingsTestTags.location(location)),
                    leadingIcon = {
                        Icon(
                            imageVector = when (location) {
                                BrowserMenuLocation.Nowhere -> Icons.Default.Block
                                BrowserMenuLocation.Tab -> Icons.Default.Web
                                BrowserMenuLocation.TabSwitcher -> Icons.Default.ViewCarousel
                                BrowserMenuLocation.Both -> Icons.Default.ContentCopy
                            },
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        expandedEntry = null
                        if (location != currentLocation) onLocationChanged(entry, location)
                    },
                )
            }
        }
    }
}
