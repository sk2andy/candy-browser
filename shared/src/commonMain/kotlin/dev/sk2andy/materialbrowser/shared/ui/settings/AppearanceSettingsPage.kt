package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.data.AddressBarColorRules
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAddressBarColorPreset
import dev.sk2andy.materialbrowser.data.BrowserAddressLoadStyle
import dev.sk2andy.materialbrowser.data.BrowserAddressBarStyle
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.data.BrowserColorPalette
import dev.sk2andy.materialbrowser.data.BrowserShapeStyle
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle
import dev.sk2andy.materialbrowser.shared.ui.LocalCandyBrowserChromeEffects
import kotlin.math.roundToInt

data class AppearanceSettingsStrings(
    val title: String,
    val back: String,
    val appearanceMode: String,
    val appearanceModeNames: Map<BrowserAppearanceMode, String>,
    val animations: String,
    val animationsSummary: String,
    val forceDarkWebsites: String,
    val forceDarkWebsitesSummary: String,
    val webContentFontSize: String,
    val colorPalette: String,
    val colorPaletteNames: Map<BrowserColorPalette, String>,
    val addressBarColor: String,
    val addressBarColorPresetNames: Map<BrowserAddressBarColorPreset, String>,
    val addressBarColorReset: String,
    val customAddressBarColorTitle: String,
    val customAddressBarColorLabel: String,
    val customAddressBarColorInvalid: String,
    val cancel: String,
    val save: String,
    val surfaceStyle: String,
    val surfaceStyleSummary: String,
    val surfaceStyleNames: Map<BrowserSurfaceStyle, String>,
    val frostedTransparency: String,
    val frostedAddressBarTransparency: String,
    val frostedBlur: String,
    val frostedBlurSummary: String,
    val shapeStyle: String,
    val shapeStyleNames: Map<BrowserShapeStyle, String>,
    val addressLoadStyle: String,
    val addressLoadStyleNames: Map<BrowserAddressLoadStyle, String>,
    val addressBarStyle: String,
    val addressBarStyleNames: Map<BrowserAddressBarStyle, String>,
)

object SharedAppearanceSettingsTestTags {
    const val APPEARANCE_MODE = "appearance_settings_mode"
    const val ANIMATIONS = "appearance_settings_animations"
    const val FORCE_DARK_WEBSITES = "appearance_settings_force_dark_websites"
    const val WEB_CONTENT_FONT_SIZE = "appearance_settings_web_content_font_size"
    const val COLOR_PALETTE = "appearance_settings_palette"
    const val ADDRESS_BAR_COLOR = "appearance_settings_address_bar_color"
    const val ADDRESS_BAR_COLOR_RESET = "appearance_settings_address_bar_color_reset"
    const val ADDRESS_BAR_CUSTOM_COLOR = "appearance_settings_address_bar_custom_color"
    const val ADDRESS_BAR_CUSTOM_COLOR_SAVE = "appearance_settings_address_bar_custom_color_save"
    const val SURFACE_STYLE = "appearance_settings_surface"
    const val SHAPE_STYLE = "appearance_settings_shape"
    const val ADDRESS_LOAD_STYLE = "appearance_settings_address_load_style"
    const val ADDRESS_BAR_STYLE = "appearance_settings_address_bar_style"
    const val FROSTED_TRANSPARENCY = "appearance_settings_frosted_transparency"
    const val FROSTED_ADDRESS_BAR_TRANSPARENCY =
        "appearance_settings_frosted_address_bar_transparency"
    const val FROSTED_BLUR = "appearance_settings_frosted_blur"
}

data class AppearanceSettingsCapabilities(
    val appearanceModes: List<BrowserAppearanceMode> = BrowserAppearanceMode.entries,
    val forceDarkWebsites: Boolean = true,
    val webContentFontSize: Boolean = true,
    val customChrome: Boolean = true,
) {
    companion object {
        val Core = AppearanceSettingsCapabilities(
            appearanceModes = listOf(
                BrowserAppearanceMode.System,
                BrowserAppearanceMode.Light,
                BrowserAppearanceMode.Dark,
            ),
            forceDarkWebsites = false,
            customChrome = false,
        )
    }
}

enum class AppearanceSettingsSection { All, Theme, Colors, Surfaces, AddressBar, Websites }

