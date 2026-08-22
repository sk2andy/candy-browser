package dev.sk2andy.materialbrowser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.DocumentsContract
import android.view.Gravity
import android.window.OnBackInvokedDispatcher
import android.widget.TextView
import androidx.webkit.WebViewCompat
import dev.sk2andy.materialbrowser.data.AppDataArchiveCodec
import dev.sk2andy.materialbrowser.data.AppDataArchiveManifest
import dev.sk2andy.materialbrowser.data.AppDataArchiveRestore
import dev.sk2andy.materialbrowser.data.AppDataArchiveRestoreException
import dev.sk2andy.materialbrowser.data.AppDataArchiveRestoreFailure
import dev.sk2andy.materialbrowser.data.AppDataArchiveRestoreResult
import dev.sk2andy.materialbrowser.data.AppDataArchiveRules
import dev.sk2andy.materialbrowser.data.AppDataArchiveStaging
import dev.sk2andy.materialbrowser.data.AppDataTransferLock
import java.io.File
import java.io.FileInputStream
import java.util.UUID

internal object AppDataTransferContract {
    const val RESULT_EXTRA = "app_data_transfer_result"
    const val RESULT_EXPORTED = "exported"
    const val RESULT_IMPORTED = "imported"
    const val RESULT_EXPORT_FAILED = "export_failed"
    const val RESULT_IMPORT_FAILED = "import_failed"
    const val RESULT_IMPORT_RECOVERED = "import_recovered"
    const val STAGING_DIRECTORY_NAME = "app_data_archive_staging"
    const val RESTORE_MARKER_FILE_NAME = "app_data_restore_pending.json"
    const val LOCK_TOKEN_EXTRA = "app_data_transfer_lock_token"

    fun exportIntent(
        context: Context,
        destination: Uri,
        mainProcessId: Int,
        lockToken: String,
    ): Intent =
        Intent(context, AppDataTransferActivity::class.java)
            .setAction(ACTION_EXPORT)
            .setData(destination)
            .putExtra(EXTRA_MAIN_PROCESS_ID, mainProcessId)
            .putExtra(LOCK_TOKEN_EXTRA, lockToken)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

    fun importIntent(
        context: Context,
        stagedFileName: String,
        mainProcessId: Int,
        lockToken: String,
    ): Intent =
        Intent(context, AppDataTransferActivity::class.java)
            .setAction(ACTION_IMPORT)
            .putExtra(EXTRA_STAGED_FILE_NAME, stagedFileName)
            .putExtra(EXTRA_MAIN_PROCESS_ID, mainProcessId)
            .putExtra(LOCK_TOKEN_EXTRA, lockToken)

    fun recoveryIntent(context: Context, mainProcessId: Int, lockToken: String): Intent =
        Intent(context, AppDataTransferActivity::class.java)
            .setAction(ACTION_RECOVER)
            .putExtra(EXTRA_MAIN_PROCESS_ID, mainProcessId)
            .putExtra(LOCK_TOKEN_EXTRA, lockToken)

    internal const val ACTION_EXPORT =
        "dev.sk2andy.materialbrowser.action.EXPORT_APP_DATA"
    internal const val ACTION_IMPORT =
        "dev.sk2andy.materialbrowser.action.IMPORT_APP_DATA"
    internal const val ACTION_RECOVER =
        "dev.sk2andy.materialbrowser.action.RECOVER_APP_DATA_IMPORT"
    internal const val EXTRA_MAIN_PROCESS_ID = "main_process_id"
    internal const val EXTRA_STAGED_FILE_NAME = "staged_file_name"
}

internal fun Context.currentWebViewIdentity(): String? =
    WebViewCompat.getCurrentWebViewPackage(this)?.let { packageInfo ->
        "${packageInfo.packageName}@${packageInfo.versionName}"
    }

