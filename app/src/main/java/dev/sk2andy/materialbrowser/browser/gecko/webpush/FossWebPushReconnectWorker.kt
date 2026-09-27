package dev.sk2andy.materialbrowser.browser.gecko.webpush

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

internal class FossWebPushReconnectWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = if (
        GeckoWebPushCoordinator.reconnectOnce(applicationContext)
    ) {
        Result.success()
    } else {
        Result.retry()
    }

    companion object {
        private const val WORK_NAME = "candy_foss_web_push_reconnect"

        fun schedule(context: Context, enabled: Boolean) {
            val workManager = WorkManager.getInstance(context.applicationContext)
            if (!enabled) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<FossWebPushReconnectWorker>(
                15,
                TimeUnit.MINUTES,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
