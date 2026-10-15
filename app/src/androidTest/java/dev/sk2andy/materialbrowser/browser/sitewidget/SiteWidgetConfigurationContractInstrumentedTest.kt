package dev.sk2andy.materialbrowser.browser.sitewidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.SiteWidgetConfigurationActivity
import dev.sk2andy.materialbrowser.SiteWidgetProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class SiteWidgetConfigurationContractInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everyGridOffersOptionalConfigurationAndLauncherReconfigure() {
        val installed = AppWidgetManager.getInstance(context).installedProviders.associateBy { it.provider }
        SiteWidgetFormat.entries.forEach { format ->
            val info = installed[SiteWidgetProvider.component(context, format)]
            assertNotNull("Missing widget provider for $format", info)
            requireNotNull(info)
            assertEquals(ComponentName(context, SiteWidgetConfigurationActivity::class.java), info.configure)
            assertTrue(info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0)
            assertTrue(info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0)
        }
    }

    @Test
    fun editDispatchAndLauncherResultRetainOnlyValidatedWidgetIdentity() {
        val widgetId = 4_321
        val provider = SiteWidgetProvider.component(context, SiteWidgetFormat.FourByThree)
        val target = requireNotNull(SiteWidgetConfigurationContract.editIntent(context, widgetId, provider))
        assertEquals(ComponentName(context, MainActivity::class.java), target.component)
        assertEquals(SiteWidgetProvider.ACTION_EDIT, target.action)
        assertEquals(widgetId, target.getIntExtra(SiteWidgetProvider.EXTRA_WIDGET_ID, 0))
        assertTrue(target.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(target.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertEquals(widgetId, SiteWidgetConfigurationContract.result(widgetId).getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
        assertNull(SiteWidgetConfigurationContract.editIntent(context, 0, provider))
        assertNull(SiteWidgetConfigurationContract.editIntent(context, widgetId, null))
        assertNull(SiteWidgetConfigurationContract.editIntent(context, widgetId, ComponentName("other.app", "OtherProvider")))
    }
}
