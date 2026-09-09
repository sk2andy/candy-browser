package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    const val Reset = "developer_options_reset"
}

@Composable
internal fun DeveloperOptionsSettingsPage(
    settings: DeveloperSettings,
    onSettingsChanged: (DeveloperSettings) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPage(
        title = stringResource(R.string.developer_options_title),
        onBack = onBack,
    ) {
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
            Text(stringResource(R.string.developer_options_reset))
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
