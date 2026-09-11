package dev.sk2andy.materialbrowser.ui

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.DeveloperSettings
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor
import kotlin.math.roundToInt

internal object DeveloperOptionsTestTags {
    const val LayoutQuietPeriod = "developer_options_layout_quiet_period"
    const val RequiredFailures = "developer_options_required_failures"
    const val ForceSafeAreaFallback = "developer_options_force_safe_area_fallback"
    const val HttpPasswordAutofill = "developer_options_http_password_autofill"
    const val InputDiagnostics = "developer_options_input_diagnostics"
    const val CopyDiagnostics = "developer_options_copy_diagnostics"
    const val ShowOnboarding = "developer_options_show_onboarding"
    const val ShowReleaseNotes = "developer_options_show_release_notes"
    const val Reset = "developer_options_reset"
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
            onClick = { onSettingsChanged(DeveloperSettings()) },
            enabled = settings != DeveloperSettings(),
            modifier = Modifier
                .align(Alignment.End)
                .testTag(DeveloperOptionsTestTags.Reset),
        ) {
            Text(stringResource(R.string.developer_options_reset_safe_area))
        }
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
