package dev.sk2andy.materialbrowser.browser.integration.devicesearch

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal object DeviceSearchRefreshScheduler {
    private const val WORK_NAME = "device_search_refresh"

    fun update(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<DeviceSearchRefreshWorker>(3L, TimeUnit.HOURS)
            .setInitialDelay(3L, TimeUnit.HOURS)
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

internal class DeviceSearchRefreshWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = suspendCoroutine { continuation ->
        DeviceSearchIndexer.refresh(applicationContext) { success ->
            continuation.resume(if (success) Result.success() else Result.retry())
        }
    }
}
