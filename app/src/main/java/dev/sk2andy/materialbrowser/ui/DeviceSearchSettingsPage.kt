package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsFeatureIllustration
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsIllustrationKind
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.integration.devicesearch.DeviceSearchItem
import dev.sk2andy.materialbrowser.browser.integration.devicesearch.DeviceSearchKind
import dev.sk2andy.materialbrowser.browser.integration.devicesearch.DeviceSearchSettings

internal object DeviceSearchTestTags {
    const val Enabled = "device_search_enabled"
    fun item(id: String): String = "device_search_item:$id"
}

@Composable
internal fun DeviceSearchSettingsPage(
    settings: DeviceSearchSettings,
    items: List<DeviceSearchItem>,
    profiles: List<BrowserProfile>,
    busy: Boolean,
    failed: Boolean,
    onSettingsChanged: (DeviceSearchSettings) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPage(title = stringResource(R.string.device_search_title), onBack = onBack) {
        Text(
            stringResource(R.string.device_search_summary),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        SettingsFeatureIllustration(
            SettingsIllustrationKind.DeviceSearch,
            active = settings.enabled,
        )
        SettingsPageSpacer()
        SettingsSwitch(
            title = stringResource(R.string.device_search_enable),
            subtitle = stringResource(R.string.device_search_enable_summary),
            checked = settings.enabled,
            prominent = true,
            enabled = !busy,
            onCheckedChange = { if (!busy) onSettingsChanged(settings.copy(enabled = it)) },
            modifier = Modifier.testTag(DeviceSearchTestTags.Enabled),
        )
        Text(
            stringResource(R.string.device_search_launcher_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        if (profiles.isNotEmpty()) {
            SettingsSectionTitle(stringResource(R.string.device_search_bookmark_profile))
            SettingsPreferenceGroup {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp).horizontalScroll(rememberScrollState())) {
                    profiles.forEach { profile ->
                        FilterChip(
                            selected = settings.bookmarkProfileId == profile.id,
                            onClick = { onSettingsChanged(settings.copy(bookmarkProfileId = profile.id)) },
                            enabled = !busy && settings.enabled,
                            label = { Text(profile.emoji) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
            }
        }
        if (busy) Text(stringResource(R.string.device_search_saving), modifier = Modifier.padding(vertical = 8.dp))
        if (failed) {
            Text(
                stringResource(R.string.device_search_failed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        DeviceSearchKind.entries.forEach { kind ->
            SettingsSectionTitle(stringResource(
                if (kind == DeviceSearchKind.Bookmark) R.string.device_search_bookmarks else R.string.device_search_articles,
            ))
            val sectionItems = items.filter { it.kind == kind }
            if (sectionItems.isEmpty()) {
                Text(
                    stringResource(R.string.device_search_empty),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            SettingsPreferenceGroup {
                sectionItems.forEachIndexed { index, item ->
                    if (index > 0) SettingsPreferenceDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall)
                            Text(item.sourceUrl, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                        Checkbox(
                            checked = item.id in settings.selectedIds,
                            enabled = !busy && settings.enabled,
                            onCheckedChange = { selected ->
                                onSettingsChanged(settings.copy(
                                    selectedIds = if (selected) settings.selectedIds + item.id else settings.selectedIds - item.id,
                                ))
                            },
                            modifier = Modifier.testTag(DeviceSearchTestTags.item(item.id)).semantics { contentDescription = item.title },
                        )
                    }
                }
            }
        }
    }
}
