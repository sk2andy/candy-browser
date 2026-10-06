package dev.sk2andy.materialbrowser.browser.gecko

import android.util.AtomicFile
import android.util.Log
import dev.sk2andy.materialbrowser.data.BrowserMemorySettings
import dev.sk2andy.materialbrowser.data.writeSafely
import java.io.File
import org.json.JSONObject

internal object GeckoHistoryCacheConfig {
    fun write(directory: File, settings: BrowserMemorySettings): String? {
        val file = File(directory, FILE_NAME)
        val configuration = JSONObject().put(
            "prefs",
            JSONObject().put(
                "browser.sessionhistory.contentViewerTimeout",
                GeckoHistoryCacheRules.contentViewerTimeoutSeconds(settings),
            ),
        )
        val saved = AtomicFile(file).writeSafely { output ->
            output.write(configuration.toString().toByteArray(Charsets.UTF_8))
        }
        if (!saved) {
            Log.w(TAG, "History cache startup configuration could not be written")
            return null
        }
        return file.absolutePath
    }

    private const val FILE_NAME = "gecko-history-cache-config.json"
    private const val TAG = "GeckoHistoryCacheConfig"
}
