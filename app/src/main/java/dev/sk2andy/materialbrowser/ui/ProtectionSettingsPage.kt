package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.blocking.BlockerSettings
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.DnsOverHttpsProvider
import dev.sk2andy.materialbrowser.browser.DnsOverHttpsRules
import dev.sk2andy.materialbrowser.browser.DnsOverHttpsSettings
import dev.sk2andy.materialbrowser.browser.PrivacySignalSettings
import dev.sk2andy.materialbrowser.browser.WebRtcProtectionMode
import dev.sk2andy.materialbrowser.data.HistoryRecordingMode
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor

internal object ProtectionSettingsTestTags {
    const val UserCaWarning = "protection_settings_user_ca_warning"
    const val ExportAppData = "protection_settings_export_app_data"
    const val ImportAppData = "protection_settings_import_app_data"
    const val Recall = "protection_settings_recall"
    const val SaveHistory = "protection_settings_save_history"
    const val ClearHistoryOnExit = "protection_settings_clear_history_on_exit"
    const val DoNotTrack = "protection_settings_do_not_track"
    const val GlobalPrivacyControl = "protection_settings_global_privacy_control"
    const val AutoDeAmp = "protection_settings_auto_de_amp"
    const val WebRtcProtection = "protection_settings_webrtc_protection"
    const val DnsOverHttps = "protection_settings_dns_over_https"
    const val CustomDnsEndpoint = "protection_settings_custom_dns_endpoint"
}

