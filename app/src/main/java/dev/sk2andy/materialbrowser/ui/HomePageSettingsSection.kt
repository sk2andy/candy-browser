package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.HomePageRules
import dev.sk2andy.materialbrowser.browser.HomePageSettings

internal object HomePageSettingsTestTags {
    const val Open = "home_page_settings_open"
    const val HomeUrl = "home_page_settings_home_url"
    const val NewTabUrl = "home_page_settings_new_tab_url"
    const val CopyHome = "home_page_settings_copy_home"
    const val Reset = "home_page_settings_reset"
    const val Save = "home_page_settings_save"
    const val Cancel = "home_page_settings_cancel"
}

@Composable
internal fun HomePageSettingsSection(
    settings: HomePageSettings,
    onSettingsChanged: (HomePageSettings) -> Unit,
) {
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    SettingsLink(
        icon = Icons.Default.Home,
        title = stringResource(R.string.settings_home_pages_title),
        subtitle = stringResource(R.string.settings_home_pages_summary),
        onClick = { editorVisible = true },
        modifier = Modifier.testTag(HomePageSettingsTestTags.Open),
    )
    if (editorVisible) {
        HomePageSettingsDialog(
            settings = settings,
            onSave = {
                onSettingsChanged(it)
                editorVisible = false
            },
            onDismiss = { editorVisible = false },
        )
    }
}

@Composable
private fun HomePageSettingsDialog(
    settings: HomePageSettings,
    onSave: (HomePageSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var homeUrl by rememberSaveable(settings.homeUrl) { mutableStateOf(settings.homeUrl) }
    var newTabUrl by rememberSaveable(settings.newTabUrl) { mutableStateOf(settings.newTabUrl) }
    val normalizedHome = HomePageRules.normalizeUrl(homeUrl)
    val normalizedNewTab = HomePageRules.normalizeUrl(newTabUrl)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_home_pages_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.settings_home_pages_help),
                    style = MaterialTheme.typography.bodyMedium,
                )
                HomeUrlField(
                    value = homeUrl,
                    onValueChange = { homeUrl = it },
                    label = stringResource(R.string.settings_home_button_url),
                    invalid = normalizedHome == null,
                    tag = HomePageSettingsTestTags.HomeUrl,
                )
                HomeUrlField(
                    value = newTabUrl,
                    onValueChange = { newTabUrl = it },
                    label = stringResource(R.string.settings_new_tab_url),
                    invalid = normalizedNewTab == null,
                    tag = HomePageSettingsTestTags.NewTabUrl,
                )
                TextButton(
                    onClick = { newTabUrl = homeUrl },
                    modifier = Modifier.testTag(HomePageSettingsTestTags.CopyHome),
                ) { Text(stringResource(R.string.settings_home_pages_copy)) }
                TextButton(
                    onClick = {
                        homeUrl = ""
                        newTabUrl = ""
                    },
                    modifier = Modifier.testTag(HomePageSettingsTestTags.Reset),
                ) { Text(stringResource(R.string.settings_home_pages_reset)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = normalizedHome != null && normalizedNewTab != null,
                onClick = {
                    if (normalizedHome != null && normalizedNewTab != null) {
                        onSave(HomePageSettings(homeUrl = normalizedHome, newTabUrl = normalizedNewTab))
                    }
                },
                modifier = Modifier.testTag(HomePageSettingsTestTags.Save),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(HomePageSettingsTestTags.Cancel)) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun HomeUrlField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    invalid: Boolean,
    tag: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(stringResource(R.string.settings_home_pages_internal)) },
        supportingText = {
            Text(stringResource(if (invalid) R.string.settings_home_pages_invalid else R.string.settings_home_pages_empty))
        },
        isError = invalid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}
