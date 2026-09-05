package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.data.BrowserColorPalette
import dev.sk2andy.materialbrowser.data.BrowserShapeStyle
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor
import kotlin.math.roundToInt

internal object AppearanceSettingsTestTags {
    const val AppearanceMode = "appearance_settings_mode"
    const val ForceDarkWebsites = "appearance_settings_force_dark_websites"
    const val ColorPalette = "appearance_settings_palette"
    const val SurfaceStyle = "appearance_settings_surface"
    const val ShapeStyle = "appearance_settings_shape"
    const val FrostedTransparency = "appearance_settings_frosted_transparency"
    const val FrostedAddressBarTransparency =
        "appearance_settings_frosted_address_bar_transparency"
    const val FrostedBlur = "appearance_settings_frosted_blur"
}

@Composable
internal fun AppearanceSettingsPage(
    settings: AppearanceSettings,
    onSettingsChanged: (AppearanceSettings) -> Unit,
    onBack: () -> Unit,
) {
    var appearanceMenuExpanded by remember { mutableStateOf(false) }
    var paletteMenuExpanded by remember { mutableStateOf(false) }
    var surfaceMenuExpanded by remember { mutableStateOf(false) }
    var shapeMenuExpanded by remember { mutableStateOf(false) }

    SettingsPage(
        title = stringResource(R.string.settings_appearance_title),
        onBack = onBack,
    ) {
        Box {
            SettingsChoice(
                title = stringResource(R.string.settings_appearance_mode),
                value = settings.appearanceMode.displayName(),
                expanded = appearanceMenuExpanded,
                onClick = { appearanceMenuExpanded = true },
                modifier = Modifier.testTag(AppearanceSettingsTestTags.AppearanceMode),
            )
            SettingsDropdown(
                expanded = appearanceMenuExpanded,
                onDismissRequest = { appearanceMenuExpanded = false },
            ) {
                BrowserAppearanceMode.entries.forEach { mode ->
                    SettingsDropdownItem(
                        label = mode.displayName(),
                        selected = mode == settings.appearanceMode,
                        onClick = {
                            appearanceMenuExpanded = false
                            onSettingsChanged(settings.copy(appearanceMode = mode))
                        },
                    )
                }
            }
        }
        SettingsPageSpacer()
        SettingsSwitch(
            title = stringResource(R.string.settings_force_dark_websites),
            subtitle = stringResource(R.string.settings_force_dark_websites_summary),
            checked = settings.forceDarkWebsites,
            onCheckedChange = { enabled ->
                onSettingsChanged(settings.copy(forceDarkWebsites = enabled))
            },
            modifier = Modifier.testTag(AppearanceSettingsTestTags.ForceDarkWebsites),
        )
        SettingsPageSpacer()
        Box {
            SettingsChoice(
                title = stringResource(R.string.settings_color_palette),
                value = settings.colorPalette.displayName(),
                expanded = paletteMenuExpanded,
                onClick = { paletteMenuExpanded = true },
                modifier = Modifier.testTag(AppearanceSettingsTestTags.ColorPalette),
            )
            SettingsDropdown(
                expanded = paletteMenuExpanded,
                onDismissRequest = { paletteMenuExpanded = false },
            ) {
                BrowserColorPalette.entries.forEach { palette ->
                    SettingsDropdownItem(
                        label = palette.displayName(),
                        selected = palette == settings.colorPalette,
                        onClick = {
                            paletteMenuExpanded = false
                            onSettingsChanged(settings.copy(colorPalette = palette))
                        },
                    )
                }
            }
        }
        SettingsPageSpacer()
        Box {
            SettingsChoice(
                title = stringResource(R.string.settings_surface_style),
                value = settings.surfaceStyle.displayName(),
                expanded = surfaceMenuExpanded,
                onClick = { surfaceMenuExpanded = true },
                modifier = Modifier.testTag(AppearanceSettingsTestTags.SurfaceStyle),
            )
            SettingsDropdown(
                expanded = surfaceMenuExpanded,
                onDismissRequest = { surfaceMenuExpanded = false },
            ) {
                BrowserSurfaceStyle.entries.forEach { style ->
                    SettingsDropdownItem(
                        label = style.displayName(),
                        selected = style == settings.surfaceStyle,
                        onClick = {
                            surfaceMenuExpanded = false
                            onSettingsChanged(settings.copy(surfaceStyle = style))
                        },
                    )
                }
            }
        }
        Text(
            stringResource(R.string.settings_surface_style_summary),
            modifier = Modifier.padding(start = 18.dp, top = 6.dp, end = 18.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (settings.surfaceStyle == BrowserSurfaceStyle.Frosted) {
            SettingsPageSpacer()
            AppearanceSlider(
                title = stringResource(R.string.settings_frosted_transparency),
                value = settings.frostedTransparencyPercent.toFloat(),
                valueRange = AppearanceSettings.MIN_FROSTED_TRANSPARENCY_PERCENT.toFloat()..
                    AppearanceSettings.MAX_FROSTED_TRANSPARENCY_PERCENT.toFloat(),
                steps = 7,
                onValueChange = { value ->
                    onSettingsChanged(
                        settings.copy(
                            frostedTransparencyPercent = value.roundToInt(),
                        ),
                    )
                },
                testTag = AppearanceSettingsTestTags.FrostedTransparency,
            )
            SettingsPageSpacer()
            AppearanceSlider(
                title = stringResource(R.string.settings_frosted_address_bar_transparency),
                value = settings.frostedAddressBarTransparencyPercent.toFloat(),
                valueRange = AppearanceSettings.MIN_FROSTED_TRANSPARENCY_PERCENT.toFloat()..
                    AppearanceSettings.MAX_FROSTED_TRANSPARENCY_PERCENT.toFloat(),
                steps = 7,
                onValueChange = { value ->
                    onSettingsChanged(
                        settings.copy(
                            frostedAddressBarTransparencyPercent = value.roundToInt(),
                        ),
                    )
                },
                testTag = AppearanceSettingsTestTags.FrostedAddressBarTransparency,
            )
            SettingsPageSpacer()
            AppearanceSlider(
                title = stringResource(R.string.settings_frosted_blur),
                value = settings.frostedBlurPercent.toFloat(),
                valueRange = AppearanceSettings.MIN_FROSTED_BLUR_PERCENT.toFloat()..
                    AppearanceSettings.MAX_FROSTED_BLUR_PERCENT.toFloat(),
                steps = 9,
                onValueChange = { value ->
                    onSettingsChanged(
                        settings.copy(frostedBlurPercent = value.roundToInt()),
                    )
                },
                testTag = AppearanceSettingsTestTags.FrostedBlur,
            )
        }
        SettingsPageSpacer()
        Box {
            SettingsChoice(
                title = stringResource(R.string.settings_shape_style),
                value = settings.shapeStyle.displayName(),
                expanded = shapeMenuExpanded,
                onClick = { shapeMenuExpanded = true },
                modifier = Modifier.testTag(AppearanceSettingsTestTags.ShapeStyle),
            )
            SettingsDropdown(
                expanded = shapeMenuExpanded,
                onDismissRequest = { shapeMenuExpanded = false },
            ) {
                BrowserShapeStyle.entries.forEach { style ->
                    SettingsDropdownItem(
                        label = style.displayName(),
                        selected = style == settings.shapeStyle,
                        onClick = {
                            shapeMenuExpanded = false
                            onSettingsChanged(settings.copy(shapeStyle = style))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearanceSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    testTag: String,
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
                    "${value.roundToInt()} %",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.testTag(testTag),
                valueRange = valueRange,
                steps = steps,
            )
        }
    }
}
