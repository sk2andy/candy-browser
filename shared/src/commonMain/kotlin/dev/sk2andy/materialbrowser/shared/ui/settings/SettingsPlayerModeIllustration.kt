package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.shared.resources.Res
import dev.sk2andy.materialbrowser.shared.resources.settings_player_mode_disabled
import dev.sk2andy.materialbrowser.shared.resources.settings_player_mode_button_fullscreen
import dev.sk2andy.materialbrowser.shared.resources.settings_player_mode_button_inline_fullscreen
import dev.sk2andy.materialbrowser.shared.resources.settings_player_mode_always_fullscreen
import dev.sk2andy.materialbrowser.shared.resources.settings_player_mode_automatic
import org.jetbrains.compose.resources.imageResource

enum class SettingsPlayerModeArtwork {
    Disabled, ButtonFullscreen, ButtonInlineAndFullscreen, AlwaysForFullscreen, Automatic,
}

/** One generated, theme-colored illustration per player mode, in a fixed preview slot. */
@Composable
fun SettingsPlayerModeIllustration(mode: SettingsPlayerModeArtwork) {
    val image = imageResource(
        when (mode) {
            SettingsPlayerModeArtwork.Disabled -> Res.drawable.settings_player_mode_disabled
            SettingsPlayerModeArtwork.ButtonFullscreen -> Res.drawable.settings_player_mode_button_fullscreen
            SettingsPlayerModeArtwork.ButtonInlineAndFullscreen ->
                Res.drawable.settings_player_mode_button_inline_fullscreen
            SettingsPlayerModeArtwork.AlwaysForFullscreen -> Res.drawable.settings_player_mode_always_fullscreen
            SettingsPlayerModeArtwork.Automatic -> Res.drawable.settings_player_mode_automatic
        },
    )
    SettingsRasterIllustration(
        image = image,
        source = IntRect(0, 0, image.width, image.height),
        tag = "settings_player_mode_artwork_${mode.name.lowercase()}",
        height = 240.dp,
        maxWidth = 400.dp,
    )
}
