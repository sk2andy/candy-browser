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
import dev.sk2andy.materialbrowser.data.BrowserChromeScrollDispatchMode
import dev.sk2andy.materialbrowser.data.DeveloperSettings
import dev.sk2andy.materialbrowser.data.GeckoSafeAreaSettings
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor
import kotlin.math.roundToInt

internal object DeveloperOptionsTestTags {
    const val BrowserChromeScrollDispatchMode = "developer_options_scroll_dispatch_mode"
    const val LayoutQuietPeriod = "developer_options_layout_quiet_period"
    const val RequiredFailures = "developer_options_required_failures"
    const val ForceSafeAreaFallback = "developer_options_force_safe_area_fallback"
    const val HttpPasswordAutofill = "developer_options_http_password_autofill"
    const val InputDiagnostics = "developer_options_input_diagnostics"
    const val CopyDiagnostics = "developer_options_copy_diagnostics"
    const val ShowOnboarding = "developer_options_show_onboarding"
    const val ShowReleaseNotes = "developer_options_show_release_notes"
    const val Reset = "developer_options_reset"
    const val GeckoSafeAreaEnabled = "developer_options_gecko_safe_area_enabled"
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
    onSettingsChanged: (DeveloperSettings) -> Unit,
    onHttpPasswordAutofillEnabledChanged: (Boolean) -> Unit = {},
    onInputDiagnosticsEnabledChanged: (Boolean) -> Unit = {},
    onCopyDiagnostics: () -> Unit = {},
    onShowOnboarding: () -> Unit = {},
    onShowReleaseNotes: () -> Unit = {},
    onBack: () -> Unit,
) {
    var httpAutofillConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    var scrollDispatchMenuExpanded by remember { mutableStateOf(false) }
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
        modifier = modifier.fillMaxWidth(),
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
        modifier = Modifier.fillMaxWidth(),
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
