package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
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
import dev.sk2andy.materialbrowser.browser.SearchShortcut
import dev.sk2andy.materialbrowser.browser.SearchShortcutRules
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
    searchShortcuts: List<SearchShortcut> = emptyList(),
    searxngSettings: SearxngSettings,
    isAiModeToggleVisible: Boolean,
    searchSuggestionProvider: SearchSuggestionProvider,
    isHistorySuggestionsEnabled: Boolean,
    onSearchEngineChanged: (SearchEngine) -> Unit,
    onSearchShortcutsChanged: (List<SearchShortcut>) -> Unit = {},
    onSearxngSettingsChanged: (SearxngSettings) -> Unit,
    onAiModeToggleVisibleChanged: (Boolean) -> Unit,
    onSearchSuggestionProviderChanged: (SearchSuggestionProvider) -> Unit,
    onHistorySuggestionsEnabledChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var instanceUrlDraft by remember { mutableStateOf(searxngSettings.instanceUrl) }
    var keywordDraft by remember { mutableStateOf("") }
    var templateDraft by remember { mutableStateOf("") }
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
            SettingsSectionTitle(stringResource(R.string.settings_search_shortcuts_title))
            Text(
                stringResource(R.string.settings_search_shortcuts_summary),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = keywordDraft,
                onValueChange = { keywordDraft = it.take(SearchShortcutRules.MAX_KEYWORD_LENGTH) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                label = { Text(stringResource(R.string.settings_search_shortcut_keyword)) },
                supportingText = {
                    Text(stringResource(R.string.settings_search_shortcut_keyword_summary))
                },
                singleLine = true,
            )
            OutlinedTextField(
                value = templateDraft,
                onValueChange = { templateDraft = it.take(SearchShortcutRules.MAX_TEMPLATE_LENGTH) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                label = { Text(stringResource(R.string.settings_search_shortcut_template)) },
                supportingText = { Text(stringResource(R.string.settings_search_shortcut_template_summary)) },
                singleLine = true,
            )
            val canAddShortcut = SearchShortcutRules.add(searchShortcuts, keywordDraft, templateDraft) != null
            OutlinedButton(
                onClick = {
                    SearchShortcutRules.add(searchShortcuts, keywordDraft, templateDraft)?.let {
                        onSearchShortcutsChanged(it)
                        keywordDraft = ""
                        templateDraft = ""
                    }
                },
                enabled = canAddShortcut,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            ) { Text(stringResource(R.string.settings_search_shortcut_add)) }
            searchShortcuts.forEach { shortcut ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${shortcut.keyword}  ·  ${shortcut.template}",
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                    )
                    OutlinedButton(
                        onClick = {
                            onSearchShortcutsChanged(
                                searchShortcuts.filterNot { it.keyword == shortcut.keyword },
                            )
                        },
                    ) {
                        Text(stringResource(R.string.settings_search_shortcut_remove))
                    }
                }
            }
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
            if (searchSuggestionProvider == SearchSuggestionProvider.SearXNG) {
                SettingsPreferenceDivider()
                Column(
                    Modifier.testTag(SearchSettingsTestTags.SearxngFallback)
                        .settingsSearchTarget(stringResource(R.string.settings_searxng_suggestion_fallback))
                        .selectableGroup(),
                ) {
                    SettingsSectionTitle(stringResource(R.string.settings_searxng_suggestion_fallback))
                    SearchSuggestionProvider.entries
                        .filterNot { it == SearchSuggestionProvider.SearXNG }
                        .forEachIndexed { index, provider ->
                            if (index > 0) SettingsPreferenceDivider()
                            SettingsRadioOption(
                                label = provider.displayName(),
                                selected = provider == searxngSettings.suggestionFallback,
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
