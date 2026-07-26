package dev.sk2andy.materialbrowser.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.capsule.SiteCapsule
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SiteCapsuleScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        composeRule.runOnIdle { controller?.destroy() }
    }

    @Test
    fun launcherCapsuleShowsFocusedWebViewAndReducedChrome() {
        lateinit var browserController: BrowserController
        lateinit var capsule: SiteCapsule
        composeRule.runOnIdle {
            browserController = BrowserController(composeRule.activity)
            capsule = SiteCapsule(
                id = "04a74ad8-7533-460c-bfbf-a135968940d5",
                name = "Example Capsule",
                startUrl = "https://example.com",
                profileId = browserController.activeProfileId,
                createdAtMillis = 1L,
                updatedAtMillis = 1L,
            )
            browserController.siteCapsules += capsule
            check(browserController.openSiteCapsule(capsule.id))
            controller = browserController
        }
        composeRule.setContent {
            MaterialTheme { SiteCapsuleBrowserScreen(browserController, capsule) }
        }

        composeRule.onNodeWithTag(SiteCapsuleTestTags.Screen).assertIsDisplayed()
        composeRule.onNodeWithTag(SiteCapsuleTestTags.WebView).assertIsDisplayed()
    }
}
