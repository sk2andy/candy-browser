package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor

internal object SettingsSearchTestTags {
    const val Query = "settings_search_query"
    const val Empty = "settings_search_empty"
    fun result(id: String): String = "settings_search_result_$id"
}

@Composable
internal fun ColumnScope.SettingsSearchField(
    query: String,
    entries: List<SettingsSearchEntry>,
    onQueryChanged: (String) -> Unit,
    onResultSelected: (SettingsSearchEntry) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = { onQueryChanged(it.take(SettingsSearchRules.MAX_QUERY_LENGTH)) },
        modifier = Modifier.fillMaxWidth().testTag(SettingsSearchTestTags.Query),
        label = { Text(stringResource(R.string.settings_search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChanged("") }) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = stringResource(R.string.history_clear_search),
                    )
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            keyboard?.hide()
            focusManager.clearFocus()
        }),
    )
    Spacer(Modifier.height(16.dp))
    if (query.isBlank()) return
    val results = SettingsSearchRules.results(query, entries)
    if (results.isEmpty()) {
        Text(
            stringResource(R.string.settings_search_empty),
            modifier = Modifier.testTag(SettingsSearchTestTags.Empty),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    results.forEach { entry ->
        SettingsSearchResult(
            entry = entry,
            onClick = {
                keyboard?.hide()
                focusManager.clearFocus()
                onResultSelected(entry)
            },
        )
        SettingsPageSpacer()
    }
}

@Composable
private fun SettingsSearchResult(entry: SettingsSearchEntry, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(SettingsSearchTestTags.result(entry.id)),
        shape = MaterialTheme.shapes.large,
        color = browserChromeColor(
            MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text(
                entry.context,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(entry.title, style = MaterialTheme.typography.titleMedium)
            if (entry.description.isNotBlank()) {
                Text(
                    entry.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
