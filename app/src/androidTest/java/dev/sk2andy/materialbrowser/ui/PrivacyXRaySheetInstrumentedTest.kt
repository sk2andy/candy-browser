package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.blocking.BlockerSettings
import dev.sk2andy.materialbrowser.blocking.PrivacyDomainSummary
import dev.sk2andy.materialbrowser.blocking.PrivacyPartyRelation
import dev.sk2andy.materialbrowser.blocking.PrivacyRequestCategory
import dev.sk2andy.materialbrowser.blocking.PrivacyXRaySnapshot
import dev.sk2andy.materialbrowser.blocking.SiteProtectionState
import dev.sk2andy.materialbrowser.browser.SiteConnectionKind
import dev.sk2andy.materialbrowser.browser.permissions.PermissionRadarEntry
import dev.sk2andy.materialbrowser.browser.permissions.PermissionRadarSnapshot
import dev.sk2andy.materialbrowser.browser.permissions.PermissionSiteKey
import dev.sk2andy.materialbrowser.browser.permissions.SitePermission
import dev.sk2andy.materialbrowser.browser.permissions.SitePermissionActivity
import dev.sk2andy.materialbrowser.browser.permissions.SitePermissionDecision
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import eightbitlab.com.blurview.BlurTarget
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyXRaySheetInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsCounterConsumesTapAndRoutesPrivacyXRay() {
        val addressClicks = AtomicInteger()
        val privacyClicks = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = addressClicks::incrementAndGet),
                ) {
                    PrivacyXRaySettingsCounter(
                        blockedCount = 7,
                        onClick = privacyClicks::incrementAndGet,
                    )
                }
            }
        }

        composeRule.onNodeWithTag(PrivacyXRayTestTags.SettingsCounter)
            .assertExists()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals(1, privacyClicks.get())
        assertEquals(0, addressClicks.get())
    }

    @Test
    fun xRayContentExpandsDomainsAndRoutesPauseAction() {
        val pauses = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                PrivacyXRayContent(
                    pageUrl = "https://news.example/article",
                    connectionKind = SiteConnectionKind.Https,
                    snapshot = sampleSnapshot(),
                    blockerSettings = BlockerSettings(),
                    siteState = SiteProtectionState(
                        host = "news.example",
                        canPersist = false,
                    ),
                    onPauseClick = pauses::incrementAndGet,
                    onResumeClick = {},
                )
            }
        }

        composeRule.onNodeWithTag(PrivacyXRayTestTags.Connection).assertExists()
        composeRule.onNodeWithText(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(
                R.string.site_connection_https,
            ),
        ).assertExists()
        composeRule.onNodeWithTag(PrivacyXRayTestTags.Total).assertExists()
        composeRule.onNodeWithText("four.example").assertDoesNotExist()
        composeRule.onNodeWithTag(PrivacyXRayTestTags.ToggleDetails)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("four.example").performScrollTo().assertExists()

        composeRule.onNodeWithTag(PrivacyXRayTestTags.Pause).performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(1, pauses.get())
    }

    @Test
    fun siteSheetKeepsPrivacyAndPermissionsBehindOneEntry() {
        val changed = AtomicReference<Pair<SitePermission, SitePermissionDecision>?>()
        val siteDataRequests = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                PrivacyXRaySheet(
                    pageUrl = "https://news.example/article",
                    connectionKind = SiteConnectionKind.Https,
                    snapshot = sampleSnapshot(),
                    blockerSettings = BlockerSettings(),
                    siteState = SiteProtectionState(host = "news.example"),
                    permissionSnapshot = PermissionRadarSnapshot(
                        site = PermissionSiteKey("personal", "https://news.example"),
                        isPrivate = false,
                        knownOrigins = listOf("https://news.example"),
                        entries = listOf(
                            PermissionRadarEntry(
                                permission = SitePermission.Camera,
                                decision = SitePermissionDecision.Ask,
                                allowedForSession = false,
                                activity = SitePermissionActivity.Idle,
                            ),
                        ),
                    ),
                    profileEmoji = "🍬",
                    websiteNotificationsSupported = true,
                    onPause = {},
                    onResume = {},
                    onPermissionOriginSelected = {},
                    onPermissionDecisionChanged = { permission, decision ->
                        changed.set(permission to decision)
                    },
                    onResetSitePermissions = {},
                    onClearSiteData = siteDataRequests::incrementAndGet,
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(PrivacyXRayTestTags.ClearSiteData).performClick()
        assertEquals(1, siteDataRequests.get())
        composeRule.onNodeWithTag(PrivacyXRayTestTags.Total).assertExists()
        composeRule.onNodeWithTag(PrivacyXRayTestTags.PermissionsTab).performClick()
        composeRule.onNodeWithText("https://news.example").assertExists()
        composeRule.onNodeWithText(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(
                R.string.permission_decision_allow,
            ),
        ).performScrollTo().performClick()
        assertEquals(SitePermission.Camera to SitePermissionDecision.Allow, changed.get())
        composeRule.onNodeWithTag(PrivacyXRayTestTags.XRayTab).performClick()
        composeRule.onNodeWithTag(PrivacyXRayTestTags.Total).assertExists()
    }

    @Test
    fun incognitoPauseWarningOffersTemporaryExceptionOnly() {
        val temporaryPauses = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme(
                settings = AppearanceSettings(surfaceStyle = BrowserSurfaceStyle.Frosted),
            ) {
                var blurTarget by remember { mutableStateOf<BlurTarget?>(null) }
                Box(Modifier.fillMaxSize()) {
                    BrowserContentBlurTarget(
                        enabled = true,
                        onTargetAttached = { blurTarget = it },
                        onTargetReleased = { if (blurTarget === it) blurTarget = null },
                        modifier = Modifier.fillMaxSize(),
                    ) {}
                    PrivacyXRaySheet(
                        pageUrl = "https://private.example",
                        connectionKind = SiteConnectionKind.Https,
                        snapshot = sampleSnapshot(),
                        blockerSettings = BlockerSettings(),
                        siteState = SiteProtectionState(
                            host = "private.example",
                            canPersist = false,
                        ),
                        permissionSnapshot = PermissionRadarSnapshot.Empty,
                        profileEmoji = "🍬",
                        websiteNotificationsSupported = true,
                        backdropSource = blurTarget.asCandyChromeBackdropSource(),
                        onPause = { persistently ->
                            if (!persistently) temporaryPauses.incrementAndGet()
                        },
                        onResume = {},
                        onPermissionOriginSelected = {},
                        onPermissionDecisionChanged = { _, _ -> },
                        onResetSitePermissions = {},
                        onDismiss = {},
                    )
                }
            }
        }

        composeRule.onAllNodesWithTag(BrowserChromeSurfaceTestTags.BackdropBlur)
            .assertCountEquals(2)
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
            .assertCountEquals(1)
        composeRule.onNodeWithTag(PrivacyXRayTestTags.Pause).performScrollTo().performClick()
        composeRule.onNodeWithTag(PrivacyXRayTestTags.Warning).assertExists()
        composeRule.onNodeWithTag(PrivacyXRayTestTags.PausePersistent).assertDoesNotExist()
        composeRule.onNodeWithTag(PrivacyXRayTestTags.PauseTemporary).performClick()

        assertEquals(1, temporaryPauses.get())
    }

    private fun sampleSnapshot() = PrivacyXRaySnapshot(
        totalBlocked = 10,
        categoryCounts = mapOf(
            PrivacyRequestCategory.Advertising to 6,
            PrivacyRequestCategory.Other to 4,
        ),
        partyCounts = mapOf(PrivacyPartyRelation.Unknown to 10),
        domains = listOf(
            domain("one.example", 4),
            domain("two.example", 3),
            domain("three.example", 2),
            domain("four.example", 1),
        ),
    )

    private fun domain(host: String, count: Int) = PrivacyDomainSummary(
        host = host,
        blockedCount = count,
        category = PrivacyRequestCategory.Other,
        partyRelation = PrivacyPartyRelation.Unknown,
    )
}
