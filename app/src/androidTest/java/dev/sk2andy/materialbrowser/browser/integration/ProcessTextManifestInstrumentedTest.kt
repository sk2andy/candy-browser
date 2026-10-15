package dev.sk2andy.materialbrowser.browser.integration

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.ReadSelectedTextActivity
import dev.sk2andy.materialbrowser.ResearchSelectedTextActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessTextManifestInstrumentedTest {
    @Test
    fun textSelectionMenuExposesResearchAndSpeechActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain").setPackage(context.packageName)
        val activities = context.packageManager.queryIntentActivities(
            intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
        )

        assertEquals(
            setOf(ResearchSelectedTextActivity::class.java.name, ReadSelectedTextActivity::class.java.name),
            activities.mapTo(hashSetOf()) { it.activityInfo.name },
        )
        assertTrue(activities.all { it.activityInfo.exported })
    }
}
