package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.shared.ui.settings.settingsSearchTarget
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsHeaderArtwork
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsHeaderIllustration
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsRadioOption
import dev.sk2andy.materialbrowser.shared.ui.settings.SearchEngineRadioOptions
import dev.sk2andy.materialbrowser.browser.SearchEngine
import dev.sk2andy.materialbrowser.browser.SearxngRules
import dev.sk2andy.materialbrowser.browser.SearxngSettings
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider

internal object SearchSettingsTestTags {
    const val SearchEngine = "search_settings_search_engine"
    const val AiMode = "search_settings_ai_mode"
    fun suggestion(provider: SearchSuggestionProvider) = "search_settings_suggestion_${provider.stableId}"
    fun fallback(provider: SearchSuggestionProvider) = "search_settings_fallback_${provider.stableId}"
    const val SearxngInstanceUrl = "search_settings_searxng_instance_url"
    const val SearxngFallback = "search_settings_searxng_fallback"
    const val HistorySuggestions = "search_settings_history_suggestions"
    const val SuggestionProvider = "search_settings_suggestion_provider"
}

@Composable
internal fun SearchSettingsPage(
    searchEngine: SearchEngine,
    searxngSettings: SearxngSettings,
    isAiModeToggleVisible: Boolean,
    searchSuggestionProvider: SearchSuggestionProvider,
    isHistorySuggestionsEnabled: Boolean,
    onSearchEngineChanged: (SearchEngine) -> Unit,
    onSearxngSettingsChanged: (SearxngSettings) -> Unit,
    onAiModeToggleVisibleChanged: (Boolean) -> Unit,
    onSearchSuggestionProviderChanged: (SearchSuggestionProvider) -> Unit,
    onHistorySuggestionsEnabledChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var instanceUrlDraft by remember { mutableStateOf(searxngSettings.instanceUrl) }
    SettingsPage(
        title = stringResource(R.string.settings_section_search),
        onBack = onBack,
    ) {
        SettingsHeaderIllustration(SettingsHeaderArtwork.SearchEngines)
        SettingsPageSpacer()
        SettingsPreferenceGroup {
            SearchEngineRadioOptions(
                title = stringResource(R.string.settings_search_engine),
                searchEngine = searchEngine,
                onSearchEngineChanged = onSearchEngineChanged,
                modifier = Modifier.testTag(SearchSettingsTestTags.SearchEngine),
            )
            SettingsPreferenceDivider()
            SettingsSwitch(
                    title = stringResource(R.string.settings_ai_mode_toggle_title),
                    subtitle = stringResource(R.string.settings_ai_mode_toggle_subtitle),
                    checked = isAiModeToggleVisible,
                    enabled = searchEngine.supportsAiSearch,
                    onCheckedChange = onAiModeToggleVisibleChanged,
                    modifier = Modifier.testTag(SearchSettingsTestTags.AiMode),
                )
            run {
                SettingsPreferenceDivider()
                val normalizedInstanceUrl = SearxngRules.normalizedInstanceUrl(
                    instanceUrlDraft,
                )
                OutlinedTextField(
                    value = instanceUrlDraft,
                    onValueChange = { value ->
                        val boundedValue = value.take(SearxngRules.MAX_INSTANCE_URL_LENGTH)
                        val normalizedValue = SearxngRules.normalizedInstanceUrl(boundedValue)
                        instanceUrlDraft = boundedValue
                        when {
                            boundedValue.isBlank() -> onSearxngSettingsChanged(
                                searxngSettings.copy(instanceUrl = ""),
                            )
                            normalizedValue != null -> onSearxngSettingsChanged(
                                searxngSettings.copy(instanceUrl = normalizedValue),
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                        .testTag(SearchSettingsTestTags.SearxngInstanceUrl)
                        .settingsSearchTarget(stringResource(R.string.settings_searxng_instance_url)),
                    label = { Text(stringResource(R.string.settings_searxng_instance_url)) },
                    supportingText = {
                        Text(
                            stringResource(
                                if (
                                    instanceUrlDraft.isNotBlank() &&
                                    normalizedInstanceUrl == null
                                ) {
                                    R.string.settings_searxng_instance_url_invalid
                                } else {
                                    R.string.settings_searxng_instance_url_summary
                                },
                            ),
                        )
                    },
                    isError = instanceUrlDraft.isNotBlank() &&
                        normalizedInstanceUrl == null,
                    enabled = searchEngine == SearchEngine.SearXNG ||
                        searchSuggestionProvider == SearchSuggestionProvider.SearXNG,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    singleLine = true,
                )
            }
        }
        SettingsPageSpacer()
        SettingsPreferenceGroup {
            SettingsSwitch(
                title = stringResource(R.string.settings_history_suggestions_title),
                subtitle = stringResource(R.string.settings_history_suggestions_summary),
                checked = isHistorySuggestionsEnabled,
                onCheckedChange = onHistorySuggestionsEnabledChanged,
                modifier = Modifier.testTag(SearchSettingsTestTags.HistorySuggestions),
            )
            SettingsPreferenceDivider()
            Column(
                Modifier.testTag(SearchSettingsTestTags.SuggestionProvider)
                    .settingsSearchTarget(stringResource(R.string.settings_search_suggestions))
                    .selectableGroup(),
            ) {
                SettingsSectionTitle(stringResource(R.string.settings_search_suggestions))
                SearchSuggestionProvider.entries.forEachIndexed { index, provider ->
                    if (index > 0) SettingsPreferenceDivider()
                    SettingsRadioOption(
                        label = provider.displayName(),
                        selected = provider == searchSuggestionProvider,
                        onClick = { onSearchSuggestionProviderChanged(provider) },
                        modifier = Modifier.testTag(SearchSettingsTestTags.suggestion(provider)),
                    )
                }
            }
            Text(
                stringResource(
                    if (searchSuggestionProvider == SearchSuggestionProvider.None) {
                        R.string.settings_search_suggestions_none_summary
                    } else if (searchSuggestionProvider == SearchSuggestionProvider.SearXNG) {
                        R.string.settings_searxng_search_suggestions_summary
                    } else {
                        R.string.settings_search_suggestions_summary
                    },
                ),
                modifier = Modifier.padding(start = 18.dp, top = 6.dp, end = 18.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            run {
                SettingsPreferenceDivider()
                val fallbackEnabled = searchSuggestionProvider == SearchSuggestionProvider.SearXNG
                Column(
                    Modifier.testTag(SearchSettingsTestTags.SearxngFallback)
                        .settingsSearchTarget(stringResource(R.string.settings_searxng_suggestion_fallback))
                        .selectableGroup()
                        .semantics { if (!fallbackEnabled) disabled() },
                ) {
                    SettingsSectionTitle(stringResource(R.string.settings_searxng_suggestion_fallback))
                    SearchSuggestionProvider.entries
                        .filterNot { it == SearchSuggestionProvider.SearXNG }
                        .forEachIndexed { index, provider ->
                            if (index > 0) SettingsPreferenceDivider()
                            SettingsRadioOption(
                                label = provider.displayName(),
                                selected = provider == searxngSettings.suggestionFallback,
                                enabled = fallbackEnabled,
                                modifier = Modifier.testTag(SearchSettingsTestTags.fallback(provider)),
                                onClick = {
                                    onSearxngSettingsChanged(
                                        searxngSettings.copy(suggestionFallback = provider),
                                    )
                                },
                            )
                        }
                }
                Text(
                    stringResource(R.string.settings_searxng_suggestion_fallback_summary),
                    modifier = Modifier.padding(start = 18.dp, top = 6.dp, end = 18.dp, bottom = 14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
