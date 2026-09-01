package dev.sk2andy.materialbrowser.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.data.AddressBarAction
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import dev.sk2andy.materialbrowser.data.HistoryEntry
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewTabFavoriteInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            controller?.destroy()
            controller = null
            clearSession()
        }
    }

    @Test
    fun favoriteOpensFromNewTabWhileAddressEditorIsVisible() {
        val favoriteUrl = "http://127.0.0.1/favorite"
        val favoriteTitle = "Example favorite"
        val browserController = createController(
            favorite = FavoriteEntry(
                url = favoriteUrl,
                title = favoriteTitle,
                addedAt = 1L,
            ),
        )
        setBrowserContent(browserController)

        composeRule.onNodeWithTag(
            AddressBarActionTestTags.action(AddressBarAction.NewTab),
        ).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            browserController.activeTabs.size == 2
        }
        val closeAddressDescription = composeRule.activity.getString(
            R.string.cd_close_address_input,
        )
        composeRule.onNodeWithContentDescription(closeAddressDescription).assertExists()

        composeRule.onNodeWithText(favoriteTitle)
            .assertHasClickAction()
            .performScrollTo()
            .performTouchInput { click() }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            browserController.selectedTab.url == favoriteUrl
        }
        composeRule.onNodeWithContentDescription(closeAddressDescription).assertDoesNotExist()
        assertEquals(favoriteUrl, browserController.selectedTab.url)
    }

    private fun createController(favorite: FavoriteEntry): BrowserController {
        lateinit var browserController: BrowserController
        composeRule.runOnIdle {
            clearSession()
            BrowserSessionStore(composeRule.activity).apply {
                saveFavorites(listOf(favorite))
                saveHistory(
                    listOf(
                        HistoryEntry(
                            url = "https://history.example/recent",
                            title = "Recent history",
                            lastVisitedAt = 2L,
                        ),
                    ),
                )
            }
            browserController = BrowserController(composeRule.activity)
            controller = browserController
        }
        return browserController
    }

    private fun setBrowserContent(browserController: BrowserController) {
        composeRule.setContent {
            MaterialBrowserTheme {
                BrowserScreen(browserController)
            }
        }
        composeRule.waitForIdle()
    }

    private fun clearSession() {
        InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences(
            BrowserSessionStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }
}
