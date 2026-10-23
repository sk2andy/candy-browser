package dev.sk2andy.materialbrowser.ui.feeds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.feeds.FeedRepository
import dev.sk2andy.materialbrowser.feeds.FeedRule
import dev.sk2andy.materialbrowser.feeds.FeedRuleAction
import dev.sk2andy.materialbrowser.feeds.FeedRules
import dev.sk2andy.materialbrowser.feeds.FeedSearch
import dev.sk2andy.materialbrowser.feeds.FeedSettings
import dev.sk2andy.materialbrowser.feeds.FeedState
import java.util.UUID

@Composable
internal fun FeedToolsScreen(
    state: FeedState,
    repository: FeedRepository,
    mutate: FeedMutation,
    onOpenSearch: (FeedSearch) -> Unit,
    onPlayQueue: () -> Unit,
    onImportOpml: () -> Unit,
    onExportOpml: () -> Unit,
    onNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingSearch by remember { mutableStateOf<FeedSearch?>(null) }
    var editingRule by remember { mutableStateOf<FeedRule?>(null) }
    var settingsVisible by remember { mutableStateOf(false) }
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            FeedToolSection(R.string.feeds_searches) {
                state.searches.forEach { search ->
                    TextButton(onClick = { onOpenSearch(search) }) { Text(search.name) }
                    Row {
                        TextButton(onClick = { editingSearch = search }) {
                            Text(stringResource(R.string.feeds_edit))
                        }
                        TextButton(onClick = { mutate { repository.removeSearch(search.id) } }) {
                            Text(stringResource(R.string.feeds_delete))
                        }
                    }
                }
                TextButton(
                    onClick = { editingSearch = FeedSearch(UUID.randomUUID().toString(), "") }
                ) {
                    Text(stringResource(R.string.feeds_new_search))
                }
            }
        }
        item {
            FeedToolSection(R.string.feeds_rules) {
                Text(
                    stringResource(R.string.feeds_rule_note),
                    style = MaterialTheme.typography.bodySmall,
                )
                state.rules.forEach { rule ->
                    Text(rule.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(
                            R.string.feeds_rule_matches,
                            state.articles.count { FeedRules.ruleMatches(it, rule, state) },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FeedSwitchRow(R.string.feeds_enabled, rule.enabled) { enabled ->
                        mutate { repository.saveRule(rule.copy(enabled = enabled)) }
                    }
                    Row {
                        TextButton(onClick = { editingRule = rule }) {
                            Text(stringResource(R.string.feeds_edit))
                        }
                        TextButton(onClick = { mutate { repository.removeRule(rule.id) } }) {
                            Text(stringResource(R.string.feeds_delete))
                        }
                    }
                }
                TextButton(
                    onClick = {
                        editingRule =
                            FeedRule(UUID.randomUUID().toString(), "", "", FeedRuleAction.Star)
                    }
                ) {
                    Text(stringResource(R.string.feeds_add_rule))
                }
            }
        }
        item {
            FeedToolSection(R.string.feeds_queue) {
                Text(
                    stringResource(R.string.feeds_queue_count, state.queue.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onPlayQueue) {
                    Text(stringResource(R.string.feeds_player_open))
                }
            }
        }
        item {
            FeedToolSection(R.string.feeds_opml) {
                TextButton(onClick = onImportOpml) { Text(stringResource(R.string.feeds_import)) }
                TextButton(onClick = onExportOpml, enabled = state.sources.isNotEmpty()) {
                    Text(stringResource(R.string.feeds_export))
                }
            }
        }
        item {
            FeedToolSection(R.string.feeds_settings) {
                TextButton(onClick = { settingsVisible = true }) {
                    Text(stringResource(R.string.feeds_storage))
                }
                FeedSwitchRow(R.string.feeds_refresh_open, state.settings.refreshOnOpen) {
                    mutate { repository.updateSettings(state.settings.copy(refreshOnOpen = it)) }
                }
                FeedSwitchRow(R.string.feeds_background, state.settings.backgroundRefresh) {
                    mutate {
                        repository.updateSettings(state.settings.copy(backgroundRefresh = it))
                    }
                }
                FeedSwitchRow(R.string.feeds_digest, state.settings.digestEnabled) {
                    if (it) onNotificationPermission()
                    mutate { repository.updateSettings(state.settings.copy(digestEnabled = it)) }
                }
            }
        }
    }
    editingSearch?.let { search ->
        FeedSearchEditor(
            search,
            state,
            onSave = {
                mutate { repository.saveSearch(it) }
                editingSearch = null
            },
            onDismiss = { editingSearch = null },
        )
    }
    editingRule?.let { rule ->
        FeedRuleEditor(
            rule,
            state,
            onSave = {
                if (it.action == FeedRuleAction.Notify) onNotificationPermission()
                mutate { repository.saveRule(it) }
                editingRule = null
            },
            onDismiss = { editingRule = null },
        )
    }
    if (settingsVisible)
        FeedSettingsDialog(
            state.settings,
            onSave = {
                mutate { repository.updateSettings(it) }
                settingsVisible = false
            },
            onDismiss = { settingsVisible = false },
        )
}

@Composable
private fun FeedToolSection(title: Int, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
internal fun FeedSearchEditor(
    search: FeedSearch,
    state: FeedState,
    onSave: (FeedSearch) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(search.name) }
    var query by remember { mutableStateOf(search.query) }
    var sourceId by remember { mutableStateOf(search.sourceId) }
    var folderId by remember { mutableStateOf(search.folderId) }
    var unreadOnly by remember { mutableStateOf(search.unreadOnly) }
    var tag by remember { mutableStateOf(search.tag.orEmpty()) }
    val draft =
        search.copy(
            name = name.trim(),
            query = query.trim(),
            sourceId = sourceId,
            folderId = folderId,
            unreadOnly = unreadOnly,
            tag = tag.trim().ifBlank { null },
        )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feeds_save_search)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text(stringResource(R.string.feeds_name)) },
                )
                OutlinedTextField(
                    query,
                    { query = it },
                    label = { Text(stringResource(R.string.feeds_query)) },
                )
                FeedScopeChoice(state, sourceId, { sourceId = it }, folderId, { folderId = it })
                OutlinedTextField(
                    tag,
                    { tag = it },
                    label = { Text(stringResource(R.string.feeds_tags)) },
                )
                FeedSwitchRow(R.string.feeds_unread_only, unreadOnly, { unreadOnly = it })
                Text(
                    stringResource(
                        R.string.feeds_rule_matches,
                        state.articles.count { FeedRules.matches(it, draft, state) },
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.feeds_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feeds_cancel)) }
        },
    )
}

