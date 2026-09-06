package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.capsule.CapsuleIconPack
import dev.sk2andy.materialbrowser.capsule.CapsuleIconPackEntry
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapsuleIconPackPickerScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun searchFiltersIconsAndVisibleIconCanBeSelected() {
        val pack = CapsuleIconPack("icons.pack", "Candy Icons")
        val chrome = entry("chrome_icon", "Chrome Icon", "chrome browser")
        val mail = entry("mail_icon", "Mail Icon", "mail inbox")
        val selected = AtomicReference<CapsuleIconPackEntry?>()
        val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        composeRule.setContent {
            MaterialTheme {
                CapsuleIconPackPickerScreen(
                    packs = listOf(pack),
                    selectedPackageName = pack.packageName,
                    entries = listOf(chrome, mail),
                    loading = false,
                    choosing = false,
                    errorMessage = null,
                    onSelectPack = {},
                    onSelectEntry = selected::set,
                    loadIcon = { bitmap },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(CapsuleIconPackPickerTestTags.Search)
            .performTextInput("chrome")
        composeRule.onNodeWithTag(CapsuleIconPackPickerTestTags.icon("mail_icon"))
            .assertDoesNotExist()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(CapsuleIconPackPickerTestTags.icon("chrome_icon"))
                    .assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(CapsuleIconPackPickerTestTags.icon("chrome_icon"))
            .assertIsDisplayed()
            .performClick()

        assertEquals(chrome, selected.get())
    }

    @Test
    fun asynchronouslyLoadedEntriesBecomeVisible() {
        val pack = CapsuleIconPack("icons.pack", "Candy Icons")
        val chrome = entry("chrome_icon", "Chrome Icon", "chrome browser")
        val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        var entries by mutableStateOf<List<CapsuleIconPackEntry>>(emptyList())
        composeRule.setContent {
            MaterialTheme {
                CapsuleIconPackPickerScreen(
                    packs = listOf(pack),
                    selectedPackageName = pack.packageName,
                    entries = entries,
                    loading = false,
                    choosing = false,
                    errorMessage = null,
                    onSelectPack = {},
                    onSelectEntry = {},
                    loadIcon = { bitmap },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(CapsuleIconPackPickerTestTags.Empty).assertIsDisplayed()
        composeRule.runOnIdle { entries = listOf(chrome) }
        composeRule.onNodeWithTag(CapsuleIconPackPickerTestTags.icon("chrome_icon"))
            .assertIsDisplayed()
    }

    private fun entry(
        drawableName: String,
        label: String,
        searchText: String,
    ) = CapsuleIconPackEntry(
        packageName = "icons.pack",
        drawableName = drawableName,
        label = label,
        searchText = searchText,
    )
}
