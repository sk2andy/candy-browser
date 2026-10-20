package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.browser.PageTranslationProvider
import dev.sk2andy.materialbrowser.browser.SearchEngine
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuAction
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuItem
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuEntry
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayoutRules
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLocation
import dev.sk2andy.materialbrowser.shared.topping.ToppingFrameScope
import dev.sk2andy.materialbrowser.shared.ui.BrowserViewportActionSink
import dev.sk2andy.materialbrowser.shared.ui.BrowserViewportSnapshot
import dev.sk2andy.materialbrowser.shared.ui.BrowserViewportTopping
import dev.sk2andy.materialbrowser.shared.ui.CandyBrowserApp
import dev.sk2andy.materialbrowser.shared.ui.CandyBrowserUiAction
import dev.sk2andy.materialbrowser.sync.SyncConnectionSettings
import dev.sk2andy.materialbrowser.sync.SyncEnrollmentOutcome
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the real shared settings through its public platform host, without an engine or network. */
@RunWith(AndroidJUnit4::class)
class SharedCoreSettingsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun browserGroupsKeepUnsupportedOptionsDisabledAndTranslationCallbackIntact() {
        val host = showSettings()
        open("Browser-Verhalten")
        listOf("Startanimation", "Beim Start Startseite öffnen", "Ziehbare Scrollleiste").forEach { label ->
            composeRule.onNodeWithText(label).performScrollTo().assertIsNotEnabled().performClick()
        }
        capture("shared-core-browser")
        composeRule.onNodeWithText("Übersetzungen").performScrollTo().performClick()
        composeRule.onNodeWithText(PageTranslationProvider.Google.displayName).performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(PageTranslationProvider.Google, host.snapshot.translationProvider)
            assertEquals(listOf("translation:Google"), host.writes)
        }
        back()
        composeRule.onNodeWithText("Browser").assertIsDisplayed()
        back()
        composeRule.onNodeWithText("Einstellungen").assertIsDisplayed()
    }

    @Test
    fun tabsRetainDisabledListOriginAndMenuBackReturnsToSharedParent() {
        val host = showSettings()
        open("Tabs & Gesten")
        composeRule.onNodeWithText("Tabs unten beginnen").assertIsOn().assertIsNotEnabled().performClick()
        composeRule.onNodeWithText("Tabs nach letzter Nutzung sortieren").assertIsNotEnabled().performClick()
        composeRule.runOnIdle { assertTrue(host.writes.isEmpty()) }
        composeRule.onNodeWithText("Darstellung der Tab-Übersicht").performClick()
        composeRule.onNodeWithText("Kompaktes Raster").performClick()
        back()
        composeRule.onNodeWithText("Tabs unten beginnen").assertIsOn().assertIsEnabled()
        composeRule.onNodeWithText("Widerstand beim Schließen von Tabs").performScrollTo()
        composeRule.onNode(hasContentDescription("Widerstand beim Schließen von Tabs"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Widerstandsphase: 50% des Schließwegs"))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(70f) }
        capture("shared-core-tabs-gestures")
        open("Menü-Aktionen")
        composeRule.onNodeWithTag(BrowserMenuPresentationSettingsTestTags.toolbarSlot(4))
            .performScrollTo().assertIsDisplayed()
        capture("shared-core-menu-toolbar")
        back()
        composeRule.onNode(hasText("Tabs & Gesten") and !hasClickAction()).assertIsDisplayed()
        back()
        composeRule.onNodeWithText("Einstellungen").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(TabOverviewMode.Grid, host.snapshot.tabOverviewMode)
            assertTrue(host.snapshot.tabListStartsAtBottom)
            assertEquals(70, host.snapshot.dismissResistancePercent)
        }
    }

    @Test
    fun searchGroupPreservesInstanceValidationAndEngineSelection() {
        val host = showSettings()
        open("Suche")
        composeRule.onNodeWithTag("settings_header_artwork_searchengines").assertIsDisplayed()
        capture("shared-core-search-approved-header")
        composeRule.onNodeWithText(SearchEngine.SearXNG.displayName).performScrollTo().performClick()
        composeRule.onNodeWithTag("search_settings_searxng_instance_url")
            .performScrollTo().performTextReplacement("https://search.example.test/")
        composeRule.runOnIdle {
            assertEquals(SearchEngine.SearXNG, host.snapshot.searchEngine)
            assertEquals("https://search.example.test", host.snapshot.searxngInstanceUrl)
        }
        composeRule.onNodeWithTag("search_settings_searxng_instance_url")
            .performTextReplacement("https://user:secret@search.example.test/?q=invalid")
        composeRule.runOnIdle { assertEquals("https://search.example.test", host.snapshot.searxngInstanceUrl) }
        composeRule.onNodeWithTag("search_settings_searxng_instance_url")
            .performTextReplacement("https://search.example.test/")
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        composeRule.waitForIdle()
        capture("shared-core-search")
        composeRule.onNodeWithText(SearchEngine.Google.displayName).performScrollTo().performClick()
        composeRule.onNodeWithTag("search_settings_searxng_instance_url").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals("https://search.example.test", host.snapshot.searxngInstanceUrl) }
    }

    @Test
    fun toppingToggleIsNamedAndKeepsScopeWhenDisabled() {
        val host = showSettings()
        open("Toppings")
        val tag = "candy_toppings_toggle:fixture"
        composeRule.onNodeWithTag(tag).performScrollTo().assertIsOn()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(hasContentDescription("Shared test topping"))
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.onNodeWithTag("candy_toppings_frame_scope:fixture")
            .performScrollTo().assertIsNotEnabled().performClick()
        composeRule.onNodeWithTag("candy_toppings_frame_scope_dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("candy_toppings_edit:fixture").assertIsEnabled()
        composeRule.onNodeWithTag("candy_toppings_delete:fixture").assertIsEnabled()
        capture("shared-core-toppings-disabled")
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.onNodeWithTag("candy_toppings_frame_scope:fixture").assertIsEnabled()
        composeRule.runOnIdle {
            assertEquals(listOf("topping:fixture:false", "topping:fixture:true"), host.writes)
            assertEquals(ToppingFrameScope.Top, host.snapshot.toppings.single().allowedFrameScope)
        }
    }

    @Test
    fun appearanceDetailsReturnToAppearanceAndRetainCoreCapabilities() {
        showSettings()
        open("Theme & Oberfläche")
        open("Farben")
        composeRule.onNodeWithText("AMOLED").assertDoesNotExist()
        composeRule.onNodeWithTag("appearance_settings_palette").assertDoesNotExist()
        composeRule.onNodeWithTag("appearance_settings_mode").assertIsDisplayed()
        back()
        open("Webseiten")
        composeRule.onNodeWithTag("appearance_settings_web_content_font_size")
            .performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag("appearance_settings_force_dark_websites").assertDoesNotExist()
        back()
        composeRule.onNodeWithText("Farben").assertIsDisplayed()
        back()
        composeRule.onNodeWithText("Einstellungen").assertIsDisplayed()
    }

    private fun showSettings(): SettingsHost {
        val host = SettingsHost()
        composeRule.setContent {
            CandyBrowserApp(snapshot = host.snapshot, actionSink = host, browserViewport = {})
        }
        return host
    }

    private fun open(label: String) {
        composeRule.onNode(hasText(label) and hasClickAction()).performScrollTo().performClick()
    }

    private fun back() {
        composeRule.onNodeWithContentDescription("Zurück").performScrollTo().performClick()
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "settings-combined-evidence").apply { mkdirs() }
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(directory, "$name.png")))
    }

    private class SettingsHost : BrowserViewportActionSink {
        var snapshot by mutableStateOf(
            BrowserViewportSnapshot(
                address = "", pageTitle = "", tabCountLabel = "0",
                canGoBack = false, canGoForward = false, isLoading = false,
                menuItems = emptyList(), tabs = emptyList(), isTabOverviewVisible = false,
                isSettingsVisible = true, tabOverviewMode = TabOverviewMode.Hero,
                tabListStartsAtBottom = true, dismissResistancePercent = 50, addressFocusRequest = 0L,
                toppings = listOf(
                    BrowserViewportTopping(
                        "fixture", "Shared test topping", true,
                        declaredFrameScope = ToppingFrameScope.AllMatching,
                    ),
                ),
            ),
        )
        val writes = mutableListOf<String>()
        override fun changeTranslationProvider(provider: PageTranslationProvider) {
            writes += "translation:$provider"
            snapshot = snapshot.copy(translationProvider = provider)
        }
        override fun changeTabOverviewMode(mode: TabOverviewMode) {
            snapshot = snapshot.copy(tabOverviewMode = mode)
        }
        override fun changeTabListStartsAtBottom(enabled: Boolean) {
            writes += "list-origin:$enabled"
            snapshot = snapshot.copy(tabListStartsAtBottom = enabled)
        }
        override fun changeDismissResistancePercent(value: Int) {
            snapshot = snapshot.copy(dismissResistancePercent = value)
        }
        override fun changeSearchEngine(searchEngine: SearchEngine) {
            snapshot = snapshot.copy(searchEngine = searchEngine)
        }
        override fun changeSearxngInstanceUrl(value: String) {
            snapshot = snapshot.copy(searxngInstanceUrl = value)
        }
        override fun changeBrowserMenuLocation(entry: BrowserMenuEntry, location: BrowserMenuLocation) {
            snapshot = snapshot.copy(menuLayout = BrowserMenuLayoutRules.update(snapshot.menuLayout, entry, location))
        }
        override fun changeBrowserMenuLayout(layout: BrowserMenuLayout) {
            snapshot = snapshot.copy(menuLayout = layout)
        }
        override fun setToppingEnabled(id: String, enabled: Boolean) {
            writes += "topping:$id:$enabled"
            snapshot = snapshot.copy(toppings = snapshot.toppings.map { if (it.id == id) it.copy(enabled = enabled) else it })
        }
        override fun setToppingFrameScope(id: String, scope: ToppingFrameScope) { writes += "scope:$id:$scope" }
        override fun addressChanged(value: String) = Unit
        override fun perform(action: CandyBrowserUiAction) = Unit
        override fun performMenu(item: BrowserFeatureMenuItem) = Unit
        override fun performTabAction(tabId: String, action: BrowserFeatureMenuAction) = Unit
        override fun addressDragged(horizontal: Double, vertical: Double, velocityX: Double, viewportWidth: Double, isAddressEditing: Boolean) = Unit
        override fun selectTab(tabId: String) = Unit
        override fun selectTabInOverview(tabId: String) = Unit
        override fun closeTab(tabId: String) = Unit
        override fun selectProfile(profileId: String) = Unit
        override fun createProfile(emoji: String, isolationEnabled: Boolean) = Unit
        override fun updateProfileEmoji(profileId: String, emoji: String) = Unit
        override fun setProfileIsolation(profileId: String, enabled: Boolean) = Unit
        override fun hideTabOverview() = Unit
        override fun showSettings() = Unit
        override fun dismissSettings() = Unit
        override fun saveTopping(id: String?, source: String) = Unit
        override fun toppingSource(id: String): String? = null
        override fun deleteTopping(id: String) = Unit
        override fun retryReader() = Unit
        override fun dismissReader() = Unit
        override fun openReaderOriginal(url: String) = Unit
        override fun openReaderLink(url: String) = Unit
        override fun dismissCandyTrail() = Unit
        override fun selectCandyTrailNode(tabId: String, nodeId: String): Boolean = false
        override fun forkCandyTrailNode(tabId: String, nodeId: String): String? = null
        override fun activateCandyTrailFork(tabId: String, forkId: String): String? = null
        override fun configure(settings: SyncConnectionSettings): Boolean = false
        override fun enroll(serverPassword: CharArray, passphrase: CharArray, onComplete: (SyncEnrollmentOutcome) -> Unit) = Unit
        override fun refresh() = Unit
    }
}
