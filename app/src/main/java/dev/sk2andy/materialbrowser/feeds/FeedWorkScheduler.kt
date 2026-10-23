package dev.sk2andy.materialbrowser.feeds

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.ProfileProtectionSession
import dev.sk2andy.materialbrowser.data.AppDataTransferLock
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object FeedWorkScheduler {
    const val PROFILE = "feeds.profile"
    const val DOWNLOADS = "feeds.downloads"

    fun update(context: Context, profileId: String, state: FeedState) {
        val preferences = context.getSharedPreferences("feeds_notifications", Context.MODE_PRIVATE)
        val activationKey = "$profileId.digest_since"
        if (state.settings.digestEnabled && !preferences.contains(activationKey)) {
            preferences.edit().putLong(activationKey, System.currentTimeMillis()).apply()
        } else if (!state.settings.digestEnabled) {
            preferences.edit().remove(activationKey).apply()
        }
        val manager = WorkManager.getInstance(context)
        val name = "feeds-refresh-$profileId"
        val enabled =
            state.settings.backgroundRefresh ||
                state.settings.digestEnabled ||
                state.sources.any { it.notificationsEnabled } ||
                state.rules.any { it.enabled && it.action == FeedRuleAction.Notify }
        if (!enabled) {
            manager.cancelUniqueWork(name)
            return
        }
        manager.enqueueUniquePeriodicWork(
            name,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<FeedRefreshWorker>(1, TimeUnit.HOURS)
                .setInputData(workDataOf(PROFILE to profileId))
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build(),
        )
    }

    fun enqueueDownloads(context: Context, profileId: String) {
        val settings = FeedRepository.get(context, profileId).state.value.settings
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "feeds-downloads-$profileId",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<FeedRefreshWorker>()
                    .setInputData(workDataOf(PROFILE to profileId, DOWNLOADS to true))
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(
                                if (settings.wifiOnly) NetworkType.UNMETERED
                                else NetworkType.CONNECTED
                            )
                            .build()
                    )
                    .build(),
            )
    }
}

/** Best-effort scheduling: Android may defer delivery; no exact-alarm permission is needed. */
class FeedRefreshWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val profileId = inputData.getString(FeedWorkScheduler.PROFILE) ?: return Result.failure()
        if (AppDataTransferLock.isActive(applicationContext)) return Result.retry()
        val profile =
            BrowserSessionStore(applicationContext).loadProfiles().first.firstOrNull {
                it.id == profileId
            } ?: return Result.success()
        if (!ProfileProtectionSession.isAccessible(profile)) {
            return if (inputData.getBoolean(FeedWorkScheduler.DOWNLOADS, false)) Result.retry()
            else Result.success()
        }
        val repository = FeedRepository.get(applicationContext, profileId)
        if (repository.load().isFailure) return Result.retry()
        if (inputData.getBoolean(FeedWorkScheduler.DOWNLOADS, false)) {
            val loader = FeedArticleLoader(applicationContext, repository)
            for (article in
                repository.state.value.articles.filter {
                    it.offlineStatus == FeedOfflineStatus.Waiting
                }) {
                if (loader.download(article).isFailure) return Result.retry()
            }
            return if (
                repository.state.value.articles.any {
                    it.offlineStatus == FeedOfflineStatus.Waiting
                }
            )
                Result.retry()
            else Result.success()
        }
        val before = repository.state.value
        if (
            !before.settings.backgroundRefresh &&
                !before.settings.digestEnabled &&
                before.sources.none { it.notificationsEnabled } &&
                before.rules.none { it.enabled && it.action == FeedRuleAction.Notify }
        )
            return Result.success()
        if (repository.refresh().isFailure) return Result.retry()
        try {
            repository.requireAccess()
        } catch (_: IllegalStateException) {
            return Result.success()
        }
        notifyIfDue(repository)
        return Result.success()
    }

    private fun notifyIfDue(repository: FeedRepository) {
        val state = repository.state.value
        val now = ZonedDateTime.now()
        if (
            FeedNotificationRules.quiet(
                now.hour,
                state.settings.quietStartHour,
                state.settings.quietEndHour,
            )
        )
            return
        val preferences =
            applicationContext.getSharedPreferences("feeds_notifications", Context.MODE_PRIVATE)
        val key = repository.profileId
        val last = preferences.getLong("$key.last", 0L)
        val digestDay =
            if (state.settings.digestEnabled)
                FeedNotificationRules.digestDay(
                    now,
                    state.settings.digestHour,
                    preferences.getLong("$key.digest_since", 0L),
                    preferences.getString("$key.day", null),
                )
            else null
        val digestDue = digestDay != null
        val articles =
            state.articles.filter { article ->
                (article.origin == FeedArticleOrigin.Feed || article.sourceIds.isNotEmpty()) &&
                    !article.isRead &&
                    !article.isHidden &&
                    article.receivedAtMillis > last &&
                    (digestDue ||
                        (!state.settings.digestEnabled &&
                            (state.sources.any {
                                it.id in article.sourceIds && it.notificationsEnabled
                            } ||
                                state.rules.any {
                                    it.enabled &&
                                        it.action == FeedRuleAction.Notify &&
                                        FeedRules.ruleMatches(article, it, state)
                                })))
            }
        if (articles.isEmpty()) return
        if (
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
        )
            return
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return
        applicationContext
            .getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    applicationContext.getString(R.string.feeds_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        val intent =
            Intent(applicationContext, MainActivity::class.java)
                .setAction(FeedLaunchContract.ACTION_OPEN)
                .putExtra(FeedLaunchContract.EXTRA_PROFILE, key)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending =
            PendingIntent.getActivity(
                applicationContext,
                key.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        // Counts only: a notification never retains article titles after its owning profile locks.
        val notification =
            NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_rss_feed)
                .setContentTitle(applicationContext.getString(R.string.feeds_notification_channel))
                .setContentText(
                    applicationContext.getString(R.string.feeds_notification_count, articles.size)
                )
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
        try {
            manager.notify("feeds-$key", 1, notification)
            preferences
                .edit()
                .putLong("$key.last", System.currentTimeMillis())
                .apply { if (digestDay != null) putString("$key.day", digestDay) }
                .apply()
        } catch (_: SecurityException) {
            // Permission can be revoked between checking and posting. Keep the cursor unchanged.
        }
    }

    companion object {
        private const val CHANNEL = "candy-feeds"
    }
}

object FeedNotificationRules {
    fun digestDay(
        now: ZonedDateTime,
        targetHour: Int,
        enabledSinceMillis: Long,
        lastTargetDay: String?,
    ): String? {
        val todayTarget =
            now.withHour(targetHour.coerceIn(0, 23)).withMinute(0).withSecond(0).withNano(0)
        val target = if (now.isBefore(todayTarget)) todayTarget.minusDays(1) else todayTarget
        val day = target.toLocalDate().toString()
        return day.takeIf {
            it != lastTargetDay && target.toInstant().toEpochMilli() >= enabledSinceMillis
        }
    }

    fun quiet(hour: Int, start: Int, end: Int): Boolean =
        when {
            start == end -> false
            start < end -> hour in start until end
            else -> hour >= start || hour < end
        }
}
