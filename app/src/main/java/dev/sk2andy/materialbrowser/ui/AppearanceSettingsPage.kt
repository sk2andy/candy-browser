package dev.sk2andy.materialbrowser.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.data.BrowserColorPalette
import dev.sk2andy.materialbrowser.data.BrowserShapeStyle
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle
import dev.sk2andy.materialbrowser.shared.ui.settings.AppearanceSettingsStrings
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor
import dev.sk2andy.materialbrowser.shared.ui.settings.AppearanceSettingsPage as SharedAppearanceSettingsPage

internal object AppearanceSettingsTestTags {
    const val AppearanceMode = "appearance_settings_mode"
    const val ForceDarkWebsites = "appearance_settings_force_dark_websites"
    const val WebContentFontSize = "appearance_settings_web_content_font_size"
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
    SharedAppearanceSettingsPage(
        settings = settings,
        strings = AppearanceSettingsStrings(
            title = stringResource(R.string.settings_appearance_title),
            back = stringResource(R.string.action_back),
            appearanceMode = stringResource(R.string.settings_appearance_mode),
            appearanceModeNames = BrowserAppearanceMode.entries.associateWith { it.displayName() },
            forceDarkWebsites = stringResource(R.string.settings_force_dark_websites),
            forceDarkWebsitesSummary = stringResource(
                R.string.settings_force_dark_websites_summary,
            ),
            webContentFontSize = stringResource(R.string.settings_web_content_font_size),
            colorPalette = stringResource(R.string.settings_color_palette),
            colorPaletteNames = BrowserColorPalette.entries.associateWith { it.displayName() },
            surfaceStyle = stringResource(R.string.settings_surface_style),
            surfaceStyleSummary = stringResource(R.string.settings_surface_style_summary),
            surfaceStyleNames = BrowserSurfaceStyle.entries.associateWith { it.displayName() },
            frostedTransparency = stringResource(R.string.settings_frosted_transparency),
            frostedAddressBarTransparency = stringResource(
                R.string.settings_frosted_address_bar_transparency,
            ),
            frostedBlur = stringResource(R.string.settings_frosted_blur),
            shapeStyle = stringResource(R.string.settings_shape_style),
            shapeStyleNames = BrowserShapeStyle.entries.associateWith { it.displayName() },
        ),
        containerColor = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
        onSettingsChanged = onSettingsChanged,
        onBack = onBack,
    )
}
