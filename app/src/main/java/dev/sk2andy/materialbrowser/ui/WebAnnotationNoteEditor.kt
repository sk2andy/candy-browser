package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsControllerCompat
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.annotation.WebAnnotation
import dev.sk2andy.materialbrowser.browser.annotation.WebAnnotationEditorRules
import dev.sk2andy.materialbrowser.browser.annotation.WebAnnotationFormat
import dev.sk2andy.materialbrowser.browser.annotation.WebAnnotationRules

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebAnnotationNoteEditor(
    annotation: WebAnnotation,
    failed: Boolean,
    canDelete: Boolean,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    saving: Boolean = false,
) {
    var note by rememberSaveable(annotation.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(annotation.note, TextRange(annotation.note.length)))
    }
    var confirmDiscard by rememberSaveable(annotation.id) { mutableStateOf(false) }
    var previewSelected by rememberSaveable(annotation.id) { mutableStateOf(false) }
    val previewScrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val compact = windowWidth < 600.dp
    val dismiss = {
        if (!saving && note.text != annotation.note) confirmDiscard = true else onDismiss()
    }
    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val window = (LocalView.current.parent as DialogWindowProvider).window
        val darkSystemIcons = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        SideEffect {
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = darkSystemIcons
                isAppearanceLightNavigationBars = darkSystemIcons
            }
        }
        Surface(
            modifier = Modifier
                .then(if (compact) Modifier else Modifier.widthIn(max = 560.dp))
                .fillMaxWidth()
                .fillMaxHeight(if (compact) 1f else 0.9f),
            shape = if (compact) RectangleShape else MaterialTheme.shapes.extraLarge,
        ) {
            Scaffold(
                modifier = Modifier.imePadding(),
                topBar = {
                    Column {
                        TopAppBar(
                            title = {},
                            navigationIcon = {
                                IconButton(onClick = dismiss) {
                                    Icon(Icons.Default.Close, stringResource(R.string.web_annotation_close))
                                }
                            },
                            actions = {
                                if (canDelete) {
                                    IconButton(onClick = onDelete, enabled = !saving) {
                                        Icon(Icons.Default.Delete, stringResource(R.string.web_annotation_delete))
                                    }
                                }
                                TextButton(onClick = { onSave(note.text) }, enabled = !saving) {
                                    Text(stringResource(R.string.web_annotation_save))
                                }
                            },
                        )
                        PrimaryTabRow(selectedTabIndex = if (previewSelected) 1 else 0) {
                            Tab(
                                selected = !previewSelected,
                                onClick = { previewSelected = false },
                                text = { Text(stringResource(R.string.web_annotation_edit)) },
                                modifier = Modifier.testTag("web_annotation_edit_tab"),
                            )
                            Tab(
                                selected = previewSelected,
                                onClick = {
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                    previewSelected = true
                                },
                                text = { Text(stringResource(R.string.web_annotation_preview)) },
                                modifier = Modifier.testTag("web_annotation_preview_tab"),
                            )
                        }
                    }
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .then(if (previewSelected) Modifier.verticalScroll(previewScrollState) else Modifier)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (saving) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    if (failed) {
                        Text(
                            stringResource(R.string.web_annotation_failed),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (previewSelected) {
                        WebAnnotationQuote(annotation.quote)
                        Surface(
                            modifier = Modifier.fillMaxWidth().testTag("web_annotation_preview"),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            if (note.text.isBlank()) {
                                Text(
                                    stringResource(R.string.web_annotation_markdown_hint),
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                WebAnnotationMarkdown(note.text, modifier = Modifier.padding(16.dp))
                            }
                        }
                    } else {
                        WebAnnotationFormatToolbar(enabled = !saving) { format ->
                            val edit = WebAnnotationEditorRules.apply(
                                note.text, note.selection.start, note.selection.end, format,
                            )
                            note = TextFieldValue(edit.text, TextRange(edit.selectionStart, edit.selectionEnd))
                            focusRequester.requestFocus()
                        }
                        val noteLabel = stringResource(R.string.web_annotation_note)
                        BasicTextField(
                            value = note,
                            onValueChange = { if (!saving && it.text.length <= WebAnnotationRules.MAX_NOTE_CHARS) note = it },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .semantics { contentDescription = noteLabel }
                                .testTag("web_annotation_editor"),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            readOnly = saving,
                        )
                        Text(
                            "${note.text.length} / ${WebAnnotationRules.MAX_NOTE_CHARS}",
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
        }
        if (confirmDiscard) {
            AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                title = { Text(stringResource(R.string.web_annotation_discard_title)) },
                text = { Text(stringResource(R.string.web_annotation_discard_message)) },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.web_annotation_discard)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDiscard = false }) {
                        Text(stringResource(R.string.web_annotation_keep_editing))
                    }
                },
            )
        }
    }
}

@Composable
private fun WebAnnotationFormatToolbar(enabled: Boolean, onFormat: (WebAnnotationFormat) -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp)) {
            WebAnnotationFormat.entries.forEach { format ->
                val (icon, label) = when (format) {
                    WebAnnotationFormat.Bold -> R.drawable.ic_annotation_bold to R.string.web_annotation_format_bold
                    WebAnnotationFormat.Italic -> R.drawable.ic_annotation_italic to R.string.web_annotation_format_italic
                    WebAnnotationFormat.Heading -> R.drawable.ic_annotation_heading to R.string.web_annotation_format_heading
                    WebAnnotationFormat.BulletList -> R.drawable.ic_annotation_list to R.string.web_annotation_format_list
                    WebAnnotationFormat.Link -> R.drawable.ic_annotation_link to R.string.web_annotation_format_link
                    WebAnnotationFormat.Code -> R.drawable.ic_annotation_code to R.string.web_annotation_format_code
                }
                IconButton(onClick = { onFormat(format) }, enabled = enabled) {
                    Icon(painterResource(icon), stringResource(label))
                }
            }
        }
    }
}
