package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.legal.CandyLegalSources
import dev.sk2andy.materialbrowser.legal.ThirdPartyComponent
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutLegalSectionInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sectionOffersThreeAccessibleDestinations() {
        composeRule.setContent {
            MaterialBrowserTheme {
                AboutLegalSection(onOpenUrl = {})
            }
        }

        listOf(
            AboutLegalTestTags.Imprint,
            AboutLegalTestTags.OpenSource,
            AboutLegalTestTags.Uassets,
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag)
                .assertExists()
                .assertHasClickAction()
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun imprintOpensVerifiedGitHubProfile() {
        val openedUrl = AtomicReference<String>()
        composeRule.setContent {
            MaterialBrowserTheme {
                AboutLegalSection(onOpenUrl = openedUrl::set)
            }
        }

        composeRule.onNodeWithTag(AboutLegalTestTags.Imprint).performClick()
        composeRule.onNodeWithTag(AboutLegalTestTags.ImprintDialog).assertExists()
        composeRule.onNodeWithTag(AboutLegalTestTags.GitHubLink).performClick()

        assertEquals(CandyLegalSources.GITHUB_PROFILE_URL, openedUrl.get())
    }

    @Test
    fun licenseAndUassetsDialogsRoutePinnedLegalLinks() {
        val openedUrl = AtomicReference<String>()
        composeRule.setContent {
            MaterialBrowserTheme {
                AboutLegalSection(onOpenUrl = openedUrl::set)
            }
        }

        composeRule.onNodeWithTag(AboutLegalTestTags.OpenSource).performClick()
        composeRule.onNodeWithTag(AboutLegalTestTags.OpenSourceDialog).assertExists()
        composeRule.onNodeWithTag(
            AboutLegalTestTags.licenseLink(ThirdPartyComponent.Uassets),
            useUnmergedTree = true,
        ).performScrollTo().performClick()
        assertEquals(CandyLegalSources.UASSETS_LICENSE_URL, openedUrl.get())

        composeRule.onNodeWithTag(AboutLegalTestTags.Uassets).performClick()
        composeRule.onNodeWithTag(AboutLegalTestTags.UassetsDialog).assertExists()
        composeRule.onNodeWithTag(AboutLegalTestTags.UassetsSourceLink).performClick()
        assertEquals(CandyLegalSources.UASSETS_SOURCE_URL, openedUrl.get())
    }

    @Test
    fun bundledNoticesRemainReadableOffline() {
        composeRule.setContent {
            MaterialBrowserTheme {
                AboutLegalSection(onOpenUrl = {})
            }
        }

        composeRule.onNodeWithTag(AboutLegalTestTags.OpenSource).performClick()
        composeRule.onNodeWithTag(
            AboutLegalTestTags.BundledNoticesLink,
            useUnmergedTree = true,
        ).performScrollTo().performClick()
        composeRule.onNodeWithTag(AboutLegalTestTags.BundledNoticesDialog).assertExists()
    }
}
