package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.shared.resources.Res
import dev.sk2andy.materialbrowser.shared.resources.settings_approved_appearance_tabs
import dev.sk2andy.materialbrowser.shared.resources.settings_approved_player_waveform
import dev.sk2andy.materialbrowser.shared.resources.settings_approved_search_engines
import dev.sk2andy.materialbrowser.shared.resources.settings_approved_translations
import dev.sk2andy.materialbrowser.shared.resources.settings_approved_downloads_v2
import dev.sk2andy.materialbrowser.shared.resources.settings_approved_profiles_v2
import dev.sk2andy.materialbrowser.shared.resources.settings_approved_gestures
import dev.sk2andy.materialbrowser.shared.resources.settings_approved_link_preview_final_v8
import dev.sk2andy.materialbrowser.shared.resources.settings_approved_tab_lifecycle
import dev.sk2andy.materialbrowser.shared.resources.settings_approved_personal_link_rules
import org.jetbrains.compose.resources.imageResource
import kotlin.math.roundToInt

/** Decorative approved raster artwork; instructional previews remain separate components. */
@Composable
fun SettingsHeaderIllustration(
    artwork: SettingsHeaderArtwork,
    backgroundColor: Color = settingsPageColor(),
    height: Dp = 200.dp,
) {
    val image = imageResource(
        when (artwork) {
            SettingsHeaderArtwork.Appearance, SettingsHeaderArtwork.Tabs ->
                Res.drawable.settings_approved_appearance_tabs
            SettingsHeaderArtwork.Player -> Res.drawable.settings_approved_player_waveform
            SettingsHeaderArtwork.SearchEngines -> Res.drawable.settings_approved_search_engines
            SettingsHeaderArtwork.Translations -> Res.drawable.settings_approved_translations
            SettingsHeaderArtwork.Downloads -> Res.drawable.settings_approved_downloads_v2
            SettingsHeaderArtwork.Profiles -> Res.drawable.settings_approved_profiles_v2
            SettingsHeaderArtwork.Gestures -> Res.drawable.settings_approved_gestures
            SettingsHeaderArtwork.LinkPreview -> Res.drawable.settings_approved_link_preview_final_v8
            SettingsHeaderArtwork.TabLifecycle -> Res.drawable.settings_approved_tab_lifecycle
            SettingsHeaderArtwork.PersonalLinkRules -> Res.drawable.settings_approved_personal_link_rules
        },
    )
    val source = remember(artwork) { SettingsHeaderArtworkRules.sourceWindow(artwork) }
    val normalization = remember {
        ColorFilter.colorMatrix(SettingsHeaderArtworkRules.backgroundNormalization())
    }
    val ink = MaterialTheme.colorScheme.primary
    val page = backgroundColor
    val paint = remember(ink, page) {
        Paint().apply {
            colorFilter = ColorFilter.colorMatrix(SettingsHeaderArtworkRules.themeColors(ink, page))
        }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .height(height)
                .testTag("settings_header_artwork_${artwork.name.lowercase()}")
                .clearAndSetSemantics { },
        ) {
            val destination = SettingsHeaderArtworkRules.destinationWindow(
                source = source.size,
                available = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            )
            // The intermediate layer clamps normalized white before theme mapping. Combining
            // the matrices would tint the offwhite paper beyond the page color in dark themes.
            drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
                try {
                    drawImage(
                        image = image,
                        srcOffset = source.topLeft,
                        srcSize = source.size,
                        dstOffset = destination.topLeft,
                        dstSize = destination.size,
                        colorFilter = normalization,
                        filterQuality = FilterQuality.Medium,
                    )
                } finally {
                    canvas.restore()
                }
            }
        }
    }
}
