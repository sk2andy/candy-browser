package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.ProfileWallpaper
import dev.sk2andy.materialbrowser.browser.ProfileWallpaperTarget
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileWallpaperEditorScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var bitmap: Bitmap? = null

    @After
    fun tearDown() {
        bitmap?.recycle()
    }

    @Test
    fun editorShowsCropControlsAndReturnsTransform() {
        val saved = AtomicReference<ProfileWallpaper?>()
        bitmap = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888)
        composeRule.setContent {
            MaterialTheme {
                ProfileWallpaperEditorScreen(
                    bitmap = requireNotNull(bitmap).asImageBitmap(),
                    wallpaperTarget = ProfileWallpaperTarget.TabSwitcher,
                    initialWallpaper = ProfileWallpaper(zoom = 1.5f),
                    hasStoredWallpaper = false,
                    loading = false,
                    errorMessage = null,
                    onChooseImage = {},
                    onSave = saved::set,
                    onRemove = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(ProfileWallpaperEditorTestTags.Screen).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.profile_tab_switcher_wallpaper_title),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileWallpaperEditorTestTags.Zoom).assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileWallpaperEditorTestTags.Save)
            .assertIsDisplayed()
            .performClick()

        assertEquals(1.5f, saved.get()?.zoom ?: 0f, 0f)
    }

    @Test
    fun storedWallpaperCanBeRemoved() {
        val removed = AtomicBoolean(false)
        bitmap = Bitmap.createBitmap(120, 200, Bitmap.Config.ARGB_8888)
        composeRule.setContent {
            MaterialTheme {
                ProfileWallpaperEditorScreen(
                    bitmap = requireNotNull(bitmap).asImageBitmap(),
                    initialWallpaper = ProfileWallpaper(),
                    hasStoredWallpaper = true,
                    loading = false,
                    errorMessage = null,
                    onChooseImage = {},
                    onSave = {},
                    onRemove = { removed.set(true) },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(ProfileWallpaperEditorTestTags.Remove)
            .assertIsDisplayed()
            .performClick()

        assertTrue(removed.get())
    }

    @Test
    fun recompositionKeepsTransformAndLoadingDisablesEditing() {
        val saved = AtomicReference<ProfileWallpaper?>()
        val loading = mutableStateOf(false)
        bitmap = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888)
        val imageBitmap = requireNotNull(bitmap).asImageBitmap()
        composeRule.setContent {
            MaterialTheme {
                ProfileWallpaperEditorScreen(
                    bitmap = imageBitmap,
                    initialWallpaper = ProfileWallpaper(zoom = 1.5f),
                    hasStoredWallpaper = false,
                    loading = loading.value,
                    errorMessage = null,
                    onChooseImage = {},
                    onSave = saved::set,
                    onRemove = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(ProfileWallpaperEditorTestTags.Zoom)
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(3f)
            }
        composeRule.runOnIdle { loading.value = true }
        composeRule.onNodeWithTag(ProfileWallpaperEditorTestTags.Zoom).assertIsNotEnabled()
        composeRule.runOnIdle { loading.value = false }
        composeRule.onNodeWithTag(ProfileWallpaperEditorTestTags.Save).performClick()

        assertTrue((saved.get()?.zoom ?: 0f) > 1.5f)
    }
}
