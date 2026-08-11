package dev.sk2andy.materialbrowser.blocking

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BundledSitePrivacyDefaultsAssetInstrumentedTest {
    @Test
    fun bundledAssetLoadsBothPresetTypes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val text = context.assets.open("site_privacy_defaults.txt")
            .bufferedReader()
            .use { it.readText() }
        val defaults = BundledSitePrivacyDefaults.load(context)
        val rules = text.lineSequence()
            .filterNot { line -> line.isBlank() || line.startsWith('#') }
            .map { line -> line.substringBefore('\t') to line.substringAfter('\t') }
            .toList()

        assertEquals(17, rules.count { (rule) -> rule == "force_vertical_scroll" })
        assertEquals(3, rules.count { (rule) -> rule == "cookie_banner_removal_disabled" })
        assertTrue(
            rules.all { (rule, host) ->
                when (rule) {
                    "force_vertical_scroll" -> defaults.forceVerticalScrolling(host)
                    "cookie_banner_removal_disabled" ->
                        defaults.cookieBannerRemovalDisabled(host)
                    else -> false
                }
            },
        )
        assertTrue(defaults.forceVerticalScrolling("www.ft.com"))
        assertTrue(defaults.cookieBannerRemovalDisabled("www.myfitnesspal.com"))
        assertFalse(defaults.cookieBannerRemovalDisabled("www.ft.com"))
        assertFalse(defaults.forceVerticalScrolling("www.myfitnesspal.com"))
    }
}
