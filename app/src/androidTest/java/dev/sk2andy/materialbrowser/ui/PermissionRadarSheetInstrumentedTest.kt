package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.permissions.PermissionPrompt
import dev.sk2andy.materialbrowser.browser.permissions.PermissionPromptChoice
import dev.sk2andy.materialbrowser.browser.permissions.PermissionRadarEntry
import dev.sk2andy.materialbrowser.browser.permissions.PermissionRadarSnapshot
import dev.sk2andy.materialbrowser.browser.permissions.PermissionSiteKey
import dev.sk2andy.materialbrowser.browser.permissions.SitePermission
import dev.sk2andy.materialbrowser.browser.permissions.SitePermissionActivity
import dev.sk2andy.materialbrowser.browser.permissions.SitePermissionDecision
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionRadarSheetInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val site = PermissionSiteKey("personal", "https://example.com")

    @Test
    fun sheetShowsOriginActivityAndRoutesDecision() {
        val changed = AtomicReference<Pair<SitePermission, SitePermissionDecision>?>()
        composeRule.setContent {
            MaterialBrowserTheme {
                PermissionRadarSheet(
                    snapshot = PermissionRadarSnapshot(
                        site = site,
                        isPrivate = false,
                        knownOrigins = listOf(site.origin),
                        entries = listOf(
                            PermissionRadarEntry(
                                permission = SitePermission.Camera,
                                decision = SitePermissionDecision.Ask,
                                allowedForSession = false,
                                activity = SitePermissionActivity.Pending,
                            ),
                        ),
                    ),
                    profileEmoji = "🍬",
                    onOriginSelected = {},
                    onDecisionChanged = { permission, decision ->
                        changed.set(permission to decision)
                    },
                    onResetSite = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(PermissionRadarTestTags.Sheet).assertExists()
        composeRule.onNodeWithText(site.origin).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.permission_radar_pending)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.permission_decision_allow))
            .assertHasClickAction()
            .performClick()

        assertEquals(
            SitePermission.Camera to SitePermissionDecision.Allow,
            changed.get(),
        )
    }

    @Test
    fun requestDialogOffersOneTimeChoice() {
        val selected = AtomicReference<PermissionPromptChoice?>()
        composeRule.setContent {
            MaterialBrowserTheme {
                PermissionPromptDialog(
                    prompt = PermissionPrompt(
                        id = 1L,
                        tabId = "tab-a",
                        site = site,
                        permissions = setOf(SitePermission.Microphone),
                        isPrivate = false,
                    ),
                    onChoice = selected::set,
                )
            }
        }

        composeRule.onNodeWithTag(PermissionRadarTestTags.Prompt).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.permission_radar_allow_once))
            .performClick()

        assertEquals(PermissionPromptChoice.AllowOnce, selected.get())
    }
}
