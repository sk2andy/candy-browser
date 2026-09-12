package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.browser.PageTranslationProvider
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.data.BrowserColorPalette
import dev.sk2andy.materialbrowser.data.BrowserShapeStyle
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle
import dev.sk2andy.materialbrowser.data.TabOverviewMode

private val candyAppearanceStrings = AppearanceSettingsStrings(
    title = "Darstellung",
    back = "Zurück",
    appearanceMode = "Erscheinungsbild",
    appearanceModeNames = mapOf(
        BrowserAppearanceMode.System to "System",
        BrowserAppearanceMode.Light to "Hell",
        BrowserAppearanceMode.Dark to "Dunkel",
        BrowserAppearanceMode.Amoled to "AMOLED",
    ),
    forceDarkWebsites = "Dunkelmodus auf Websites erzwingen",
    forceDarkWebsitesSummary =
        "Bei dunkler Browserdarstellung werden Websites ohne eigenes dunkles Design automatisch abgedunkelt. Kann zu Darstellungsfehlern führen.",
    webContentFontSize = "Schriftgröße auf Websites",
    colorPalette = "Farbwelt",
    colorPaletteNames = mapOf(
        BrowserColorPalette.Dynamic to "Material You",
        BrowserColorPalette.Candy to "Candy",
        BrowserColorPalette.Neutral to "Neutral",
    ),
    surfaceStyle = "Oberflächen",
    surfaceStyleSummary =
        "Klar nutzt deckende Flächen. Frosted nutzt transparente Flächen mit Hintergrund-Blur.",
    surfaceStyleNames = mapOf(
        BrowserSurfaceStyle.Clear to "Klar",
        BrowserSurfaceStyle.Frosted to "Frosted",
    ),
    frostedTransparency = "Transparenz",
    frostedAddressBarTransparency = "Adressleisten-Transparenz",
    frostedBlur = "Weichzeichnungsgrad",
    shapeStyle = "Form",
    shapeStyleNames = mapOf(
        BrowserShapeStyle.Angular to "Kantig",
        BrowserShapeStyle.Rounded to "Rund",
        BrowserShapeStyle.ExtraRounded to "Extra rund",
    ),
)

private val candyTabOverviewStrings = TabOverviewSettingsStrings(
    overviewMode = "Darstellung der Tab-Übersicht",
    modeNames = mapOf(
        TabOverviewMode.Hero to "Coverflow",
        TabOverviewMode.Grid to "Kompaktes Raster",
        TabOverviewMode.List to "Liste ohne Vorschauen",
    ),
    listStartsAtBottom = "Tabs unten beginnen",
    listStartsAtBottomSummary =
        "Öffnet Liste oder Raster bei den neuesten Tabs und verankert kurze Ansichten in Daumennähe.",
)

private val candyTranslationStrings = TranslationProviderSettingsStrings(
    title = "Anbieter für Seitenübersetzung",
    providerNames = PageTranslationProvider.entries.associateWith { it.displayName },
    providerSummaries = mapOf(
        PageTranslationProvider.Google to
            "Google erhält beim Übersetzen die vollständige Seitenadresse.",
        PageTranslationProvider.Yandex to
            "Beim Übersetzen wird die vollständige Seitenadresse an diesen Anbieter gesendet.",
        PageTranslationProvider.Kagi to
            "Kagi Translate erfordert derzeit ein aktives Kagi-Abo.",
    ),
)

@Composable
internal fun CandyAppearanceSettingsPage(onBack: () -> Unit) {
    AppearanceSettingsPage(
        settings = AppearanceSettings(),
        strings = candyAppearanceStrings,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        onSettingsChanged = {},
        onBack = onBack,
        enabled = false,
    )
}

@Composable
internal fun CandyTabsAndGesturesSettingsPage(
    tabOverviewMode: TabOverviewMode,
    onTabOverviewModeChanged: (TabOverviewMode) -> Unit,
    tabListStartsAtBottom: Boolean,
    onTabListStartsAtBottomChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPage(
        title = "Tabs & Gesten",
        backContentDescription = "Zurück",
        onBack = onBack,
    ) {
        SettingsSectionTitle("Tabs")
        Spacer(Modifier.height(8.dp))
        TabOverviewSettings(
            mode = tabOverviewMode,
            listStartsAtBottom = tabListStartsAtBottom,
            strings = candyTabOverviewStrings,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            onModeChanged = onTabOverviewModeChanged,
            onListStartsAtBottomChanged = onTabListStartsAtBottomChanged,
        )
        Spacer(Modifier.height(2.dp))
        SettingsSwitch(
            title = "Tabs nach letzter Nutzung sortieren",
            subtitle = "Pins bleiben vorn; manuelles Sortieren ist dann aus.",
            checked = false,
            enabled = false,
            onCheckedChange = {},
        )
        Spacer(Modifier.height(14.dp))
        SettingsSectionTitle("Gesten")
        Spacer(Modifier.height(2.dp))
        TabDismissResistanceSettings(
            valuePercent = 40,
            title = "Widerstand beim Schließen von Tabs",
            summary = { value -> "Widerstandsphase: $value% des Schließwegs" },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            enabled = false,
            onValueChanged = {},
        )
    }
}

@Composable
internal fun CandyBrowserSettingsPage(
    translationProvider: PageTranslationProvider,
    onTranslationProviderChanged: (PageTranslationProvider) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPage(
        title = "Browser",
        backContentDescription = "Zurück",
        onBack = onBack,
    ) {
        SettingsSwitch(
            title = "Startanimation",
            subtitle = "Zeigt die Candy-Animation beim normalen App-Start.",
            checked = false,
            enabled = false,
            onCheckedChange = {},
        )
        Spacer(Modifier.height(8.dp))
        SettingsSwitch(
            title = "Beim Start Startseite öffnen",
            subtitle = "Öffnet beim Start einen neuen leeren Tab.",
            checked = false,
            enabled = false,
            onCheckedChange = {},
        )
        Spacer(Modifier.height(8.dp))
        SettingsSwitch(
            title = "Ziehbare Scrollleiste",
            subtitle = "Erscheint beim Scrollen und blendet sich danach aus.",
            checked = false,
            enabled = false,
            onCheckedChange = {},
        )
        Spacer(Modifier.height(8.dp))
        TranslationProviderSettings(
            provider = translationProvider,
            strings = candyTranslationStrings,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            enabled = true,
            onProviderChanged = onTranslationProviderChanged,
        )
    }
}