@Composable
private fun FeedRuleEditor(
    rule: FeedRule,
    state: FeedState,
    onSave: (FeedRule) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(rule.name) }
    var query by remember { mutableStateOf(rule.query) }
    var source by remember { mutableStateOf(rule.sourceId) }
    var folder by remember { mutableStateOf(rule.folderId) }
    var action by remember { mutableStateOf(rule.action) }
    var tag by remember { mutableStateOf(rule.tag) }
    val draft =
        rule.copy(
            name = name.trim(),
            query = query.trim(),
            sourceId = source,
            folderId = folder,
            action = action,
            tag = tag.trim(),
        )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feeds_add_rule)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text(stringResource(R.string.feeds_name)) },
                )
                OutlinedTextField(
                    query,
                    { query = it },
                    label = { Text(stringResource(R.string.feeds_query)) },
                )
                FeedScopeChoice(state, source, { source = it }, folder, { folder = it })
                Text(
                    stringResource(R.string.feeds_rule_action),
                    style = MaterialTheme.typography.titleSmall,
                )
                FeedRuleAction.entries.forEach { value ->
                    FilterChip(
                        action == value,
                        { action = value },
                        label = {
                            Text(
                                stringResource(
                                    when (value) {
                                        FeedRuleAction.MarkRead -> R.string.feeds_rule_read
                                        FeedRuleAction.Star -> R.string.feeds_rule_star
                                        FeedRuleAction.Hide -> R.string.feeds_rule_hide
                                        FeedRuleAction.Tag -> R.string.feeds_rule_tag
                                        FeedRuleAction.Notify -> R.string.feeds_rule_notify
                                    }
                                )
                            )
                        },
                    )
                }
                if (action == FeedRuleAction.Tag)
                    OutlinedTextField(
                        tag,
                        { tag = it },
                        label = { Text(stringResource(R.string.feeds_tags)) },
                    )
                Text(
                    stringResource(
                        R.string.feeds_rule_matches,
                        state.articles.count { FeedRules.ruleMatches(it, draft, state) },
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(draft) },
                enabled =
                    name.isNotBlank() &&
                        query.isNotBlank() &&
                        (action != FeedRuleAction.Tag || tag.isNotBlank()),
            ) {
                Text(stringResource(R.string.feeds_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feeds_cancel)) }
        },
    )
}

@Composable
private fun FeedScopeChoice(
    state: FeedState,
    source: String?,
    onSource: (String?) -> Unit,
    folder: String?,
    onFolder: (String?) -> Unit,
) {
    Text(stringResource(R.string.feeds_source), style = MaterialTheme.typography.titleSmall)
    FilterChip(
        source == null,
        { onSource(null) },
        label = { Text(stringResource(R.string.feeds_any_source)) },
    )
    state.sources.forEach { item ->
        FilterChip(item.id == source, { onSource(item.id) }, label = { Text(item.title) })
    }
    FeedFolderChoice(state, folder, onFolder)
}

@Composable
private fun FeedSettingsDialog(
    settings: FeedSettings,
    onSave: (FeedSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var budget by remember { mutableStateOf(settings.offlineBudgetMb.toString()) }
    var wifi by remember { mutableStateOf(settings.wifiOnly) }
    var hour by remember { mutableStateOf(settings.digestHour.toString()) }
    var quietStart by remember { mutableStateOf(settings.quietStartHour.toString()) }
    var quietEnd by remember { mutableStateOf(settings.quietEndHour.toString()) }
    val valid =
        (budget.toIntOrNull() ?: -1) in 10..250 &&
            (hour.toIntOrNull() ?: -1) in 0..23 &&
            (quietStart.toIntOrNull() ?: -1) in 0..23 &&
            (quietEnd.toIntOrNull() ?: -1) in 0..23
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feeds_settings)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    budget,
                    { budget = it },
                    label = { Text(stringResource(R.string.feeds_budget)) },
                    singleLine = true,
                )
                FeedSwitchRow(R.string.feeds_wifi, wifi, { wifi = it })
                OutlinedTextField(
                    hour,
                    { hour = it },
                    label = { Text(stringResource(R.string.feeds_digest_hour)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    quietStart,
                    { quietStart = it },
                    label = { Text(stringResource(R.string.feeds_quiet_start)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    quietEnd,
                    { quietEnd = it },
                    label = { Text(stringResource(R.string.feeds_quiet_end)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        settings.copy(
                            offlineBudgetMb = budget.toInt(),
                            wifiOnly = wifi,
                            digestHour = hour.toInt(),
                            quietStartHour = quietStart.toInt(),
                            quietEndHour = quietEnd.toInt(),
                        )
                    )
                },
                enabled = valid,
            ) {
                Text(stringResource(R.string.feeds_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feeds_cancel)) }
        },
    )
}
