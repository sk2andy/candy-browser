package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.TabStack
import dev.sk2andy.materialbrowser.browser.TabStackColor
import dev.sk2andy.materialbrowser.capsule.SiteCapsuleEditorRequest
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebsiteFeatureScreenshotInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun capturesSiteCapsuleEditor() {
        composeRule.setContent {
            MaterialBrowserTheme {
                SiteCapsuleEditorScreen(
                    request = SiteCapsuleEditorRequest(
                        existing = null,
                        sourceTabId = "design-system",
                        sourceTitle = "Design Library",
                        sourceUrl = "https://m3.material.io",
                        profiles = listOf(
                            BrowserProfile("candy", "🍬"),
                            BrowserProfile("work", "💼"),
                        ),
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

        composeRule.onNodeWithTag(SiteCapsuleTestTags.Editor).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.capsule_navigation_mode),
        ).performScrollTo()
        saveScreenshot(
            fileName = "candy-site-capsule.png",
            testTag = SiteCapsuleTestTags.Editor,
            width = 1080,
            height = 2400,
        )
    }

    @Test
    fun capturesCandyStackFolder() {
        val tabs = listOf(
            BrowserTab(
                id = "material",
                lastAccessedAt = 1L,
                title = "Material Design",
                url = "https://m3.material.io",
            ),
            BrowserTab(
                id = "compose",
                lastAccessedAt = 2L,
                title = "Jetpack Compose",
                url = "https://developer.android.com/compose",
            ),
            BrowserTab(
                id = "kotlin",
                lastAccessedAt = 3L,
                title = "Kotlin",
                url = "https://kotlinlang.org",
            ),
        )
        val previews = mapOf(
            "material" to previewBitmap("Material", Color.rgb(255, 239, 246)),
            "compose" to previewBitmap("Compose", Color.rgb(229, 236, 255)),
            "kotlin" to previewBitmap("Kotlin", Color.rgb(241, 231, 255)),
        )
        composeRule.setContent {
            MaterialBrowserTheme {
                TabStackFolderDialog(
                    stack = TabStack(
                        id = "design",
                        profileId = "candy",
                        name = "Design research",
                        color = TabStackColor.Grape,
                        tabIds = tabs.map(BrowserTab::id),
                        previewTabId = "compose",
                    ),
                    tabs = tabs,
                    mode = TabOverviewMode.Hero,
                    previews = previews,
                    favicons = emptyMap(),
                    favorites = emptyList(),
                    onSelectTab = {},
                    onPreviewTabChanged = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(TabStackTestTags.Folder).assertIsDisplayed()
        saveScreenshot(
            fileName = "candy-stacks.png",
            testTag = TabStackTestTags.Folder,
            width = 1080,
            height = 1379,
        )
        previews.values.forEach(Bitmap::recycle)
    }

    private fun saveScreenshot(
        fileName: String,
        testTag: String,
        width: Int,
        height: Int,
    ) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val outputDirectory = File(
            requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)),
            "website",
        )
        assertTrue(outputDirectory.exists() || outputDirectory.mkdirs())
        val output = File(outputDirectory, fileName)
        val captured = composeRule.onNodeWithTag(testTag).captureToImage().asAndroidBitmap()
        val screenshot = if (captured.width == width && captured.height == height) {
            captured
        } else {
            Bitmap.createScaledBitmap(captured, width, height, true)
        }
        try {
            assertEquals(width, screenshot.width)
            assertEquals(height, screenshot.height)
            FileOutputStream(output).use { stream ->
                assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        } finally {
            screenshot.recycle()
            if (captured !== screenshot) captured.recycle()
        }
        assertTrue(output.length() > 0L)
    }

    private fun previewBitmap(title: String, background: Int): Bitmap =
        Bitmap.createBitmap(540, 900, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            canvas.drawColor(background)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(37, 33, 45)
                textSize = 54f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            canvas.drawText(title, 52f, 110f, paint)
            paint.color = Color.rgb(118, 87, 220)
            canvas.drawRoundRect(52f, 160f, 488f, 184f, 12f, 12f, paint)
            paint.color = Color.argb(58, 37, 33, 45)
            repeat(6) { index ->
                val top = 244f + index * 76f
                val right = if (index % 2 == 0) 462f else 410f
                canvas.drawRoundRect(52f, top, right, top + 24f, 12f, 12f, paint)
            }
        }
}
