package dev.sk2andy.materialbrowser.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.browser.ReleaseNotesBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseNotesRepositoryInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun packagedNotesAndReferencedScreenshotsAreReadableOffline() {
        val content = requireNotNull(
            ReleaseNotesRepository(context).load(BuildConfig.RELEASE_NOTES_VERSION),
        )
        val images = content.document.blocks.filterIsInstance<ReleaseNotesBlock.Image>()

        assertEquals(BuildConfig.RELEASE_NOTES_VERSION, content.versionName)
        assertTrue(content.document.title.text.isNotBlank())
        assertTrue(images.isNotEmpty())
        images.forEach { image ->
            context.assets.open(image.assetPath).use { input ->
                assertTrue(input.read() >= 0)
            }
        }
    }
}
