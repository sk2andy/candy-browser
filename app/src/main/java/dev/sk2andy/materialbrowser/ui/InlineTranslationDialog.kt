package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.translation.InlineTranslationCoordinator
import dev.sk2andy.materialbrowser.browser.translation.InlineTranslationPair
import java.util.Locale

@Composable
internal fun InlineTranslationDialog(
    coordinator: InlineTranslationCoordinator,
    providerName: String,
) {
    val state = coordinator.state ?: return
    val locale = LocalConfiguration.current.locales[0]
    var source by remember(state.text, state.pairs) { mutableStateOf<String?>(null) }
    var target by remember(state.text, state.pairs) {
        mutableStateOf(state.pairs.firstOrNull {
            Locale.forLanguageTag(it.target).language == locale.language
        }?.target)
    }
    val targets = state.pairs.filter { source == null || it.source == source }.map { it.target }.distinct()
    LaunchedEffect(source, targets) { if (target !in targets) target = null }
    val pair = state.pairs.firstOrNull { it == InlineTranslationPair(source.orEmpty(), target.orEmpty()) }
    AlertDialog(
        onDismissRequest = coordinator::dismiss,
        title = { Text(stringResource(R.string.inline_translation_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(state.text.take(240), style = MaterialTheme.typography.bodySmall)
                when {
                    state.loading || state.translating -> CircularProgressIndicator()
                    state.pairs.isEmpty() -> Text(stringResource(R.string.inline_translation_unavailable))
                    else -> {
                        Text(stringResource(R.string.inline_translation_local_description))
                        TranslationLanguagePicker(
                            label = stringResource(R.string.inline_translation_source),
                            selected = source,
                            languages = state.pairs.map { it.source }.distinct(),
                            onSelected = { source = it },
                        )
                        TranslationLanguagePicker(
                            label = stringResource(R.string.inline_translation_target),
                            selected = target,
                            languages = targets,
                            onSelected = { target = it },
                        )
                    }
                }
                if (state.failed) Text(stringResource(R.string.inline_translation_failed))
                if (state.canManageLanguages) {
                    TextButton(onClick = coordinator::manageLanguages) {
                        Text(stringResource(R.string.inline_translation_manage_languages))
                    }
                }
                if (!state.loading && !state.translating) {
                    Text(stringResource(R.string.inline_translation_fallback_description, providerName))
                    TextButton(
                        onClick = coordinator::usePageProvider,
                        modifier = Modifier.testTag("inline_translation_fallback"),
                    ) { Text(stringResource(R.string.inline_translation_fallback, providerName)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { pair?.let(coordinator::translate) },
                enabled = pair != null && !state.loading && !state.translating,
                modifier = Modifier.testTag("inline_translation_apply"),
            ) { Text(stringResource(R.string.inline_translation_apply)) }
        },
        dismissButton = {
            TextButton(onClick = coordinator::dismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun TranslationLanguagePicker(
    label: String,
    selected: String?,
    languages: List<String>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    fun display(tag: String) = Locale.forLanguageTag(tag).getDisplayName(locale)
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${selected?.let(::display) ?: stringResource(R.string.inline_translation_choose_language)}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            languages.sortedBy(::display).forEach { language ->
                DropdownMenuItem(
                    text = { Text(display(language)) },
                    onClick = {
                        onSelected(language)
                        expanded = false
                    },
                )
            }
        }
    }
}
