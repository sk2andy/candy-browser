package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.os.Bundle
import android.os.Parcel
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.UUID

class TabWebViewStateStore(context: Context) {
    private val directory = File(context.noBackupFilesDir, DIRECTORY_NAME)
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
        if (!directory.exists() && !directory.mkdirs()) return false
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

        val atomicFile = AtomicFile(target)
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

    fun delete(tabId: String) {
        val target = fileFor(tabId) ?: return
        AtomicFile(target).delete()
    }

    fun prune(validTabIds: Set<String>) {
        val validNames = validTabIds.mapNotNull(::stateFileName).toSet()
        val orphanBaseNames = directory.listFiles()
            ?.map { file -> atomicBaseName(file.name) }
            ?.filterNot(validNames::contains)
            ?.toSet()
            .orEmpty()
        orphanBaseNames.forEach { baseName ->
            AtomicFile(File(directory, baseName)).delete()
        }
    }

    fun clear() {
        directory.listFiles()?.forEach(File::delete)
        directory.delete()
    }

    internal fun fileFor(tabId: String): File? = stateFileName(tabId)?.let { File(directory, it) }

    private companion object {
        const val DIRECTORY_NAME = "tab_webview_states"
        const val MAX_FILE_SIZE_BYTES = 8 * 1_024 * 1_024
    }
}

private fun stateFileName(tabId: String): String? = runCatching {
    "${UUID.fromString(tabId)}.bin"
}.getOrNull()

private fun atomicBaseName(fileName: String): String = when {
    fileName.endsWith(".new") -> fileName.removeSuffix(".new")
    fileName.endsWith(".bak") -> fileName.removeSuffix(".bak")
    else -> fileName
}
