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
import dev.sk2andy.materialbrowser.data.BrowserAddressLoadStyle
import dev.sk2andy.materialbrowser.data.BrowserAddressBarStyle
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.data.BrowserColorPalette
import dev.sk2andy.materialbrowser.data.BrowserShapeStyle
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuBottomSheetContentLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuPresentation
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuConfigurationSection
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuEntry
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLocation

private val candyAppearanceStrings = AppearanceSettingsStrings(
    title = "Theme & Oberfläche",
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
    addressLoadStyle = "Ladeanzeige der Adressleiste",
    addressLoadStyleNames = mapOf(
        BrowserAddressLoadStyle.Rainbow to "Regenbogen",
        BrowserAddressLoadStyle.Tonal to "Tonal",
    ),
    addressBarStyle = "Stil der Adressleiste",
    addressBarStyleNames = mapOf(
        BrowserAddressBarStyle.Classic to "Klassisch",
        BrowserAddressBarStyle.Segmented to "Segmentiert",
    ),
)

internal val candyTabOverviewStrings = TabOverviewSettingsStrings(
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
internal fun CandyAppearanceSettingsPage(
    settings: AppearanceSettings,
    onSettingsChanged: (AppearanceSettings) -> Unit,
    onBack: () -> Unit,
    section: AppearanceSettingsSection = AppearanceSettingsSection.Theme,
    onDestinationChanged: (dev.sk2andy.materialbrowser.ui.SettingsDestination) -> Unit = {},
) {
    AppearanceSettingsPage(
        settings = settings,
        section = section,
        navigationContent = {
            if (section == AppearanceSettingsSection.Theme) {
                SettingsPageSpacer()
                SettingsPreferenceGroup(containerColor = settingsRowColor()) {
                    SettingsSelectionLink("Farben", "Hell, dunkel oder Systemeinstellung",
                        onClick = { onDestinationChanged(dev.sk2andy.materialbrowser.ui.SettingsDestination.AppearanceColors) })
                    SettingsPreferenceDivider()
                    SettingsSelectionLink("Webseiten", "Seitengröße",
                        onClick = { onDestinationChanged(dev.sk2andy.materialbrowser.ui.SettingsDestination.AppearanceWebsites) })
                }
            }
        },
        strings = candyAppearanceStrings.copy(
            title = when (section) {
                AppearanceSettingsSection.Colors -> "Farben"
                AppearanceSettingsSection.Websites -> "Webseiten"
                else -> candyAppearanceStrings.title
            },
            animationsSummary =
                "Bewegungen und Übergänge in Candy anzeigen. " +
                    "Die Systemeinstellung zur Bewegungsreduktion bleibt wirksam.",
            webContentFontSize = "Seitengröße",
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        onSettingsChanged = onSettingsChanged,
        onBack = onBack,
        capabilities = AppearanceSettingsCapabilities.Core,
    )
}

@Composable
internal fun CandyTabsAndGesturesSettingsPage(
    tabOverviewMode: TabOverviewMode,
    onTabOverviewModeChanged: (TabOverviewMode) -> Unit,
    tabListStartsAtBottom: Boolean,
    onTabListStartsAtBottomChanged: (Boolean) -> Unit,
    dismissResistancePercent: Int,
    onDismissResistanceChanged: (Int) -> Unit,
    onMenuActions: () -> Unit,
    onTabOverview: () -> Unit,
    onBack: () -> Unit,
) {
    SettingsPage(
        title = "Tabs & Gesten",
        backContentDescription = "Zurück",
        onBack = onBack,
    ) {
        SettingsSectionTitle("Tabs")
        Spacer(Modifier.height(8.dp))
        SettingsPreferenceGroup(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
            TabOverviewSettings(
                mode = tabOverviewMode,
                listStartsAtBottom = tabListStartsAtBottom,
                strings = candyTabOverviewStrings,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                onOpenMode = onTabOverview,
                onListStartsAtBottomChanged = onTabListStartsAtBottomChanged,
            )
            SettingsPreferenceDivider()
            SettingsSwitch(
                title = "Tabs nach letzter Nutzung sortieren",
                subtitle = "Pins bleiben vorn; manuelles Sortieren ist dann aus.",
                checked = false,
                enabled = false,
                onCheckedChange = {},
            )
        }
        SettingsPageSpacer()
        SettingsSectionTitle("Gesten")
        Spacer(Modifier.height(8.dp))
        SettingsPreferenceGroup(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
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
            SettingsPreferenceDivider()
            TabDismissResistanceSettings(
                valuePercent = dismissResistancePercent,
                title = "Widerstand beim Schließen von Tabs",
                summary = { value -> "Widerstandsphase: $value% des Schließwegs" },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                enabled = true,
                onValueChanged = onDismissResistanceChanged,
            )
        }
    }
}

internal object CandyBrowserMenuSettingsResources : BrowserMenuSettingsResources,
    BrowserMenuPresentationSettingsResources {
    @Composable
    override fun presentationTitle(): String = "Menü-Darstellung"

    @Composable
    override fun presentationSummary(): String =
        "Wähle das normale Menü oder ein Bottom Sheet mit scrollbaren Bereichen oder zwei Seiten zum Wischen."

    @Composable
    override fun presentationLabel(presentation: BrowserMenuPresentation): String = when (presentation) {
        BrowserMenuPresentation.Floating -> "Normales Menü"
        BrowserMenuPresentation.BottomSheet -> "Bottom Sheet"
    }

    @Composable
    override fun contentLayoutTitle(): String = "Bereiche im Bottom Sheet"

    @Composable
    override fun contentLayoutLabel(layout: BrowserMenuBottomSheetContentLayout): String = when (layout) {
        BrowserMenuBottomSheetContentLayout.Scroll -> "Vertikal scrollen"
        BrowserMenuBottomSheetContentLayout.SwipePages -> "Zwei Seiten zum Wischen"
    }

    @Composable
    override fun toolbarTitle(): String = "Werkzeugleiste im Bottom Sheet"

    @Composable
    override fun toolbarSummary(): String =
        "Wähle fünf Aktionen von links nach rechts. Bereits belegte Aktionen tauschen ihren Platz. Ein leerer Platz blendet die Schaltfläche aus."

    @Composable
    override fun toolbarSlotTitle(position: Int): String = "Aktion $position"

    @Composable
    override fun emptySlotLabel(): String = "Leerer Platz"

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
        BrowserMenuEntry.OpenFeeds -> "Feeds"
        BrowserMenuEntry.OpenLibrary -> "Bibliothek"
        BrowserMenuEntry.DiscoverFeeds -> "Feeds auf dieser Seite finden"
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
        BrowserMenuEntry.StatusBarScroll -> "Statusleisten-Tipp zum Seitenanfang"
        BrowserMenuEntry.ToppingCommands -> "Topping-Befehle"
        BrowserMenuEntry.FirefoxPageActions -> "Firefox-Seitenaktionen"
        BrowserMenuEntry.CandyTrail -> "Candy Trail"
        BrowserMenuEntry.Constellations -> "Candy Constellations"
        BrowserMenuEntry.WebAnnotations -> "Highlights & notes"
        BrowserMenuEntry.AddSiteCapsule -> "Site Capsule hinzufügen"
        BrowserMenuEntry.CreateSiteWidget -> "Website-Widget"
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
    onLayoutChanged: (BrowserMenuLayout) -> Unit,
    onOpenPresentation: () -> Unit,
    onOpenContentLayout: () -> Unit,
    onBack: () -> Unit,
) {
    BrowserMenuSettingsPage(
        layout = layout,
        resources = CandyBrowserMenuSettingsResources,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        availableEntries = availableEntries,
        onLocationChanged = onLocationChanged,
        onBack = onBack,
        presentationSettings = {
            BrowserMenuPresentationSettings(
                layout = layout,
                resources = CandyBrowserMenuSettingsResources,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                availableEntries = availableEntries,
                onLayoutChanged = onLayoutChanged,
                onOpenPresentation = onOpenPresentation,
                onOpenContentLayout = onOpenContentLayout,
            )
        },
    )
}

@Composable
internal fun CandyBrowserSettingsPage(
    translationProvider: PageTranslationProvider,
    onTranslationProviderChanged: (PageTranslationProvider) -> Unit,
    onBack: () -> Unit,
    onTranslations: () -> Unit = {},
) {
    SettingsPage(
        title = "Browser",
        backContentDescription = "Zurück",
        onBack = onBack,
    ) {
        SettingsSectionTitle("Start")
        Spacer(Modifier.height(8.dp))
        SettingsPreferenceGroup(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
            SettingsSwitch(
                title = "Startanimation",
                subtitle = "Zeigt die Candy-Animation beim normalen App-Start.",
                checked = false,
                enabled = false,
                onCheckedChange = {},
            )
            SettingsPreferenceDivider()
            SettingsSwitch(
                title = "Beim Start Startseite öffnen",
                subtitle = "Öffnet beim Start einen neuen leeren Tab.",
                checked = false,
                enabled = false,
                onCheckedChange = {},
            )
        }
        SettingsPageSpacer()
        SettingsSectionTitle("Webseiten")
        Spacer(Modifier.height(8.dp))
        SettingsPreferenceGroup(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
            SettingsSwitch(
                title = "Ziehbare Scrollleiste",
                subtitle = "Erscheint beim Scrollen und blendet sich danach aus.",
                checked = false,
                enabled = false,
                onCheckedChange = {},
            )
            SettingsPreferenceDivider()
            SettingsSelectionLink(
                title = "Übersetzungen",
                value = translationProvider.displayName,
                onClick = onTranslations,
            )
        }
    }
}

@Composable
internal fun CandyTranslationSettingsPage(
    provider: PageTranslationProvider,
    onProviderChanged: (PageTranslationProvider) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPage(title = "Übersetzungen", backContentDescription = "Zurück", onBack = onBack) {
        SettingsHeaderIllustration(SettingsHeaderArtwork.Translations)
        SettingsPageSpacer()
        SettingsPreferenceGroup(containerColor = settingsRowColor()) {
            TranslationProviderSettings(
                provider = provider,
                strings = candyTranslationStrings,
                containerColor = settingsRowColor(),
                enabled = true,
                onProviderChanged = onProviderChanged,
            )
        }
    }
}
