package dev.sk2andy.materialbrowser.shared.ui.settings

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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.data.BrowserColorPalette
import dev.sk2andy.materialbrowser.data.BrowserShapeStyle
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle
import kotlin.math.roundToInt

data class AppearanceSettingsStrings(
    val title: String,
    val back: String,
    val appearanceMode: String,
    val appearanceModeNames: Map<BrowserAppearanceMode, String>,
    val forceDarkWebsites: String,
    val forceDarkWebsitesSummary: String,
    val webContentFontSize: String,
    val colorPalette: String,
    val colorPaletteNames: Map<BrowserColorPalette, String>,
    val surfaceStyle: String,
    val surfaceStyleSummary: String,
    val surfaceStyleNames: Map<BrowserSurfaceStyle, String>,
    val frostedTransparency: String,
    val frostedAddressBarTransparency: String,
    val frostedBlur: String,
    val shapeStyle: String,
    val shapeStyleNames: Map<BrowserShapeStyle, String>,
)

object SharedAppearanceSettingsTestTags {
    const val APPEARANCE_MODE = "appearance_settings_mode"
    const val FORCE_DARK_WEBSITES = "appearance_settings_force_dark_websites"
    const val WEB_CONTENT_FONT_SIZE = "appearance_settings_web_content_font_size"
    const val COLOR_PALETTE = "appearance_settings_palette"
    const val SURFACE_STYLE = "appearance_settings_surface"
    const val SHAPE_STYLE = "appearance_settings_shape"
    const val FROSTED_TRANSPARENCY = "appearance_settings_frosted_transparency"
    const val FROSTED_ADDRESS_BAR_TRANSPARENCY =
        "appearance_settings_frosted_address_bar_transparency"
    const val FROSTED_BLUR = "appearance_settings_frosted_blur"
}

