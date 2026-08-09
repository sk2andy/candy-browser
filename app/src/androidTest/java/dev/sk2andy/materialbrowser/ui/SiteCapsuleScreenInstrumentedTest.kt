package dev.sk2andy.materialbrowser.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.capsule.SiteCapsule
import dev.sk2andy.materialbrowser.capsule.CapsuleChromeMode
import dev.sk2andy.materialbrowser.capsule.SiteCapsuleEditorRequest
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
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

    @Test
    fun noControlsCapsuleShowsOnlyWebView() {
        lateinit var browserController: BrowserController
        lateinit var capsule: SiteCapsule
        composeRule.runOnIdle {
            browserController = BrowserController(composeRule.activity)
            capsule = SiteCapsule(
                id = "d56094fd-e8fb-49d9-aac8-9b85a99c759f",
                name = "Immersive Capsule",
                startUrl = "https://example.com",
                profileId = browserController.activeProfileId,
                chromeMode = CapsuleChromeMode.NoControls,
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

        composeRule.onNodeWithTag(SiteCapsuleTestTags.WebView).assertIsDisplayed()
        composeRule.onNodeWithTag(SiteCapsuleTestTags.Chrome).assertDoesNotExist()
    }

    @Test
    fun editorScreenReturnsSubmissionForActivityResult() {
        val submission = AtomicReference<dev.sk2andy.materialbrowser.capsule.SiteCapsuleEditorSubmission?>()
        composeRule.setContent {
            MaterialTheme {
                SiteCapsuleEditorScreen(
                    request = SiteCapsuleEditorRequest(
                        existing = null,
                        sourceTabId = "source-tab",
                        sourceTitle = "Example Capsule",
                        sourceUrl = "https://example.com",
                        profiles = listOf(BrowserProfile("candy", "🍬")),
                        activeProfileId = "candy",
                        profileIsolationSupported = true,
                        pinningSupported = true,
                        canCreate = true,
                        canCreateDedicatedProfile = true,
                        previewIcon = null,
                    ),
                    onSubmit = submission::set,
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(SiteCapsuleTestTags.Editor).assertIsDisplayed()
        composeRule.onNodeWithTag(SiteCapsuleTestTags.Save)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals("source-tab", submission.get()?.sourceTabId)
        assertEquals("Example Capsule", submission.get()?.name)
        assertEquals("https://example.com", submission.get()?.startUrl)
    }
}
