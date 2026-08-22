package dev.sk2andy.materialbrowser.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import org.json.JSONArray
import org.json.JSONObject

internal enum class AppDataArchiveRestoreFailure {
    InvalidDirectory,
    BackupDirectoryNotEmpty,
    MoveFailed,
    RollbackFailed,
}

internal enum class AppDataArchiveRestoreResult {
    Completed,
    CompletedKeepRecoveryData,
}

internal class AppDataArchiveRestoreException(
    val failure: AppDataArchiveRestoreFailure,
    cause: Throwable? = null,
) : IOException(failure.name, cause)

internal object AppDataArchiveRestore {
    fun replacePersistentData(
        dataDirectory: File,
        extractedDataDirectory: File,
        emptyBackupDirectory: File,
        recoveryMarker: File,
    ): AppDataArchiveRestoreResult {
        validateDirectories(dataDirectory, extractedDataDirectory, emptyBackupDirectory)
        val rootNames = (
            AppDataArchiveRules.persistentRootNames(dataDirectory) +
                AppDataArchiveRules.persistentRootNames(extractedDataDirectory)
            ).sorted()
        val journal = RestoreJournal(
            extractedDirectory = extractedDataDirectory.canonicalPath,
            backupDirectory = emptyBackupDirectory.canonicalPath,
            committed = false,
            roots = rootNames.map { rootName ->
                RestoreRoot(rootName, File(dataDirectory, rootName).exists())
            },
        )
        writeJournal(recoveryMarker, journal)
        try {
            rootNames.forEach { rootName ->
                val current = File(dataDirectory, rootName)
                val replacement = File(extractedDataDirectory, rootName)
                val backup = File(emptyBackupDirectory, rootName)
                if (current.exists()) move(current, backup)
                if (replacement.exists()) {
                    applyPrivatePermissions(replacement)
                    move(replacement, current)
                }
            }
            writeJournal(recoveryMarker, journal.copy(committed = true))
        } catch (failure: IOException) {
            if (readJournal(recoveryMarker)?.committed == true) {
                return completedResult(recoveryMarker)
            }
            if (!recoverInterruptedRestore(dataDirectory, recoveryMarker)) {
                throw AppDataArchiveRestoreException(
                    AppDataArchiveRestoreFailure.RollbackFailed,
                    failure,
                )
            }
            throw AppDataArchiveRestoreException(AppDataArchiveRestoreFailure.MoveFailed, failure)
        }
        return completedResult(recoveryMarker)
    }

    fun hasInterruptedRestore(recoveryMarker: File): Boolean = recoveryMarker.isFile

    fun cleanupOrphanedWorkDirectories(stateDirectory: File, recoveryMarker: File) {
        if (recoveryMarker.exists()) return
        if (runCatching { syncDirectory(stateDirectory) }.isFailure) return
        stateDirectory.listFiles().orEmpty().forEach { file ->
            if (file.isDirectory && RESTORE_WORK_DIRECTORY.matches(file.name)) {
                file.deleteRecursively()
            }
        }
        runCatching { syncDirectory(stateDirectory) }
    }

    fun recoverInterruptedRestore(dataDirectory: File, recoveryMarker: File): Boolean {
        val journal = readJournal(recoveryMarker) ?: return false
        val markerParent = recoveryMarker.parentFile?.canonicalFile ?: return false
        val extractedDirectory = File(journal.extractedDirectory).canonicalFile
        val backupDirectory = File(journal.backupDirectory).canonicalFile
        if (!extractedDirectory.isWithin(markerParent) || !backupDirectory.isWithin(markerParent)) {
            return false
        }
        if (journal.committed) {
            val finalized = finalizeCommittedMarker(recoveryMarker)
            if (finalized) extractedDirectory.parentFile?.deleteRecursively()
            return true
        }
        val recovered = runCatching {
            journal.roots.asReversed().forEach { root ->
                if (!AppDataArchiveRules.shouldExportTopLevel(root.name) ||
                    '/' in root.name ||
                    '\\' in root.name
                ) {
                    throw IOException("Invalid recovery root")
                }
                val current = File(dataDirectory, root.name)
                val backup = File(backupDirectory, root.name)
                val replacement = File(extractedDirectory, root.name)
                when {
                    backup.exists() -> {
                        if (current.exists() && !current.deleteRecursively()) {
                            throw IOException("Could not remove replacement root")
                        }
                        move(backup, current)
                    }
                    !root.originallyPresent && !replacement.exists() -> {
                        if (current.exists() && !current.deleteRecursively()) {
                            throw IOException("Could not remove new root")
                        }
                        syncDirectory(current.parentFile)
                    }
                }
            }
        }.isSuccess
        if (!recovered) return false
        val deletionSynced = finalizeCommittedMarker(recoveryMarker)
        if (!deletionSynced) return true
        extractedDirectory.parentFile?.deleteRecursively()
        return true
    }

