package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.SearchShortcut
import dev.sk2andy.materialbrowser.browser.SearchShortcutRules
import dev.sk2andy.materialbrowser.shared.ui.settings.settingsSearchTarget

internal object SearchShortcutsSettingsTestTags {
    const val Add = "search_shortcuts_add"
    const val Keyword = "search_shortcuts_keyword"
    const val Template = "search_shortcuts_template"
    const val Save = "search_shortcuts_save"
    const val Cancel = "search_shortcuts_cancel"
    fun edit(keyword: String) = "search_shortcuts_edit_$keyword"
    fun remove(keyword: String) = "search_shortcuts_remove_$keyword"
}

@Composable
internal fun SearchShortcutsSettings(
    shortcuts: List<SearchShortcut>,
    onShortcutsChanged: (List<SearchShortcut>) -> Unit,
) {
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    var editedKeyword by rememberSaveable { mutableStateOf<String?>(null) }
    SettingsPreferenceGroup {
        Column(
            Modifier.settingsSearchTarget(stringResource(R.string.settings_search_shortcuts_title)),
        ) {
            SettingsSectionTitle(stringResource(R.string.settings_search_shortcuts_title))
            Text(
                stringResource(R.string.settings_search_shortcuts_summary),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    editedKeyword = null
                    editorVisible = true
                },
                enabled = shortcuts.size < SearchShortcutRules.MAX_SHORTCUTS,
                modifier = Modifier
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .testTag(SearchShortcutsSettingsTestTags.Add),
            ) {
                Text(stringResource(R.string.settings_search_shortcut_add))
            }
        }
        shortcuts.forEach { shortcut ->
            SettingsPreferenceDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "!${shortcut.keyword}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        shortcut.template,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = {
                        editedKeyword = shortcut.keyword
                        editorVisible = true
                    },
                    modifier = Modifier.testTag(SearchShortcutsSettingsTestTags.edit(shortcut.keyword)),
                ) {
                    Icon(
                        Icons.Default.Edit,
                        stringResource(R.string.settings_search_shortcut_edit_description, shortcut.keyword),
                    )
                }
                IconButton(
                    onClick = {
                        onShortcutsChanged(shortcuts.filterNot { it.keyword == shortcut.keyword })
                    },
                    modifier = Modifier.testTag(SearchShortcutsSettingsTestTags.remove(shortcut.keyword)),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        stringResource(R.string.settings_search_shortcut_remove_description, shortcut.keyword),
                    )
                }
            }
        }
    }
    if (editorVisible) {
        SearchShortcutEditorDialog(
            shortcuts = shortcuts,
            editedKeyword = editedKeyword,
            onSave = {
                onShortcutsChanged(it)
                editorVisible = false
            },
            onDismiss = { editorVisible = false },
        )
    }
}

@Composable
private fun SearchShortcutEditorDialog(
    shortcuts: List<SearchShortcut>,
    editedKeyword: String?,
    onSave: (List<SearchShortcut>) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = shortcuts.firstOrNull { it.keyword == editedKeyword }
    var keyword by rememberSaveable(editedKeyword) {
        mutableStateOf(initial?.let { "!${it.keyword}" }.orEmpty())
    }
    var template by rememberSaveable(editedKeyword) { mutableStateOf(initial?.template.orEmpty()) }
    val updatedShortcuts = if (editedKeyword == null) {
        SearchShortcutRules.add(shortcuts, keyword, template)
    } else {
        SearchShortcutRules.update(shortcuts, editedKeyword, keyword, template)
    }
    val hasInvalidInput = keyword.isNotBlank() && template.isNotBlank() && updatedShortcuts == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (editedKeyword == null) R.string.settings_search_shortcut_add
                    else R.string.settings_search_shortcut_edit,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (editedKeyword == null) {
                    Text(
                        stringResource(R.string.settings_search_shortcut_examples),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            R.string.settings_search_shortcut_github to
                                SearchShortcut("gh", "https://github.com/search?q=%s"),
                            R.string.settings_search_shortcut_youtube to
                                SearchShortcut("yt", "https://www.youtube.com/results?search_query=%s"),
                            R.string.settings_search_shortcut_amazon to
                                SearchShortcut("amazon", "https://www.amazon.de/s?k=%s"),
                        ).forEach { (label, shortcut) ->
                            AssistChip(
                                onClick = {
                                    keyword = "!${shortcut.keyword}"
                                    template = shortcut.template
                                },
                                label = { Text(stringResource(label)) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it.take(SearchShortcutRules.MAX_KEYWORD_LENGTH + 1) },
                    modifier = Modifier.fillMaxWidth().testTag(SearchShortcutsSettingsTestTags.Keyword),
                    label = { Text(stringResource(R.string.settings_search_shortcut_keyword)) },
                    placeholder = { Text("!gh") },
                    supportingText = { Text(stringResource(R.string.settings_search_shortcut_keyword_summary)) },
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it.take(SearchShortcutRules.MAX_TEMPLATE_LENGTH) },
                    modifier = Modifier.fillMaxWidth().testTag(SearchShortcutsSettingsTestTags.Template),
                    label = { Text(stringResource(R.string.settings_search_shortcut_template)) },
                    placeholder = { Text("https://github.com/search?q=%s") },
                    supportingText = { Text(stringResource(R.string.settings_search_shortcut_template_summary)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    singleLine = true,
                )
                if (hasInvalidInput) {
                    Text(
                        stringResource(R.string.settings_search_shortcut_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { updatedShortcuts?.let(onSave) },
                enabled = updatedShortcuts != null,
                modifier = Modifier.testTag(SearchShortcutsSettingsTestTags.Save),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(SearchShortcutsSettingsTestTags.Cancel),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
