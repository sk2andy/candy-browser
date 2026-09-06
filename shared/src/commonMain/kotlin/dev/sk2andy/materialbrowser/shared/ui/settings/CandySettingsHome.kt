package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.browser.PageTranslationProvider
import dev.sk2andy.materialbrowser.browser.SearchEngine
import dev.sk2andy.materialbrowser.shared.ui.BrowserViewportTopping
import dev.sk2andy.materialbrowser.ui.SettingsDestination

internal object CandySettingsRouteRules {
    fun isEnabled(destination: SettingsDestination): Boolean =
        destination == SettingsDestination.Appearance ||
            destination == SettingsDestination.Search ||
            destination == SettingsDestination.TabsAndGestures ||
            destination == SettingsDestination.Browser ||
            destination == SettingsDestination.Userscripts ||
            destination == SettingsDestination.Sync
}

internal object CandySettingsHomeResources : SettingsHomeResources {
    @Composable
    override fun text(label: SettingsHomeLabel): String = when (label) {
        SettingsHomeLabel.Title -> "Einstellungen"
        SettingsHomeLabel.Back -> "Zurück"
        SettingsHomeLabel.SearchTitle -> "Suche"
        SettingsHomeLabel.SearchSummary -> "Suchmaschine und Vorschläge"
        SettingsHomeLabel.SyncTitle -> "Synchronisierung"
        SettingsHomeLabel.SyncSummary ->
            "Selbst gehostete, Ende-zu-Ende-verschlüsselte Geräte-Tabs"
        SettingsHomeLabel.TabsAndGesturesTitle -> "Tabs & Gesten"
        SettingsHomeLabel.TabsAndGesturesSummary ->
            "Tab-Layout, Aufbewahrung, Profile und Bedienelemente"
        SettingsHomeLabel.AppearanceTitle -> "Darstellung"
        SettingsHomeLabel.AppearanceSummary -> "Farben, Oberflächen und Form"
        SettingsHomeLabel.BrowserTitle -> "Browser"
        SettingsHomeLabel.BrowserSummary -> "Oberfläche und Standardbrowser-Verhalten"
        SettingsHomeLabel.DownloadsTitle -> "Downloads"
        SettingsHomeLabel.UserscriptsTitle -> "Toppings"
        SettingsHomeLabel.UserscriptsSummary -> "Passende Seiten mit Toppings anpassen"
        SettingsHomeLabel.FirefoxExtensionsTitle -> "Firefox-Erweiterungen"
        SettingsHomeLabel.FirefoxExtensionsSummary ->
            "Mit Gecko surfen und Mozilla-signierte Add-ons installieren"
        SettingsHomeLabel.SiteCapsulesTitle -> "Site Capsules"
        SettingsHomeLabel.SiteCapsulesSummary ->
            "Gespeicherte Site Capsules und Verknüpfungen verwalten"
        SettingsHomeLabel.ProtectionAndDataTitle -> "Schutz & Daten"
        SettingsHomeLabel.ProtectionAndDataSummary ->
            "Tracking-Schutz, Cookies, Berechtigungen und Browserdaten"
        SettingsHomeLabel.AboutLegalTitle -> "Über Candy & Rechtliches"
        SettingsHomeLabel.AboutLegalSummary -> "Version, Impressum, Lizenzen und Quellen"
    }
}

@Composable
internal fun CandySettingsHomeIcon(
    icon: SettingsHomeIcon,
    modifier: Modifier,
    tint: Color,
) {
    val imageVector = when (icon) {
        SettingsHomeIcon.Search -> Icons.Filled.Search
        SettingsHomeIcon.Sync -> Icons.Filled.Refresh
        SettingsHomeIcon.TabsAndGestures -> Icons.AutoMirrored.Filled.List
        SettingsHomeIcon.Appearance -> Icons.Filled.Face
        SettingsHomeIcon.Browser -> Icons.Filled.Settings
        SettingsHomeIcon.Downloads -> Icons.Filled.Download
        SettingsHomeIcon.Userscripts,
        SettingsHomeIcon.FirefoxExtensions,
        -> Icons.Filled.Extension
        SettingsHomeIcon.SiteCapsules -> Icons.Filled.Favorite
        SettingsHomeIcon.ProtectionAndData -> Icons.Filled.Lock
        SettingsHomeIcon.AboutLegal -> Icons.Filled.Info
    }
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
internal fun CandySettingsHome(
    searchEngine: SearchEngine,
    searxngInstanceUrl: String,
    onSearchEngineChanged: (SearchEngine) -> Unit,
    onSearxngInstanceUrlChanged: (String) -> Unit,
    tabOverviewMode: TabOverviewMode,
    onTabOverviewModeChanged: (TabOverviewMode) -> Unit,
    translationProvider: PageTranslationProvider,
    onTranslationProviderChanged: (PageTranslationProvider) -> Unit,
    toppings: List<BrowserViewportTopping>,
    onSaveTopping: (id: String?, source: String) -> Unit,
    toppingSource: (id: String) -> String?,
    onSetToppingEnabled: (id: String, enabled: Boolean) -> Unit,
    onDeleteTopping: (id: String) -> Unit,
    onDismiss: () -> Unit,
    syncState: SyncSettingsUiState = SyncSettingsUiState(),
    syncActions: SyncSettingsActionSink? = null,
) {
    var destination by remember { mutableStateOf(SettingsDestination.Home) }
    SettingsRouter(destination = destination) { currentDestination ->
        when (currentDestination) {
            SettingsDestination.Home -> SettingsHomePage(
                downloadSummary = "Candy-eigener Downloader",
                resources = CandySettingsHomeResources,
                linkContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                icon = ::CandySettingsHomeIcon,
                onDestinationChanged = { destination = it },
                onDismiss = onDismiss,
                onOpenFirefoxExtensions = null,
                isDestinationEnabled = CandySettingsRouteRules::isEnabled,
            )

            SettingsDestination.Appearance -> CandyAppearanceSettingsPage(
                onBack = { destination = SettingsDestination.Home },
            )

            SettingsDestination.Search -> CandySearchSettingsPage(
                searchEngine = searchEngine,
                searxngInstanceUrl = searxngInstanceUrl,
                onSearchEngineChanged = onSearchEngineChanged,
                onSearxngInstanceUrlChanged = onSearxngInstanceUrlChanged,
                onBack = { destination = SettingsDestination.Home },
            )

            SettingsDestination.TabsAndGestures -> CandyTabsAndGesturesSettingsPage(
                tabOverviewMode = tabOverviewMode,
                onTabOverviewModeChanged = onTabOverviewModeChanged,
                onBack = { destination = SettingsDestination.Home },
            )

            SettingsDestination.Browser -> CandyBrowserSettingsPage(
                translationProvider = translationProvider,
                onTranslationProviderChanged = onTranslationProviderChanged,
                onBack = { destination = SettingsDestination.Home },
            )

            SettingsDestination.Userscripts -> CandyToppingsSettingsPage(
                toppings = toppings,
                onSave = onSaveTopping,
                toppingSource = toppingSource,
                onSetEnabled = onSetToppingEnabled,
                onDelete = onDeleteTopping,
                onBack = { destination = SettingsDestination.Home },
            )

            SettingsDestination.Sync -> if (syncActions != null) {
                SyncSettingsPage(syncState, syncActions) {
                    destination = SettingsDestination.Home
                }
            }

            else -> Unit
        }
    }
}
