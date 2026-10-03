package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.AppLanguageRules
import java.util.Locale

@Composable
internal fun AppLanguageSettings(
    languageTag: String,
    supportedLocales: List<Locale>,
    onLanguageChanged: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val systemLabel = stringResource(R.string.settings_app_language_system)
    val selectedLocale = AppLanguageRules.selectedLocale(languageTag, supportedLocales)
    Box {
        SettingsChoice(
            title = stringResource(R.string.settings_app_language),
            value = selectedLocale?.getDisplayName(selectedLocale) ?: systemLabel,
            expanded = expanded,
            onClick = { expanded = true },
            modifier = Modifier.testTag(BrowserSettingsTestTags.AppLanguage),
        )
        SettingsDropdown(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SettingsDropdownItem(
                label = systemLabel,
                selected = languageTag.isEmpty(),
                onClick = {
                    expanded = false
                    onLanguageChanged("")
                },
            )
            supportedLocales.forEach { locale ->
                SettingsDropdownItem(
                    label = locale.getDisplayName(locale),
                    selected = locale == selectedLocale,
                    onClick = {
                        expanded = false
                        onLanguageChanged(locale.toLanguageTag())
                    },
                )
            }
        }
    }
}