@Composable
internal fun ProtectionAndDataSettingsPage(
    blockerSettings: BlockerSettings,
    blockedCount: Int,
    browserEngineKind: AndroidBrowserEngineKind = AndroidBrowserEngineKind.GeckoView,
    isDnsOverHttpsSupported: Boolean = browserEngineKind == AndroidBrowserEngineKind.GeckoView,
    webRtcProtectionMode: WebRtcProtectionMode = WebRtcProtectionMode.Default,
    privacySignalSettings: PrivacySignalSettings = PrivacySignalSettings.Default,
    isAutoDeAmpEnabled: Boolean = true,
    dnsOverHttpsSettings: DnsOverHttpsSettings = DnsOverHttpsRules.Default,
    isRecallEnabled: Boolean = false,
    historyRecordingMode: HistoryRecordingMode = HistoryRecordingMode.Enabled,
    trustsUserCertificates: Boolean,
    onBlockerSettingsChanged: (BlockerSettings) -> Unit,
    onWebRtcProtectionModeChanged: (WebRtcProtectionMode) -> Unit = {},
    onPrivacySignalSettingsChanged: (PrivacySignalSettings) -> Unit = {},
    onAutoDeAmpEnabledChanged: (Boolean) -> Unit = {},
    onDnsOverHttpsSettingsChanged: (DnsOverHttpsSettings) -> Unit = {},
    onRecallEnabledChanged: (Boolean) -> Unit = {},
    onHistoryRecordingModeChanged: (HistoryRecordingMode) -> Unit = {},
    onPrivacyXRay: () -> Unit,
    onPermissionRadar: () -> Unit,
    onFilterStudio: () -> Unit,
    onExportAppData: () -> Unit = {},
    onImportAppData: () -> Unit = {},
    onClearData: () -> Unit,
    onBack: () -> Unit,
) {
    var webRtcMenuExpanded by remember { mutableStateOf(false) }
    var dnsMenuExpanded by remember { mutableStateOf(false) }
    var customDnsDialogVisible by rememberSaveable { mutableStateOf(false) }
    if (customDnsDialogVisible) {
        CustomDnsEndpointDialog(
            initialEndpoint = dnsOverHttpsSettings.customEndpoint,
            onConfirm = { endpoint ->
                customDnsDialogVisible = false
                onDnsOverHttpsSettingsChanged(
                    DnsOverHttpsSettings(
                        provider = DnsOverHttpsProvider.Custom,
                        customEndpoint = endpoint,
                    ),
                )
            },
            onDismiss = { customDnsDialogVisible = false },
        )
    }
    SettingsPage(
        title = stringResource(R.string.settings_protection_data_title),
        onBack = onBack,
    ) {
        SettingsSectionTitle(stringResource(R.string.settings_protection_group_tools))
        Spacer(Modifier.height(8.dp))
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
        SettingsSectionTitle(stringResource(R.string.settings_section_protection))
        Spacer(Modifier.height(8.dp))
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
        SettingsSwitch(
            title = stringResource(R.string.settings_do_not_track_title),
            subtitle = stringResource(R.string.settings_do_not_track_summary),
            checked = privacySignalSettings.doNotTrackEnabled,
            onCheckedChange = { enabled ->
                onPrivacySignalSettingsChanged(
                    privacySignalSettings.copy(doNotTrackEnabled = enabled),
                )
            },
            modifier = Modifier.testTag(ProtectionSettingsTestTags.DoNotTrack),
        )
        SettingsSwitch(
            title = stringResource(R.string.settings_global_privacy_control_title),
            subtitle = stringResource(R.string.settings_global_privacy_control_summary),
            checked = privacySignalSettings.globalPrivacyControlEnabled,
            onCheckedChange = { enabled ->
                onPrivacySignalSettingsChanged(
                    privacySignalSettings.copy(globalPrivacyControlEnabled = enabled),
                )
            },
            modifier = Modifier.testTag(ProtectionSettingsTestTags.GlobalPrivacyControl),
        )
        SettingsSwitch(
            title = stringResource(R.string.settings_auto_de_amp_title),
            subtitle = stringResource(R.string.settings_auto_de_amp_summary),
            checked = isAutoDeAmpEnabled,
            onCheckedChange = onAutoDeAmpEnabledChanged,
            modifier = Modifier.testTag(ProtectionSettingsTestTags.AutoDeAmp),
        )
        Spacer(Modifier.height(8.dp))
        Box {
            SettingsChoice(
                title = stringResource(R.string.settings_dns_over_https_title),
                value = if (isDnsOverHttpsSupported) {
                    dnsOverHttpsSettings.provider.displayName()
                } else {
                    stringResource(R.string.settings_dns_over_https_unavailable)
                },
                expanded = dnsMenuExpanded,
                onClick = { dnsMenuExpanded = true },
                modifier = Modifier.testTag(ProtectionSettingsTestTags.DnsOverHttps),
                enabled = isDnsOverHttpsSupported,
            )
            SettingsDropdown(
                expanded = isDnsOverHttpsSupported && dnsMenuExpanded,
                onDismissRequest = { dnsMenuExpanded = false },
            ) {
                DnsOverHttpsProvider.entries.forEach { provider ->
                    SettingsDropdownItem(
                        label = provider.displayName(),
                        selected = provider == dnsOverHttpsSettings.provider,
                        onClick = {
                            dnsMenuExpanded = false
                            if (provider == DnsOverHttpsProvider.Custom) {
                                customDnsDialogVisible = true
                            } else if (provider != dnsOverHttpsSettings.provider) {
                                onDnsOverHttpsSettingsChanged(
                                    dnsOverHttpsSettings.copy(provider = provider),
                                )
                            }
                        },
                    )
                }
            }
        }
        Text(
            text = stringResource(
                if (!isDnsOverHttpsSupported) {
                    R.string.settings_dns_over_https_system_webview_summary
                } else if (dnsOverHttpsSettings.provider == DnsOverHttpsProvider.System) {
                    R.string.settings_dns_over_https_system_summary
                } else {
                    R.string.settings_dns_over_https_gecko_summary
                },
            ),
            modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 18.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = if (isDnsOverHttpsSupported) 1f else 0.6f,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Box {
            SettingsChoice(
                title = stringResource(R.string.settings_webrtc_protection_title),
                value = webRtcProtectionMode.displayName(),
                expanded = webRtcMenuExpanded,
                onClick = { webRtcMenuExpanded = true },
                modifier = Modifier.testTag(ProtectionSettingsTestTags.WebRtcProtection),
            )
            SettingsDropdown(
                expanded = webRtcMenuExpanded,
                onDismissRequest = { webRtcMenuExpanded = false },
            ) {
                WebRtcProtectionMode.entries.forEach { mode ->
                    SettingsDropdownItem(
                        label = mode.displayName(),
                        selected = mode == webRtcProtectionMode,
                        onClick = {
                            webRtcMenuExpanded = false
                            if (mode != webRtcProtectionMode) {
                                onWebRtcProtectionModeChanged(mode)
                            }
                        },
                    )
                }
            }
        }
        Text(
            text = stringResource(
                when (webRtcProtectionMode) {
                    WebRtcProtectionMode.Standard -> R.string.settings_webrtc_standard_summary
                    WebRtcProtectionMode.HideLocalNetworkIp -> when (browserEngineKind) {
                        AndroidBrowserEngineKind.GeckoView ->
                            R.string.settings_webrtc_hide_local_ip_gecko_summary
                        AndroidBrowserEngineKind.SystemWebView ->
                            R.string.settings_webrtc_policy_system_summary
                    }
                    WebRtcProtectionMode.DisableNonProxiedUdp -> when (browserEngineKind) {
                        AndroidBrowserEngineKind.GeckoView ->
                            R.string.settings_webrtc_disable_non_proxied_udp_gecko_summary
                        AndroidBrowserEngineKind.SystemWebView ->
                            R.string.settings_webrtc_policy_system_summary
                    }
                    WebRtcProtectionMode.ProtectIpAddresses -> when (browserEngineKind) {
                        AndroidBrowserEngineKind.GeckoView ->
                            R.string.settings_webrtc_protect_gecko_summary
                        AndroidBrowserEngineKind.SystemWebView ->
                            R.string.settings_webrtc_protect_system_summary
                    }
                    WebRtcProtectionMode.Block -> R.string.settings_webrtc_block_summary
                },
            ),
            modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 18.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_protection_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        SettingsSectionTitle(stringResource(R.string.history_title))
        SettingsSwitch(
            title = stringResource(R.string.history_save_title),
            subtitle = stringResource(R.string.history_save_summary),
            checked = historyRecordingMode != HistoryRecordingMode.Disabled,
            onCheckedChange = { enabled ->
                onHistoryRecordingModeChanged(
                    if (enabled) HistoryRecordingMode.Enabled else HistoryRecordingMode.Disabled,
                )
            },
            modifier = Modifier.testTag(ProtectionSettingsTestTags.SaveHistory),
        )
        SettingsSwitch(
            title = stringResource(R.string.history_clear_on_exit_title),
            subtitle = stringResource(R.string.history_clear_on_exit_summary),
            checked = historyRecordingMode == HistoryRecordingMode.ClearOnExit,
            enabled = historyRecordingMode != HistoryRecordingMode.Disabled,
            onCheckedChange = { enabled ->
                onHistoryRecordingModeChanged(
                    if (enabled) {
                        HistoryRecordingMode.ClearOnExit
                    } else {
                        HistoryRecordingMode.Enabled
                    },
                )
            },
            modifier = Modifier.testTag(ProtectionSettingsTestTags.ClearHistoryOnExit),
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
        SettingsSectionTitle(stringResource(R.string.settings_protection_group_app_data))
        Spacer(Modifier.height(8.dp))
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
private fun CustomDnsEndpointDialog(
    initialEndpoint: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var endpointDraft by rememberSaveable(initialEndpoint) { mutableStateOf(initialEndpoint) }
    val normalizedEndpoint = DnsOverHttpsRules.normalizedCustomEndpoint(endpointDraft)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_dns_over_https_custom_dialog_title)) },
        text = {
            OutlinedTextField(
                value = endpointDraft,
                onValueChange = { value ->
                    endpointDraft = value.take(DnsOverHttpsRules.MAX_ENDPOINT_LENGTH)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ProtectionSettingsTestTags.CustomDnsEndpoint),
                label = { Text(stringResource(R.string.settings_dns_over_https_custom_endpoint)) },
                supportingText = {
                    Text(
                        stringResource(
                            if (endpointDraft.isNotBlank() && normalizedEndpoint == null) {
                                R.string.settings_dns_over_https_custom_invalid
                            } else {
                                R.string.settings_dns_over_https_custom_summary
                            },
                        ),
                    )
                },
                isError = endpointDraft.isNotBlank() && normalizedEndpoint == null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = normalizedEndpoint != null,
                onClick = { normalizedEndpoint?.let(onConfirm) },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun WebRtcProtectionMode.displayName(): String = stringResource(
    when (this) {
        WebRtcProtectionMode.Standard -> R.string.settings_webrtc_mode_standard
        WebRtcProtectionMode.HideLocalNetworkIp ->
            R.string.settings_webrtc_mode_hide_local_network_ip
        WebRtcProtectionMode.DisableNonProxiedUdp ->
            R.string.settings_webrtc_mode_disable_non_proxied_udp
        WebRtcProtectionMode.ProtectIpAddresses -> R.string.settings_webrtc_mode_protect
        WebRtcProtectionMode.Block -> R.string.settings_webrtc_mode_block
    },
)

@Composable
private fun DnsOverHttpsProvider.displayName(): String = stringResource(
    when (this) {
        DnsOverHttpsProvider.System -> R.string.settings_dns_over_https_provider_system
        DnsOverHttpsProvider.Cloudflare -> R.string.settings_dns_over_https_provider_cloudflare
        DnsOverHttpsProvider.Google -> R.string.settings_dns_over_https_provider_google
        DnsOverHttpsProvider.Quad9 -> R.string.settings_dns_over_https_provider_quad9
        DnsOverHttpsProvider.Custom -> R.string.settings_dns_over_https_provider_custom
    },
)

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
