package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.browser.PageTranslationProvider
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAddressBarColorPreset
import dev.sk2andy.materialbrowser.data.BrowserAddressBarStyle
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.data.BrowserColorPalette
import dev.sk2andy.materialbrowser.data.BrowserShapeStyle
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuConfigurationSection
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuEntry
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLocation

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
    animations = "Animationen",
    animationsSummary =
        "Animationen in Candy und auf unterstützten Websites anzeigen. Ausschalten, um Bewegungen zu überspringen.",
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
    addressBarColor = "Adressleistenfarbe",
    addressBarColorPresetNames = mapOf(
        BrowserAddressBarColorPreset.Theme to "Theme",
        BrowserAddressBarColorPreset.Dimmed to "Gedimmt",
        BrowserAddressBarColorPreset.Graphite to "Graphit",
        BrowserAddressBarColorPreset.Black to "Schwarz",
        BrowserAddressBarColorPreset.Custom to "Benutzerdefiniert",
    ),
    addressBarColorReset = "Auf Theme-Farbe zurücksetzen",
    customAddressBarColorTitle = "Eigene Adressleistenfarbe",
    customAddressBarColorLabel = "Hex-Farbe",
    customAddressBarColorInvalid = "#RGB oder #RRGGBB eingeben.",
    cancel = "Abbrechen",
    save = "Speichern",
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
    frostedBlurSummary =
        "Website-Blur hängt von Android-Version und Browser-Engine ab.",
    shapeStyle = "Form",
    shapeStyleNames = mapOf(
        BrowserShapeStyle.Angular to "Kantig",
        BrowserShapeStyle.Rounded to "Rund",
        BrowserShapeStyle.ExtraRounded to "Extra rund",
    ),
    addressBarStyle = "Stil der Adressleiste",
    addressBarStyleNames = mapOf(
        BrowserAddressBarStyle.Classic to "Klassisch",
        BrowserAddressBarStyle.Segmented to "Segmentiert",
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
    onMenuActions: () -> Unit,
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
        Spacer(Modifier.height(8.dp))
        SettingsLink(
            title = "Menü-Aktionen",
            subtitle = "Aktionen für Tab- und Tab-Switcher-Menü auswählen",
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = { modifier, tint ->
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = null,
                    modifier = modifier,
                    tint = tint,
                )
            },
            onClick = onMenuActions,
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

internal object CandyBrowserMenuSettingsResources : BrowserMenuSettingsResources {
    @Composable
    override fun title(): String = "Menü-Aktionen"

    @Composable
    override fun back(): String = "Zurück"

    @Composable
    override fun intro(): String =
        "Lege für jede Aktion fest, wo sie erscheint. Kontextgebundene Aktionen bieten nur passende Menüs an."

    @Composable
    override fun sectionTitle(section: BrowserMenuConfigurationSection): String = when (section) {
        BrowserMenuConfigurationSection.Toolbar -> "Werkzeugleiste"
        BrowserMenuConfigurationSection.Page -> "Seite"
        BrowserMenuConfigurationSection.Toppings -> "Toppings"
        BrowserMenuConfigurationSection.Extensions -> "Erweiterungen"
        BrowserMenuConfigurationSection.Candy -> "Candy"
        BrowserMenuConfigurationSection.Browser -> "Browser"
        BrowserMenuConfigurationSection.TabSwitcher -> "Tab-Switcher"
    }

    @Composable
    override fun entryLabel(entry: BrowserMenuEntry): String = when (entry) {
        BrowserMenuEntry.Back -> "Zurück"
        BrowserMenuEntry.Forward -> "Vorwärts"
        BrowserMenuEntry.Reload -> "Neu laden / Laden stoppen"
        BrowserMenuEntry.Favorite -> "Favorit"
        BrowserMenuEntry.Pin -> "Tab anheften"
        BrowserMenuEntry.ShowTabs -> "Tabs anzeigen"
        BrowserMenuEntry.NewTab -> "Neuer Tab"
        BrowserMenuEntry.CloseTab -> "Tab schließen"
        BrowserMenuEntry.DuplicateTab -> "Tab duplizieren"
        BrowserMenuEntry.Reader -> "Reader öffnen"
        BrowserMenuEntry.Translate -> "Seite übersetzen"
        BrowserMenuEntry.FindInPage -> "Auf Seite suchen"
        BrowserMenuEntry.Share -> "Teilen"
        BrowserMenuEntry.OpenExternal -> "Extern öffnen"
        BrowserMenuEntry.Print -> "Drucken"
        BrowserMenuEntry.CookieBannerRemoval -> "Cookie-Banner entfernen"
        BrowserMenuEntry.ForceVerticalScrolling -> "Vertikales Scrollen erzwingen"
        BrowserMenuEntry.ForcePageZooming -> "Seitenzoom erzwingen"
        BrowserMenuEntry.ForceSafeArea -> "Safe Area erzwingen"
        BrowserMenuEntry.AlwaysBlockPopups -> "Pop-ups immer blockieren"
        BrowserMenuEntry.DesktopView -> "Desktop-Ansicht"
        BrowserMenuEntry.DomainMute -> "Website stummschalten"
        BrowserMenuEntry.ToppingCommands -> "Topping-Befehle"
        BrowserMenuEntry.FirefoxPageActions -> "Firefox-Seitenaktionen"
        BrowserMenuEntry.CandyTrail -> "Candy Trail"
        BrowserMenuEntry.AddSiteCapsule -> "Site Capsule hinzufügen"
        BrowserMenuEntry.Summarize -> "Zusammenfassen"
        BrowserMenuEntry.Snooze -> "Tab schlummern"
        BrowserMenuEntry.AddressBarDocking -> "Adressleiste parken"
        BrowserMenuEntry.OpenSnoozedTabs -> "Schlummernde Tabs"
        BrowserMenuEntry.OpenFavorites -> "Favoriten"
        BrowserMenuEntry.OpenDownloads -> "Downloads"
        BrowserMenuEntry.OpenHistory -> "Verlauf"
        BrowserMenuEntry.OpenFirefoxExtensions -> "Firefox-Erweiterungen"
        BrowserMenuEntry.OpenSettings -> "Einstellungen"
        BrowserMenuEntry.MoveToProfile -> "In Profil verschieben"
        BrowserMenuEntry.TabStacks -> "Candy Stacks"
        BrowserMenuEntry.CloseAllTabs -> "Alle Tabs schließen"
    }

    @Composable
    override fun locationLabel(location: BrowserMenuLocation): String = when (location) {
        BrowserMenuLocation.Nowhere -> "Nirgends"
        BrowserMenuLocation.Tab -> "Tab-Menü"
        BrowserMenuLocation.TabSwitcher -> "Tab-Switcher-Menü"
        BrowserMenuLocation.Both -> "Beide Menüs"
    }
}

@Composable
internal fun CandyBrowserMenuSettingsPage(
    layout: BrowserMenuLayout,
    availableEntries: List<BrowserMenuEntry>,
    onLocationChanged: (BrowserMenuEntry, BrowserMenuLocation) -> Unit,
    onBack: () -> Unit,
) {
    BrowserMenuSettingsPage(
        layout = layout,
        resources = CandyBrowserMenuSettingsResources,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        availableEntries = availableEntries,
        onLocationChanged = onLocationChanged,
        onBack = onBack,
    )
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