@Composable
fun AppearanceSettingsPage(
    settings: AppearanceSettings,
    strings: AppearanceSettingsStrings,
    containerColor: Color,
    onSettingsChanged: (AppearanceSettings) -> Unit,
    onBack: () -> Unit,
    enabled: Boolean = true,
) {
    var appearanceMenuExpanded by remember { mutableStateOf(false) }
    var paletteMenuExpanded by remember { mutableStateOf(false) }
    var surfaceMenuExpanded by remember { mutableStateOf(false) }
    var shapeMenuExpanded by remember { mutableStateOf(false) }
    var pendingWebContentFontSize by remember(settings.webContentFontSizePercent) {
        mutableFloatStateOf(settings.webContentFontSizePercent.toFloat())
    }

    SettingsPage(
        title = strings.title,
        backContentDescription = strings.back,
        onBack = onBack,
    ) {
        Box {
            SettingsChoice(
                title = strings.appearanceMode,
                value = strings.appearanceModeNames.getValue(settings.appearanceMode),
                expanded = appearanceMenuExpanded,
                onClick = { appearanceMenuExpanded = true },
                containerColor = containerColor,
                modifier = Modifier.testTag(SharedAppearanceSettingsTestTags.APPEARANCE_MODE),
                enabled = enabled,
            )
            SettingsDropdown(
                expanded = enabled && appearanceMenuExpanded,
                onDismissRequest = { appearanceMenuExpanded = false },
            ) {
                BrowserAppearanceMode.entries.forEach { mode ->
                    SettingsDropdownItem(
                        label = strings.appearanceModeNames.getValue(mode),
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
            title = strings.forceDarkWebsites,
            subtitle = strings.forceDarkWebsitesSummary,
            checked = settings.forceDarkWebsites,
            enabled = enabled,
            onCheckedChange = { value ->
                onSettingsChanged(settings.copy(forceDarkWebsites = value))
            },
            modifier = Modifier.testTag(SharedAppearanceSettingsTestTags.FORCE_DARK_WEBSITES),
        )
        SettingsPageSpacer()
        AppearanceSlider(
            title = strings.webContentFontSize,
            value = pendingWebContentFontSize,
            valueRange = AppearanceSettings.MIN_WEB_CONTENT_FONT_SIZE_PERCENT.toFloat()..
                AppearanceSettings.MAX_WEB_CONTENT_FONT_SIZE_PERCENT.toFloat(),
            steps = (
                AppearanceSettings.MAX_WEB_CONTENT_FONT_SIZE_PERCENT -
                    AppearanceSettings.MIN_WEB_CONTENT_FONT_SIZE_PERCENT
                ) / AppearanceSettings.WEB_CONTENT_FONT_SIZE_STEP_PERCENT - 1,
            enabled = enabled,
            containerColor = containerColor,
            onValueChange = { value -> pendingWebContentFontSize = value },
            onValueChangeFinished = {
                val updated = settings.copy(
                    webContentFontSizePercent = pendingWebContentFontSize.roundToInt(),
                ).normalized()
                pendingWebContentFontSize = updated.webContentFontSizePercent.toFloat()
                if (updated != settings) onSettingsChanged(updated)
            },
            testTag = SharedAppearanceSettingsTestTags.WEB_CONTENT_FONT_SIZE,
        )
        SettingsPageSpacer()
        Box {
            SettingsChoice(
                title = strings.colorPalette,
                value = strings.colorPaletteNames.getValue(settings.colorPalette),
                expanded = paletteMenuExpanded,
                onClick = { paletteMenuExpanded = true },
                containerColor = containerColor,
                modifier = Modifier.testTag(SharedAppearanceSettingsTestTags.COLOR_PALETTE),
                enabled = enabled,
            )
            SettingsDropdown(
                expanded = enabled && paletteMenuExpanded,
                onDismissRequest = { paletteMenuExpanded = false },
            ) {
                BrowserColorPalette.entries.forEach { palette ->
                    SettingsDropdownItem(
                        label = strings.colorPaletteNames.getValue(palette),
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
                title = strings.surfaceStyle,
                value = strings.surfaceStyleNames.getValue(settings.surfaceStyle),
                expanded = surfaceMenuExpanded,
                onClick = { surfaceMenuExpanded = true },
                containerColor = containerColor,
                modifier = Modifier.testTag(SharedAppearanceSettingsTestTags.SURFACE_STYLE),
                enabled = enabled,
            )
            SettingsDropdown(
                expanded = enabled && surfaceMenuExpanded,
                onDismissRequest = { surfaceMenuExpanded = false },
            ) {
                BrowserSurfaceStyle.entries.forEach { style ->
                    SettingsDropdownItem(
                        label = strings.surfaceStyleNames.getValue(style),
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
            strings.surfaceStyleSummary,
            modifier = Modifier.padding(start = 18.dp, top = 6.dp, end = 18.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (settings.surfaceStyle == BrowserSurfaceStyle.Frosted) {
            SettingsPageSpacer()
            AppearanceSlider(
                title = strings.frostedTransparency,
                value = settings.frostedTransparencyPercent.toFloat(),
                valueRange = AppearanceSettings.MIN_FROSTED_TRANSPARENCY_PERCENT.toFloat()..
                    AppearanceSettings.MAX_FROSTED_TRANSPARENCY_PERCENT.toFloat(),
                steps = 7,
                enabled = enabled,
                containerColor = containerColor,
                onValueChange = { value ->
                    onSettingsChanged(settings.copy(frostedTransparencyPercent = value.roundToInt()))
                },
                testTag = SharedAppearanceSettingsTestTags.FROSTED_TRANSPARENCY,
            )
            SettingsPageSpacer()
            AppearanceSlider(
                title = strings.frostedAddressBarTransparency,
                value = settings.frostedAddressBarTransparencyPercent.toFloat(),
                valueRange = AppearanceSettings.MIN_FROSTED_TRANSPARENCY_PERCENT.toFloat()..
                    AppearanceSettings.MAX_FROSTED_TRANSPARENCY_PERCENT.toFloat(),
                steps = 7,
                enabled = enabled,
                containerColor = containerColor,
                onValueChange = { value ->
                    onSettingsChanged(
                        settings.copy(frostedAddressBarTransparencyPercent = value.roundToInt()),
                    )
                },
                testTag = SharedAppearanceSettingsTestTags.FROSTED_ADDRESS_BAR_TRANSPARENCY,
            )
            SettingsPageSpacer()
            AppearanceSlider(
                title = strings.frostedBlur,
                value = settings.frostedBlurPercent.toFloat(),
                valueRange = AppearanceSettings.MIN_FROSTED_BLUR_PERCENT.toFloat()..
                    AppearanceSettings.MAX_FROSTED_BLUR_PERCENT.toFloat(),
                steps = 9,
                enabled = enabled,
                containerColor = containerColor,
                onValueChange = { value ->
                    onSettingsChanged(settings.copy(frostedBlurPercent = value.roundToInt()))
                },
                testTag = SharedAppearanceSettingsTestTags.FROSTED_BLUR,
            )
        }
        SettingsPageSpacer()
        Box {
            SettingsChoice(
                title = strings.shapeStyle,
                value = strings.shapeStyleNames.getValue(settings.shapeStyle),
                expanded = shapeMenuExpanded,
                onClick = { shapeMenuExpanded = true },
                containerColor = containerColor,
                modifier = Modifier.testTag(SharedAppearanceSettingsTestTags.SHAPE_STYLE),
                enabled = enabled,
            )
            SettingsDropdown(
                expanded = enabled && shapeMenuExpanded,
                onDismissRequest = { shapeMenuExpanded = false },
            ) {
                BrowserShapeStyle.entries.forEach { style ->
                    SettingsDropdownItem(
                        label = strings.shapeStyleNames.getValue(style),
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
    enabled: Boolean,
    containerColor: Color,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    testTag: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
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
                onValueChangeFinished = onValueChangeFinished,
                enabled = enabled,
                modifier = Modifier.testTag(testTag),
                valueRange = valueRange,
                steps = steps,
            )
        }
    }
}
