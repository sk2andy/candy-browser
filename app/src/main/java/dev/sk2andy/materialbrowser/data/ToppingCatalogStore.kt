package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.util.AtomicFile
import dev.sk2andy.materialbrowser.browser.userscript.ToppingCatalog
import dev.sk2andy.materialbrowser.browser.userscript.ToppingCatalogParseResult
import dev.sk2andy.materialbrowser.browser.userscript.ToppingCatalogParser
import dev.sk2andy.materialbrowser.browser.userscript.ToppingVerifier
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

internal data class CachedToppingCatalog(
    val bytes: ByteArray,
    val catalog: ToppingCatalog,
)

internal class ToppingCatalogStore(
    context: Context,
    fileName: String = FILE_NAME,
) {
    private val atomicFile = AtomicFile(File(context.noBackupFilesDir, fileName))

    @Synchronized
    fun load(): CachedToppingCatalog? {
        return try {
            val bytes = atomicFile.openRead().use { input ->
                input.readNBytes(ToppingCatalogParser.MAX_MANIFEST_BYTES + 1)
            }
            val catalog = parse(bytes) ?: run {
                atomicFile.delete()
                return null
            }
            CachedToppingCatalog(bytes = bytes, catalog = catalog)
        } catch (_: FileNotFoundException) {
            null
        } catch (_: Exception) {
            atomicFile.delete()
            null
        }
    }

    @Synchronized
    fun save(bytes: ByteArray): Boolean {
        if (parse(bytes) == null) return false
        var output: FileOutputStream? = null
        return try {
            output = atomicFile.startWrite()
            output.write(bytes)
            atomicFile.finishWrite(output)
            true
        } catch (_: Exception) {
            output?.let(atomicFile::failWrite)
            false
        }
    }

    @Synchronized
    fun clear() = atomicFile.delete()

    private fun parse(bytes: ByteArray): ToppingCatalog? {
        if (bytes.size !in 1..ToppingCatalogParser.MAX_MANIFEST_BYTES) return null
        val json = ToppingVerifier.decodeUtf8(bytes) ?: return null
        return (ToppingCatalogParser.parse(json) as? ToppingCatalogParseResult.Accepted)?.catalog
    }

    internal companion object {
        const val FILE_NAME = "topping_catalog.json"
    }
}
