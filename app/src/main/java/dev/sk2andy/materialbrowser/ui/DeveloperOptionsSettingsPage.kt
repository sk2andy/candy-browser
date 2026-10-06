package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.shared.ui.settings.settingsSearchTarget
import dev.sk2andy.materialbrowser.browser.gecko.GeckoLoggingStatus
import dev.sk2andy.materialbrowser.data.BrowserChromeScrollDispatchMode
import dev.sk2andy.materialbrowser.data.BrowserMemorySettings
import dev.sk2andy.materialbrowser.data.DeveloperSettings
import dev.sk2andy.materialbrowser.data.GeckoSafeAreaSettings
import dev.sk2andy.materialbrowser.data.GeckoLoggingRules
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor
import kotlin.math.roundToInt

internal object DeveloperOptionsTestTags {
    const val ForegroundTabIdleTimeout = "developer_options_foreground_tab_idle_timeout"
    const val BackgroundWarmTabCount = "developer_options_background_warm_tab_count"
    const val HistoryCacheLifetime = "developer_options_history_cache_lifetime"
    const val BrowserChromeScrollDispatchMode = "developer_options_scroll_dispatch_mode"
    const val LayoutQuietPeriod = "developer_options_layout_quiet_period"
    const val RequiredFailures = "developer_options_required_failures"
    const val ForceSafeAreaFallback = "developer_options_force_safe_area_fallback"
    const val HttpPasswordAutofill = "developer_options_http_password_autofill"
    const val InputDiagnostics = "developer_options_input_diagnostics"
    const val AppLogging = "developer_options_app_logging"
    const val ExportLogs = "developer_options_export_logs"
    const val ClearLogs = "developer_options_clear_logs"
    const val GeckoLogging = "developer_options_gecko_logging"
    const val GeckoLoggingWarning = "developer_options_gecko_logging_warning"
    const val GeckoLoggingStatus = "developer_options_gecko_logging_status"
    const val GeckoLoggingModules = "developer_options_gecko_logging_modules"
    const val ApplyGeckoLoggingModules = "developer_options_apply_gecko_logging_modules"
    const val ExportGeckoLogs = "developer_options_export_gecko_logs"
    const val ClearGeckoLogs = "developer_options_clear_gecko_logs"
    const val CopyDiagnostics = "developer_options_copy_diagnostics"
    const val ShowOnboarding = "developer_options_show_onboarding"
    const val ShowReleaseNotes = "developer_options_show_release_notes"
    const val Reset = "developer_options_reset"
    const val GeckoSafeAreaEnabled = "developer_options_gecko_safe_area_enabled"
    const val GeckoAddInsetToNegativeTop = "developer_options_gecko_add_inset_to_negative_top"
    const val GeckoRecheckAddedElements = "developer_options_gecko_recheck_added_elements"
    const val GeckoRecheckChangedElements = "developer_options_gecko_recheck_changed_elements"
    const val GeckoRequireInteraction = "developer_options_gecko_require_interaction"
    const val GeckoRecheckOnResize = "developer_options_gecko_recheck_on_resize"
    const val GeckoInteractionWindow = "developer_options_gecko_interaction_window"
    const val GeckoMutationDebounce = "developer_options_gecko_mutation_debounce"
    const val GeckoMaxElementsPerBatch = "developer_options_gecko_max_elements_per_batch"
    const val GeckoMaxBatchDuration = "developer_options_gecko_max_batch_duration"
    const val GeckoMaxInitialElements = "developer_options_gecko_max_initial_elements"
    const val GeckoReset = "developer_options_gecko_reset"
}

