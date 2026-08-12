package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.os.Bundle
import android.os.Parcel
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException

class TabWebViewStateStore(context: Context) {
    private val files = AtomicTabFileDirectory(
        directory = File(context.noBackupFilesDir, DIRECTORY_NAME),
        extension = FILE_EXTENSION,
    )
    private val classLoader = context.classLoader

    fun load(tabId: String): Bundle? {
        val file = fileFor(tabId) ?: return null
        val atomicFile = AtomicFile(file)
        val bytes = try {
            atomicFile.openRead().use { input ->
                if (atomicFile.baseFile.length() !in 1..MAX_FILE_SIZE_BYTES) {
                    atomicFile.delete()
                    return null
                }
                input.readBytes()
            }
        } catch (_: FileNotFoundException) {
            return null
        } catch (_: Exception) {
            atomicFile.delete()
            return null
        }
        val parcel = Parcel.obtain()
        return try {
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            parcel.readBundle(classLoader) ?: run {
                atomicFile.delete()
                null
            }
        } catch (_: Exception) {
            atomicFile.delete()
            null
        } finally {
            parcel.recycle()
        }
    }

    fun save(tabId: String, state: Bundle): Boolean {
        if (!files.ensureExists()) return false
        val target = fileFor(tabId) ?: return false
        val parcel = Parcel.obtain()
        val bytes = try {
            parcel.writeBundle(state)
            parcel.marshall()
        } catch (_: Exception) {
            return false
        } finally {
            parcel.recycle()
        }
        if (bytes.size !in 1..MAX_FILE_SIZE_BYTES) return false

        return AtomicFile(target).writeSafely { output ->
            output.write(bytes)
        }
    }

    fun delete(tabId: String) = files.delete(tabId)

    fun prune(validTabIds: Set<String>) = files.prune(validTabIds)

    fun clear() = files.clearAndRemoveDirectory()

    internal fun fileFor(tabId: String): File? = files.fileFor(tabId)

    private companion object {
        const val DIRECTORY_NAME = "tab_webview_states"
        const val FILE_EXTENSION = "bin"
        const val MAX_FILE_SIZE_BYTES = 8 * 1_024 * 1_024
    }
}
