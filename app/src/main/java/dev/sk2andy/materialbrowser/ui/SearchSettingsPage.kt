package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Box
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
import dev.sk2andy.materialbrowser.browser.SearchEngine
import dev.sk2andy.materialbrowser.browser.SearxngRules
import dev.sk2andy.materialbrowser.browser.SearxngSettings
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider

internal object SearchSettingsTestTags {
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
    var searchEngineMenuExpanded by remember { mutableStateOf(false) }
    var searchSuggestionMenuExpanded by remember { mutableStateOf(false) }
    var fallbackMenuExpanded by remember { mutableStateOf(false) }
    var instanceUrlDraft by remember { mutableStateOf(searxngSettings.instanceUrl) }
    SettingsPage(
        title = stringResource(R.string.settings_section_search),
        onBack = onBack,
    ) {
        Box {
            SettingsChoice(
                title = stringResource(R.string.settings_search_engine),
                value = searchEngine.displayName,
                expanded = searchEngineMenuExpanded,
                onClick = { searchEngineMenuExpanded = true },
            )
            SettingsDropdown(
                expanded = searchEngineMenuExpanded,
                onDismissRequest = { searchEngineMenuExpanded = false },
            ) {
                SearchEngine.entries.forEach { engine ->
                    SettingsDropdownItem(
                        label = engine.displayName,
                        selected = engine == searchEngine,
                        onClick = {
                            searchEngineMenuExpanded = false
                            onSearchEngineChanged(engine)
                        },
                    )
                }
            }
        }
        if (searchEngine.supportsAiSearch) {
            SettingsPageSpacer()
            SettingsSwitch(
                title = stringResource(R.string.settings_ai_mode_toggle_title),
                subtitle = stringResource(R.string.settings_ai_mode_toggle_subtitle),
                checked = isAiModeToggleVisible,
                onCheckedChange = onAiModeToggleVisibleChanged,
            )
        }
        if (
            searchEngine == SearchEngine.SearXNG ||
            searchSuggestionProvider == SearchSuggestionProvider.SearXNG
        ) {
            SettingsPageSpacer()
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
                    .testTag(SearchSettingsTestTags.SearxngInstanceUrl),
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
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                singleLine = true,
            )
        }
        SettingsPageSpacer()
        SettingsSwitch(
            title = stringResource(R.string.settings_history_suggestions_title),
            subtitle = stringResource(R.string.settings_history_suggestions_summary),
            checked = isHistorySuggestionsEnabled,
            onCheckedChange = onHistorySuggestionsEnabledChanged,
            modifier = Modifier.testTag(SearchSettingsTestTags.HistorySuggestions),
        )
        SettingsPageSpacer()
        Box {
            SettingsChoice(
                title = stringResource(R.string.settings_search_suggestions),
                value = searchSuggestionProvider.displayName(),
                expanded = searchSuggestionMenuExpanded,
                onClick = { searchSuggestionMenuExpanded = true },
                modifier = Modifier.testTag(SearchSettingsTestTags.SuggestionProvider),
            )
            SettingsDropdown(
                expanded = searchSuggestionMenuExpanded,
                onDismissRequest = { searchSuggestionMenuExpanded = false },
            ) {
                SearchSuggestionProvider.entries.forEach { provider ->
                    SettingsDropdownItem(
                        label = provider.displayName(),
                        selected = provider == searchSuggestionProvider,
                        onClick = {
                            searchSuggestionMenuExpanded = false
                            onSearchSuggestionProviderChanged(provider)
                        },
                    )
                }
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
            SettingsPageSpacer()
            Box {
                SettingsChoice(
                    title = stringResource(R.string.settings_searxng_suggestion_fallback),
                    value = searxngSettings.suggestionFallback.displayName(),
                    expanded = fallbackMenuExpanded,
                    onClick = { fallbackMenuExpanded = true },
                    modifier = Modifier.testTag(SearchSettingsTestTags.SearxngFallback),
                )
                SettingsDropdown(
                    expanded = fallbackMenuExpanded,
                    onDismissRequest = { fallbackMenuExpanded = false },
                ) {
                    SearchSuggestionProvider.entries
                        .filterNot { it == SearchSuggestionProvider.SearXNG }
                        .forEach { provider ->
                            SettingsDropdownItem(
                                label = provider.displayName(),
                                selected = provider == searxngSettings.suggestionFallback,
                                onClick = {
                                    fallbackMenuExpanded = false
                                    onSearxngSettingsChanged(
                                        searxngSettings.copy(suggestionFallback = provider),
                                    )
                                },
                            )
                        }
                }
            }
            Text(
                stringResource(R.string.settings_searxng_suggestion_fallback_summary),
                modifier = Modifier.padding(start = 18.dp, top = 6.dp, end = 18.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
