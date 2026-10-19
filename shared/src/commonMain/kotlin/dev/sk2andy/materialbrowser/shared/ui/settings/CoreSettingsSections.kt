package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.browser.PageTranslationProvider
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.shared.ui.LocalCandyBrowserChromeEffects
import kotlin.math.roundToInt

data class TabOverviewSettingsStrings(
    val overviewMode: String,
    val modeNames: Map<TabOverviewMode, String>,
    val listStartsAtBottom: String,
    val listStartsAtBottomSummary: String,
)

@Composable
fun TabOverviewSettings(
    mode: TabOverviewMode,
    listStartsAtBottom: Boolean,
    strings: TabOverviewSettingsStrings,
    containerColor: Color,
    onOpenMode: () -> Unit,
    onListStartsAtBottomChanged: (Boolean) -> Unit,
    listStartsAtBottomAvailable: Boolean = true,
    listStartsAtBottomModifier: Modifier = Modifier,
) {
    SettingsSelectionLink(
        title = strings.overviewMode,
        value = strings.modeNames.getValue(mode),
        onClick = onOpenMode,
    )
    SettingsPageSpacer()
    SettingsSwitch(
        title = strings.listStartsAtBottom,
        subtitle = strings.listStartsAtBottomSummary,
        checked = listStartsAtBottom,
        enabled = listStartsAtBottomAvailable && mode != TabOverviewMode.Hero,
        onCheckedChange = onListStartsAtBottomChanged,
        modifier = listStartsAtBottomModifier,
    )
}

@Composable
fun TabDismissResistanceSettings(
    valuePercent: Int,
    title: String,
    summary: @Composable (Int) -> String,
    containerColor: Color,
    enabled: Boolean,
    onValueChanged: (Int) -> Unit,
) {
    var value by remember(valuePercent) { mutableFloatStateOf(valuePercent.toFloat()) }
    val valueDescription = summary(value.roundToInt())
    Surface(
        modifier = Modifier.fillMaxWidth().settingsSearchTarget(title),
        shape = settingsRowShape(),
        color = settingsRowColor(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                valueDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LocalCandyBrowserChromeEffects.current.settingsSlider(
                value = value,
                onValueChange = { value = it },
                onValueChangeFinished = { onValueChanged(value.roundToInt()) },
                enabled = enabled,
                modifier = Modifier.semantics {
                    contentDescription = title
                    stateDescription = valueDescription
                },
                valueRange = 10f..90f,
                steps = 7,
                backgroundColor = containerColor,
            )
        }
    }
}

data class TranslationProviderSettingsStrings(
    val title: String,
    val providerNames: Map<PageTranslationProvider, String>,
    val providerSummaries: Map<PageTranslationProvider, String>,
)

@Composable
fun TranslationProviderSettings(
    provider: PageTranslationProvider,
    strings: TranslationProviderSettingsStrings,
    containerColor: Color,
    enabled: Boolean,
    onProviderChanged: (PageTranslationProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        SettingsChoice(
            title = strings.title,
            value = strings.providerNames.getValue(provider),
            expanded = menuExpanded,
            onClick = { menuExpanded = true },
            containerColor = containerColor,
            enabled = enabled,
            modifier = modifier,
        )
        SettingsDropdown(
            expanded = enabled && menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            PageTranslationProvider.entries.forEach { candidate ->
                SettingsDropdownItem(
                    label = strings.providerNames.getValue(candidate),
                    selected = candidate == provider,
                    onClick = {
                        menuExpanded = false
                        onProviderChanged(candidate)
                    },
                )
            }
        }
    }
    Text(
        strings.providerSummaries.getValue(provider),
        modifier = Modifier.padding(
            start = 18.dp,
            top = 6.dp,
            end = 18.dp,
            bottom = if (LocalSettingsPreferenceGroup.current) 14.dp else 0.dp,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
