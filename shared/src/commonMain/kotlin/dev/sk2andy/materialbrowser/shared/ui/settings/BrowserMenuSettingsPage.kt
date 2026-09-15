package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
) {
    var expandedEntry by remember { mutableStateOf<BrowserMenuEntry?>(null) }
    SettingsPage(
        title = resources.title(),
        backContentDescription = resources.back(),
        onBack = onBack,
    ) {
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
            entries.forEachIndexed { index, entry ->
                val currentLocation = BrowserMenuLayoutRules.location(layout, entry)
                Box(modifier = Modifier.testTag(BrowserMenuSettingsTestTags.entry(entry))) {
                    SettingsChoice(
                        title = resources.entryLabel(entry),
                        value = resources.locationLabel(currentLocation),
                        expanded = expandedEntry == entry,
                        onClick = { expandedEntry = entry },
                        containerColor = containerColor,
                    )
                    SettingsDropdown(
                        expanded = expandedEntry == entry,
                        onDismissRequest = { expandedEntry = null },
                    ) {
                        BrowserMenuLayoutRules.allowedLocations(entry).forEach { location ->
                            SettingsDropdownItem(
                                label = resources.locationLabel(location),
                                selected = currentLocation == location,
                                onClick = {
                                    expandedEntry = null
                                    if (location != currentLocation) {
                                        onLocationChanged(entry, location)
                                    }
                                },
                            )
                        }
                    }
                }
                if (index != entries.lastIndex) Spacer(Modifier.height(4.dp))
            }
            if (section != BrowserMenuConfigurationSection.entries.last()) {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
