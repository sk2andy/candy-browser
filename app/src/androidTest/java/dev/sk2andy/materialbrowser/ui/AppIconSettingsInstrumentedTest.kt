package dev.sk2andy.materialbrowser.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.AppIconOption
import dev.sk2andy.materialbrowser.data.AppIconSelection
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppIconSettingsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val evidenceDirectory = File(context.getExternalFilesDir(null), "issue305-evidence")

    @Test
    fun outlineChoicesSwitchRepeatedlyAndRestoreClassic() {
        val selection = AppIconSelection(context)
        // Explicit evidence runs leave the final UI selection for an external
        // force-stop/reboot check. Normal test runs always restore Classic.
        val evidenceChoice = when (InstrumentationRegistry.getArguments().getString("evidenceIcon")) {
            "light" -> AppIconOption.OutlineLight to R.string.app_icon_outline_light
            "dark" -> AppIconOption.OutlineDark to R.string.app_icon_outline_dark
            else -> null
        }
        val choices = listOf(
            AppIconOption.OutlineLight to R.string.app_icon_outline_light,
            AppIconOption.OutlineDark to R.string.app_icon_outline_dark,
            AppIconOption.OutlineLight to R.string.app_icon_outline_light,
            AppIconOption.OutlineDark to R.string.app_icon_outline_dark,
        ) + listOf(evidenceChoice ?: (AppIconOption.Classic to R.string.app_icon_classic))
        selection.select(AppIconOption.Classic)
        try {
            composeRule.setContent {
                MaterialBrowserTheme(settings = AppearanceSettings()) {
                    AppIconSettings()
                }
            }
            choices.forEach { (option, label) ->
                composeRule.onNodeWithTag(AppIconSettingsTestTags.Choice).performClick()
                composeRule.onNode(
                    hasText(context.getString(label)) and !hasTestTag(AppIconSettingsTestTags.Choice),
                ).performClick()
                assertEquals(option, selection.selected())
                assertEquals(option, AppIconSelection(context).selected())
                assertLauncherContracts(option)
                composeRule.onNodeWithTag(AppIconSettingsTestTags.Choice).performClick()
                composeRule.onNode(hasText(context.getString(label)) and isSelected()).assertIsSelected()
                composeRule.waitForIdle()
                evidenceDirectory.mkdirs()
                UiDevice.getInstance(instrumentation).takeScreenshot(
                    File(evidenceDirectory, "settings-${option.name}.png"),
                )
                composeRule.onNode(hasText(context.getString(label)) and isSelected()).performClick()
            }
        } finally {
            if (evidenceChoice == null) selection.select(AppIconOption.Classic)
        }
    }

    @Test
    fun adaptiveContoursRemainUnfilledAndRenderAcrossMasksAndSizes() {
        evidenceDirectory.mkdirs()
        val classic = AdaptiveIconDrawable(
            context.getDrawable(R.drawable.ic_launcher_background),
            context.getDrawable(R.drawable.ic_launcher_foreground),
        )
        val icons = listOf(
            "Classic" to classic,
            "OutlineLight" to context.getDrawable(R.mipmap.ic_launcher_outline_light),
            "OutlineDark" to context.getDrawable(R.mipmap.ic_launcher_outline_dark),
        )
        icons.forEach { (name, drawable) ->
            val icon = drawable as AdaptiveIconDrawable
            for (size in listOf(48, 192, 432)) {
                icon.setBounds(0, 0, size, size)
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                icon.draw(Canvas(bitmap))
                save(bitmap, "$name-adaptive-$size.png")
            }
            for (mask in listOf("circle", "rounded-square", "squircle")) {
                val size = 192
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val clip = Path().apply {
                    when (mask) {
                        "circle" -> addCircle(96f, 96f, 96f, Path.Direction.CW)
                        "rounded-square" -> addRoundRect(0f, 0f, 192f, 192f, 38f, 38f, Path.Direction.CW)
                        else -> {
                            moveTo(96f, 0f)
                            cubicTo(176f, 0f, 192f, 16f, 192f, 96f)
                            cubicTo(192f, 176f, 176f, 192f, 96f, 192f)
                            cubicTo(16f, 192f, 0f, 176f, 0f, 96f)
                            cubicTo(0f, 16f, 16f, 0f, 96f, 0f)
                            close()
                        }
                    }
                }
                canvas.clipPath(clip)
                // AdaptiveIconDrawable's layer bounds include its standard extra inset.
                icon.setBounds(0, 0, size, size)
                icon.background.draw(canvas)
                icon.foreground.draw(canvas)
                save(bitmap, "$name-$mask.png")
            }
            if (name != "Classic") {
                // Independently sampled points lie inside the three original filled leaves.
                val foreground = icon.foreground
                foreground.setBounds(0, 0, 432, 432)
                val bitmap = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
                foreground.draw(Canvas(bitmap))
                for ((x, y) in listOf(195 to 126, 132 to 267, 303 to 235)) {
                    assertEquals("Leaf interior must be transparent: $name", 0, Color.alpha(bitmap.getPixel(x, y)))
                }
                assertTrue(bitmapHasInk(bitmap))
                save(bitmap, "$name-foreground.png")
                val monochrome = requireNotNull(icon.monochrome)
                monochrome.setBounds(0, 0, 432, 432)
                val themed = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
                monochrome.draw(Canvas(themed))
                assertEquals(0, Color.alpha(themed.getPixel(195, 126)))
                assertTrue(bitmapHasInk(themed))
                save(themed, "$name-monochrome.png")
            }
        }
    }

    private fun assertLauncherContracts(option: AppIconOption) {
        val manager = context.packageManager
        AppIconOption.entries.forEach { entry ->
            assertEquals(
                if (entry == option) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                manager.getComponentEnabledSetting(entry.componentName(context)),
            )
        }
        val launcher = manager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName),
            0,
        )
        assertEquals(listOf(option.componentName(context).className), launcher.map { it.activityInfo.name })
        assertEquals(
            "dev.sk2andy.materialbrowser.MainActivity",
            launcher.single().activityInfo.targetActivity,
        )
        val info = manager.getActivityInfo(option.componentName(context), PackageManager.GET_META_DATA)
        assertTrue(info.metaData.getInt("android.app.shortcuts") != 0)
        val shortcutManager = context.getSystemService(ShortcutManager::class.java)
        assertTrue(shortcutManager.manifestShortcuts.any { it.id == "launcher_new_tab" })
        assertTrue(shortcutManager.manifestShortcuts.any { it.id == "launcher_new_private_tab" })
        for (command in listOf(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_DEFAULT),
            Intent(Intent.ACTION_SEND).setType("text/plain").addCategory(Intent.CATEGORY_DEFAULT),
        )) {
            assertTrue(manager.queryIntentActivities(command.setPackage(context.packageName), 0)
                .any { it.activityInfo.name == "dev.sk2andy.materialbrowser.MainActivity" })
        }
    }

    private fun bitmapHasInk(bitmap: Bitmap): Boolean =
        (0 until bitmap.height).any { y ->
            (0 until bitmap.width).any { x -> Color.alpha(bitmap.getPixel(x, y)) > 0 }
        }

    private fun save(bitmap: Bitmap, name: String) {
        File(evidenceDirectory, name).outputStream().use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }
}
