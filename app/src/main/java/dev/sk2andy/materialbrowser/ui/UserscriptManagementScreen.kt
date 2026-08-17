package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import kotlinx.coroutines.launch

internal data class UserscriptUiItem(
    val id: String,
    val name: String,
    val source: String,
    val enabled: Boolean,
    val runAtLabel: String,
    val urlPatterns: List<String>,
)

internal object UserscriptManagementTestTags {
    const val Screen = "userscript_management_screen"
    const val List = "userscript_management_list"
    const val Add = "userscript_management_add"
    const val Discover = "userscript_management_discover"
    const val Import = "userscript_management_import"
    const val EmptyState = "userscript_management_empty"
    const val Editor = "userscript_management_editor"
    const val EditorSource = "userscript_management_editor_source"
    const val EditorSave = "userscript_management_editor_save"
    const val ActionError = "userscript_management_action_error"
    const val DeleteConfirmation = "userscript_management_delete_confirmation"
    const val DeleteConfirm = "userscript_management_delete_confirm"

    fun script(id: String) = "userscript_management_script_$id"
    fun toggle(id: String) = "userscript_management_toggle_$id"
    fun edit(id: String) = "userscript_management_edit_$id"
    fun delete(id: String) = "userscript_management_delete_$id"
}

@Composable
internal fun UserscriptManagementScreen(
    scripts: List<UserscriptUiItem>,
    isRuntimeSupported: Boolean = true,
    onToggle: (id: String, enabled: Boolean, onResult: (String?) -> Unit) -> Unit,
    onSave: (id: String?, source: String, onResult: (String?) -> Unit) -> Unit,
    onDelete: (id: String, onResult: (String?) -> Unit) -> Unit,
    onImport: () -> Unit,
    onDiscover: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val newScriptName = stringResource(R.string.userscript_new_script_name)
    var editorVisible by remember { mutableStateOf(false) }
    var editorId by remember { mutableStateOf<String?>(null) }
    var editorInitialSource by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<UserscriptUiItem?>(null) }
    var busyScriptIds by remember { mutableStateOf(emptySet<String>()) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var deleteSaving by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val mutationPending = busyScriptIds.isNotEmpty() || deleteSaving
    val openEditor: (String?, String) -> Unit = { id, source ->
        editorId = id
        editorInitialSource = source
        editorVisible = true
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(UserscriptManagementTestTags.Screen),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.userscript_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.userscript_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag(UserscriptManagementTestTags.List),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    UserscriptSafetyCard(isRuntimeSupported)
                }
                item {
                    Button(
                        onClick = onDiscover,
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(minHeight = 52.dp)
                            .testTag(UserscriptManagementTestTags.Discover),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(stringResource(R.string.topping_discover))
                    }
                }
                item {
                    OutlinedButton(
                        onClick = {
                            openEditor(null, userscriptTemplate(newScriptName))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(minHeight = 52.dp)
                            .testTag(UserscriptManagementTestTags.Add),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.userscript_add))
                    }
                }
                item {
                    OutlinedButton(
                        onClick = onImport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(minHeight = 48.dp)
                            .testTag(UserscriptManagementTestTags.Import),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(stringResource(R.string.userscript_import))
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 2.dp)
                            .semantics { heading() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.userscript_saved_section),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            pluralStringResource(
                                R.plurals.userscript_count,
                                scripts.size,
                                scripts.size,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (scripts.isEmpty()) {
                    item {
                        UserscriptEmptyState(
                            onAdd = {
                                openEditor(null, userscriptTemplate(newScriptName))
                            },
                        )
                    }
                } else {
                    items(scripts, key = UserscriptUiItem::id) { script ->
                        UserscriptCard(
                            script = script,
                            enabled = !mutationPending,
                            onToggle = { enabled ->
                                busyScriptIds += script.id
                                onToggle(script.id, enabled) { error ->
                                    busyScriptIds -= script.id
                                    if (error != null) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(error)
                                        }
                                    }
                                }
                            },
                            onEdit = { openEditor(script.id, script.source) },
                            onDelete = {
                                deleteError = null
                                pendingDelete = script
                            },
                        )
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .testTag(UserscriptManagementTestTags.ActionError),
        )
    }

    if (editorVisible) {
        UserscriptEditorDialog(
            initialSource = editorInitialSource,
            isNew = editorId == null,
            onDismiss = { editorVisible = false },
            onSave = { source, onResult ->
                onSave(editorId, source) { error ->
                    if (error == null) editorVisible = false
                    onResult(error)
                }
            },
        )
    }

    pendingDelete?.let { script ->
        AlertDialog(
            onDismissRequest = {
                if (!deleteSaving) pendingDelete = null
            },
            modifier = Modifier.testTag(UserscriptManagementTestTags.DeleteConfirmation),
            title = { Text(stringResource(R.string.userscript_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.userscript_delete_body, script.name))
                    deleteError?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteSaving = true
                        deleteError = null
                        onDelete(script.id) { error ->
                            deleteSaving = false
                            if (error == null) {
                                pendingDelete = null
                            } else {
                                deleteError = error
                            }
                        }
                    },
                    enabled = !deleteSaving,
                    modifier = Modifier.testTag(UserscriptManagementTestTags.DeleteConfirm),
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDelete = null },
                    enabled = !deleteSaving,
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun UserscriptSafetyCard(isRuntimeSupported: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                stringResource(R.string.userscript_risk_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.userscript_risk_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.userscript_scope_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.userscript_regular_tabs_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                stringResource(R.string.userscript_no_gm_apis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                stringResource(R.string.userscript_reload_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (!isRuntimeSupported) {
                Text(
                    stringResource(R.string.userscript_runtime_unsupported),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun UserscriptEmptyState(onAdd: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UserscriptManagementTestTags.EmptyState),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.userscript_empty_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.userscript_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onAdd) {
                Text(stringResource(R.string.userscript_add_first))
            }
        }
    }
}

@Composable
private fun UserscriptCard(
    script: UserscriptUiItem,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UserscriptManagementTestTags.script(script.id)),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    script.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.userscript_run_at, script.runAtLabel),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(
                        R.string.userscript_matches,
                        script.urlPatterns.takeIf { it.isNotEmpty() }
                            ?.let(::userscriptPatternSummary)
                            ?: stringResource(R.string.userscript_no_patterns),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = script.enabled,
                onCheckedChange = onToggle,
                enabled = enabled,
                modifier = Modifier
                    .testTag(UserscriptManagementTestTags.toggle(script.id))
                    .semantics { contentDescription = script.name },
            )
            IconButton(
                onClick = onEdit,
                enabled = enabled,
                modifier = Modifier.testTag(UserscriptManagementTestTags.edit(script.id)),
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(
                        R.string.userscript_edit_description,
                        script.name,
                    ),
                )
            }
            IconButton(
                onClick = onDelete,
                enabled = enabled,
                modifier = Modifier.testTag(UserscriptManagementTestTags.delete(script.id)),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(
                        R.string.userscript_delete_description,
                        script.name,
                    ),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun UserscriptEditorDialog(
    initialSource: String,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (source: String, onResult: (String?) -> Unit) -> Unit,
) {
    var source by remember(initialSource) { mutableStateOf(initialSource) }
    var validationError by remember(initialSource) { mutableStateOf<String?>(null) }
    var saving by remember(initialSource) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(UserscriptManagementTestTags.Editor),
        title = {
            Text(
                stringResource(
                    if (isNew) R.string.userscript_editor_add_title
                    else R.string.userscript_editor_edit_title,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.userscript_editor_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = source,
                    onValueChange = {
                        source = it
                        validationError = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UserscriptManagementTestTags.EditorSource),
                    label = { Text(stringResource(R.string.userscript_source_label)) },
                    supportingText = validationError?.let { error ->
                        { Text(error) }
                    },
                    isError = validationError != null,
                    minLines = 8,
                    maxLines = 16,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    saving = true
                    onSave(source) { error ->
                        saving = false
                        validationError = error
                    }
                },
                enabled = source.isNotBlank() && !saving,
                modifier = Modifier.testTag(UserscriptManagementTestTags.EditorSave),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun userscriptPatternSummary(patterns: List<String>): String = when {
    patterns.isEmpty() -> "—"
    patterns.size <= 2 -> patterns.joinToString(separator = "\n")
    else -> patterns.take(2).joinToString(separator = "\n") + "  +${patterns.size - 2}"
}

private fun userscriptTemplate(name: String): String = """
    // ==UserScript==
    // @name $name
    // @match https://example.com/*
    // @run-at document-end
    // ==/UserScript==

    (() => {
      'use strict';

    })();
""".trimIndent()