    private fun validateDirectories(
        dataDirectory: File,
        extractedDataDirectory: File,
        backupDirectory: File,
    ) {
        if (!dataDirectory.isDirectory || !extractedDataDirectory.isDirectory) {
            throw AppDataArchiveRestoreException(AppDataArchiveRestoreFailure.InvalidDirectory)
        }
        if (Files.isSymbolicLink(dataDirectory.toPath()) ||
            Files.isSymbolicLink(extractedDataDirectory.toPath()) ||
            Files.isSymbolicLink(backupDirectory.toPath())
        ) {
            throw AppDataArchiveRestoreException(AppDataArchiveRestoreFailure.InvalidDirectory)
        }
        if (backupDirectory.exists() && backupDirectory.list()?.isNotEmpty() == true) {
            throw AppDataArchiveRestoreException(
                AppDataArchiveRestoreFailure.BackupDirectoryNotEmpty,
            )
        }
        if (!backupDirectory.exists() && !backupDirectory.mkdirs()) {
            throw AppDataArchiveRestoreException(AppDataArchiveRestoreFailure.InvalidDirectory)
        }
    }

    private fun move(source: File, target: File) {
        target.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create parent")
        }
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath())
        }
        syncDirectory(source.parentFile)
        if (source.parentFile?.canonicalFile != target.parentFile?.canonicalFile) {
            syncDirectory(target.parentFile)
        }
    }

    private fun applyPrivatePermissions(file: File) {
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setExecutable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
        if (file.isDirectory) {
            file.setExecutable(true, true)
            file.listFiles().orEmpty().forEach(::applyPrivatePermissions)
        }
    }

    private fun writeJournal(marker: File, journal: RestoreJournal) {
        marker.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) {
                throw AppDataArchiveRestoreException(AppDataArchiveRestoreFailure.InvalidDirectory)
            }
        }
        val temporary = File(marker.parentFile, "${marker.name}.tmp")
        val json = JSONObject()
            .put("version", JOURNAL_VERSION)
            .put("extractedDirectory", journal.extractedDirectory)
            .put("backupDirectory", journal.backupDirectory)
            .put("committed", journal.committed)
            .put(
                "roots",
                JSONArray().apply {
                    journal.roots.forEach { root ->
                        put(
                            JSONObject()
                                .put("name", root.name)
                                .put("originallyPresent", root.originallyPresent),
                        )
                    }
                },
        )
        try {
            FileOutputStream(temporary).use { output ->
                output.write(json.toString().toByteArray(StandardCharsets.UTF_8))
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    marker.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    marker.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            FileOutputStream(marker, true).use { output -> output.fd.sync() }
            syncDirectory(marker.parentFile)
        } catch (failure: IOException) {
            temporary.delete()
            throw AppDataArchiveRestoreException(
                AppDataArchiveRestoreFailure.MoveFailed,
                failure,
            )
        }
    }

    private fun readJournal(marker: File): RestoreJournal? = runCatching {
        val json = JSONObject(marker.readText(StandardCharsets.UTF_8))
        if (json.getInt("version") != JOURNAL_VERSION) return null
        val roots = json.getJSONArray("roots")
        RestoreJournal(
            extractedDirectory = json.getString("extractedDirectory"),
            backupDirectory = json.getString("backupDirectory"),
            committed = json.optBoolean("committed", false),
            roots = List(roots.length()) { index ->
                val root = roots.getJSONObject(index)
                RestoreRoot(
                    name = root.getString("name"),
                    originallyPresent = root.getBoolean("originallyPresent"),
                )
            },
        )
    }.getOrNull()

    private fun File.isWithin(parent: File): Boolean =
        path.startsWith(parent.path + File.separator)

    private fun completedResult(recoveryMarker: File): AppDataArchiveRestoreResult =
        if (finalizeCommittedMarker(recoveryMarker)) {
            AppDataArchiveRestoreResult.Completed
        } else {
            AppDataArchiveRestoreResult.CompletedKeepRecoveryData
        }

    private fun finalizeCommittedMarker(recoveryMarker: File): Boolean {
        if (recoveryMarker.exists() && !recoveryMarker.delete()) return false
        return runCatching { syncDirectory(recoveryMarker.parentFile) }.isSuccess
    }

    private fun syncDirectory(directory: File?) {
        if (directory == null) throw IOException("Missing directory")
        java.nio.channels.FileChannel.open(
            directory.toPath(),
            StandardOpenOption.READ,
        ).use { channel -> channel.force(true) }
    }

    private data class RestoreJournal(
        val extractedDirectory: String,
        val backupDirectory: String,
        val committed: Boolean,
        val roots: List<RestoreRoot>,
    )

    private data class RestoreRoot(
        val name: String,
        val originallyPresent: Boolean,
    )

    private const val JOURNAL_VERSION = 1
    private val RESTORE_WORK_DIRECTORY = Regex("restore_[0-9a-f-]{36}")
}
