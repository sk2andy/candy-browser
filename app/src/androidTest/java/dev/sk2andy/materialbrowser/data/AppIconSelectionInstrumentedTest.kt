package dev.sk2andy.materialbrowser.data

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppIconSelectionInstrumentedTest {
    @Test
    fun switchingIconsKeepsExactlyOneLauncherAliasEnabled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.packageManager
        val selection = AppIconSelection(context)
        val original = selection.selected()
        try {
            listOf(AppIconOption.Cookie, AppIconOption.OutlineLight, AppIconOption.Classic).forEach { option ->
                selection.select(option)
                assertEquals(option, selection.selected())
                AppIconOption.entries.forEach { entry ->
                    assertEquals(
                        if (entry == option) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        manager.getComponentEnabledSetting(entry.componentName(context)),
                    )
                }
                val launcher = manager.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setPackage(context.packageName),
                    0,
                )
                assertEquals(listOf(option.componentName(context).className), launcher.map { it.activityInfo.name })
            }
        } finally {
            selection.select(original)
        }
    }
}
