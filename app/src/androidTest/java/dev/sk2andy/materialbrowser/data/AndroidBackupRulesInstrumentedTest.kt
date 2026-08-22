package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class AndroidBackupRulesInstrumentedTest {
    @Test
    fun encryptedCloudBackupIncludesPortableStateButNotWebViewRoot() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
        val parser = context.resources.getXml(R.xml.data_extraction_rules)
        var inCloudBackup = false
        var encryptedCloudBackup = false
        val included = mutableSetOf<Pair<String, String>>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "cloud-backup" -> {
                        inCloudBackup = true
                        encryptedCloudBackup = parser.getAttributeValue(
                            null,
                            "disableIfNoEncryptionCapabilities",
                        ) == "true"
                    }
                    "include" -> if (inCloudBackup) {
                        included += parser.getAttributeValue(null, "domain") to
                            parser.getAttributeValue(null, "path")
                    }
                }
            } else if (
                parser.eventType == XmlPullParser.END_TAG && parser.name == "cloud-backup"
            ) {
                inCloudBackup = false
            }
            parser.next()
        }

        assertTrue(encryptedCloudBackup)
        assertTrue(("sharedpref" to ".") in included)
        assertTrue(("file" to "candy_filter_rules.json") in included)
        assertTrue(("file" to "user_scripts.json") in included)
        assertFalse(("root" to ".") in included)
    }
}
