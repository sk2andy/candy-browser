package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.shared.resources.Res
import dev.sk2andy.materialbrowser.shared.resources.settings_address_bar
import org.jetbrains.compose.resources.imageResource

/** Classic and segmented address bars share the settings artwork's live theme coloring. */
@Composable
fun SettingsAddressBarIllustration() {
    val image = imageResource(Res.drawable.settings_address_bar)
    SettingsRasterIllustration(
        image = image,
        source = IntRect(0, 0, image.width, image.height),
        tag = "settings_address_bar_artwork",
        height = 220.dp,
        maxWidth = 400.dp,
    )
}
