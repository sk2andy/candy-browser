package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

internal object BackedUpAtomicFileMigration {
    @Synchronized
    fun fromNoBackupDirectory(
        context: Context,
        fileName: String,
        maxBytes: Int,
    ): AtomicFile {
        val target = AtomicFile(File(context.filesDir, fileName))
        val legacy = AtomicFile(File(context.noBackupFilesDir, fileName))
        if (target.hasState()) {
            legacy.delete()
            return target
        }
        if (!legacy.hasState()) return target
        val bytes = runCatching {
            legacy.openRead().use { input -> input.readNBytes(maxBytes + 1) }
        }.getOrNull()
        if (bytes == null || bytes.size !in 1..maxBytes || !writeAtomically(target.baseFile, bytes)) {
            return legacy
        }
        legacy.delete()
        return target
    }

    private fun AtomicFile.hasState(): Boolean =
        baseFile.exists() || File("${baseFile.path}.bak").exists()

    private fun writeAtomically(target: File, bytes: ByteArray): Boolean {
        val parent = requireNotNull(target.parentFile)
        val temporary = File(parent, ".${target.name}.migration")
        return runCatching {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath())
            }
            FileChannel.open(parent.toPath(), StandardOpenOption.READ).use { channel ->
                channel.force(true)
            }
            true
        }.getOrElse {
            temporary.delete()
            runCatching {
                Files.deleteIfExists(target.toPath())
                FileChannel.open(
                    parent.toPath(),
                    StandardOpenOption.READ,
                ).use { channel -> channel.force(true) }
            }
            false
        }
    }
}
