package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import dev.sk2andy.materialbrowser.browser.annotation.WebAnnotationCoordinator
import dev.sk2andy.materialbrowser.browser.annotation.WebAnnotationRules

@Composable
internal fun WebAnnotationDialogs(coordinator: WebAnnotationCoordinator) {
    if (coordinator.isLibraryVisible && coordinator.editor == null) {
        AlertDialog(
            onDismissRequest = coordinator::dismissLibrary,
            title = { Text(stringResource(R.string.web_annotation_title)) },
            text = {
                LazyColumn {
                    if (coordinator.pageAnnotations.isEmpty()) {
                        item { Text(stringResource(R.string.web_annotation_empty)) }
                    }
                    items(coordinator.pageAnnotations, key = { it.id }) { value ->
                        Column(Modifier.fillMaxWidth().clickable { coordinator.edit(value) }.padding(vertical = 12.dp)) {
                            Text(value.quote, style = MaterialTheme.typography.bodyMedium)
                            if (value.note.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(value.note, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = coordinator::dismissLibrary) { Text(stringResource(R.string.web_annotation_close)) }
            },
        )
    }
    coordinator.editor?.let { annotation ->
        var note by remember(annotation.id) { mutableStateOf(annotation.note) }
        AlertDialog(
            onDismissRequest = coordinator::dismissEditor,
            title = { Text(stringResource(R.string.web_annotation_mark)) },
            text = {
                LazyColumn {
                    item { Text(annotation.quote, style = MaterialTheme.typography.bodyMedium) }
                    item {
                        Text(
                            stringResource(R.string.web_annotation_saved_quote),
                            modifier = Modifier.padding(vertical = 12.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        OutlinedTextField(
                            value = note,
                            onValueChange = { if (it.length <= WebAnnotationRules.MAX_NOTE_CHARS) note = it },
                            label = { Text(stringResource(R.string.web_annotation_note)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                        )
                        if (coordinator.failed) Text(
                            stringResource(R.string.web_annotation_failed), color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (annotation in coordinator.pageAnnotations) {
                        item {
                            TextButton(onClick = { coordinator.delete(annotation) }) {
                                Text(stringResource(R.string.web_annotation_delete))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { coordinator.save(note) }) { Text(stringResource(R.string.web_annotation_save)) }
            },
            dismissButton = {
                TextButton(onClick = coordinator::dismissEditor) { Text(stringResource(R.string.web_annotation_cancel)) }
            },
        )
    }
}
