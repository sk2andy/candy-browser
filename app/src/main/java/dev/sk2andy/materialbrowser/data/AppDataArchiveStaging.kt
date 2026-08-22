package dev.sk2andy.materialbrowser.data

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

internal data class StagedAppDataArchive(
    val fileName: String,
    val inspection: AppDataArchiveInspection,
)

internal object AppDataArchiveStaging {
    fun stage(input: InputStream, stagingDirectory: File): StagedAppDataArchive {
        ensureStagingDirectory(stagingDirectory)
        val fileName = "${UUID.randomUUID()}.zip"
        val stagedFile = File(stagingDirectory, fileName)
        try {
            BufferedInputStream(input).use { source ->
                BufferedOutputStream(FileOutputStream(stagedFile)).use { target ->
                    copyBounded(source, target)
                }
            }
            val inspection = FileInputStream(stagedFile).use(AppDataArchiveCodec::inspect)
            return StagedAppDataArchive(fileName, inspection)
        } catch (failure: Throwable) {
            stagedFile.delete()
            if (failure is AppDataArchiveException) throw failure
            if (failure is IOException) {
                throw AppDataArchiveException(
                    AppDataArchiveFailure.ArchiveIo,
                    cause = failure,
                )
            }
            throw failure
        }
    }

    fun resolve(stagingDirectory: File, fileName: String): File? {
        if (!FILE_NAME.matches(fileName)) return null
        val file = File(stagingDirectory, fileName)
        return file.takeIf(File::isFile)
    }

    private fun ensureStagingDirectory(directory: File) {
        if (directory.exists() && !directory.isDirectory) {
            throw AppDataArchiveException(AppDataArchiveFailure.ArchiveIo)
        }
        if (!directory.exists() && !directory.mkdirs()) {
            throw AppDataArchiveException(AppDataArchiveFailure.ArchiveIo)
        }
        directory.listFiles().orEmpty().forEach { stale ->
            if (stale.isFile && FILE_NAME.matches(stale.name)) stale.delete()
        }
    }

    private fun copyBounded(input: InputStream, output: java.io.OutputStream) {
        var totalBytes = 0L
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return
            if (read == 0) continue
            if (totalBytes > MAX_ARCHIVE_BYTES - read) {
                throw AppDataArchiveException(AppDataArchiveFailure.TotalSizeExceeded)
            }
            output.write(buffer, 0, read)
            totalBytes += read
        }
    }

    private val FILE_NAME = Regex("[0-9a-f-]{36}\\.zip")
    private const val COPY_BUFFER_BYTES = 16 * 1024
    private const val MAX_ARCHIVE_BYTES =
        AppDataArchiveRules.MAX_TOTAL_BYTES + 32L * 1024L * 1024L
}
