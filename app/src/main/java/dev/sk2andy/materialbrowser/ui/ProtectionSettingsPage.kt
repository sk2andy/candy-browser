package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.blocking.BlockerSettings
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor

internal object ProtectionSettingsTestTags {
    const val UserCaWarning = "protection_settings_user_ca_warning"
    const val ExportAppData = "protection_settings_export_app_data"
    const val ImportAppData = "protection_settings_import_app_data"
    const val Recall = "protection_settings_recall"
}

@Composable
internal fun ProtectionAndDataSettingsPage(
    blockerSettings: BlockerSettings,
    blockedCount: Int,
    isRecallEnabled: Boolean = false,
    trustsUserCertificates: Boolean,
    onBlockerSettingsChanged: (BlockerSettings) -> Unit,
    onRecallEnabledChanged: (Boolean) -> Unit = {},
    onPrivacyXRay: () -> Unit,
    onPermissionRadar: () -> Unit,
    onFilterStudio: () -> Unit,
    onExportAppData: () -> Unit = {},
    onImportAppData: () -> Unit = {},
    onClearData: () -> Unit,
    onBack: () -> Unit,
) {
    SettingsPage(
        title = stringResource(R.string.settings_protection_data_title),
        onBack = onBack,
    ) {
        Surface(
            onClick = onPermissionRadar,
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.tertiaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.permission_radar_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.permission_radar_settings_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                Text(
                    "◉",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        PrivacyXRaySettingsCounter(
            blockedCount = blockedCount,
            onClick = onPrivacyXRay,
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            onClick = onFilterStudio,
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp)) {
                Text(
                    stringResource(R.string.filter_studio_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.filter_studio_settings_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        if (trustsUserCertificates) {
            Spacer(Modifier.height(18.dp))
            UserCaTrustWarning()
        }
        Spacer(Modifier.height(18.dp))
        SettingsSwitch(
            title = stringResource(R.string.settings_block_ads_title),
            subtitle = stringResource(R.string.settings_block_ads_subtitle),
            checked = blockerSettings.blockAdsAndTrackers,
            onCheckedChange = {
                onBlockerSettingsChanged(blockerSettings.copy(blockAdsAndTrackers = it))
            },
        )
        SettingsSwitch(
            title = stringResource(R.string.settings_hide_cookie_banners_title),
            subtitle = stringResource(R.string.settings_hide_cookie_banners_subtitle),
            checked = blockerSettings.hideCookieConsent,
            onCheckedChange = {
                onBlockerSettingsChanged(blockerSettings.copy(hideCookieConsent = it))
            },
        )
        SettingsSwitch(
            title = stringResource(R.string.settings_block_third_party_cookies_title),
            subtitle = stringResource(R.string.settings_block_third_party_cookies_subtitle),
            checked = blockerSettings.blockThirdPartyCookies,
            onCheckedChange = {
                onBlockerSettingsChanged(blockerSettings.copy(blockThirdPartyCookies = it))
            },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_protection_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        SettingsSwitch(
            title = stringResource(R.string.recall_settings_title),
            subtitle = stringResource(R.string.recall_settings_summary),
            checked = isRecallEnabled,
            onCheckedChange = onRecallEnabledChanged,
            modifier = Modifier.testTag(ProtectionSettingsTestTags.Recall),
        )
        Spacer(Modifier.height(16.dp))
        DataArchiveAction(
            title = stringResource(R.string.data_archive_export_title),
            summary = stringResource(R.string.data_archive_export_summary),
            onClick = onExportAppData,
            modifier = Modifier.testTag(ProtectionSettingsTestTags.ExportAppData),
        )
        Spacer(Modifier.height(8.dp))
        DataArchiveAction(
            title = stringResource(R.string.data_archive_import_title),
            summary = stringResource(R.string.data_archive_import_summary),
            onClick = onImportAppData,
            modifier = Modifier.testTag(ProtectionSettingsTestTags.ImportAppData),
        )
        Spacer(Modifier.height(16.dp))
        Surface(
            onClick = onClearData,
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Text(
                stringResource(R.string.action_clear_browsing_data),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DataArchiveAction(
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp),
        shape = MaterialTheme.shapes.large,
        color = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UserCaTrustWarning(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ProtectionSettingsTestTags.UserCaWarning),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                stringResource(R.string.settings_user_ca_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.settings_user_ca_summary),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
