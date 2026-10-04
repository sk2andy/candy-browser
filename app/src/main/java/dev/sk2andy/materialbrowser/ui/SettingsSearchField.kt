package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor

internal object SettingsSearchTestTags {
    const val Query = "settings_search_query"
    const val Empty = "settings_search_empty"
    fun result(id: String): String = "settings_search_result_$id"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColumnScope.SettingsSearchField(
    query: String,
    entries: List<SettingsSearchEntry>,
    onQueryChanged: (String) -> Unit,
    onResultSelected: (SettingsSearchEntry) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = { onQueryChanged(it.take(SettingsSearchRules.MAX_QUERY_LENGTH)) },
                onSearch = {
                    keyboard?.hide()
                    focusManager.clearFocus()
                },
                expanded = false,
                onExpandedChange = {},
                placeholder = { Text(stringResource(R.string.settings_search_hint)) },
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
                modifier = Modifier.testTag(SettingsSearchTestTags.Query),
            )
        },
        expanded = false,
        onExpandedChange = {},
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = SearchBarDefaults.colors(
            containerColor = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {}
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
        return
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column {
            results.forEach { entry ->
                SettingsSearchResult(
                    entry = entry,
                    onClick = {
                        keyboard?.hide()
                        focusManager.clearFocus()
                        onResultSelected(entry)
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsSearchResult(entry: SettingsSearchEntry, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(entry.title) },
        overlineContent = { Text(entry.context) },
        supportingContent = if (entry.description.isBlank()) null else {
            {
                Text(
                    entry.description,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SettingsSearchTestTags.result(entry.id))
            .clickable(role = Role.Button, onClick = onClick),
    )
}