@Composable
internal fun DeveloperOptionsSettingsPage(
    settings: DeveloperSettings,
    isHttpPasswordAutofillEnabled: Boolean = false,
    isHttpPasswordAutofillSupported: Boolean = false,
    isInputDiagnosticsEnabled: Boolean = false,
    isGeckoLoggingSupported: Boolean = false,
    geckoLoggingStatus: GeckoLoggingStatus = GeckoLoggingStatus.Disabled,
    onSettingsChanged: (DeveloperSettings) -> Unit,
    onHttpPasswordAutofillEnabledChanged: (Boolean) -> Unit = {},
    onInputDiagnosticsEnabledChanged: (Boolean) -> Unit = {},
    onCopyDiagnostics: () -> Unit = {},
    onExportLogs: () -> Unit = {},
    onClearLogs: () -> Unit = {},
    onExportGeckoLogs: () -> Unit = {},
    onClearGeckoLogs: () -> Unit = {},
    onShowOnboarding: () -> Unit = {},
    onShowReleaseNotes: () -> Unit = {},
    onBack: () -> Unit,
) {
    var httpAutofillConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    var scrollDispatchMenuExpanded by remember { mutableStateOf(false) }
    var geckoModulesDraft by rememberSaveable(settings.geckoLoggingModules) {
        mutableStateOf(settings.geckoLoggingModules)
    }
    SettingsPage(
        title = stringResource(R.string.developer_options_title),
        onBack = onBack,
    ) {
        SettingsSectionTitle(stringResource(R.string.developer_options_security_section))
        Spacer(Modifier.height(8.dp))
        SettingsSwitch(
            title = stringResource(R.string.settings_http_password_autofill_title),
            subtitle = stringResource(
                if (isHttpPasswordAutofillSupported) {
                    R.string.settings_http_password_autofill_gecko_summary
                } else {
                    R.string.settings_http_password_autofill_system_webview_summary
                },
            ),
            checked = isHttpPasswordAutofillSupported && isHttpPasswordAutofillEnabled,
            enabled = isHttpPasswordAutofillSupported,
            onCheckedChange = { enabled ->
                if (enabled) httpAutofillConfirmationVisible = true
                else onHttpPasswordAutofillEnabledChanged(false)
            },
            modifier = Modifier.testTag(DeveloperOptionsTestTags.HttpPasswordAutofill),
        )
        Text(
            text = stringResource(R.string.settings_http_password_autofill_warning),
            modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 18.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle(stringResource(R.string.developer_options_diagnostics_section))
        Spacer(Modifier.height(8.dp))
        SettingsSwitch(
            title = stringResource(R.string.developer_options_app_logging),
            subtitle = stringResource(R.string.developer_options_app_logging_summary),
            checked = settings.appLoggingEnabled,
            onCheckedChange = { enabled ->
                onSettingsChanged(settings.copy(appLoggingEnabled = enabled))
            },
            modifier = Modifier.testTag(DeveloperOptionsTestTags.AppLogging),
        )
        SettingsPageSpacer()
        DeveloperAction(
            title = stringResource(R.string.developer_options_export_logs),
            summary = stringResource(R.string.developer_options_export_logs_summary),
            onClick = onExportLogs,
            modifier = Modifier.testTag(DeveloperOptionsTestTags.ExportLogs),
        )
        SettingsPageSpacer()
        DeveloperAction(
            title = stringResource(R.string.developer_options_clear_logs),
            summary = stringResource(R.string.developer_options_clear_logs_summary),
            onClick = onClearLogs,
            modifier = Modifier.testTag(DeveloperOptionsTestTags.ClearLogs),
        )
        SettingsPageSpacer()
        SettingsSwitch(
            title = stringResource(R.string.developer_options_gecko_logging),
            subtitle = stringResource(
                if (isGeckoLoggingSupported) {
                    R.string.developer_options_gecko_logging_summary
                } else {
                    R.string.developer_options_gecko_logging_unsupported
                },
            ),
            checked = isGeckoLoggingSupported && settings.geckoLoggingEnabled,
            enabled = isGeckoLoggingSupported,
            onCheckedChange = { enabled ->
                onSettingsChanged(settings.copy(geckoLoggingEnabled = enabled))
            },
            modifier = Modifier.testTag(DeveloperOptionsTestTags.GeckoLogging),
        )
        if (isGeckoLoggingSupported) {
            Text(
                text = stringResource(geckoLoggingStatus.labelResource()),
                modifier = Modifier
                    .padding(start = 18.dp, top = 8.dp, end = 18.dp)
                    .testTag(DeveloperOptionsTestTags.GeckoLoggingStatus),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.developer_options_gecko_logging_warning),
            modifier = Modifier
                .padding(start = 18.dp, top = 8.dp, end = 18.dp)
                .testTag(DeveloperOptionsTestTags.GeckoLoggingWarning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        SettingsPageSpacer()
        val validGeckoModules = GeckoLoggingRules.isValidModules(geckoModulesDraft)
        OutlinedTextField(
            value = geckoModulesDraft,
            onValueChange = { value ->
                geckoModulesDraft = value.take(GeckoLoggingRules.MAX_MODULES_LENGTH)
            },
            enabled = isGeckoLoggingSupported,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DeveloperOptionsTestTags.GeckoLoggingModules)
                .settingsSearchTarget(stringResource(R.string.developer_options_gecko_logging_modules)),
            label = { Text(stringResource(R.string.developer_options_gecko_logging_modules)) },
            supportingText = {
                Text(
                    stringResource(
                        if (validGeckoModules) {
                            R.string.developer_options_gecko_logging_modules_summary
                        } else {
                            R.string.developer_options_gecko_logging_modules_invalid
                        },
                    ),
                )
            },
            isError = !validGeckoModules,
            singleLine = true,
        )
        Button(
            onClick = {
                onSettingsChanged(
                    settings.copy(
                        geckoLoggingModules = GeckoLoggingRules.normalizedModules(geckoModulesDraft),
                    ),
                )
            },
            enabled = isGeckoLoggingSupported && validGeckoModules &&
                GeckoLoggingRules.normalizedModules(geckoModulesDraft) != settings.geckoLoggingModules,
            modifier = Modifier.testTag(DeveloperOptionsTestTags.ApplyGeckoLoggingModules),
        ) {
            Text(stringResource(R.string.developer_options_gecko_logging_modules_apply))
        }
        SettingsPageSpacer()
        DeveloperAction(
            title = stringResource(R.string.developer_options_export_gecko_logs),
            summary = stringResource(R.string.developer_options_export_gecko_logs_summary),
            onClick = onExportGeckoLogs,
            modifier = Modifier.testTag(DeveloperOptionsTestTags.ExportGeckoLogs),
        )
        SettingsPageSpacer()
        DeveloperAction(
            title = stringResource(R.string.developer_options_clear_gecko_logs),
            summary = stringResource(R.string.developer_options_clear_gecko_logs_summary),
            onClick = onClearGeckoLogs,
            modifier = Modifier.testTag(DeveloperOptionsTestTags.ClearGeckoLogs),
        )
        SettingsPageSpacer()
        SettingsSwitch(
            title = stringResource(R.string.developer_options_input_diagnostics),
            subtitle = stringResource(R.string.developer_options_input_diagnostics_summary),
            checked = isInputDiagnosticsEnabled,
            onCheckedChange = onInputDiagnosticsEnabledChanged,
            modifier = Modifier.testTag(DeveloperOptionsTestTags.InputDiagnostics),
        )
        SettingsPageSpacer()
        DeveloperAction(
            title = stringResource(R.string.developer_options_copy_diagnostics),
            summary = stringResource(R.string.developer_options_copy_diagnostics_summary),
            onClick = onCopyDiagnostics,
            modifier = Modifier.testTag(DeveloperOptionsTestTags.CopyDiagnostics),
        )
        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle(stringResource(R.string.developer_options_presentations_section))
        Spacer(Modifier.height(8.dp))
        DeveloperAction(
            title = stringResource(R.string.developer_options_show_onboarding),
            summary = stringResource(R.string.developer_options_show_onboarding_summary),
            onClick = onShowOnboarding,
            modifier = Modifier.testTag(DeveloperOptionsTestTags.ShowOnboarding),
        )
        SettingsPageSpacer()
        DeveloperAction(
            title = stringResource(R.string.developer_options_show_release_notes),
            summary = stringResource(R.string.developer_options_show_release_notes_summary),
            onClick = onShowReleaseNotes,
            modifier = Modifier.testTag(DeveloperOptionsTestTags.ShowReleaseNotes),
        )
        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle(stringResource(R.string.developer_options_experiments_section))
        Spacer(Modifier.height(8.dp))
        SettingsSwitch(
            title = stringResource(R.string.developer_options_force_safe_area_fallback),
            subtitle = stringResource(
                R.string.developer_options_force_safe_area_fallback_summary,
            ),
            checked = settings.forceSafeAreaFallback,
            onCheckedChange = { enabled ->
                onSettingsChanged(settings.copy(forceSafeAreaFallback = enabled))
            },
            modifier = Modifier.testTag(DeveloperOptionsTestTags.ForceSafeAreaFallback),
        )
        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle(stringResource(R.string.developer_options_performance_section))
        Spacer(Modifier.height(8.dp))
        Box {
            SettingsChoice(
                title = stringResource(R.string.developer_options_scroll_dispatch_mode),
                value = settings.browserChromeScrollDispatchMode.displayName(),
                expanded = scrollDispatchMenuExpanded,
                onClick = { scrollDispatchMenuExpanded = true },
                modifier = Modifier.testTag(
                    DeveloperOptionsTestTags.BrowserChromeScrollDispatchMode,
                ),
            )
            SettingsDropdown(
                expanded = scrollDispatchMenuExpanded,
                onDismissRequest = { scrollDispatchMenuExpanded = false },
            ) {
                BrowserChromeScrollDispatchMode.entries.forEach { mode ->
                    SettingsDropdownItem(
                        label = mode.displayName(),
                        selected = mode == settings.browserChromeScrollDispatchMode,
                        onClick = {
                            scrollDispatchMenuExpanded = false
                            onSettingsChanged(
                                settings.copy(browserChromeScrollDispatchMode = mode),
                            )
                        },
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.developer_options_scroll_dispatch_mode_summary),
            modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 18.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        BrowserMemorySettingsSection(
            settings = settings.browserMemorySettings,
            onSettingsChanged = { memorySettings ->
                onSettingsChanged(settings.copy(browserMemorySettings = memorySettings.normalized()))
            },
        )
        Spacer(Modifier.height(18.dp))
        SettingsSectionTitle(stringResource(R.string.developer_options_safe_area_section))
        Text(
            stringResource(R.string.developer_options_safe_area_summary),
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DeveloperSettingsSlider(
            title = stringResource(R.string.developer_options_layout_quiet_period),
            summary = stringResource(
                R.string.developer_options_layout_quiet_period_summary,
            ),
            valueLabel = stringResource(
                R.string.developer_options_milliseconds_value,
                settings.safeAreaLayoutQuietPeriodMillis,
            ),
            value = settings.safeAreaLayoutQuietPeriodMillis,
            range = DeveloperSettings.MIN_SAFE_AREA_LAYOUT_QUIET_PERIOD_MILLIS..
                DeveloperSettings.MAX_SAFE_AREA_LAYOUT_QUIET_PERIOD_MILLIS,
            step = DeveloperSettings.SAFE_AREA_LAYOUT_QUIET_PERIOD_STEP_MILLIS,
            testTag = DeveloperOptionsTestTags.LayoutQuietPeriod,
            onValueChanged = { value ->
                onSettingsChanged(
                    settings.copy(safeAreaLayoutQuietPeriodMillis = value),
                )
            },
        )
        SettingsPageSpacer()
        DeveloperSettingsSlider(
            title = stringResource(R.string.developer_options_required_failures),
            summary = stringResource(R.string.developer_options_required_failures_summary),
            valueLabel = pluralStringResource(
                R.plurals.developer_options_failed_checks_value,
                settings.safeAreaRequiredFailureCount,
                settings.safeAreaRequiredFailureCount,
            ),
            value = settings.safeAreaRequiredFailureCount,
            range = DeveloperSettings.MIN_SAFE_AREA_REQUIRED_FAILURE_COUNT..
                DeveloperSettings.MAX_SAFE_AREA_REQUIRED_FAILURE_COUNT,
            step = 1,
            testTag = DeveloperOptionsTestTags.RequiredFailures,
            onValueChanged = { value ->
                onSettingsChanged(
                    settings.copy(safeAreaRequiredFailureCount = value),
                )
            },
        )
        TextButton(
            onClick = { onSettingsChanged(settings.withDefaultSafeAreaSettings()) },
            enabled = !settings.hasDefaultSafeAreaSettings,
            modifier = Modifier
                .align(Alignment.End)
                .testTag(DeveloperOptionsTestTags.Reset),
        ) {
            Text(stringResource(R.string.developer_options_reset_safe_area))
        }
        Spacer(Modifier.height(18.dp))
        GeckoSafeAreaSettingsSection(
            settings = settings.geckoSafeAreaSettings,
            onSettingsChanged = { geckoSettings ->
                onSettingsChanged(settings.copy(geckoSafeAreaSettings = geckoSettings.normalized()))
            },
        )
    }
    if (httpAutofillConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { httpAutofillConfirmationVisible = false },
            title = { Text(stringResource(R.string.developer_options_http_warning_title)) },
            text = { Text(stringResource(R.string.developer_options_http_warning_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        httpAutofillConfirmationVisible = false
                        onHttpPasswordAutofillEnabledChanged(true)
                    },
                ) {
                    Text(stringResource(R.string.developer_options_http_warning_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { httpAutofillConfirmationVisible = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private fun GeckoLoggingStatus.labelResource(): Int = when (this) {
    GeckoLoggingStatus.Disabled -> R.string.developer_options_gecko_logging_status_disabled
    GeckoLoggingStatus.Starting -> R.string.developer_options_gecko_logging_status_starting
    GeckoLoggingStatus.Recording -> R.string.developer_options_gecko_logging_status_recording
    GeckoLoggingStatus.PausedForPrivateBrowsing ->
        R.string.developer_options_gecko_logging_status_private
    GeckoLoggingStatus.PausedForExport -> R.string.developer_options_gecko_logging_status_export
    GeckoLoggingStatus.LimitReached -> R.string.developer_options_gecko_logging_status_limit
    GeckoLoggingStatus.Failed -> R.string.developer_options_gecko_logging_status_failed
}

@Composable
private fun GeckoSafeAreaSettingsSection(
    settings: GeckoSafeAreaSettings,
    onSettingsChanged: (GeckoSafeAreaSettings) -> Unit,
) {
    Column {
        SettingsSectionTitle(stringResource(R.string.developer_options_gecko_safe_area_section))
        Text(
            text = stringResource(R.string.developer_options_gecko_safe_area_summary),
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsSwitch(
            title = stringResource(R.string.developer_options_gecko_safe_area_enabled),
            subtitle = stringResource(R.string.developer_options_gecko_safe_area_enabled_summary),
            checked = settings.enabled,
            onCheckedChange = { onSettingsChanged(settings.copy(enabled = it)) },
            modifier = Modifier.testTag(DeveloperOptionsTestTags.GeckoSafeAreaEnabled),
        )
        SettingsPageSpacer()
        SettingsSwitch(
            title = stringResource(R.string.developer_options_gecko_add_inset_to_negative_top),
            subtitle = stringResource(
                R.string.developer_options_gecko_add_inset_to_negative_top_summary,
            ),
            checked = settings.addInsetToNegativeTop,
            enabled = settings.enabled,
            onCheckedChange = { onSettingsChanged(settings.copy(addInsetToNegativeTop = it)) },
            modifier = Modifier.testTag(DeveloperOptionsTestTags.GeckoAddInsetToNegativeTop),
        )
        SettingsPageSpacer()
        SettingsSwitch(
            title = stringResource(R.string.developer_options_gecko_recheck_added_elements),
            subtitle = stringResource(R.string.developer_options_gecko_recheck_added_elements_summary),
            checked = settings.recheckAddedElements,
            enabled = settings.enabled,
            onCheckedChange = { onSettingsChanged(settings.copy(recheckAddedElements = it)) },
            modifier = Modifier.testTag(DeveloperOptionsTestTags.GeckoRecheckAddedElements),
        )
        SettingsPageSpacer()
        SettingsSwitch(
            title = stringResource(R.string.developer_options_gecko_recheck_changed_elements),
            subtitle = stringResource(R.string.developer_options_gecko_recheck_changed_elements_summary),
            checked = settings.recheckChangedElements,
            enabled = settings.enabled,
            onCheckedChange = { onSettingsChanged(settings.copy(recheckChangedElements = it)) },
            modifier = Modifier.testTag(DeveloperOptionsTestTags.GeckoRecheckChangedElements),
        )
        SettingsPageSpacer()
        SettingsSwitch(
            title = stringResource(R.string.developer_options_gecko_require_interaction),
            subtitle = stringResource(R.string.developer_options_gecko_require_interaction_summary),
            checked = settings.requireInteractionForUpdates,
            enabled = settings.enabled,
            onCheckedChange = { onSettingsChanged(settings.copy(requireInteractionForUpdates = it)) },
            modifier = Modifier.testTag(DeveloperOptionsTestTags.GeckoRequireInteraction),
        )
        SettingsPageSpacer()
        SettingsSwitch(
            title = stringResource(R.string.developer_options_gecko_recheck_on_resize),
            subtitle = stringResource(R.string.developer_options_gecko_recheck_on_resize_summary),
            checked = settings.recheckOnResize,
            enabled = settings.enabled,
            onCheckedChange = { onSettingsChanged(settings.copy(recheckOnResize = it)) },
            modifier = Modifier.testTag(DeveloperOptionsTestTags.GeckoRecheckOnResize),
        )
        SettingsPageSpacer()
        DeveloperSettingsSlider(
            title = stringResource(R.string.developer_options_gecko_interaction_window),
            summary = stringResource(R.string.developer_options_gecko_interaction_window_summary),
            valueLabel = stringResource(
                R.string.developer_options_milliseconds_value,
                settings.interactionWindowMillis,
            ),
            value = settings.interactionWindowMillis,
            range = GeckoSafeAreaSettings.MIN_INTERACTION_WINDOW_MILLIS..
                GeckoSafeAreaSettings.MAX_INTERACTION_WINDOW_MILLIS,
            step = GeckoSafeAreaSettings.INTERACTION_WINDOW_STEP_MILLIS,
            testTag = DeveloperOptionsTestTags.GeckoInteractionWindow,
            enabled = settings.enabled,
            onValueChanged = { onSettingsChanged(settings.copy(interactionWindowMillis = it)) },
        )
        SettingsPageSpacer()
        DeveloperSettingsSlider(
            title = stringResource(R.string.developer_options_gecko_mutation_debounce),
            summary = stringResource(R.string.developer_options_gecko_mutation_debounce_summary),
            valueLabel = stringResource(
                R.string.developer_options_milliseconds_value,
                settings.mutationDebounceMillis,
            ),
            value = settings.mutationDebounceMillis,
            range = GeckoSafeAreaSettings.MIN_MUTATION_DEBOUNCE_MILLIS..
                GeckoSafeAreaSettings.MAX_MUTATION_DEBOUNCE_MILLIS,
            step = GeckoSafeAreaSettings.MUTATION_DEBOUNCE_STEP_MILLIS,
            testTag = DeveloperOptionsTestTags.GeckoMutationDebounce,
            enabled = settings.enabled,
            onValueChanged = { onSettingsChanged(settings.copy(mutationDebounceMillis = it)) },
        )
        SettingsPageSpacer()
        DeveloperSettingsSlider(
            title = stringResource(R.string.developer_options_gecko_max_elements_per_batch),
            summary = stringResource(R.string.developer_options_gecko_max_elements_per_batch_summary),
            valueLabel = pluralStringResource(
                R.plurals.developer_options_gecko_elements_value,
                settings.maxElementsPerBatch,
                settings.maxElementsPerBatch,
            ),
            value = settings.maxElementsPerBatch,
            range = GeckoSafeAreaSettings.MIN_MAX_ELEMENTS_PER_BATCH..
                GeckoSafeAreaSettings.MAX_MAX_ELEMENTS_PER_BATCH,
            step = GeckoSafeAreaSettings.MAX_ELEMENTS_PER_BATCH_STEP,
            testTag = DeveloperOptionsTestTags.GeckoMaxElementsPerBatch,
            enabled = settings.enabled,
            onValueChanged = { onSettingsChanged(settings.copy(maxElementsPerBatch = it)) },
        )
        SettingsPageSpacer()
        DeveloperSettingsSlider(
            title = stringResource(R.string.developer_options_gecko_max_batch_duration),
            summary = stringResource(R.string.developer_options_gecko_max_batch_duration_summary),
            valueLabel = stringResource(
                R.string.developer_options_milliseconds_value,
                settings.maxBatchDurationMillis,
            ),
            value = settings.maxBatchDurationMillis,
            range = GeckoSafeAreaSettings.MIN_MAX_BATCH_DURATION_MILLIS..
                GeckoSafeAreaSettings.MAX_MAX_BATCH_DURATION_MILLIS,
            step = GeckoSafeAreaSettings.MAX_BATCH_DURATION_STEP_MILLIS,
            testTag = DeveloperOptionsTestTags.GeckoMaxBatchDuration,
            enabled = settings.enabled,
            onValueChanged = { onSettingsChanged(settings.copy(maxBatchDurationMillis = it)) },
        )
        SettingsPageSpacer()
        DeveloperSettingsSlider(
            title = stringResource(R.string.developer_options_gecko_max_initial_elements),
            summary = stringResource(R.string.developer_options_gecko_max_initial_elements_summary),
            valueLabel = pluralStringResource(
                R.plurals.developer_options_gecko_elements_value,
                settings.maxInitialElements,
                settings.maxInitialElements,
            ),
            value = settings.maxInitialElements,
            range = GeckoSafeAreaSettings.MIN_MAX_INITIAL_ELEMENTS..
                GeckoSafeAreaSettings.MAX_MAX_INITIAL_ELEMENTS,
            step = GeckoSafeAreaSettings.MAX_INITIAL_ELEMENTS_STEP,
            testTag = DeveloperOptionsTestTags.GeckoMaxInitialElements,
            enabled = settings.enabled,
            onValueChanged = { onSettingsChanged(settings.copy(maxInitialElements = it)) },
        )
        TextButton(
            onClick = { onSettingsChanged(settings.withDefaults()) },
            enabled = !settings.hasDefaultSettings,
            modifier = Modifier.align(Alignment.End).testTag(DeveloperOptionsTestTags.GeckoReset),
        ) {
            Text(stringResource(R.string.developer_options_gecko_reset))
        }
    }
}

@Composable
private fun BrowserChromeScrollDispatchMode.displayName(): String = stringResource(
    when (this) {
        BrowserChromeScrollDispatchMode.Optimized ->
            R.string.developer_options_scroll_dispatch_optimized
        BrowserChromeScrollDispatchMode.Fixed120Hz ->
            R.string.developer_options_scroll_dispatch_120_hz
        BrowserChromeScrollDispatchMode.Fixed60Hz ->
            R.string.developer_options_scroll_dispatch_60_hz
        BrowserChromeScrollDispatchMode.Fixed30Hz ->
            R.string.developer_options_scroll_dispatch_30_hz
        BrowserChromeScrollDispatchMode.Fixed15Hz ->
            R.string.developer_options_scroll_dispatch_15_hz
    },
)

private val DeveloperSettings.hasDefaultSafeAreaSettings: Boolean
    get() =
        safeAreaLayoutQuietPeriodMillis ==
        DeveloperSettings.DEFAULT_SAFE_AREA_LAYOUT_QUIET_PERIOD_MILLIS &&
            safeAreaRequiredFailureCount ==
            DeveloperSettings.DEFAULT_SAFE_AREA_REQUIRED_FAILURE_COUNT &&
            !forceSafeAreaFallback

private fun DeveloperSettings.withDefaultSafeAreaSettings(): DeveloperSettings = copy(
    safeAreaLayoutQuietPeriodMillis =
        DeveloperSettings.DEFAULT_SAFE_AREA_LAYOUT_QUIET_PERIOD_MILLIS,
    safeAreaRequiredFailureCount = DeveloperSettings.DEFAULT_SAFE_AREA_REQUIRED_FAILURE_COUNT,
    forceSafeAreaFallback = false,
)

@Composable
private fun DeveloperAction(
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().settingsSearchTarget(title),
        shape = MaterialTheme.shapes.large,
        color = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BrowserMemorySettingsSection(
    settings: BrowserMemorySettings,
    onSettingsChanged: (BrowserMemorySettings) -> Unit,
) {
    SettingsSectionTitle(stringResource(R.string.developer_options_memory_section))
    Spacer(Modifier.height(8.dp))
    DeveloperSettingsSlider(
        title = stringResource(R.string.developer_options_foreground_tab_idle_timeout),
        summary = stringResource(R.string.developer_options_foreground_tab_idle_timeout_summary),
        valueLabel = stringResource(
            R.string.developer_options_minutes_value,
            settings.foregroundTabIdleTimeoutMinutes,
        ),
        value = settings.foregroundTabIdleTimeoutMinutes,
        range = BrowserMemorySettings.MIN_FOREGROUND_TAB_IDLE_TIMEOUT_MINUTES..
            BrowserMemorySettings.MAX_FOREGROUND_TAB_IDLE_TIMEOUT_MINUTES,
        step = 1,
        testTag = DeveloperOptionsTestTags.ForegroundTabIdleTimeout,
        onValueChanged = { value ->
            onSettingsChanged(settings.copy(foregroundTabIdleTimeoutMinutes = value))
        },
    )
    SettingsPageSpacer()
    DeveloperSettingsSlider(
        title = stringResource(R.string.developer_options_background_warm_tab_count),
        summary = stringResource(R.string.developer_options_background_warm_tab_count_summary),
        valueLabel = stringResource(
            R.string.developer_options_tabs_value,
            settings.backgroundWarmTabCount,
        ),
        value = settings.backgroundWarmTabCount,
        range = BrowserMemorySettings.MIN_BACKGROUND_WARM_TAB_COUNT..
            BrowserMemorySettings.MAX_BACKGROUND_WARM_TAB_COUNT,
        step = 1,
        testTag = DeveloperOptionsTestTags.BackgroundWarmTabCount,
        onValueChanged = { value ->
            onSettingsChanged(settings.copy(backgroundWarmTabCount = value))
        },
    )
    SettingsPageSpacer()
    DeveloperSettingsSlider(
        title = stringResource(R.string.developer_options_history_cache_lifetime),
        summary = stringResource(R.string.developer_options_history_cache_lifetime_summary),
        valueLabel = stringResource(
            R.string.developer_options_minutes_value,
            settings.historyCacheLifetimeMinutes,
        ),
        value = settings.historyCacheLifetimeMinutes,
        range = BrowserMemorySettings.MIN_HISTORY_CACHE_LIFETIME_MINUTES..
            BrowserMemorySettings.MAX_HISTORY_CACHE_LIFETIME_MINUTES,
        step = 1,
        testTag = DeveloperOptionsTestTags.HistoryCacheLifetime,
        onValueChanged = { value ->
            onSettingsChanged(settings.copy(historyCacheLifetimeMinutes = value))
        },
    )
}

@Composable
private fun DeveloperSettingsSlider(
    title: String,
    summary: String,
    valueLabel: String,
    value: Int,
    range: IntRange,
    step: Int,
    testTag: String,
    enabled: Boolean = true,
    onValueChanged: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.settingsSearchTarget(title).fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    valueLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                enabled = enabled,
                value = value.toFloat(),
                onValueChange = { candidate ->
                    val snapped = range.first +
                        ((candidate - range.first) / step).roundToInt() * step
                    onValueChanged(snapped.coerceIn(range))
                },
                modifier = Modifier.testTag(testTag),
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = ((range.last - range.first) / step - 1).coerceAtLeast(0),
            )
        }
    }
}
