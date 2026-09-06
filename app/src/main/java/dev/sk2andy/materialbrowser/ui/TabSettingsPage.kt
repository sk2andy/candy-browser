package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserSessionResidencyRules
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.shared.ui.settings.TabDismissResistanceSettings
import dev.sk2andy.materialbrowser.shared.ui.settings.TabOverviewSettings
import dev.sk2andy.materialbrowser.shared.ui.settings.TabOverviewSettingsStrings
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor
import kotlin.math.roundToInt

internal object TabSettingsTestTags {
    const val ResidentTabLimit = "tab_settings_resident_limit"
    const val ListStartsAtBottom = "tab_settings_list_starts_at_bottom"
    const val AutomaticSorting = "tab_settings_automatic_sorting"
    const val AddressBarDocking = "tab_settings_address_bar_docking"
}

@Composable
internal fun TabsAndGesturesSettingsPage(
    inactiveTabLifetime: InactiveTabLifetime,
    residentTabLimit: Int,
    tabOverviewMode: TabOverviewMode,
    tabListStartsAtBottom: Boolean,
    automaticTabSortingEnabled: Boolean,
    dismissResistancePercent: Int,
    profilesEnabled: Boolean,
    isAddressBarDockingEnabled: Boolean,
    onInactiveTabLifetimeChanged: (InactiveTabLifetime) -> Unit,
    onResidentTabLimitChanged: (Int) -> Unit,
    onTabOverviewModeChanged: (TabOverviewMode) -> Unit,
    onTabListStartsAtBottomChanged: (Boolean) -> Unit,
    onAutomaticTabSortingEnabledChanged: (Boolean) -> Unit,
    onDismissResistancePercentChanged: (Int) -> Unit,
    onProfilesEnabledChanged: (Boolean) -> Unit,
    onAddressBarDockingEnabledChanged: (Boolean) -> Unit,
    onAddressBarActions: () -> Unit,
    onBack: () -> Unit,
) {
    var lifetimeMenuExpanded by remember { mutableStateOf(false) }
    var residentLimit by remember(residentTabLimit) {
        mutableFloatStateOf(residentTabLimit.toFloat())
    }
    SettingsPage(
        title = stringResource(R.string.settings_tabs_gestures_title),
        onBack = onBack,
    ) {
        SettingsSectionTitle(stringResource(R.string.settings_section_tabs))
        Spacer(Modifier.height(8.dp))
        TabOverviewSettings(
            mode = tabOverviewMode,
            listStartsAtBottom = tabListStartsAtBottom,
            strings = TabOverviewSettingsStrings(
                overviewMode = stringResource(R.string.settings_tab_overview_mode),
                modeNames = TabOverviewMode.entries.associateWith { it.displayName() },
                listStartsAtBottom = stringResource(
                    R.string.settings_tab_list_starts_at_bottom_title,
                ),
                listStartsAtBottomSummary = stringResource(
                    R.string.settings_tab_list_starts_at_bottom_subtitle,
                ),
            ),
            containerColor = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
            onModeChanged = onTabOverviewModeChanged,
            onListStartsAtBottomChanged = onTabListStartsAtBottomChanged,
            listStartsAtBottomModifier = Modifier.testTag(
                TabSettingsTestTags.ListStartsAtBottom,
            ),
        )
        Spacer(Modifier.height(2.dp))
        SettingsSwitch(
            title = stringResource(R.string.settings_automatic_tab_sorting_title),
            subtitle = stringResource(R.string.settings_automatic_tab_sorting_subtitle),
            checked = automaticTabSortingEnabled,
            onCheckedChange = onAutomaticTabSortingEnabledChanged,
            modifier = Modifier.testTag(TabSettingsTestTags.AutomaticSorting),
        )
        SettingsPageSpacer()
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(
                    stringResource(R.string.settings_resident_tab_limit),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    pluralStringResource(
                        R.plurals.settings_resident_tab_limit_summary,
                        residentLimit.roundToInt(),
                        residentLimit.roundToInt(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = residentLimit,
                    onValueChange = { residentLimit = it },
                    onValueChangeFinished = {
                        onResidentTabLimitChanged(residentLimit.roundToInt())
                    },
                    modifier = Modifier.testTag(TabSettingsTestTags.ResidentTabLimit),
                    valueRange = BrowserSessionResidencyRules.MIN_LIMIT.toFloat()..
                        BrowserSessionResidencyRules.MAX_LIMIT.toFloat(),
                    steps = BrowserSessionResidencyRules.MAX_LIMIT -
                        BrowserSessionResidencyRules.MIN_LIMIT - 1,
                )
            }
        }
        SettingsPageSpacer()
        Box {
            SettingsChoice(
                title = stringResource(R.string.settings_auto_close_tabs),
                value = inactiveTabLifetime.displayName(),
                expanded = lifetimeMenuExpanded,
                onClick = { lifetimeMenuExpanded = true },
            )
            SettingsDropdown(
                expanded = lifetimeMenuExpanded,
                onDismissRequest = { lifetimeMenuExpanded = false },
            ) {
                InactiveTabLifetime.entries.forEach { lifetime ->
                    SettingsDropdownItem(
                        label = lifetime.displayName(),
                        selected = lifetime == inactiveTabLifetime,
                        onClick = {
                            lifetimeMenuExpanded = false
                            onInactiveTabLifetimeChanged(lifetime)
                        },
                    )
                }
            }
        }
        SettingsPageSpacer()
        SettingsSwitch(
            title = stringResource(R.string.settings_profiles_title),
            subtitle = stringResource(R.string.settings_profiles_subtitle),
            checked = profilesEnabled,
            onCheckedChange = onProfilesEnabledChanged,
        )
        Spacer(Modifier.height(14.dp))
        SettingsSectionTitle(stringResource(R.string.settings_section_gestures))
        Spacer(Modifier.height(2.dp))
        SettingsLink(
            icon = ImageVector.vectorResource(R.drawable.ic_switch_to_tab),
            title = stringResource(R.string.settings_address_bar_actions_title),
            subtitle = stringResource(R.string.settings_address_bar_actions_summary),
            onClick = onAddressBarActions,
        )
        Spacer(Modifier.height(2.dp))
        SettingsSwitch(
            title = stringResource(R.string.settings_address_bar_docking_title),
            subtitle = stringResource(R.string.settings_address_bar_docking_subtitle),
            checked = isAddressBarDockingEnabled,
            onCheckedChange = onAddressBarDockingEnabledChanged,
            modifier = Modifier.testTag(TabSettingsTestTags.AddressBarDocking),
        )
        Spacer(Modifier.height(2.dp))
        TabDismissResistanceSettings(
            valuePercent = dismissResistancePercent,
            title = stringResource(R.string.settings_tab_dismiss_resistance),
            summary = { value ->
                stringResource(R.string.settings_tab_dismiss_resistance_summary, value)
            },
            containerColor = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
            enabled = true,
            onValueChanged = onDismissResistancePercentChanged,
        )
    }
}
