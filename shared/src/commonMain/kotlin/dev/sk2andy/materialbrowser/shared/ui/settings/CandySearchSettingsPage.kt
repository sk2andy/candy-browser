package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.browser.SearchEngine
import dev.sk2andy.materialbrowser.browser.SearchSettingsRules

internal object CandySearchSettingsTestTags {
    const val SEARCH_ENGINE = "search_settings_search_engine"
    const val SEARXNG_INSTANCE_URL = "search_settings_searxng_instance_url"
}

@Composable
internal fun CandySearchSettingsPage(
    searchEngine: SearchEngine,
    searxngInstanceUrl: String,
    onSearchEngineChanged: (SearchEngine) -> Unit,
    onSearxngInstanceUrlChanged: (String) -> Unit,
    onBack: () -> Unit,
) {
    var engineMenuExpanded by remember { mutableStateOf(false) }
    var instanceUrlDraft by remember(searxngInstanceUrl) {
        mutableStateOf(searxngInstanceUrl)
    }
    SettingsPage(
        title = "Suche",
        backContentDescription = "Zurück",
        onBack = onBack,
    ) {
        Box {
            SettingsChoice(
                title = "Suchmaschine",
                value = searchEngine.displayName,
                expanded = engineMenuExpanded,
                onClick = { engineMenuExpanded = true },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.testTag(CandySearchSettingsTestTags.SEARCH_ENGINE),
            )
            SettingsDropdown(
                expanded = engineMenuExpanded,
                onDismissRequest = { engineMenuExpanded = false },
            ) {
                SearchEngine.entries.forEach { candidate ->
                    SettingsDropdownItem(
                        label = candidate.displayName,
                        selected = candidate == searchEngine,
                        onClick = {
                            engineMenuExpanded = false
                            onSearchEngineChanged(candidate)
                        },
                    )
                }
            }
        }

        if (searchEngine == SearchEngine.SearXNG) {
            SettingsPageSpacer()
            val normalizedInstanceUrl = SearchSettingsRules.normalizedSearxngInstanceUrl(
                instanceUrlDraft,
            )
            OutlinedTextField(
                value = instanceUrlDraft,
                onValueChange = { value ->
                    val boundedValue = value.take(
                        SearchSettingsRules.MAX_SEARXNG_INSTANCE_URL_LENGTH,
                    )
                    val normalizedValue = SearchSettingsRules.normalizedSearxngInstanceUrl(
                        boundedValue,
                    )
                    instanceUrlDraft = boundedValue
                    when {
                        boundedValue.isBlank() -> onSearxngInstanceUrlChanged("")
                        normalizedValue != null -> onSearxngInstanceUrlChanged(normalizedValue)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(CandySearchSettingsTestTags.SEARXNG_INSTANCE_URL),
                label = { Text("SearXNG-Instanz-URL") },
                supportingText = {
                    Text(
                        if (instanceUrlDraft.isNotBlank() && normalizedInstanceUrl == null) {
                            "Gib eine gültige HTTP- oder HTTPS-URL ohne Zugangsdaten, Abfrage oder Fragment ein."
                        } else {
                            "Basis-URL, optional mit Pfad. HTTPS wird empfohlen."
                        },
                    )
                },
                isError = instanceUrlDraft.isNotBlank() && normalizedInstanceUrl == null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                singleLine = true,
            )
            Text(
                "Suchanfragen werden direkt in der ausgewählten Instanz geöffnet.",
                modifier = Modifier.padding(start = 18.dp, top = 6.dp, end = 18.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
