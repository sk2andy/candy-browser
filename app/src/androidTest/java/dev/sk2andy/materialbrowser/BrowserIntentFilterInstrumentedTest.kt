package dev.sk2andy.materialbrowser

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserIntentFilterInstrumentedTest {
    @Test
    fun resolvesUntypedWebLinks() {
        assertTrue(resolvesCandyBrowser(scheme = "http", mimeType = null))
        assertTrue(resolvesCandyBrowser(scheme = "https", mimeType = null))
    }

    @Test
    fun resolvesHtmlWebLinks() {
        assertTrue(resolvesCandyBrowser(scheme = "http", mimeType = "text/html"))
        assertTrue(resolvesCandyBrowser(scheme = "https", mimeType = "text/html"))
    }

    @Test
    fun doesNotResolveUnsupportedWebMimeTypes() {
        assertFalse(resolvesCandyBrowser(scheme = "http", mimeType = "application/pdf"))
        assertFalse(resolvesCandyBrowser(scheme = "https", mimeType = "application/pdf"))
    }

    private fun resolvesCandyBrowser(scheme: String, mimeType: String?): Boolean {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uri = Uri.parse("$scheme://example.com/article")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            setPackage(context.packageName)
            if (mimeType == null) {
                data = uri
            } else {
                setDataAndType(uri, mimeType)
            }
        }

        return context.packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
        ).any { resolveInfo ->
            resolveInfo.activityInfo.packageName == context.packageName &&
                resolveInfo.activityInfo.name == MainActivity::class.java.name
        }
    }
}
