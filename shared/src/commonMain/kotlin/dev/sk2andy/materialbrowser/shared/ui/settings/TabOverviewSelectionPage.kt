package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.shared.resources.Res
import dev.sk2andy.materialbrowser.shared.resources.settings_tab_overview_coverflow
import dev.sk2andy.materialbrowser.shared.resources.settings_tab_overview_grid
import dev.sk2andy.materialbrowser.shared.resources.settings_tab_overview_list
import org.jetbrains.compose.resources.imageResource

object TabOverviewSelectionTestTags {
    fun option(mode: TabOverviewMode): String = "tab_overview_option_${mode.wireValue}"
}

@Composable
fun TabOverviewSelectionPage(
    mode: TabOverviewMode,
    strings: TabOverviewSettingsStrings,
    backContentDescription: String,
    onModeChanged: (TabOverviewMode) -> Unit,
    onBack: () -> Unit,
    testTagPrefix: String = "tab_overview_option",
) {
    SettingsSelectionPage(
        title = strings.overviewMode,
        backContentDescription = backContentDescription,
        onBack = onBack,
        preview = { TabOverviewPreview(mode) },
    ) {
        TabOverviewMode.entries.forEachIndexed { index, candidate ->
            if (index > 0) SettingsPreferenceDivider()
            SettingsRadioOption(
                label = strings.modeNames.getValue(candidate),
                selected = candidate == mode,
                modifier = Modifier.testTag("${testTagPrefix}_${candidate.wireValue}"),
                onClick = { if (candidate != mode) onModeChanged(candidate) },
            )
        }
    }
}

/** Flat tab arrangements share the Player illustrations' live theme coloring and preview slot. */
@Composable
private fun TabOverviewPreview(mode: TabOverviewMode) {
    val image = imageResource(
        when (mode) {
            TabOverviewMode.Hero -> Res.drawable.settings_tab_overview_coverflow
            TabOverviewMode.Grid -> Res.drawable.settings_tab_overview_grid
            TabOverviewMode.List -> Res.drawable.settings_tab_overview_list
        },
    )
    SettingsRasterIllustration(
        image = image,
        source = IntRect(0, 0, image.width, image.height),
        tag = "settings_tab_overview_artwork_${mode.wireValue}",
        height = 260.dp,
        maxWidth = 400.dp,
    )
}
