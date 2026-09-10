package dev.sk2andy.materialbrowser.browser.gecko

import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStoreDownloadStreamSinkInstrumentedTest {
    @Test
    fun committedDownloadUsesConfiguredSubdirectory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val store = BrowserSessionStore(context)
        val originalSettings = store.loadDownloadSettings()
        val subdirectory = "CandyTest-${System.nanoTime()}"
        var entry: GeckoDownloadStreamEntry? = null

        try {
            store.saveDownloadSettings(
                originalSettings.copy(downloadSubdirectory = subdirectory),
            )
            val opened = MediaStoreDownloadStreamSink(context).open(
                fileName = "folder-fixture.txt",
                mimeType = "text/plain",
            )
            entry = opened
            opened.output.write("Candy folder fixture".toByteArray(StandardCharsets.UTF_8))
            opened.commit()

            resolver.query(
                opened.uri,
                arrayOf(MediaStore.Downloads.RELATIVE_PATH),
                null,
                null,
                null,
            )?.use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(
                    "Download/$subdirectory",
                    cursor.getString(0).trimEnd('/'),
                )
            } ?: error("MediaStore did not return the committed download")
        } finally {
            entry?.let { resolver.delete(it.uri, null, null) }
            store.saveDownloadSettings(originalSettings)
        }
    }
}
