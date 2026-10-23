package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.reader.PocketSpeechCatalog
import dev.sk2andy.materialbrowser.reader.PocketSpeechModelState
import java.util.Locale

@Composable
internal fun PocketSpeechOptions(
    state: PocketSpeechModelState,
    selected: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onDownload: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    articleLanguage: String? = null,
    onUseLanguage: ((String) -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsPreferenceGroup {
            SettingsSwitch(
                title = stringResource(R.string.reader_pocket_title),
                subtitle = stringResource(R.string.reader_pocket_summary),
                checked = selected,
                enabled = state.loaded,
                onCheckedChange = onEnabledChanged,
                modifier = Modifier.testTag("reader_pocket_enabled"),
            )
        }
        Text(stringResource(R.string.reader_pocket_download_summary), style = MaterialTheme.typography.bodySmall)
        if (selected && onUseLanguage != null) {
            Text(
                if (articleLanguage == null) stringResource(R.string.reader_pocket_unknown_language)
                else if (PocketSpeechCatalog.languages.none { it.code == articleLanguage })
                    stringResource(R.string.reader_pocket_unsupported_language, languageName(articleLanguage))
                else stringResource(R.string.reader_pocket_article_language, languageName(articleLanguage)),
                modifier = Modifier.testTag("reader_pocket_article_language"),
            )
        }
        if (!state.loaded) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        PocketSpeechCatalog.languages.forEach { language ->
            val status = state.languages[language.code]
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(languageName(language.code), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.reader_pocket_download_size, language.downloadBytes / 1_000_000, language.installedBytes / 1_000_000),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (status?.busy == true) {
                    val progress = status.progress
                    if (progress == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    else {
                        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.reader_voice_downloading, (progress * 100).toInt()))
                    }
                }
                if (status?.error == true) {
                    Text(stringResource(R.string.reader_voice_model_error), color = MaterialTheme.colorScheme.error)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        status?.busy == true && status.progress != null -> TextButton(
                            onClick = { onCancel(language.code) },
                            modifier = Modifier.testTag("reader_pocket_cancel_${language.code}"),
                        ) { Text(stringResource(R.string.reader_pocket_cancel)) }
                        status?.busy == true -> Unit
                        status?.ready == true -> {
                            if (onUseLanguage != null) TextButton(
                                onClick = { onUseLanguage(language.code) },
                                modifier = Modifier.testTag("reader_pocket_use_${language.code}"),
                            ) { Text(stringResource(R.string.reader_pocket_use_language)) }
                            TextButton(
                                onClick = { onDelete(language.code) },
                                modifier = Modifier.testTag("reader_pocket_delete_${language.code}"),
                            ) { Text(stringResource(R.string.reader_pocket_remove)) }
                        }
                        else -> TextButton(
                            onClick = { onDownload(language.code) },
                            enabled = state.loaded,
                            modifier = Modifier.testTag("reader_pocket_download_${language.code}"),
                        ) { Text(stringResource(R.string.reader_voice_download)) }
                    }
                }
                if (status?.ready == true) Text(
                    stringResource(R.string.reader_pocket_downloaded),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text(stringResource(R.string.reader_pocket_license_summary), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { uriHandler.openUri("https://huggingface.co/kyutai/pocket-tts") }) {
            Text(stringResource(R.string.reader_pocket_license))
        }
    }
}

private fun languageName(code: String): String =
    Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault())
