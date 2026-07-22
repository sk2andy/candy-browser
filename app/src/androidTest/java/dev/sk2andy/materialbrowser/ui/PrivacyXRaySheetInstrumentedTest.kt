package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
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
        composeRule.onNodeWithTag(PrivacyXRayTestTags.ToggleDetails).performClick()
        composeRule.onNodeWithText("four.example").assertExists()

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
