package dev.sk2andy.materialbrowser.data

import android.content.Context
import java.io.File
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import java.util.UUID

internal object AppDataTransferLock {
    fun activate(context: Context, processId: Int): String? = withGuard(context) {
        val lock = lockFile(context)
        readOwner(lock)?.let { owner ->
            if (owner.isAlive(context)) return@withGuard null
            lock.delete()
        }
        val owner = Owner(
            token = UUID.randomUUID().toString(),
            processId = processId,
            processStartTicks = processStartTicks(processId) ?: return@withGuard null,
        )
        if (!writeOwner(lock, owner)) return@withGuard null
        owner.token
    }

    fun claim(
        context: Context,
        token: String,
        expectedProcessId: Int,
        processId: Int,
    ): Boolean = withGuard(context) {
        val lock = lockFile(context)
        val owner = readOwner(lock) ?: return@withGuard false
        if (owner.token != token) return@withGuard false
        val mayClaim = owner.processId == expectedProcessId || !owner.isAlive(context)
        if (!mayClaim) return@withGuard false
        val claimed = owner.copy(
            processId = processId,
            processStartTicks = processStartTicks(processId) ?: return@withGuard false,
        )
        writeOwner(lock, claimed)
    } ?: false

    fun isActive(context: Context): Boolean = withGuard(context) {
        val lock = lockFile(context)
        val owner = readOwner(lock) ?: return@withGuard false
        if (owner.isAlive(context)) return@withGuard true
        lock.delete()
        false
    } ?: true

    fun release(context: Context, token: String) {
        withGuard(context) {
            val lock = lockFile(context)
            if (readOwner(lock)?.token == token) lock.delete()
        }
    }

    private fun <T> withGuard(context: Context, action: () -> T): T? = runCatching {
        FileChannel.open(
            guardFile(context).toPath(),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        ).use { channel ->
            channel.lock().use { action() }
        }
    }.getOrNull()

    private fun writeOwner(file: File, owner: Owner): Boolean = runCatching {
        val temporary = File(file.parentFile, "${file.name}.${owner.processId}.tmp")
        temporary.writeText(
            "${owner.token}\n${owner.processId}\n${owner.processStartTicks}\n",
            StandardCharsets.UTF_8,
        )
        if (!temporary.renameTo(file)) {
            temporary.delete()
            return@runCatching false
        }
        true
    }.getOrDefault(false)

    private fun readOwner(file: File): Owner? = runCatching {
        val values = file.readLines(StandardCharsets.UTF_8)
        Owner(
            token = values[0],
            processId = values[1].toInt(),
            processStartTicks = values[2].toLong(),
        )
    }.getOrNull()

    private fun Owner.isAlive(context: Context): Boolean =
        processStartTicks(processId) == processStartTicks &&
            processCommand(processId)?.let { command ->
                command == context.packageName || command == "${context.packageName}:dataTransfer"
            } == true

    private fun processStartTicks(processId: Int): Long? = runCatching {
        File("/proc/$processId/stat").readText()
            .substringAfterLast(") ")
            .split(' ')[19]
            .toLong()
    }.getOrNull()

    private fun processCommand(processId: Int): String? = runCatching {
        File("/proc/$processId/cmdline").readText().substringBefore('\u0000')
    }.getOrNull()

    private fun lockFile(context: Context) = File(stateDirectory(context), FILE_NAME)

    private fun guardFile(context: Context) = File(stateDirectory(context), GUARD_FILE_NAME)

    private fun stateDirectory(context: Context) =
        File(context.applicationInfo.dataDir, AppDataArchiveRules.TRANSFER_STATE_DIRECTORY_NAME)
            .also { directory -> directory.mkdirs() }

    private data class Owner(
        val token: String,
        val processId: Int,
        val processStartTicks: Long,
    )

    private const val FILE_NAME = "app_data_transfer.lock"
    private const val GUARD_FILE_NAME = "app_data_transfer.guard"
}
