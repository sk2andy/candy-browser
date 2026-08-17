package dev.sk2andy.materialbrowser.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ToppingCatalogStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fileName = "topping-catalog-${UUID.randomUUID()}.json"

    @Test
    fun atomicallyRoundTripsOnlyValidatedCatalogs() {
        val store = ToppingCatalogStore(context, fileName)
        val bytes = manifest().toByteArray()

        assertTrue(store.save(bytes))
        val cached = store.load()
        assertNotNull(cached)
        requireNotNull(cached)
        assertEquals("calm-reader", cached.catalog.toppings.single().id)
        assertTrue(bytes.contentEquals(cached.bytes))
        store.clear()
    }

    @Test
    fun rejectsInvalidSaveAndDeletesCorruptCache() {
        val store = ToppingCatalogStore(context, fileName)
        assertFalse(store.save("{}".toByteArray()))

        val target = File(context.noBackupFilesDir, fileName)
        target.writeText("not-json")
        assertEquals(null, store.load())
        assertFalse(target.exists())
    }

    @Test
    fun deletesOversizeCacheAfterBoundedRead() {
        val store = ToppingCatalogStore(context, fileName)
        val target = File(context.noBackupFilesDir, fileName)
        RandomAccessFile(target, "rw").use { file ->
            file.setLength(dev.sk2andy.materialbrowser.browser.userscript.ToppingCatalogParser.MAX_MANIFEST_BYTES + 1L)
        }

        assertEquals(null, store.load())
        assertFalse(target.exists())
    }

    private fun manifest(): String = JSONObject()
        .put("schemaVersion", 1)
        .put(
            "toppings",
            JSONArray().put(
                JSONObject()
                    .put("id", "calm-reader")
                    .put("name", "Calm Reader")
                    .put("description", "Calmer articles.")
                    .put("author", "Candy Browser")
                    .put("license", "MIT")
                    .put("version", "1.0.0")
                    .put("source", "toppings/calm-reader.user.js")
                    .put("matches", JSONArray().put("https://example.com/*"))
                    .put("sha256", "0".repeat(64)),
            ),
        )
        .toString()
}