@Composable
fun AppearanceSettingsPage(
    settings: AppearanceSettings,
    strings: AppearanceSettingsStrings,
    containerColor: Color,
    onSettingsChanged: (AppearanceSettings) -> Unit,
    onBack: () -> Unit,
    enabled: Boolean = true,
    forceDarkWebsitesAvailable: Boolean = true,
    capabilities: AppearanceSettingsCapabilities = AppearanceSettingsCapabilities(),
    section: AppearanceSettingsSection = AppearanceSettingsSection.All,
    navigationContent: @Composable ColumnScope.() -> Unit = {},
    additionalAppearanceContent: @Composable ColumnScope.() -> Unit = {},
) {
    var customAddressBarColorDialogVisible by remember { mutableStateOf(false) }
    var pendingCustomAddressBarColor by remember(settings.addressBarCustomColorHex) {
        mutableStateOf(settings.addressBarCustomColorHex)
    }
    var pendingWebContentFontSize by remember(settings.webContentFontSizePercent) {
        mutableFloatStateOf(settings.webContentFontSizePercent.toFloat())
    }

    SettingsPage(
        title = strings.title,
        backContentDescription = strings.back,
        onBack = onBack,
    ) {
        if (section in setOf(
                AppearanceSettingsSection.Colors,
                AppearanceSettingsSection.Surfaces,
                AppearanceSettingsSection.AddressBar,
            )
        ) {
            AppearanceSettingsIllustration(settings)
            SettingsPageSpacer()
        }
        if (section == AppearanceSettingsSection.All || section == AppearanceSettingsSection.Colors) {
            SettingsPreferenceGroup(containerColor = containerColor) {
                AppearanceRadioChoices(
                    title = strings.appearanceMode,
                    selected = settings.appearanceMode,
                    choices = capabilities.appearanceModes,
                    labels = strings.appearanceModeNames,
                    enabled = enabled,
                    testTag = SharedAppearanceSettingsTestTags.APPEARANCE_MODE,
                    onSelected = { value -> onSettingsChanged(settings.copy(appearanceMode = value)) },
                )
                SettingsPageSpacer()
                additionalAppearanceContent()
            }
        }
        if (section == AppearanceSettingsSection.All || section == AppearanceSettingsSection.Theme) {
            SettingsPreferenceGroup(containerColor = containerColor) {
                SettingsSwitch(
                    title = strings.animations,
                    subtitle = strings.animationsSummary,
                    checked = settings.animationsEnabled,
                    enabled = enabled,
                    onCheckedChange = { value ->
                        onSettingsChanged(settings.copy(animationsEnabled = value))
                    },
                    modifier = Modifier.testTag(SharedAppearanceSettingsTestTags.ANIMATIONS),
                )
            }
        }
        if (section == AppearanceSettingsSection.All || section == AppearanceSettingsSection.Websites) {
            SettingsPreferenceGroup(containerColor = containerColor) {
                if (capabilities.forceDarkWebsites) {
                    SettingsPageSpacer()
                    SettingsSwitch(
                        title = strings.forceDarkWebsites,
                        subtitle = strings.forceDarkWebsitesSummary,
                        checked = settings.forceDarkWebsites,
                        enabled = enabled && forceDarkWebsitesAvailable,
                        onCheckedChange = { value ->
                            onSettingsChanged(settings.copy(forceDarkWebsites = value))
                        },
                        modifier = Modifier.testTag(SharedAppearanceSettingsTestTags.FORCE_DARK_WEBSITES),
                    )
                }
                if (capabilities.webContentFontSize) {
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
                }
            }
        }
        if (capabilities.customChrome) {
            if (section == AppearanceSettingsSection.All || section == AppearanceSettingsSection.Colors) {
                SettingsPageSpacer()
                SettingsPreferenceGroup(containerColor = containerColor) {
                    AppearanceRadioChoices(
                        title = strings.colorPalette,
                        selected = settings.colorPalette,
                        choices = BrowserColorPalette.entries,
                        labels = strings.colorPaletteNames,
                        enabled = enabled,
                        testTag = SharedAppearanceSettingsTestTags.COLOR_PALETTE,
                        onSelected = { value -> onSettingsChanged(settings.copy(colorPalette = value)) },
                    )
                }
                SettingsPageSpacer()
            }
            if (section == AppearanceSettingsSection.All || section == AppearanceSettingsSection.AddressBar) {
                SettingsPreferenceGroup(containerColor = containerColor) {
                    AppearanceRadioChoices(
                        title = strings.addressBarColor,
                        selected = settings.addressBarColorPreset,
                        choices = BrowserAddressBarColorPreset.entries,
                        labels = strings.addressBarColorPresetNames,
                        enabled = enabled,
                        testTag = SharedAppearanceSettingsTestTags.ADDRESS_BAR_COLOR,
                        allowReselection = true,
                        onSelected = { value ->
                            if (value == BrowserAddressBarColorPreset.Custom) {
                                pendingCustomAddressBarColor = settings.addressBarCustomColorHex
                                customAddressBarColorDialogVisible = true
                            } else {
                                onSettingsChanged(settings.copy(addressBarColorPreset = value))
                            }
                        },
                    )
                    TextButton(
                        onClick = {
                            pendingCustomAddressBarColor = ""
                            onSettingsChanged(
                                settings.copy(
                                    addressBarColorPreset = BrowserAddressBarColorPreset.Theme,
                                    addressBarCustomColorHex = "",
                                ),
                            )
                        },
                        enabled = enabled && (
                            settings.addressBarColorPreset != BrowserAddressBarColorPreset.Theme ||
                                settings.addressBarCustomColorHex.isNotEmpty()
                            ),
                        modifier = Modifier.testTag(
                            SharedAppearanceSettingsTestTags.ADDRESS_BAR_COLOR_RESET,
                        ),
                    ) {
                        Text(strings.addressBarColorReset)
                    }
                    SettingsPageSpacer()
                    AppearanceRadioChoices(
                        title = strings.addressLoadStyle,
                        selected = settings.addressLoadStyle,
                        choices = BrowserAddressLoadStyle.entries,
                        labels = strings.addressLoadStyleNames,
                        enabled = enabled,
                        testTag = SharedAppearanceSettingsTestTags.ADDRESS_LOAD_STYLE,
                        onSelected = { value -> onSettingsChanged(settings.copy(addressLoadStyle = value)) },
                    )
                    SettingsPageSpacer()
                    AppearanceRadioChoices(
                        title = strings.addressBarStyle,
                        selected = settings.addressBarStyle,
                        choices = BrowserAddressBarStyle.entries,
                        labels = strings.addressBarStyleNames,
                        enabled = enabled,
                        testTag = SharedAppearanceSettingsTestTags.ADDRESS_BAR_STYLE,
                        onSelected = { value -> onSettingsChanged(settings.copy(addressBarStyle = value)) },
                    )
                }
            }
            if (section == AppearanceSettingsSection.All || section == AppearanceSettingsSection.Surfaces) {
                SettingsPreferenceGroup(containerColor = containerColor) {
                    AppearanceRadioChoices(
                        title = strings.shapeStyle,
                        selected = settings.shapeStyle,
                        choices = BrowserShapeStyle.entries,
                        labels = strings.shapeStyleNames,
                        enabled = enabled,
                        testTag = SharedAppearanceSettingsTestTags.SHAPE_STYLE,
                        onSelected = { value -> onSettingsChanged(settings.copy(shapeStyle = value)) },
                    )
                }
                SettingsPageSpacer()
                SettingsPreferenceGroup(containerColor = containerColor) {
                    AppearanceRadioChoices(
                        title = strings.surfaceStyle,
                        selected = settings.surfaceStyle,
                        choices = BrowserSurfaceStyle.entries,
                        labels = strings.surfaceStyleNames,
                        enabled = enabled,
                        testTag = SharedAppearanceSettingsTestTags.SURFACE_STYLE,
                        onSelected = { value -> onSettingsChanged(settings.copy(surfaceStyle = value)) },
                    )
                    Text(
                        strings.surfaceStyleSummary,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SettingsPreferenceDivider()
                    AppearanceSlider(
                        title = strings.frostedTransparency,
                        value = settings.frostedTransparencyPercent.toFloat(),
                        valueRange = AppearanceSettings.MIN_FROSTED_TRANSPARENCY_PERCENT.toFloat()..
                            AppearanceSettings.MAX_FROSTED_TRANSPARENCY_PERCENT.toFloat(),
                        steps = 7,
                        enabled = enabled && settings.surfaceStyle == BrowserSurfaceStyle.Frosted,
                        containerColor = containerColor,
                        onValueChange = { value ->
                            onSettingsChanged(settings.copy(frostedTransparencyPercent = value.roundToInt()))
                        },
                        testTag = SharedAppearanceSettingsTestTags.FROSTED_TRANSPARENCY,
                        inPreferenceGroup = true,
                    )
                    SettingsPreferenceDivider()
                    AppearanceSlider(
                        title = strings.frostedAddressBarTransparency,
                        value = settings.frostedAddressBarTransparencyPercent.toFloat(),
                        valueRange = AppearanceSettings.MIN_FROSTED_TRANSPARENCY_PERCENT.toFloat()..
                            AppearanceSettings.MAX_FROSTED_TRANSPARENCY_PERCENT.toFloat(),
                        steps = 7,
                        enabled = enabled && settings.surfaceStyle == BrowserSurfaceStyle.Frosted,
                        containerColor = containerColor,
                        onValueChange = { value ->
                            onSettingsChanged(
                                settings.copy(frostedAddressBarTransparencyPercent = value.roundToInt()),
                            )
                        },
                        testTag = SharedAppearanceSettingsTestTags.FROSTED_ADDRESS_BAR_TRANSPARENCY,
                        inPreferenceGroup = true,
                    )
                    SettingsPreferenceDivider()
                    AppearanceSlider(
                        title = strings.frostedBlur,
                        value = settings.frostedBlurPercent.toFloat(),
                        valueRange = AppearanceSettings.MIN_FROSTED_BLUR_PERCENT.toFloat()..
                            AppearanceSettings.MAX_FROSTED_BLUR_PERCENT.toFloat(),
                        steps = 9,
                        enabled = enabled && settings.surfaceStyle == BrowserSurfaceStyle.Frosted,
                        containerColor = containerColor,
                        onValueChange = { value ->
                            onSettingsChanged(settings.copy(frostedBlurPercent = value.roundToInt()))
                        },
                        testTag = SharedAppearanceSettingsTestTags.FROSTED_BLUR,
                        inPreferenceGroup = true,
                    )
                    Text(
                        strings.frostedBlurSummary,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (settings.surfaceStyle == BrowserSurfaceStyle.Frosted) 1f else 0.6f,
                        ),
                    )
                }
                SettingsPageSpacer()
            }
        }
        navigationContent()
    }

    if (capabilities.customChrome && customAddressBarColorDialogVisible) {
        val normalizedColor = AddressBarColorRules.normalizeHex(pendingCustomAddressBarColor)
        AlertDialog(
            onDismissRequest = { customAddressBarColorDialogVisible = false },
            title = { Text(strings.customAddressBarColorTitle) },
            text = {
                OutlinedTextField(
                    value = pendingCustomAddressBarColor,
                    onValueChange = { pendingCustomAddressBarColor = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SharedAppearanceSettingsTestTags.ADDRESS_BAR_CUSTOM_COLOR),
                    label = { Text(strings.customAddressBarColorLabel) },
                    supportingText = if (
                        pendingCustomAddressBarColor.isNotBlank() && normalizedColor == null
                    ) {
                        { Text(strings.customAddressBarColorInvalid) }
                    } else {
                        null
                    },
                    isError = pendingCustomAddressBarColor.isNotBlank() && normalizedColor == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val color = normalizedColor ?: return@TextButton
                        customAddressBarColorDialogVisible = false
                        onSettingsChanged(
                            settings.copy(
                                addressBarColorPreset = BrowserAddressBarColorPreset.Custom,
                                addressBarCustomColorHex = color,
                            ),
                        )
                    },
                    enabled = normalizedColor != null,
                    modifier = Modifier.testTag(
                        SharedAppearanceSettingsTestTags.ADDRESS_BAR_CUSTOM_COLOR_SAVE,
                    ),
                ) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { customAddressBarColorDialogVisible = false }) {
                    Text(strings.cancel)
                }
            },
        )
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
    inPreferenceGroup: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().settingsSearchTarget(title),
        shape = settingsRowShape(),
        color = settingsRowColor(),
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
                    color = if (enabled) MaterialTheme.colorScheme.primary else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                )
            }
            LocalCandyBrowserChromeEffects.current.settingsSlider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                enabled = enabled,
                modifier = Modifier.testTag(testTag).semantics {
                    contentDescription = title
                    stateDescription = "${value.roundToInt()} %"
                },
                valueRange = valueRange,
                steps = steps,
                backgroundColor = containerColor,
            )
        }
    }
}

@Composable
private fun <T> AppearanceRadioChoices(
    title: String,
    selected: T,
    choices: List<T>,
    labels: Map<T, String>,
    enabled: Boolean,
    testTag: String,
    allowReselection: Boolean = false,
    onSelected: (T) -> Unit,
) {
    Column(Modifier.testTag(testTag).settingsSearchTarget(title).selectableGroup()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        )
        choices.forEachIndexed { index, choice ->
            if (index > 0) SettingsPreferenceDivider()
            SettingsRadioOption(
                label = labels.getValue(choice),
                selected = choice == selected,
                enabled = enabled,
                onClick = { if (choice != selected || allowReselection) onSelected(choice) },
            )
        }
    }
}
