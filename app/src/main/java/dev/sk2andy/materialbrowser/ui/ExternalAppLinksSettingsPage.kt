package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.ExternalAppLinkHandling
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsHeaderArtwork
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsHeaderIllustration
import dev.sk2andy.materialbrowser.shared.ui.settings.settingsSearchTarget

internal object ExternalAppLinksSettingsTestTags {
    const val Handling = "external_app_links_handling"
    const val Empty = "external_app_links_empty"
    const val ResetAll = "external_app_links_reset_all"
    const val ConfirmResetAll = "external_app_links_confirm_reset_all"
    fun reset(domain: String): String = "external_app_links_reset:$domain"
}

@Composable
internal fun ExternalAppLinksSettingsPage(
    externalAppLinkHandling: ExternalAppLinkHandling,
    siteExceptions: List<String>,
    onExternalAppLinkHandlingChanged: (ExternalAppLinkHandling) -> Unit,
    onResetSiteException: (String) -> Unit,
    onResetAllSiteExceptions: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmResetAll by rememberSaveable { mutableStateOf(false) }
    SettingsPage(title = stringResource(R.string.settings_external_app_links_title), onBack = onBack) {
        SettingsHeaderIllustration(SettingsHeaderArtwork.ExternalApps)
        SettingsPageSpacer()
        SettingsPreferenceGroup {
            BrowserRadioChoices(
                title = stringResource(R.string.settings_external_app_links_title),
                selected = externalAppLinkHandling,
                choices = ExternalAppLinkHandling.entries,
                label = { it.displayName() },
                enabled = true,
                onSelected = onExternalAppLinkHandlingChanged,
                modifier = Modifier.testTag(ExternalAppLinksSettingsTestTags.Handling),
            )
            Text(
                text = stringResource(R.string.settings_external_app_links_summary),
                modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 18.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingsPageSpacer()
        Column(Modifier.settingsSearchTarget(stringResource(R.string.settings_external_app_sites_title))) {
            SettingsSectionTitle(stringResource(R.string.settings_external_app_sites_title))
            Text(
                text = stringResource(R.string.settings_external_app_sites_summary),
                modifier = Modifier.padding(bottom = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsPreferenceGroup {
                if (siteExceptions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_external_app_sites_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp).testTag(ExternalAppLinksSettingsTestTags.Empty),
                    )
                } else {
                    siteExceptions.forEachIndexed { index, domain ->
                        if (index > 0) SettingsPreferenceDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(text = domain, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = stringResource(R.string.settings_external_app_site_always_candy),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val resetDescription = stringResource(R.string.settings_external_app_site_reset_description, domain)
                            TextButton(
                                onClick = { onResetSiteException(domain) },
                                modifier = Modifier.testTag(ExternalAppLinksSettingsTestTags.reset(domain))
                                    .semantics { contentDescription = resetDescription },
                            ) {
                                Text(stringResource(R.string.settings_external_app_site_reset))
                            }
                        }
                    }
                }
            }
            if (siteExceptions.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = { confirmResetAll = true },
                        modifier = Modifier.testTag(ExternalAppLinksSettingsTestTags.ResetAll),
                    ) {
                        Text(stringResource(R.string.settings_external_app_sites_reset_all))
                    }
                }
            }
        }
    }
    if (confirmResetAll && siteExceptions.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { confirmResetAll = false },
            title = { Text(stringResource(R.string.settings_external_app_sites_reset_all)) },
            text = { Text(stringResource(R.string.settings_external_app_sites_reset_all_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmResetAll = false
                        onResetAllSiteExceptions()
                    },
                    modifier = Modifier.testTag(ExternalAppLinksSettingsTestTags.ConfirmResetAll),
                ) {
                    Text(stringResource(R.string.settings_external_app_sites_reset_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmResetAll = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
