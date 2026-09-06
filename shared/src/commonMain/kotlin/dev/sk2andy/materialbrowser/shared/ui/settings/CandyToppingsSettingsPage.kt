package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.shared.ui.BrowserViewportTopping

internal object CandyToppingsSettingsTestTags {
    const val Screen = "candy_toppings_settings"
    const val Add = "candy_toppings_add"
    const val Editor = "candy_toppings_editor"
    const val EditorSource = "candy_toppings_editor_source"
    const val EditorSave = "candy_toppings_editor_save"
    const val DeleteConfirmation = "candy_toppings_delete_confirmation"

    fun topping(id: String) = "candy_toppings_item:$id"
    fun toggle(id: String) = "candy_toppings_toggle:$id"
    fun edit(id: String) = "candy_toppings_edit:$id"
    fun delete(id: String) = "candy_toppings_delete:$id"
}

@Composable
internal fun CandyToppingsSettingsPage(
    toppings: List<BrowserViewportTopping>,
    onSave: (id: String?, source: String) -> Unit,
    toppingSource: (id: String) -> String?,
    onSetEnabled: (id: String, enabled: Boolean) -> Unit,
    onDelete: (id: String) -> Unit,
    onBack: () -> Unit,
) {
    var editor by remember { mutableStateOf<EditableTopping?>(null) }
    var isAdding by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<BrowserViewportTopping?>(null) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag(CandyToppingsSettingsTestTags.Screen),
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
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück",
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Toppings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Lokale Skripte für passende Webseiten",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    ToppingSafetyCard()
                }
                item {
                    OutlinedButton(
                        onClick = { isAdding = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(CandyToppingsSettingsTestTags.Add),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Topping hinzufügen")
                    }
                }
                if (toppings.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                "Noch keine Toppings gespeichert.",
                                modifier = Modifier.padding(18.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                } else {
                    items(toppings, key = BrowserViewportTopping::id) { topping ->
                        ToppingCard(
                            topping = topping,
                            onSetEnabled = onSetEnabled,
                            onEdit = {
                                toppingSource(topping.id)?.let { source ->
                                    editor = topping.copyWithSource(source)
                                }
                            },
                            onDelete = { pendingDelete = topping },
                        )
                    }
                }
            }
        }
    }

    if (isAdding) {
        ToppingEditorDialog(
            initialSource = newToppingTemplate,
            title = "Topping hinzufügen",
            onDismiss = { isAdding = false },
            onSave = { source ->
                onSave(null, source)
                isAdding = false
            },
        )
    }
    editor?.let { topping ->
        ToppingEditorDialog(
            initialSource = topping.source,
            title = "Topping bearbeiten",
            onDismiss = { editor = null },
            onSave = { source ->
                onSave(topping.id, source)
                editor = null
            },
        )
    }
    pendingDelete?.let { topping ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            modifier = Modifier.testTag(CandyToppingsSettingsTestTags.DeleteConfirmation),
            title = { Text("Topping löschen?") },
            text = { Text("„${topping.name}“ wird dauerhaft entfernt.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(topping.id)
                        pendingDelete = null
                    },
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Abbrechen")
                }
            },
        )
    }
}

private data class EditableTopping(
    val id: String,
    val source: String,
)

private fun BrowserViewportTopping.copyWithSource(source: String) = EditableTopping(
    id = id,
    source = source,
)

@Composable
private fun ToppingSafetyCard() {
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
                "Nur vertrauenswürdige Skripte installieren",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Toppings dürfen Seiten verändern. Candy führt sie nur in normalen Tabs, " +
                    "für deklarierte HTTP(S)-Adressen und in getrennten WebKit-Welten aus.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun ToppingCard(
    topping: BrowserViewportTopping,
    onSetEnabled: (id: String, enabled: Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(CandyToppingsSettingsTestTags.topping(topping.id)),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    topping.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    topping.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = topping.enabled,
                onCheckedChange = { enabled -> onSetEnabled(topping.id, enabled) },
                modifier = Modifier.testTag(CandyToppingsSettingsTestTags.toggle(topping.id)),
            )
            IconButton(
                onClick = onEdit,
                modifier = Modifier.testTag(CandyToppingsSettingsTestTags.edit(topping.id)),
            ) {
                Icon(Icons.Default.Edit, contentDescription = "${topping.name} bearbeiten")
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag(CandyToppingsSettingsTestTags.delete(topping.id)),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "${topping.name} löschen",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ToppingEditorDialog(
    initialSource: String,
    title: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var source by remember(initialSource) { mutableStateOf(initialSource) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(CandyToppingsSettingsTestTags.Editor),
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(CandyToppingsSettingsTestTags.EditorSource),
                label = { Text("Userscript-Quelle") },
                minLines = 8,
                maxLines = 16,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(source) },
                enabled = source.isNotBlank(),
                modifier = Modifier.testTag(CandyToppingsSettingsTestTags.EditorSave),
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        },
    )
}

private val newToppingTemplate = """
    // ==UserScript==
    // @name Neues Topping
    // @match https://example.com/*
    // @run-at document-end
    // ==/UserScript==

    (() => {
      'use strict';
    })();
""".trimIndent()