class AppDataTransferActivity : Activity() {
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
        ) {}
        var request = requestFrom(intent)
        if (request == null) {
            finishAndRemoveTask()
            return
        }
        val lockToken = intent.getStringExtra(AppDataTransferContract.LOCK_TOKEN_EXTRA)
        if (lockToken == null || !AppDataTransferLock.claim(
                context = this,
                token = lockToken,
                expectedProcessId = request.mainProcessId,
                processId = Process.myPid(),
            )
        ) {
            finishAndRemoveTask()
            return
        }
        if (AppDataArchiveRestore.hasInterruptedRestore(restoreRecoveryMarker())) {
            request = TransferRequest.Recover(request.mainProcessId)
        }
        statusView = TextView(this).apply {
            gravity = Gravity.CENTER
            text = getString(
                when (request) {
                    is TransferRequest.Export -> R.string.data_archive_export_progress
                    is TransferRequest.Import,
                    is TransferRequest.Recover,
                    -> R.string.data_archive_restore_progress
                },
            )
            textSize = 18f
        }
        setContentView(statusView)
        Thread {
            waitForMainActivityToStop()
            val attempt = runCatching {
                stopMainProcess(request.mainProcessId)
                when (request) {
                    is TransferRequest.Export -> export(request.destination)
                    is TransferRequest.Import -> restore(request.stagedFileName)
                    is TransferRequest.Recover -> recoverInterruptedRestore()
                }
            }
            if (attempt.isFailure && request is TransferRequest.Recover) {
                runOnUiThread {
                    statusView.setText(R.string.data_archive_recovery_failed)
                }
                return@Thread
            }
            val result = attempt.fold(
                onSuccess = {
                    when (request) {
                        is TransferRequest.Export -> AppDataTransferContract.RESULT_EXPORTED
                        is TransferRequest.Import -> AppDataTransferContract.RESULT_IMPORTED
                        is TransferRequest.Recover ->
                            AppDataTransferContract.RESULT_IMPORT_RECOVERED
                    }
                },
                onFailure = {
                    when (request) {
                        is TransferRequest.Export ->
                            AppDataTransferContract.RESULT_EXPORT_FAILED
                        is TransferRequest.Import ->
                            AppDataTransferContract.RESULT_IMPORT_FAILED
                        is TransferRequest.Recover ->
                            AppDataTransferContract.RESULT_IMPORT_FAILED
                    }
                },
            )
            AppDataTransferLock.release(this, lockToken)
            runOnUiThread { restartMain(result) }
        }.start()
    }

    private fun export(destination: Uri) {
        try {
            val output = checkNotNull(contentResolver.openOutputStream(destination, "wt"))
            output.use { stream ->
                AppDataArchiveCodec.export(
                    dataDirectory = applicationInfo.dataDir.let(::File).toPath(),
                    manifest = currentManifest(),
                    output = stream,
                )
            }
        } catch (failure: Throwable) {
            runCatching { DocumentsContract.deleteDocument(contentResolver, destination) }
            throw failure
        }
    }

    private fun restore(stagedFileName: String) {
        val stagingDirectory = File(cacheDir, AppDataTransferContract.STAGING_DIRECTORY_NAME)
        val stagedFile = checkNotNull(
            AppDataArchiveStaging.resolve(stagingDirectory, stagedFileName),
        )
        val workDirectory = File(transferStateDirectory(), "restore_${UUID.randomUUID()}")
        val extractedDirectory = File(workDirectory, "extracted")
        val backupDirectory = File(workDirectory, "backup")
        var restoreApplied = false
        var preserveWorkDirectory = false
        try {
            FileInputStream(stagedFile).use { input ->
                AppDataArchiveCodec.extract(input, extractedDirectory.toPath())
            }
            try {
                val restoreResult = AppDataArchiveRestore.replacePersistentData(
                    dataDirectory = File(applicationInfo.dataDir),
                    extractedDataDirectory = extractedDirectory,
                    emptyBackupDirectory = backupDirectory,
                    recoveryMarker = restoreRecoveryMarker(),
                )
                preserveWorkDirectory =
                    restoreResult == AppDataArchiveRestoreResult.CompletedKeepRecoveryData
            } catch (failure: AppDataArchiveRestoreException) {
                preserveWorkDirectory =
                    failure.failure == AppDataArchiveRestoreFailure.RollbackFailed
                throw failure
            }
            restoreApplied = true
            stagedFile.delete()
        } finally {
            if (!preserveWorkDirectory &&
                (restoreApplied || !restoreRecoveryMarker().exists())
            ) {
                workDirectory.deleteRecursively()
            }
        }
    }

    private fun recoverInterruptedRestore() {
        check(
            AppDataArchiveRestore.recoverInterruptedRestore(
                dataDirectory = File(applicationInfo.dataDir),
                recoveryMarker = restoreRecoveryMarker(),
            ),
        )
    }

    private fun restoreRecoveryMarker() =
        File(transferStateDirectory(), AppDataTransferContract.RESTORE_MARKER_FILE_NAME)

    private fun transferStateDirectory() =
        File(applicationInfo.dataDir, AppDataArchiveRules.TRANSFER_STATE_DIRECTORY_NAME)
            .also { directory -> directory.mkdirs() }

    private fun currentManifest(): AppDataArchiveManifest = AppDataArchiveManifest(
        packageName = packageName,
        appVersionName = BuildConfig.VERSION_NAME,
        appVersionCode = BuildConfig.VERSION_CODE.toLong(),
        webViewVersion = currentWebViewIdentity(),
        sdkInt = Build.VERSION.SDK_INT,
        exportedAtEpochMillis = System.currentTimeMillis(),
    )

    private fun requestFrom(intent: Intent): TransferRequest? {
        val processId = intent.getIntExtra(AppDataTransferContract.EXTRA_MAIN_PROCESS_ID, -1)
        return when (intent.action) {
            AppDataTransferContract.ACTION_EXPORT -> intent.data?.let { destination ->
                TransferRequest.Export(destination, processId)
            }

            AppDataTransferContract.ACTION_IMPORT ->
                intent.getStringExtra(AppDataTransferContract.EXTRA_STAGED_FILE_NAME)
                    ?.let { fileName -> TransferRequest.Import(fileName, processId) }

            AppDataTransferContract.ACTION_RECOVER -> TransferRequest.Recover(processId)

            else -> null
        }
    }

    private fun restartMain(result: String) {
        startActivity(
            Intent.makeRestartActivityTask(componentNameForMain()).putExtra(
                AppDataTransferContract.RESULT_EXTRA,
                result,
            ),
        )
        finishAndRemoveTask()
    }

    private fun componentNameForMain() = android.content.ComponentName(this, MainActivity::class.java)

    private fun waitForMainActivityToStop() {
        try {
            Thread.sleep(MAIN_ACTIVITY_STOP_GRACE_MILLIS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun stopMainProcess(processId: Int) {
        if (processId <= 0 || processId == Process.myPid()) return
        Process.killProcess(processId)
        val deadlineNanos = System.nanoTime() + MAIN_PROCESS_EXIT_TIMEOUT_NANOS
        while (File("/proc/$processId").exists()) {
            check(System.nanoTime() < deadlineNanos) { "Main process did not stop" }
            try {
                Thread.sleep(MAIN_PROCESS_EXIT_POLL_MILLIS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                error("Interrupted while stopping main process")
            }
        }
    }

    private sealed interface TransferRequest {
        val mainProcessId: Int

        data class Export(
            val destination: Uri,
            override val mainProcessId: Int,
        ) : TransferRequest

        data class Import(
            val stagedFileName: String,
            override val mainProcessId: Int,
        ) : TransferRequest

        data class Recover(
            override val mainProcessId: Int,
        ) : TransferRequest
    }

    private companion object {
        const val MAIN_ACTIVITY_STOP_GRACE_MILLIS = 500L
        const val MAIN_PROCESS_EXIT_POLL_MILLIS = 25L
        const val MAIN_PROCESS_EXIT_TIMEOUT_NANOS = 5_000_000_000L
    }
}
