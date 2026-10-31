package dev.sk2andy.materialbrowser.browser.integration

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class PageShareLauncherInstrumentedTest {
    @Test
    fun sharesOnlySelectedTextThroughChooser() {
        var launched: Intent? = null
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun startActivity(intent: Intent) { launched = intent }
        }
        val selected = "Selected words https://example.org/path?q=one&other=two"
        assertEquals(PageShareResult.Launched, PageShareLauncher(context).launchText(selected))
        val chooser = requireNotNull(launched)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertTrue(chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        val target = requireNotNull(chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java))
        assertEquals(Intent.ACTION_SEND, target.action)
        assertEquals("text/plain", target.type)
        assertEquals(selected, target.getStringExtra(Intent.EXTRA_TEXT))
        assertFalse(target.hasExtra(Intent.EXTRA_TITLE))
        assertFalse(target.hasExtra(Intent.EXTRA_SUBJECT))
    }

    @Test
    fun unavailableOrForbiddenChooserReturnsUnsupported() {
        for (failure in listOf(ActivityNotFoundException(), SecurityException())) {
            val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
                override fun startActivity(intent: Intent) { throw failure }
            }
            assertEquals(PageShareResult.Unsupported, PageShareLauncher(context).launchText("Selected words"))
        }
    }
}
