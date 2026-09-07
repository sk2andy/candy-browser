package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.capsule.SiteCapsule
import dev.sk2andy.materialbrowser.capsule.CapsuleChromeMode
import dev.sk2andy.materialbrowser.capsule.CapsuleIconColor
import dev.sk2andy.materialbrowser.capsule.CapsuleIconMode
import dev.sk2andy.materialbrowser.capsule.SiteCapsuleEditorRequest
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
        composeRule.onNodeWithTag(SiteCapsuleTestTags.WebContent).assertIsDisplayed()
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

        composeRule.onNodeWithTag(SiteCapsuleTestTags.WebContent).assertIsDisplayed()
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
        composeRule.onNodeWithTag(SiteCapsuleTestTags.iconEmojiQuickPick("⭐"))
            .performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertContentDescriptionEquals(
                composeRule.activity.getString(R.string.capsule_icon_emoji_option, "⭐"),
            )
            .performClick()
            .assertIsSelected()
        composeRule.onNodeWithTag(SiteCapsuleTestTags.IconEmoji)
            .performScrollTo()
            .performTextReplacement("🚀")
        composeRule.onNodeWithTag(SiteCapsuleTestTags.iconColor(CapsuleIconColor.Sky))
            .performScrollTo()
            .performClick()
            .assertIsSelected()
        composeRule.onNodeWithTag(SiteCapsuleTestTags.Save)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals("source-tab", submission.get()?.sourceTabId)
        assertEquals("Example Capsule", submission.get()?.name)
        assertEquals("https://example.com", submission.get()?.startUrl)
        assertEquals("🚀", submission.get()?.iconEmoji)
        assertEquals(CapsuleIconColor.Sky, submission.get()?.iconColor)
    }

    @Test
    fun legacyRenderedFaviconIsPreservedUntilCustomizationChoosesEmojiFallback() {
        val submission = AtomicReference<dev.sk2andy.materialbrowser.capsule.SiteCapsuleEditorSubmission?>()
        val renderedIcon = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.MAGENTA)
        }
        composeRule.setContent {
            MaterialTheme {
                SiteCapsuleEditorScreen(
                    request = SiteCapsuleEditorRequest(
                        existing = SiteCapsule(
                            id = "04a74ad8-7533-460c-bfbf-a135968940d5",
                            name = "Legacy",
                            startUrl = "https://legacy.example",
                            profileId = "candy",
                            createdAtMillis = 1L,
                            updatedAtMillis = 2L,
                        ),
                        sourceTabId = null,
                        sourceTitle = "",
                        sourceUrl = "",
                        profiles = listOf(BrowserProfile("candy", "🍬")),
                        activeProfileId = "candy",
                        profileIsolationSupported = true,
                        pinningSupported = true,
                        canCreate = true,
                        canCreateDedicatedProfile = true,
                        previewIcon = renderedIcon,
                        previewIconIsRendered = true,
                    ),
                    onSubmit = submission::set,
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(SiteCapsuleTestTags.iconColor(CapsuleIconColor.Sky))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(SiteCapsuleTestTags.Save)
            .performScrollTo()
            .performClick()

        assertEquals(CapsuleIconMode.ProfileFallback, submission.get()?.iconMode)
        assertEquals(CapsuleIconColor.Sky, submission.get()?.iconColor)
        assertNull(submission.get()?.sourceFavicon)
    }

    @Test
    fun editorRestoresCapsuleOwnedIconCustomization() {
        composeRule.setContent {
            MaterialTheme {
                SiteCapsuleEditorScreen(
                    request = SiteCapsuleEditorRequest(
                        existing = SiteCapsule(
                            id = "04a74ad8-7533-460c-bfbf-a135968940d5",
                            name = "Mail",
                            startUrl = "https://mail.example",
                            profileId = "candy",
                            iconEmoji = "📬",
                            iconColor = CapsuleIconColor.Charcoal,
                            createdAtMillis = 1L,
                            updatedAtMillis = 2L,
                        ),
                        sourceTabId = null,
                        sourceTitle = "",
                        sourceUrl = "",
                        profiles = listOf(BrowserProfile("candy", "🍬")),
                        activeProfileId = "candy",
                        profileIsolationSupported = true,
                        pinningSupported = true,
                        canCreate = true,
                        canCreateDedicatedProfile = true,
                        previewIcon = null,
                    ),
                    onSubmit = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(SiteCapsuleTestTags.IconEmoji)
            .performScrollTo()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.EditableText,
                    AnnotatedString("📬"),
                ),
            )
        composeRule.onNodeWithTag(SiteCapsuleTestTags.iconColor(CapsuleIconColor.Charcoal))
            .performScrollTo()
            .assertIsSelected()
    }

    @Test
    fun editorSubmitsCustomIconAndOpensCropControls() {
        val submission = AtomicReference<dev.sk2andy.materialbrowser.capsule.SiteCapsuleEditorSubmission?>()
        val editRequests = java.util.concurrent.atomic.AtomicInteger()
        val customIcon = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        composeRule.setContent {
            MaterialTheme {
                SiteCapsuleEditorScreen(
                    request = SiteCapsuleEditorRequest(
                        existing = null,
                        sourceTabId = "source-tab",
                        sourceTitle = "Custom Capsule",
                        sourceUrl = "https://custom.example",
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
                    customIcon = customIcon,
                    customIconRevision = 1,
                    onEditCustomIcon = { editRequests.incrementAndGet() },
                )
            }
        }

        composeRule.onNodeWithTag(SiteCapsuleTestTags.CustomIcon)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(SiteCapsuleTestTags.EditCustomIcon)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(SiteCapsuleTestTags.Save)
            .performScrollTo()
            .performClick()

        assertEquals(1, editRequests.get())
        assertEquals(CapsuleIconMode.Custom, submission.get()?.iconMode)
        assertSame(customIcon, submission.get()?.customIcon)

    }

    @Test
    fun retainedCustomIconDoesNotOverrideSelectedFallbackMode() {
        val submission = AtomicReference<dev.sk2andy.materialbrowser.capsule.SiteCapsuleEditorSubmission?>()
        val customIcon = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        composeRule.setContent {
            MaterialTheme {
                SiteCapsuleEditorScreen(
                    request = SiteCapsuleEditorRequest(
                        existing = SiteCapsule(
                            id = "04a74ad8-7533-460c-bfbf-a135968940d5",
                            name = "Fallback",
                            startUrl = "https://fallback.example",
                            profileId = "candy",
                            iconMode = CapsuleIconMode.ProfileFallback,
                            createdAtMillis = 1L,
                            updatedAtMillis = 2L,
                        ),
                        sourceTabId = null,
                        sourceTitle = "",
                        sourceUrl = "",
                        profiles = listOf(BrowserProfile("candy", "🍬")),
                        activeProfileId = "candy",
                        profileIsolationSupported = true,
                        pinningSupported = true,
                        canCreate = true,
                        canCreateDedicatedProfile = true,
                        previewIcon = null,
                        customIcon = customIcon,
                    ),
                    onSubmit = submission::set,
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(SiteCapsuleTestTags.Save)
            .performScrollTo()
            .performClick()

        assertEquals(CapsuleIconMode.ProfileFallback, submission.get()?.iconMode)
        assertSame(customIcon, submission.get()?.customIcon)
    }
}
