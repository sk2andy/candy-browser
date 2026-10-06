package dev.sk2andy.materialbrowser.browser.gecko

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.data.BrowserMemorySettings
import java.io.File
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.ContentBlocking

@RunWith(AndroidJUnit4::class)
class GeckoHistoryCacheConfigInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun startupConfigurationKeepsNativeCacheCapacityAndSuppliesExpiryPreference() {
        val directory = File(instrumentation.targetContext.cacheDir, UUID.randomUUID().toString())
        try {
            val path = requireNotNull(GeckoHistoryCacheConfig.write(directory, BrowserMemorySettings()))
            val configuration = JSONObject(File(path).readText())
            assertEquals(setOf("prefs"), configuration.keys().asSequence().toSet())
            val prefs = configuration.getJSONObject("prefs")
            assertEquals(
                setOf("browser.sessionhistory.contentViewerTimeout"),
                prefs.keys().asSequence().toSet(),
            )
            assertEquals(200, prefs.getInt("browser.sessionhistory.contentViewerTimeout"))
            instrumentation.runOnMainSync {
                val settings = GeckoRuntimeSettingsFactory.create(
                    contentBlocking = ContentBlocking.Settings.Builder().build(),
                    configFilePath = path,
                )
                assertEquals(path, settings.configFilePath)
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun nextStartupReplacesPreviousExpiryConfiguration() {
        val directory = File(instrumentation.targetContext.cacheDir, UUID.randomUUID().toString())
        try {
            val firstPath = requireNotNull(GeckoHistoryCacheConfig.write(directory, BrowserMemorySettings()))
            val updatedPath = requireNotNull(
                GeckoHistoryCacheConfig.write(
                    directory,
                    BrowserMemorySettings(historyCacheLifetimeMinutes = 10),
                ),
            )
            assertEquals(firstPath, updatedPath)
            val prefs = JSONObject(File(updatedPath).readText()).getJSONObject("prefs")
            assertEquals(400, prefs.getInt("browser.sessionhistory.contentViewerTimeout"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedWriteDoesNotSupplyStaleConfigurationPath() {
        val directory = File(instrumentation.targetContext.cacheDir, UUID.randomUUID().toString())
        directory.writeText("not a directory")
        try {
            assertNull(GeckoHistoryCacheConfig.write(directory, BrowserMemorySettings()))
            assertEquals("not a directory", directory.readText())
        } finally {
            directory.delete()
        }
    }
}
