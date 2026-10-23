package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.CandyTrail
import dev.sk2andy.materialbrowser.browser.CandyTrailNode
import dev.sk2andy.materialbrowser.constellations.ConstellationDocument
import dev.sk2andy.materialbrowser.constellations.ConstellationSnapshot
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.data.BrowserColorPalette
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Deterministic dense fixtures for reviewing graph hierarchy and clipping on a real device. */
@RunWith(AndroidJUnit4::class)
class GraphPolishVisualInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun constellationTopicsRenderInCandyLightTheme() {
        val topics = listOf(
            listOf("Kotlin coroutines", "Kotlin structured concurrency", "Kotlin flows", "Kotlin testing"),
            listOf("Garden herbs", "Garden planting", "Garden soil", "Garden watering"),
            listOf("Travel Kyoto", "Travel Osaka", "Travel Tokyo", "Travel Japan by train"),
        )
        val documents = topics.flatMapIndexed { group, titles ->
            titles.mapIndexed { index, title ->
                ConstellationDocument(
                    profileId = "visual",
                    url = "https://topic$group.example/article-$index",
                    title = title,
                    excerpt = "A saved page about $title, available locally in this profile.",
                    visitedAt = (group * titles.size + index).toLong(),
                    embedding = List(topics.size) { if (it == group) 1f else 0f },
                )
            }
        }
        composeRule.setContent {
            MaterialBrowserTheme(
                AppearanceSettings(
                    appearanceMode = BrowserAppearanceMode.Light,
                    colorPalette = BrowserColorPalette.Candy,
                ),
            ) {
                ConstellationScreen(
                    profileName = "Personal",
                    snapshot = ConstellationSnapshot(
                        profileId = "visual", enabled = true, modelReady = true, documents = documents,
                    ),
                    onEnable = {},
                    onDisable = {},
                    onDownloadModel = {},
                    onOpenPage = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasTestTag(ConstellationTestTags.Graph)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(ConstellationTestTags.Graph).assertIsDisplayed()
        saveScreenshot("constellations-light")
        composeRule.onNodeWithTag(ConstellationTestTags.Expand).performClick()
        saveScreenshot("constellations-expanded")
        composeRule.onNodeWithTag(ConstellationTestTags.ListToggle).performClick()
        composeRule.onNodeWithTag(ConstellationTestTags.PageList)
            .performScrollToNode(hasTestTag(ConstellationTestTags.page(documents.first().url)))
        composeRule.onNodeWithTag(ConstellationTestTags.page(documents.first().url)).performClick()
        composeRule.onNodeWithTag(ConstellationTestTags.Details).assertIsDisplayed()
        composeRule.onNodeWithTag(ConstellationTestTags.Graph).assertIsDisplayed()
        saveScreenshot("constellations-details")
    }

    @Test
    fun branchingTrailRendersInCandyDarkTheme() {
        val titles = listOf(
            "Discover Android", "Kotlin language", "Compose layouts", "Material Expressive",
            "Motion and interaction", "Accessible components", "Color and typography",
        )
        val parents = listOf(null, "n0", "n1", "n2", "n3", "n2", "n3")
        val tab = BrowserTab(id = "visual", lastAccessedAt = 7L, title = titles[4])
        val trail = CandyTrail(
            tabId = tab.id,
            nodes = titles.mapIndexed { index, title ->
                CandyTrailNode("n$index", parents[index], "https://android.example/$index", title, index.toLong())
            },
            currentNodeId = "n4",
            nextOrdinal = titles.size.toLong(),
        )
        composeRule.setContent {
            MaterialBrowserTheme(
                AppearanceSettings(
                    appearanceMode = BrowserAppearanceMode.Dark,
                    colorPalette = BrowserColorPalette.Candy,
                ),
            ) {
                CandyTrailScreen(
                    tab = tab,
                    trail = trail,
                    favicon = null,
                    forkFavicons = emptyMap(),
                    predictiveBackProgress = 0f,
                    predictiveBackEdgeSign = 1,
                    onSelectNode = { false },
                    onNodeSelectionFinished = {},
                    onForkNode = { null },
                    onForkCreationFinished = {},
                    onSelectFork = { null },
                    onForkSelectionFinished = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()
        saveScreenshot("trails-dark")
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.candy_trail_current_page))
            .performClick()
        saveScreenshot("trails-current")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.candy_trail_list_view)).performClick()
        composeRule.onNodeWithText(titles.last()).assertIsDisplayed()
        saveScreenshot("trails-list")
    }

    private fun saveScreenshot(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        assertTrue("Graph must occupy the available screen", bitmap.width > 0 && bitmap.height > 0)
        val output = File(composeRule.activity.getExternalFilesDir(null), "graph-polish/$name.png")
        check(output.parentFile!!.mkdirs() || output.parentFile!!.isDirectory)
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
