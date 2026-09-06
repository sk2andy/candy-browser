package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.actions.ExternalDownloadManagerApp
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.data.BrowserColorPalette
import dev.sk2andy.materialbrowser.data.BrowserDownloadSettings
import dev.sk2andy.materialbrowser.data.BrowserShapeStyle
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle
import dev.sk2andy.materialbrowser.data.DownloadManagerMode
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor

@Composable
internal fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    dev.sk2andy.materialbrowser.shared.ui.settings.SettingsPage(
        title = title,
        backContentDescription = stringResource(R.string.action_back),
        onBack = onBack,
        content = content,
    )
}

@Composable
internal fun BrowserDownloadSettings.displayName(
    externalManagers: List<ExternalDownloadManagerApp>,
): String = when (managerMode) {
    DownloadManagerMode.BuiltIn -> stringResource(R.string.settings_download_manager_builtin)
    DownloadManagerMode.AskEveryTime -> stringResource(R.string.settings_download_manager_ask)
    DownloadManagerMode.External -> externalManagers
        .firstOrNull { it.id == externalManagerId }
        ?.label
        ?: stringResource(R.string.settings_download_manager_external_unavailable)
}

@Composable
internal fun PrivacyXRaySettingsCounter(
    blockedCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .testTag(PrivacyXRayTestTags.SettingsCounter),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pluralStringResource(
                    R.plurals.blocked_requests_count,
                    blockedCount,
                    blockedCount,
                ),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "◈",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun InactiveTabLifetime.displayName(): String = when (this) {
    InactiveTabLifetime.Never -> stringResource(R.string.tab_lifetime_never)
    InactiveTabLifetime.Immediately -> stringResource(R.string.tab_lifetime_immediately)
    InactiveTabLifetime.SixHours -> pluralStringResource(R.plurals.tab_lifetime_hours, 6, 6)
    InactiveTabLifetime.OneDay -> pluralStringResource(R.plurals.tab_lifetime_days, 1, 1)
    InactiveTabLifetime.ThreeDays -> pluralStringResource(R.plurals.tab_lifetime_days, 3, 3)
    InactiveTabLifetime.SevenDays -> pluralStringResource(R.plurals.tab_lifetime_days, 7, 7)
    InactiveTabLifetime.ThirtyDays -> pluralStringResource(R.plurals.tab_lifetime_days, 30, 30)
}

@Composable
internal fun TabOverviewMode.displayName(): String = when (this) {
    TabOverviewMode.Hero -> stringResource(R.string.tab_overview_mode_hero)
    TabOverviewMode.Grid -> stringResource(R.string.tab_overview_mode_grid)
    TabOverviewMode.List -> stringResource(R.string.tab_overview_mode_list)
}

@Composable
internal fun SearchSuggestionProvider.displayName(): String = when (this) {
    SearchSuggestionProvider.None -> stringResource(R.string.search_suggestion_provider_none)
    SearchSuggestionProvider.DuckDuckGo -> "DuckDuckGo"
    SearchSuggestionProvider.Google -> "Google"
    SearchSuggestionProvider.Brave -> "Brave Search"
    SearchSuggestionProvider.Ecosia -> "Ecosia"
    SearchSuggestionProvider.Qwant -> "Qwant"
    SearchSuggestionProvider.Startpage -> "Startpage"
    SearchSuggestionProvider.Kagi -> "Kagi"
    SearchSuggestionProvider.SearXNG -> "SearXNG"
}

@Composable
internal fun BrowserAppearanceMode.displayName(): String = when (this) {
    BrowserAppearanceMode.System -> stringResource(R.string.appearance_mode_system)
    BrowserAppearanceMode.Light -> stringResource(R.string.appearance_mode_light)
    BrowserAppearanceMode.Dark -> stringResource(R.string.appearance_mode_dark)
    BrowserAppearanceMode.Amoled -> stringResource(R.string.appearance_mode_amoled)
}

@Composable
internal fun BrowserColorPalette.displayName(): String = when (this) {
    BrowserColorPalette.Dynamic -> stringResource(R.string.color_palette_dynamic)
    BrowserColorPalette.Candy -> stringResource(R.string.color_palette_candy)
    BrowserColorPalette.Neutral -> stringResource(R.string.color_palette_neutral)
}

@Composable
internal fun BrowserSurfaceStyle.displayName(): String = when (this) {
    BrowserSurfaceStyle.Clear -> stringResource(R.string.surface_style_clear)
    BrowserSurfaceStyle.Frosted -> stringResource(R.string.surface_style_frosted)
}

@Composable
internal fun BrowserShapeStyle.displayName(): String = when (this) {
    BrowserShapeStyle.Angular -> stringResource(R.string.shape_style_angular)
    BrowserShapeStyle.Rounded -> stringResource(R.string.shape_style_rounded)
    BrowserShapeStyle.ExtraRounded -> stringResource(R.string.shape_style_extra_rounded)
}

@Composable
internal fun SettingsSectionTitle(text: String) {
    dev.sk2andy.materialbrowser.shared.ui.settings.SettingsSectionTitle(text)
}

@Composable
internal fun SettingsChoice(
    title: String,
    value: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    dev.sk2andy.materialbrowser.shared.ui.settings.SettingsChoice(
        title = title,
        value = value,
        expanded = expanded,
        onClick = onClick,
        containerColor = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier,
    )
}

@Composable
internal fun SettingsDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    dev.sk2andy.materialbrowser.shared.ui.settings.SettingsDropdown(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        content = content,
    )
}

@Composable
internal fun SettingsDropdownItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    dev.sk2andy.materialbrowser.shared.ui.settings.SettingsDropdownItem(
        label = label,
        selected = selected,
        onClick = onClick,
    )
}

@Composable
internal fun SettingsLink(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    dev.sk2andy.materialbrowser.shared.ui.settings.SettingsLink(
        title = title,
        subtitle = subtitle,
        containerColor = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
        icon = { modifier, tint ->
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = modifier,
                tint = tint,
            )
        },
        onClick = onClick,
    )
}

@Composable
internal fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    dev.sk2andy.materialbrowser.shared.ui.settings.SettingsSwitch(
        title = title,
        subtitle = subtitle,
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsPageSpacer() {
    dev.sk2andy.materialbrowser.shared.ui.settings.SettingsPageSpacer()
}
