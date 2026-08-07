package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.blocking.BlockerSettings
import dev.sk2andy.materialbrowser.blocking.PrivacyDomainSummary
import dev.sk2andy.materialbrowser.blocking.PrivacyPartyRelation
import dev.sk2andy.materialbrowser.blocking.PrivacyRequestCategory
import dev.sk2andy.materialbrowser.blocking.PrivacyXRaySnapshot
import dev.sk2andy.materialbrowser.blocking.SiteProtectionState
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyXRaySheetInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun counterHasAccessibleTouchTarget() {
        val clicks = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                PrivacyXRayBadge(blockedCount = 7, onClick = clicks::incrementAndGet)
            }
        }

        composeRule.onNodeWithTag(PrivacyXRayTestTags.Counter)
            .assertExists()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals(1, clicks.get())
    }

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
    fun incognitoPauseWarningOffersTemporaryExceptionOnly() {
        val temporaryPauses = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                PrivacyXRaySheet(
                    snapshot = sampleSnapshot(),
                    blockerSettings = BlockerSettings(),
                    siteState = SiteProtectionState(
                        host = "private.example",
                        canPersist = false,
                    ),
                    onPause = { persistently ->
                        if (!persistently) temporaryPauses.incrementAndGet()
                    },
                    onResume = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(PrivacyXRayTestTags.Pause).performScrollTo().performClick()
        composeRule.onNodeWithTag(PrivacyXRayTestTags.Warning).assertExists()
        composeRule.onNodeWithTag(PrivacyXRayTestTags.PausePersistent).assertDoesNotExist()
        composeRule.onNodeWithTag(PrivacyXRayTestTags.PauseTemporary).performClick()

        assertEquals(1, temporaryPauses.get())
    }

    @Test
    fun domainOptionsReflectStateAndRouteBothChanges() {
        val cookieChanges = AtomicInteger()
        val scrollChanges = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                PrivacyXRayContent(
                    snapshot = sampleSnapshot(),
                    blockerSettings = BlockerSettings(),
                    siteState = SiteProtectionState(
                        host = "news.example",
                        cookieBannerRemovalDisabled = true,
                        forceVerticalScrolling = false,
                        canPersist = true,
                    ),
                    onPauseClick = {},
                    onResumeClick = {},
                    onCookieBannerRemovalEnabledChange = { enabled ->
                        if (enabled) cookieChanges.incrementAndGet()
                    },
                    onForceVerticalScrollingChange = { enabled ->
                        if (enabled) scrollChanges.incrementAndGet()
                    },
                )
            }
        }

        val siteOptionsTop = composeRule
            .onNodeWithTag(PrivacyXRayTestTags.SiteOptionsTitle)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val xRayTop = composeRule
            .onNodeWithTag(PrivacyXRayTestTags.XRayTitle)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val cookieToggleTop = composeRule
            .onNodeWithTag(PrivacyXRayTestTags.CookieBannerRemoval)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val scrollToggleTop = composeRule
            .onNodeWithTag(PrivacyXRayTestTags.ForceVerticalScrolling)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertTrue(siteOptionsTop < cookieToggleTop)
        assertTrue(cookieToggleTop < scrollToggleTop)
        assertTrue(scrollToggleTop < xRayTop)

        composeRule.onNodeWithTag(PrivacyXRayTestTags.CookieBannerRemoval)
            .performScrollTo()
            .assertIsEnabled()
            .assertIsOff()
            .performClick()
        composeRule.onNodeWithTag(PrivacyXRayTestTags.ForceVerticalScrolling)
            .performScrollTo()
            .assertIsEnabled()
            .assertIsOff()
            .performClick()

        assertEquals(1, cookieChanges.get())
        assertEquals(1, scrollChanges.get())
    }

    @Test
    fun cookieDomainOptionIsDisabledWhenGlobalProtectionIsOff() {
        composeRule.setContent {
            MaterialBrowserTheme {
                PrivacyXRayContent(
                    snapshot = sampleSnapshot(),
                    blockerSettings = BlockerSettings(hideCookieConsent = false),
                    siteState = SiteProtectionState(host = "news.example"),
                    onPauseClick = {},
                    onResumeClick = {},
                )
            }
        }

        composeRule.onNodeWithTag(PrivacyXRayTestTags.CookieBannerRemoval)
            .performScrollTo()
            .assertIsNotEnabled()
            .assertIsOff()
        composeRule.onNodeWithTag(PrivacyXRayTestTags.ForceVerticalScrolling)
            .performScrollTo()
            .assertIsEnabled()
            .assertIsOff()
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
