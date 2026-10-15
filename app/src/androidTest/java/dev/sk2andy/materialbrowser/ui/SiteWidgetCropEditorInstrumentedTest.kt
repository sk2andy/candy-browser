package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetCrop
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetFormat
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SiteWidgetCropEditorInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rowAndColumnSlidersSubmitMatchingCrop() {
        val bitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
        val saved = AtomicReference<Pair<SiteWidgetFormat, SiteWidgetCrop>?>()
        composeRule.setContent {
            MaterialTheme {
                SiteWidgetCropEditor(
                    bitmap = bitmap.asImageBitmap(),
                    onSubmit = { format, crop, _ -> saved.set(format to crop) },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(SiteWidgetCropEditorTestTags.Preview).assertIsDisplayed()
        composeRule.onNodeWithTag(SiteWidgetCropEditorTestTags.Columns)
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }
        composeRule.onNodeWithTag(SiteWidgetCropEditorTestTags.Rows)
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(6f) }
        composeRule.onNodeWithTag(SiteWidgetCropEditorTestTags.Submit).performScrollTo().performClick()

        assertEquals(SiteWidgetFormat(3, 6), saved.get()?.first)
        assertEquals(SiteWidgetCrop(0f, 0f, 1f, 1f), saved.get()?.second)
    }

    @Test
    fun zoomAndImageDragChangeSubmittedSelection() {
        val bitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
        val saved = AtomicReference<SiteWidgetCrop?>()
        composeRule.setContent {
            MaterialTheme {
                SiteWidgetCropEditor(
                    bitmap = bitmap.asImageBitmap(),
                    onSubmit = { _, crop, _ -> saved.set(crop) },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(SiteWidgetCropEditorTestTags.Zoom)
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        composeRule.onNodeWithTag(SiteWidgetCropEditorTestTags.Preview)
            .performTouchInput { swipeUp() }
        composeRule.onNodeWithTag(SiteWidgetCropEditorTestTags.Submit).performScrollTo().performClick()

        val crop = saved.get()!!
        assertEquals(0.5f, crop.width, 0.001f)
        assertEquals(0.1875f, crop.height, 0.001f)
        assertTrue(crop.top > 0.40625f)
        assertTrue(crop.top >= 0f)
    }

    @Test
    fun intervalSliderSubmitsSelectedSchedule() {
        val bitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
        val interval = AtomicReference<Int?>()
        composeRule.setContent {
            MaterialTheme {
                SiteWidgetCropEditor(
                    bitmap = bitmap.asImageBitmap(),
                    initialRefreshIntervalMinutes = 60,
                    onSubmit = { _, _, minutes -> interval.set(minutes) },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(SiteWidgetCropEditorTestTags.Interval)
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        composeRule.onNodeWithTag(SiteWidgetCropEditorTestTags.Submit).performScrollTo().performClick()

        assertEquals(180, interval.get())
    }

    @Test
    fun replacementKeepsFormatAndInitialInterval() {
        val bitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
        val format = AtomicReference<SiteWidgetFormat?>()
        val interval = AtomicReference<Int?>()
        composeRule.setContent {
            MaterialTheme {
                SiteWidgetCropEditor(
                    bitmap = bitmap.asImageBitmap(),
                    initialFormat = SiteWidgetFormat(2, 5),
                    formatChangeEnabled = false,
                    initialRefreshIntervalMinutes = 30,
                    onSubmit = { selected, _, minutes ->
                        format.set(selected)
                        interval.set(minutes)
                    },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(SiteWidgetCropEditorTestTags.Columns).assertIsNotEnabled()
        composeRule.onNodeWithTag(SiteWidgetCropEditorTestTags.Rows).assertIsNotEnabled()
        composeRule.onNodeWithTag(SiteWidgetCropEditorTestTags.Submit).performScrollTo().performClick()

        assertEquals(SiteWidgetFormat(2, 5), format.get())
        assertEquals(30, interval.get())
    }

    @Test
    fun savingDisablesChangesAndRepeatedSubmission() {
        val bitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
        composeRule.setContent {
            MaterialTheme {
                SiteWidgetCropEditor(
                    bitmap = bitmap.asImageBitmap(),
                    saving = true,
                    onSubmit = { _, _, _ -> error("Unexpected submit while saving") },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(SiteWidgetCropEditorTestTags.Submit).assertIsNotEnabled()
        composeRule.onNodeWithTag(SiteWidgetCropEditorTestTags.Size).assertIsNotEnabled()
        composeRule.onNodeWithTag(SiteWidgetCropEditorTestTags.Columns).assertIsNotEnabled()
        composeRule.onNodeWithTag(SiteWidgetCropEditorTestTags.Rows).assertIsNotEnabled()
        composeRule.onNodeWithTag(SiteWidgetCropEditorTestTags.Interval).assertIsNotEnabled()
    }
}
