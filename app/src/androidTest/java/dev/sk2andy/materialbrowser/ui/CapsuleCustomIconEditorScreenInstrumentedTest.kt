package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.capsule.CapsuleIconCrop
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapsuleCustomIconEditorScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun cropPreviewProvidesZoomReplaceAndSaveControls() {
        val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val saved = AtomicReference<CapsuleIconCrop?>()
        composeRule.setContent {
            MaterialTheme {
                CapsuleCustomIconEditorScreen(
                    bitmap = bitmap.asImageBitmap(),
                    loading = false,
                    errorMessage = null,
                    onChooseImage = {},
                    onSave = saved::set,
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(CapsuleCustomIconEditorTestTags.Preview).assertIsDisplayed()
        composeRule.onNodeWithTag(CapsuleCustomIconEditorTestTags.Zoom).assertIsDisplayed()
        composeRule.onNodeWithTag(CapsuleCustomIconEditorTestTags.ChooseIconPack)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(CapsuleCustomIconEditorTestTags.Save)
            .assertIsDisplayed()
            .performClick()

        assertEquals(CapsuleIconCrop(), saved.get())
    }
}
