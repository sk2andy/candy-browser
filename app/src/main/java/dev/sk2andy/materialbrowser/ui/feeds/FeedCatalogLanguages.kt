package dev.sk2andy.materialbrowser.ui.feeds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.ui.LibrarySearchBar
import java.text.Normalizer
import java.util.Locale

internal data class FeedCatalogLanguage(
    val code: String,
    val label: String,
    val nativeLabel: String,
)

internal object FeedCatalogLanguageRules {
    fun baseLanguage(value: String): String =
        when (val code = value.lowercase(Locale.ROOT).substringBefore('-').substringBefore('_')) {
            "iw" -> "he"
            "in" -> "id"
            "ji" -> "yi"
            else -> code
        }

    fun choices(displayLocale: Locale): List<FeedCatalogLanguage> =
        Locale.getISOLanguages()
            .mapNotNull { code ->
                val canonical = baseLanguage(code)
                val locale = Locale.forLanguageTag(canonical)
                // Android's ICU list also includes the undetermined/root locale.
                if (locale.language.isBlank()) return@mapNotNull null
                val label =
                    locale.getDisplayLanguage(displayLocale).ifBlank {
                        locale.getDisplayLanguage(Locale.ENGLISH).ifBlank { canonical }
                    }
                FeedCatalogLanguage(
                    canonical,
                    label,
                    locale.getDisplayLanguage(locale).ifBlank { label },
                )
            }
            .distinctBy { it.code }
            .sortedBy { it.label.lowercase(displayLocale) }

    fun matches(language: FeedCatalogLanguage, query: String): Boolean {
        val needle = folded(query.trim())
        return needle.isEmpty() ||
            folded(
                    listOf(
                            language.code,
                            language.label,
                            language.nativeLabel,
                            Locale.forLanguageTag(language.code).getDisplayLanguage(Locale.ENGLISH),
                        )
                        .joinToString(" ")
                )
                .contains(needle)
    }

    private fun folded(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKD)
            .replace("\\p{M}".toRegex(), "")
            .lowercase(Locale.ROOT)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedCatalogLanguageSheet(
    selected: List<String>,
    onApply: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val choices = remember(locale) { FeedCatalogLanguageRules.choices(locale) }
    var draft by remember(selected) { mutableStateOf(selected.toSet()) }
    var query by remember { mutableStateOf("") }
    val filtered =
        remember(choices, query) { choices.filter { FeedCatalogLanguageRules.matches(it, query) } }
    val closeDescription = stringResource(R.string.feed_catalog_close_languages)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            FeedSheetDragHandle(closeDescription, Modifier.testTag("feed_catalog_language_handle"))
        },
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).navigationBarsPadding().imePadding()) {
            LazyColumn(
                Modifier.weight(1f).testTag("feed_catalog_language_list"),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item {
                    Column(
                        Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.feed_catalog_languages_title),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            stringResource(R.string.feed_catalog_languages_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    LibrarySearchBar(
                        query = query,
                        placeholder = stringResource(R.string.feed_catalog_language_search),
                        clearContentDescription = stringResource(R.string.feed_catalog_clear),
                        testTag = "feed_catalog_language_search",
                        onQueryChange = { query = it },
                    )
                }
                item {
                    LanguageOption(
                        label = stringResource(R.string.feed_catalog_all_languages),
                        supporting = stringResource(R.string.feed_catalog_world_selected),
                        selected = draft.isEmpty(),
                        onSelect = { draft = emptySet() },
                        modifier = Modifier.testTag("feed_catalog_language_all"),
                    )
                }
                items(filtered, key = { it.code }) { language ->
                    LanguageOption(
                        label = language.label,
                        supporting = language.nativeLabel,
                        selected = language.code in draft,
                        onSelect = {
                            draft =
                                if (language.code in draft) draft - language.code
                                else draft + language.code
                        },
                        modifier = Modifier.testTag("feed_catalog_language_${language.code}"),
                    )
                }
                if (filtered.isEmpty())
                    item {
                        Text(
                            stringResource(R.string.feed_catalog_language_none),
                            Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(
                    onClick = { onApply(draft.sorted()) },
                    modifier = Modifier.fillMaxWidth().testTag("feed_catalog_language_apply"),
                ) {
                    Text(stringResource(R.string.feed_catalog_language_apply))
                }
                Text(
                    if (draft.isEmpty()) stringResource(R.string.feed_catalog_world_selected)
                    else
                        pluralStringResource(
                            R.plurals.feed_catalog_languages_selection,
                            draft.size,
                            draft.size,
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LanguageOption(
    label: String,
    supporting: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onSelect() })
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = selected, onCheckedChange = null)
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                supporting,
                style =
                    MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
